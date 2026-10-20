package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class NexusMapDiskSourceVerifierTest {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void validCompressedAndPlainSourcesMatchWithoutChangingBytes() throws Exception {
        var f = fixture(); var first = f.verify();
        assertEquals(f.expected.intent().ticket(), first.ticket()); assertEquals(31, first.mapId());
        assertTrue(first.nativeFileHash().matches("[a-f0-9]{64}"));
        var beforeNative = Files.readAllBytes(f.nativeFile); var beforeDetail = Files.readAllBytes(f.detailFile);
        assertEquals(first, f.verify()); assertArrayEquals(beforeNative, Files.readAllBytes(f.nativeFile));
        assertArrayEquals(beforeDetail, Files.readAllBytes(f.detailFile));
        write(f.nativeFile, f.nativeData(), false); write(f.detailFile, f.detailData(), false);
        var plain = f.verify(); assertEquals(first.nativeTerrainHash(), plain.nativeTerrainHash());
        assertEquals(first.detailTerrainHash(), plain.detailTerrainHash());
        assertNotEquals(first.nativeFileHash(), plain.nativeFileHash());
    }
    @Test void absentSourcesRefuseRatherThanBootstrap() throws Exception {
        var f = fixture(); Files.delete(f.nativeFile);
        assertThrows(IOException.class, f::verify); assertFalse(Files.exists(f.nativeFile));
        write(f.nativeFile, f.nativeData(), true); Files.delete(f.detailFile);
        assertThrows(IOException.class, f::verify); assertFalse(Files.exists(f.detailFile));
    }
    @Test void malformedAndTruncatedSourcesArePreserved() throws Exception {
        var f = fixture(); byte[] bad = {1, 2, 3}; Files.write(f.nativeFile, bad);
        assertThrows(IOException.class, f::verify); assertArrayEquals(bad, Files.readAllBytes(f.nativeFile));
        write(f.nativeFile, f.nativeData(), true);
        byte[] truncated = Arrays.copyOf(Files.readAllBytes(f.detailFile), 12); Files.write(f.detailFile, truncated);
        assertThrows(IOException.class, f::verify); assertArrayEquals(truncated, Files.readAllBytes(f.detailFile));
    }
    @Test void futureVersionsNeverDecodeAsCurrentSources() throws Exception {
        var f = fixture(); var nativeTag = f.nativeData();
        write(f.nativeFile, nativeTag, true, SharedConstants.getCurrentVersion().dataVersion().version() + 1);
        assertThrows(IOException.class, f::verify);
        write(f.nativeFile, nativeTag, true); var detail = f.detailData(); detail.putInt("version", 2);
        write(f.detailFile, detail, true); assertThrows(IOException.class, f::verify);
    }
    @Test void nativePixelLengthAndScaleCannotBeSilentlyNormalized() throws Exception {
        var f = fixture();
        for (int length : new int[] {0, 16383, 16385}) {
            var data = f.nativeData(); data.putByteArray("colors", new byte[length]); write(f.nativeFile, data, true);
            assertThrows(IOException.class, f::verify);
        }
        var data = f.nativeData(); data.putByte("scale", (byte) 5); write(f.nativeFile, data, true);
        assertThrows(IOException.class, f::verify);
    }
    @Test void partialOrDuplicateDetailRowsNeverDisappearDuringVerification() throws Exception {
        var f = fixture(); var detail = f.detailData();
        var bad = new CompoundTag(); bad.putString("map_id", "invalid"); detail.getListOrEmpty("maps").add(bad);
        write(f.detailFile, detail, true); assertThrows(IOException.class, f::verify);
        detail = f.detailData(); detail.getListOrEmpty("pages").add(detail.getListOrEmpty("pages").getFirst().copy());
        write(f.detailFile, detail, true); assertThrows(IOException.class, f::verify);
    }
    @Test void changedPixelsAndOrphanPagesInvalidateExpectedSnapshot() throws Exception {
        var f = fixture(); var nativeTag = f.nativeData(); var colors = new byte[16384]; colors[0] = 8;
        nativeTag.putByteArray("colors", colors); write(f.nativeFile, nativeTag, true);
        assertThrows(IOException.class, f::verify); write(f.nativeFile, f.nativeData(), true);
        var detail = f.detailData(); var orphan = page(2); detail.getListOrEmpty("pages").add(orphan);
        write(f.detailFile, detail, true); assertThrows(IOException.class, f::verify);
    }
    @Test void filesystemKindsAndQuotasRefuseWithoutReplacement() throws Exception {
        var f = fixture(); Files.delete(f.nativeFile); Files.createDirectories(f.nativeFile);
        assertThrows(IOException.class, f::verify); Files.delete(f.nativeFile);
        Files.createSymbolicLink(f.nativeFile, directory.resolve("missing-target"));
        assertThrows(IOException.class, f::verify); assertTrue(Files.isSymbolicLink(f.nativeFile));
        Files.delete(f.nativeFile); Files.write(f.nativeFile, new byte[(int) NexusMapDiskSourceVerifier.NATIVE_LIMIT + 1]);
        assertThrows(IOException.class, f::verify); assertEquals(NexusMapDiskSourceVerifier.NATIVE_LIMIT + 1, Files.size(f.nativeFile));
    }
    @Test void changesOrDisappearanceAfterDecodeCannotBeAcknowledged() throws Exception {
        var f = fixture();
        assertThrows(IOException.class, () -> f.verify(() -> {
            var data = f.nativeData(); data.putString("probe", "semantic pixels unchanged but file changed"); write(f.nativeFile, data, true);
        }));
        write(f.nativeFile, f.nativeData(), true);
        assertThrows(IOException.class, () -> f.verify(() -> Files.delete(f.detailFile)));
        assertFalse(Files.exists(f.detailFile));
    }
    @Test void decodeBytesCannotBeReplacedThenRestoredUnderAnotherHash() throws Exception {
        var f = fixture(); var a = f.nativeData(); var colors = new byte[16384]; colors[0] = 8; a.putByteArray("colors", colors);
        write(f.nativeFile, a, true);
        assertThrows(IOException.class, () -> NexusMapDiskSourceVerifier.verify(f.nativeSource(), f.detailSource(),
                REGISTRIES, f.expected, () -> true,
                () -> write(f.nativeFile, f.nativeData(), true),
                () -> write(f.nativeFile, a, true)));
    }
    @Test void cancellationAndBadGzipFooterNeverProduceObservation() throws Exception {
        var f = fixture(); var alive = new AtomicBoolean(false);
        assertThrows(CancellationException.class, () -> NexusMapDiskSourceVerifier.verify(f.nativeSource(), f.detailSource(),
                REGISTRIES, f.expected, alive::get));
        alive.set(true);
        assertThrows(CancellationException.class, () -> NexusMapDiskSourceVerifier.verify(f.nativeSource(), f.detailSource(),
                REGISTRIES, f.expected, alive::get, () -> alive.set(false)));
        byte[] bytes = Files.readAllBytes(f.nativeFile); bytes[bytes.length - 1] ^= 1; Files.write(f.nativeFile, bytes);
        assertThrows(IOException.class, f::verify);
    }

    @Test void coldNativeObservationRequiresNeitherTicketNorDetailAndKeepsImmutablePixels() throws Exception {
        var f = fixture(); Files.delete(f.detailFile);
        var observed = NexusMapDiskSourceVerifier.observeNative(f.nativeSource(), new MapId(31), REGISTRIES, () -> true);
        assertEquals(31, observed.base().mapId); assertEquals(4, observed.base().colors().get(0));
        assertEquals(NexusMapDiskSourceVerifier.baseHash(observed.base()), observed.terrainHash());
        f.map.colors[0] = 12; assertEquals(4, observed.base().colors().get(0));
        assertThrows(java.nio.ReadOnlyBufferException.class, () -> observed.base().colors().put(0, (byte) 8));
        assertFalse(Files.exists(f.detailFile));
    }
    @Test void coldNativeCompressedPlainAndCallerAssignedIdAreExplicit() throws Exception {
        var f = fixture(); var first = observe(f); write(f.nativeFile, f.nativeData(), false); var plain = observe(f);
        assertEquals(first.terrainHash(), plain.terrainHash()); assertNotEquals(first.fileHash(), plain.fileHash());
        var anotherId = NexusMapDiskSourceVerifier.observeNative(f.nativeSource(), new MapId(32), REGISTRIES, () -> true);
        assertEquals(plain.fileHash(), anotherId.fileHash()); assertNotEquals(plain.terrainHash(), anotherId.terrainHash());
        // Map files do not contain their MapId; a controller must supply the canonical inventory descriptor.
        assertThrows(IllegalArgumentException.class, () -> NexusMapDiskSourceVerifier.observeNative(f.nativeSource(), null, REGISTRIES, () -> true));
        assertThrows(IllegalArgumentException.class, () -> NexusMapDiskSourceVerifier.observeNative(f.nativeSource(), new MapId(-1), REGISTRIES, () -> true));
    }
    @Test void coldNativeMissingCorruptFutureAndNonRegularFilesNeverBecomeEmpty() throws Exception {
        var f = fixture(); Files.delete(f.nativeFile); assertThrows(IOException.class, () -> observe(f)); assertFalse(Files.exists(f.nativeFile));
        Files.write(f.nativeFile, new byte[]{1, 2, 3}); assertThrows(IOException.class, () -> observe(f));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(f.nativeFile));
        write(f.nativeFile, f.nativeData(), true, SharedConstants.getCurrentVersion().dataVersion().version() + 1);
        assertThrows(IOException.class, () -> observe(f)); Files.delete(f.nativeFile); Files.createDirectory(f.nativeFile);
        assertThrows(IOException.class, () -> observe(f)); Files.delete(f.nativeFile);
        Files.createSymbolicLink(f.nativeFile, directory.resolve("absent")); assertThrows(IOException.class, () -> observe(f));
    }
    @Test void coldNativePixelScaleCodecAndFiniteCenterChecksRefuse() throws Exception {
        var f = fixture();
        for (int size : new int[]{0, 16383, 16385}) {
            var data = f.nativeData(); data.putByteArray("colors", new byte[size]); write(f.nativeFile, data, true);
            assertThrows(IOException.class, () -> observe(f));
        }
        var data = f.nativeData(); data.putByte("scale", (byte) 5); write(f.nativeFile, data, true); assertThrows(IOException.class, () -> observe(f));
        data = f.nativeData(); data.putString("dimension", "INVALID DIMENSION"); write(f.nativeFile, data, true); assertThrows(IOException.class, () -> observe(f));
        data = f.nativeData(); data.putInt("xCenter", Integer.MAX_VALUE); write(f.nativeFile, data, true); assertThrows(IOException.class, () -> observe(f));
        data = f.nativeData(); data.putInt("zCenter", Integer.MIN_VALUE); write(f.nativeFile, data, true); assertThrows(IOException.class, () -> observe(f));
    }
    @Test void coldNativeQuotaAndGzipTrailerAreStrict() throws Exception {
        var f = fixture(); byte[] bad = Files.readAllBytes(f.nativeFile); bad[bad.length - 1] ^= 1; Files.write(f.nativeFile, bad);
        assertThrows(IOException.class, () -> observe(f)); assertArrayEquals(bad, Files.readAllBytes(f.nativeFile));
        try (var file = new java.io.RandomAccessFile(f.nativeFile.toFile(), "rw")) { file.setLength(NexusMapDiskSourceVerifier.NATIVE_LIMIT + 1); }
        assertThrows(IOException.class, () -> observe(f));
    }
    @Test void coldNativeChangedOrDeletedAfterDecodeRefuses() throws Exception {
        var f = fixture();
        assertThrows(IOException.class, () -> NexusMapDiskSourceVerifier.observeNative(f.nativeSource(), new MapId(31), REGISTRIES, () -> true,
                () -> { }, () -> { var data = f.nativeData(); data.putString("probe", "changed bytes"); write(f.nativeFile, data, true); }));
        assertThrows(IOException.class, () -> NexusMapDiskSourceVerifier.observeNative(f.nativeSource(), new MapId(31), REGISTRIES, () -> true,
                () -> { }, () -> Files.delete(f.nativeFile)));
    }
    @Test void coldNativeDecodedStreamCannotBeSwappedAndRestoredUnderAnotherHash() throws Exception {
        var f = fixture(); var other = f.nativeData(); byte[] colors = new byte[16384]; colors[0] = 8; other.putByteArray("colors", colors);
        write(f.nativeFile, other, true);
        assertThrows(IOException.class, () -> NexusMapDiskSourceVerifier.observeNative(f.nativeSource(), new MapId(31), REGISTRIES, () -> true,
                () -> write(f.nativeFile, f.nativeData(), true), () -> write(f.nativeFile, other, true)));
    }
    @Test void coldNativeCancellationCannotReturnObservation() throws Exception {
        var f = fixture(); var alive = new AtomicBoolean(false);
        assertThrows(CancellationException.class, () -> NexusMapDiskSourceVerifier.observeNative(f.nativeSource(), new MapId(31), REGISTRIES, alive::get));
        alive.set(true);
        assertThrows(CancellationException.class, () -> NexusMapDiskSourceVerifier.observeNative(f.nativeSource(), new MapId(31), REGISTRIES, alive::get,
                () -> { }, () -> alive.set(false)));
    }
    private static NexusMapDiskSourceVerifier.NativeObservation observe(Fixture f) throws IOException {
        return NexusMapDiskSourceVerifier.observeNative(f.nativeSource(), new MapId(31), REGISTRIES, () -> true);
    }
    private Fixture fixture() throws Exception {
        var f = new Fixture(directory.resolve("map.dat"), directory.resolve("detail.dat"));
        write(f.nativeFile, f.nativeData(), true); write(f.detailFile, f.detailData(), true); return f;
    }
    private static final class Fixture {
        final Path nativeFile, detailFile;
        final MapItemSavedData map = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD);
        final NexusMapSourceSnapshot expected;
        Fixture(Path nativeFile, Path detailFile) {
            this.nativeFile = nativeFile; this.detailFile = detailFile; map.colors[0] = 4;
            var id = new MapId(31); var ledger = new NexusMapBindingSavedData(); var unit = UUID.randomUUID();
            assertTrue(ledger.bind(id, unit, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), map));
            byte[] pixels = new byte[16384]; pixels[0] = 4;
            var detail = new NexusLegacyTerrainSnapshot(Map.of(1, new NexusLegacyTerrainSnapshot.Page(1, 64, 64, 0, false, pixels)),
                    Map.of(31, List.of(1)));
            expected = new NexusMapSourceSnapshot(ledger.prepareSparseImport(id, unit, map).orElseThrow(),
                    new NexusMapSourceSnapshot.Base(id, map), detail);
        }
        CompoundTag nativeData() { return (CompoundTag) MapItemSavedData.CODEC.encodeStart(NbtOps.INSTANCE, map).getOrThrow(); }
        CompoundTag detailData() {
            var data = new CompoundTag(); data.putInt("version", 1); var pages = new ListTag(); pages.add(page(1)); data.put("pages", pages);
            var index = new CompoundTag(); index.putInt("map_id", 31); var refs = new ListTag(); refs.add(IntTag.valueOf(1)); index.put("pages", refs);
            var maps = new ListTag(); maps.add(index); data.put("maps", maps); return data;
        }
        NexusMapDiskSourceVerifier.Source nativeSource() { return new NexusMapDiskSourceVerifier.Source(nativeFile, DataFixTypes.SAVED_DATA_MAP_DATA, DataFixers.getDataFixer()); }
        NexusMapDiskSourceVerifier.Source detailSource() { return new NexusMapDiskSourceVerifier.Source(detailFile, DataFixTypes.SAVED_DATA_COMMAND_STORAGE, DataFixers.getDataFixer()); }
        NexusMapDiskSourceVerifier.Observation verify() throws IOException { return verify(() -> { }); }
        NexusMapDiskSourceVerifier.Observation verify(NexusMapDiskSourceVerifier.AfterDecode hook) throws IOException {
            return NexusMapDiskSourceVerifier.verify(nativeSource(), detailSource(), REGISTRIES, expected, () -> true, hook);
        }
    }
    private static CompoundTag page(int id) {
        var p = new CompoundTag(); p.putInt("id", id); p.putInt("x", 64); p.putInt("z", 64); p.putInt("scale", 0); p.putBoolean("historical", false);
        byte[] colors = new byte[16384]; colors[0] = 4; p.putByteArray("colors", colors); return p;
    }
    private static void write(Path file, CompoundTag data, boolean compressed) throws IOException {
        write(file, data, compressed, SharedConstants.getCurrentVersion().dataVersion().version());
    }
    private static void write(Path file, CompoundTag data, boolean compressed, int version) throws IOException {
        var tag = new CompoundTag(); tag.putInt("DataVersion", version); tag.put("data", data);
        if (compressed) NbtIo.writeCompressed(tag, file);
        else try (var out = new DataOutputStream(Files.newOutputStream(file))) { NbtIo.write(tag, out); }
    }
}
