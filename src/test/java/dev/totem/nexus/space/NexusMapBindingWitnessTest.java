package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class NexusMapBindingWitnessTest {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000941");
    @TempDir Path directory;

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void semanticSnapshotIgnoresBindingInsertionOrderButIncludesImportTicket() {
        var first = ledger(0, 16);
        var reverse = ledger(16, 0);
        assertEquals(NexusMapBindingWitness.capture(first), NexusMapBindingWitness.capture(reverse));
        first.prepareSparseImport(new MapId(0), UNIT, map()).orElseThrow();
        assertNotEquals(NexusMapBindingWitness.capture(first), NexusMapBindingWitness.capture(reverse));
    }

    @Test
    void establishmentAndExactRetryPreserveCanonicalAndMarkerBytes() throws Exception {
        var source = ledger(941);
        source.prepareSparseImport(new MapId(941), UNIT, map()).orElseThrow();
        var canonical = write(source);
        var original = Files.readAllBytes(canonical);
        try (var reader = reader()) {
            var expected = NexusMapBindingWitness.capture(source);
            NexusMapBindingWitness.establish(reader, canonical, REGISTRIES, expected);
            assertTrue(NexusMapBindingWitness.present(canonical));
            var marker = Files.readAllBytes(NexusMapBindingWitness.path(canonical));
            assertEquals(8, marker.length);
            NexusMapBindingWitness.establish(reader, canonical, REGISTRIES, expected);
            assertArrayEquals(marker, Files.readAllBytes(NexusMapBindingWitness.path(canonical)));
            assertArrayEquals(original, Files.readAllBytes(canonical));
        }
    }

    @Test
    void missingOrDifferentSnapshotCannotEstablishWitness() throws Exception {
        var canonical = canonical();
        try (var reader = reader()) {
            var expected = NexusMapBindingWitness.capture(ledger(941));
            assertThrows(IOException.class, () -> NexusMapBindingWitness.establish(reader, canonical, REGISTRIES, expected));
            write(ledger(942));
            var original = Files.readAllBytes(canonical);
            assertThrows(IOException.class, () -> NexusMapBindingWitness.establish(reader, canonical, REGISTRIES, expected));
            assertFalse(NexusMapBindingWitness.present(canonical));
            assertArrayEquals(original, Files.readAllBytes(canonical));
        }
    }

    @Test
    void witnessMakesMissingCanonicalARefusalInsteadOfAbsence() throws Exception {
        var source = ledger(941);
        var canonical = write(source);
        try (var reader = reader()) {
            NexusMapBindingWitness.establish(reader, canonical, REGISTRIES, NexusMapBindingWitness.capture(source));
        }
        Files.delete(canonical);
        try (var reopened = reader()) {
            assertThrows(IllegalStateException.class, () -> NexusMapBindingStorage.readStrict(
                    reopened, NexusMapBindingSavedData.TYPE, canonical, REGISTRIES));
            assertTrue(Files.notExists(canonical));
        }
    }

    @Test
    void corruptPartialOrFutureMarkerIsNeverReplaced() throws Exception {
        var source = ledger(941);
        var canonical = write(source);
        var original = Files.readAllBytes(canonical);
        byte[][] invalid = {new byte[0], new byte[7], new byte[9], new byte[8],
                ByteBuffer.allocate(8).putInt(0x4e584257).putInt(2).array()};
        try (var reader = reader()) {
            for (byte[] bytes : invalid) {
                Files.write(NexusMapBindingWitness.path(canonical), bytes);
                assertThrows(IOException.class, () -> NexusMapBindingWitness.present(canonical));
                assertThrows(IllegalStateException.class, () -> NexusMapBindingWitness.establish(
                        reader, canonical, REGISTRIES, NexusMapBindingWitness.capture(source)));
                assertArrayEquals(bytes, Files.readAllBytes(NexusMapBindingWitness.path(canonical)));
                assertArrayEquals(original, Files.readAllBytes(canonical));
            }
        }
    }

    @Test
    void markerDirectoryAndDanglingLinkCannotLookAbsent() throws Exception {
        var canonical = write(ledger(941));
        var marker = NexusMapBindingWitness.path(canonical);
        Files.createDirectory(marker);
        assertThrows(IOException.class, () -> NexusMapBindingWitness.present(canonical));
        Files.delete(marker);
        Files.createSymbolicLink(marker, directory.resolve("missing-target"));
        assertThrows(IOException.class, () -> NexusMapBindingWitness.present(canonical));
        assertTrue(Files.isSymbolicLink(marker));
    }

    @Test
    void postCreateForceFailureIsUncertainAndRetryConfirmsSameMarker() throws Exception {
        var source = ledger(941);
        var canonical = write(source);
        var expected = NexusMapBindingWitness.capture(source);
        try (var reader = reader()) {
            assertThrows(NexusMapBindingWitness.UncertainCommit.class, () -> NexusMapBindingWitness.establish(
                    reader, canonical, REGISTRIES, expected, () -> { throw new IOException("injected directory-force failure"); }));
            assertTrue(NexusMapBindingWitness.present(canonical));
            var marker = Files.readAllBytes(NexusMapBindingWitness.path(canonical));
            NexusMapBindingWitness.establish(reader, canonical, REGISTRIES, expected);
            assertArrayEquals(marker, Files.readAllBytes(NexusMapBindingWitness.path(canonical)));
        }
    }

    @Test
    void snapshotChangeBeforeAcknowledgmentCannotPassBarrier() throws Exception {
        var source = ledger(941);
        var canonical = write(source);
        var expected = NexusMapBindingWitness.capture(source);
        try (var reader = reader()) {
            assertThrows(NexusMapBindingWitness.UncertainCommit.class, () -> NexusMapBindingWitness.establish(
                    reader, canonical, REGISTRIES, expected, () -> write(ledger(942))));
            assertTrue(NexusMapBindingWitness.present(canonical));
            assertThrows(IOException.class, () -> NexusMapBindingWitness.establish(reader, canonical, REGISTRIES, expected));
            var disk = NexusMapBindingStorage.readStrict(reader, NexusMapBindingSavedData.TYPE, canonical, REGISTRIES);
            assertTrue(disk.get(new MapId(942)).isPresent());
            assertTrue(disk.get(new MapId(941)).isEmpty());
        }
    }

    private Path canonical() { return NexusMapBindingSavedData.TYPE.id().withSuffix(".dat").resolveAgainst(directory); }
    private SavedDataStorage reader() { return new SavedDataStorage(directory, DataFixers.getDataFixer(), REGISTRIES); }
    private Path write(NexusMapBindingSavedData ledger) throws IOException {
        var file = canonical();
        var envelope = new CompoundTag();
        envelope.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        envelope.put("data", NexusMapBindingSavedData.CODEC.encodeStart(NbtOps.INSTANCE, ledger).getOrThrow());
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(envelope, file);
        return file;
    }
    private static MapItemSavedData map() {
        return MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD);
    }
    private static NexusMapBindingSavedData ledger(int... ids) {
        var ledger = new NexusMapBindingSavedData();
        for (int id : ids) assertTrue(ledger.bind(new MapId(id), UNIT, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), map()));
        return ledger;
    }
}
