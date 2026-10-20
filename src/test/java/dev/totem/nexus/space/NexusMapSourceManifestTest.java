package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.totem.nexus.space.NexusMapSourceManifest.*;
import static org.junit.jupiter.api.Assertions.*;

class NexusMapSourceManifestTest {
    @TempDir Path directory;
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-000000000031");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000021");
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static String h(int n) { return String.format("%064x", n); }
    private static NexusMapBindingSavedData.Entry source() {
        return new NexusMapBindingSavedData.Entry(31, UNIT, GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 2)),
                128, 256, List.of(12, 13), 2);
    }
    private static Manifest manifest() { return capture(new NexusMapBindingWitness.Expected(h(1)),
            new NexusMapBindingSavedData.SparseImportIntent(TICKET, source(), 2, false), observation(TICKET, 31)); }
    private static NexusMapDiskSourceVerifier.Observation observation(UUID ticket, int mapId) {
        return new NexusMapDiskSourceVerifier.Observation(ticket, mapId, h(3), h(4), h(5), h(6));
    }
    private void publish(Manifest manifest) throws IOException { NexusMapSourceManifest.publish(directory, manifest, () -> true); }
    private byte[] bytes() throws IOException { return Files.readAllBytes(path(directory, TICKET)); }

    @Test void durableRoundTripExactRetryAndColdRead() throws Exception {
        assertTrue(read(directory, TICKET).isEmpty());
        assertEquals(0, directory.toFile().list().length);
        Manifest expected = manifest(); var stages = new ArrayList<Stage>();
        NexusMapSourceManifest.publish(directory, expected, () -> true, stages::add);
        assertEquals(List.of(Stage.CREATED, Stage.WRITTEN, Stage.FILE_FORCED, Stage.DIRECTORY_FORCED), stages);
        assertEquals(expected, read(directory, TICKET).orElseThrow());
        byte[] before = bytes(); assertEquals(BYTES, before.length);
        stages.clear(); NexusMapSourceManifest.publish(directory, expected, () -> true, stages::add);
        assertEquals(List.of(Stage.FILE_FORCED, Stage.DIRECTORY_FORCED), stages);
        assertArrayEquals(before, bytes());
        assertEquals(expected, read(Path.of(directory.toString()), TICKET).orElseThrow());
    }
    @Test void allObservationAndMetadataFieldsMustMatchOnRetry() throws Exception {
        var expected = manifest(); publish(expected); byte[] before = bytes();
        for (int field = 0; field < 7; field++) {
            String[] values = {expected.ledgerHash(), expected.intentHash(), expected.nativeFileHash(),
                    expected.detailFileHash(), expected.nativeTerrainHash(), expected.detailTerrainHash()};
            int mapId = expected.mapId();
            if (field == 6) mapId++; else values[field] = h(99);
            var conflict = new Manifest(TICKET, mapId, values[0], values[1], values[2], values[3], values[4], values[5]);
            assertThrows(IOException.class, () -> publish(conflict)); assertArrayEquals(before, bytes());
        }
    }
    @Test void captureBindsEveryIntentFieldAndOrderedAncestors() {
        var s = source(); var expected = manifest();
        var alternatives = List.of(
                new NexusMapBindingSavedData.Entry(32, UNIT, s.anchor(), 128, 256, s.detailAncestors(), 2),
                new NexusMapBindingSavedData.Entry(31, UUID.randomUUID(), s.anchor(), 128, 256, s.detailAncestors(), 2),
                new NexusMapBindingSavedData.Entry(31, UNIT, GlobalPos.of(Level.NETHER, s.anchor().pos()), 128, 256, s.detailAncestors(), 2),
                new NexusMapBindingSavedData.Entry(31, UNIT, GlobalPos.of(Level.OVERWORLD, new BlockPos(2, 64, 2)), 128, 256, s.detailAncestors(), 2),
                new NexusMapBindingSavedData.Entry(31, UNIT, GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 65, 2)), 128, 256, s.detailAncestors(), 2),
                new NexusMapBindingSavedData.Entry(31, UNIT, GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 3)), 128, 256, s.detailAncestors(), 2),
                new NexusMapBindingSavedData.Entry(31, UNIT, s.anchor(), 129, 256, s.detailAncestors(), 2),
                new NexusMapBindingSavedData.Entry(31, UNIT, s.anchor(), 128, 257, s.detailAncestors(), 2),
                new NexusMapBindingSavedData.Entry(31, UNIT, s.anchor(), 128, 256, List.of(13, 12), 2),
                new NexusMapBindingSavedData.Entry(31, UNIT, s.anchor(), 128, 256, List.of(12), 2),
                new NexusMapBindingSavedData.Entry(31, UNIT, s.anchor(), 128, 256, s.detailAncestors(), -1));
        var ledger = new NexusMapBindingWitness.Expected(h(1));
        for (var alternative : alternatives) {
            var value = capture(ledger, new NexusMapBindingSavedData.SparseImportIntent(TICKET, alternative, 2, false),
                    observation(TICKET, alternative.mapId()));
            assertNotEquals(expected.intentHash(), value.intentHash());
        }
        var legacy = alternatives.getLast();
        assertNotEquals(capture(ledger, new NexusMapBindingSavedData.SparseImportIntent(TICKET, legacy, 2, false), observation(TICKET, 31)).intentHash(),
                capture(ledger, new NexusMapBindingSavedData.SparseImportIntent(TICKET, legacy, 3, false), observation(TICKET, 31)).intentHash());
        assertNotEquals(expected.intentHash(), capture(ledger,
                new NexusMapBindingSavedData.SparseImportIntent(TICKET, s, 2, true), observation(TICKET, 31)).intentHash());
        UUID other = UUID.randomUUID();
        assertNotEquals(expected.intentHash(), capture(ledger,
                new NexusMapBindingSavedData.SparseImportIntent(other, s, 2, false), observation(other, 31)).intentHash());
    }
    @Test void wrongTicketOrMapCannotCaptureAndInvalidHashesCannotPersist() {
        var ledger = new NexusMapBindingWitness.Expected(h(1));
        var intent = new NexusMapBindingSavedData.SparseImportIntent(TICKET, source(), 2, false);
        assertThrows(IllegalArgumentException.class, () -> capture(ledger, intent, observation(UUID.randomUUID(), 31)));
        assertThrows(IllegalArgumentException.class, () -> capture(ledger, intent, observation(TICKET, 32)));
        assertThrows(IllegalArgumentException.class, () -> new Manifest(TICKET, -1, h(1), h(2), h(3), h(4), h(5), h(6)));
        for (String invalid : List.of("", "f".repeat(63), "g".repeat(64), "A".repeat(64), "0".repeat(65)))
            assertThrows(IllegalArgumentException.class, () -> new Manifest(TICKET, 31, invalid, h(2), h(3), h(4), h(5), h(6)));
    }
    @Test void partialOversizedAndCorruptFilesAreNeverRepaired() throws Exception {
        publish(manifest()); byte[] valid = bytes();
        for (int size : new int[]{0, 7, BYTES - 1, BYTES + 1, 8192}) {
            byte[] bad = Arrays.copyOf(valid, size); Files.write(path(directory, TICKET), bad);
            assertThrows(IOException.class, () -> read(directory, TICKET));
            assertThrows(IOException.class, () -> publish(manifest())); assertArrayEquals(bad, bytes());
        }
        for (int offset : new int[]{0, 4, 8, 24, 28, BYTES - 1}) {
            byte[] bad = valid.clone(); bad[offset] ^= 1; Files.write(path(directory, TICKET), bad);
            assertThrows(IOException.class, () -> publish(manifest())); assertArrayEquals(bad, bytes());
        }
    }
    @Test void validChecksumCannotHideUnsupportedVersionOrWrongIdentity() throws Exception {
        publish(manifest()); byte[] valid = bytes();
        for (int offset : new int[]{0, 4, 8, 24}) {
            byte[] bad = valid.clone();
            if (offset == 24) ByteBuffer.wrap(bad).putInt(offset, -1); else bad[offset + 3] ^= 4;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(bad, BYTES - 32));
            System.arraycopy(digest, 0, bad, BYTES - 32, 32); Files.write(path(directory, TICKET), bad);
            assertThrows(IOException.class, () -> read(directory, TICKET)); assertArrayEquals(bad, bytes());
        }
    }
    @Test void filesystemKindsAndMissingDirectoryRefuseWithoutBootstrap() throws Exception {
        Path target = path(directory, TICKET); Files.createDirectory(target);
        assertThrows(IOException.class, () -> publish(manifest())); Files.delete(target);
        Path outside = directory.resolve("other"); Files.writeString(outside, "untouched");
        Files.createSymbolicLink(target, outside);
        assertThrows(IOException.class, () -> publish(manifest())); assertEquals("untouched", Files.readString(outside));
        Files.delete(target); Files.createSymbolicLink(target, directory.resolve("missing"));
        assertThrows(IOException.class, () -> read(directory, TICKET)); Files.delete(target);
        Path missing = directory.resolve("not-created");
        assertThrows(IOException.class, () -> NexusMapSourceManifest.publish(missing, manifest(), () -> true));
        assertFalse(Files.exists(missing));
        Path linked = directory.resolve("linked"); Files.createSymbolicLink(linked, directory);
        assertThrows(IOException.class, () -> NexusMapSourceManifest.publish(linked, manifest(), () -> true));
    }
    @Test void cancellationBeforeCreationLeavesNoFile() {
        assertThrows(CancellationException.class, () -> NexusMapSourceManifest.publish(directory, manifest(), () -> false));
        assertFalse(Files.exists(path(directory, TICKET)));
    }
    @Test void everyDurabilityFailureReportsUncertaintyAndOnlyExactCompleteRetryWorks() throws Exception {
        for (Stage failAt : Stage.values()) {
            Path run = Files.createDirectory(directory.resolve(failAt.name()));
            var failure = assertThrows(UncertainCommit.class, () -> NexusMapSourceManifest.publish(run, manifest(), () -> true,
                    stage -> { if (stage == failAt) throw new IOException("injected force boundary failure"); }));
            assertInstanceOf(IOException.class, failure.getCause());
            if (failAt == Stage.CREATED) {
                assertEquals(0, Files.size(path(run, TICKET)));
                assertThrows(IOException.class, () -> NexusMapSourceManifest.publish(run, manifest(), () -> true));
            } else {
                byte[] before = Files.readAllBytes(path(run, TICKET));
                NexusMapSourceManifest.publish(run, manifest(), () -> true);
                assertEquals(manifest(), read(run, TICKET).orElseThrow());
                assertArrayEquals(before, Files.readAllBytes(path(run, TICKET)));
            }
        }
    }
    @Test void cancellationAfterCreationIsUncertainAndCannotAcknowledge() throws Exception {
        for (Stage cancelAt : Stage.values()) {
            Path run = Files.createDirectory(directory.resolve(cancelAt.name())); var alive = new AtomicBoolean(true);
            var failure = assertThrows(UncertainCommit.class, () -> NexusMapSourceManifest.publish(run, manifest(), alive::get,
                    stage -> { if (stage == cancelAt) alive.set(false); }));
            assertInstanceOf(CancellationException.class, failure.getCause());
            assertTrue(Files.exists(path(run, TICKET)));
        }
    }
    @Test void postForceReplacementOrDeletionNeverAcknowledges() throws Exception {
        for (boolean delete : new boolean[]{false, true}) {
            Path run = Files.createDirectory(directory.resolve(Boolean.toString(delete)));
            assertThrows(UncertainCommit.class, () -> NexusMapSourceManifest.publish(run, manifest(), () -> true, stage -> {
                if (stage == Stage.DIRECTORY_FORCED) {
                    if (delete) Files.delete(path(run, TICKET)); else Files.writeString(path(run, TICKET), "bad");
                }
            }));
            if (!delete) assertEquals("bad", Files.readString(path(run, TICKET)));
        }
    }
    @Test void distinctTicketsRemainIndependent() throws Exception {
        publish(manifest()); byte[] first = bytes(); var m = manifest(); UUID other = UUID.randomUUID();
        var second = new Manifest(other, 32, m.ledgerHash(), m.intentHash(), m.nativeFileHash(), m.detailFileHash(), m.nativeTerrainHash(), m.detailTerrainHash());
        publish(second); assertArrayEquals(first, bytes()); assertEquals(second, read(directory, other).orElseThrow());
    }
}
