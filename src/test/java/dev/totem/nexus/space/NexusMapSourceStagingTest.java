package dev.totem.nexus.space;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.totem.nexus.space.NexusMapSourceStaging.*;
import static org.junit.jupiter.api.Assertions.*;

class NexusMapSourceStagingTest {
    @TempDir Path root;
    Path directory, source;
    private static final Limits LIMITS = new Limits(16, 32L << 20);
    @BeforeEach void setup() throws Exception {
        directory = Files.createDirectory(root.resolve("staged")); source = root.resolve("map.dat");
        byte[] bytes = new byte[150003]; new Random(31).nextBytes(bytes); Files.write(source, bytes);
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private String hash() throws Exception { return hash(Files.readAllBytes(source)); }
    private Blob stage(String hash) throws IOException { return NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, hash, LIMITS, () -> true); }
    private List<Path> entries() throws IOException { try (var files = Files.list(directory)) { return files.toList(); } }

    @Test void exactBytesRoundTripRetryAndColdVerifySurviveOriginalDeletion() throws Exception {
        byte[] bytes = Files.readAllBytes(source); String expected = hash(); var steps = new ArrayList<Step>();
        Blob blob = NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, expected, LIMITS, () -> true, steps::add);
        assertEquals(new Blob(Kind.NATIVE, expected, bytes.length), blob);
        assertEquals(List.of(Step.CREATED, Step.COPIED, Step.FILE_FORCED, Step.PUBLISHED, Step.DIRECTORY_FORCED), steps);
        assertArrayEquals(bytes, Files.readAllBytes(path(directory, Kind.NATIVE, expected))); assertEquals(1, entries().size());
        Files.delete(source); steps.clear();
        assertEquals(blob, NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, expected, LIMITS, () -> true, steps::add));
        assertEquals(List.of(Step.FILE_FORCED, Step.DIRECTORY_FORCED), steps);
        assertEquals(blob, verify(directory, Kind.NATIVE, expected, () -> true));
        assertArrayEquals(bytes, Files.readAllBytes(path(directory, Kind.NATIVE, expected)));
    }
    @Test void mismatchedSourceNeverPublishesOrChangesOriginal() throws Exception {
        byte[] bytes = Files.readAllBytes(source); String wrong = "f".repeat(64);
        assertThrows(IOException.class, () -> stage(wrong)); assertTrue(entries().isEmpty());
        assertArrayEquals(bytes, Files.readAllBytes(source));
    }
    @Test void sourceGrowthShrinkAndSameSizeReplacementCannotMatchOldObservation() throws Exception {
        byte[] original = Files.readAllBytes(source); String expected = hash();
        for (int change = 0; change < 3; change++) {
            Files.write(source, original); int mode = change;
            assertThrows(IOException.class, () -> NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, expected,
                    new Limits(4, 2L * original.length), () -> true, step -> {
                        if (step != Step.CREATED) return;
                        if (mode == 0) Files.write(source, new byte[]{1}, StandardOpenOption.APPEND);
                        else if (mode == 1) Files.write(source, new byte[]{1});
                        else { byte[] different = original.clone(); different[0] ^= 1; Files.write(source, different); }
                    }));
            assertTrue(entries().isEmpty());
        }
    }
    @Test void fileAndByteQuotaIncludeAbandonedPendingFilesAndSurviveColdCalls() throws Exception {
        Files.write(source, new byte[]{1, 2, 3}); String first = hash();
        var small = new Limits(2, 6); NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, first, small, () -> true);
        Files.write(source, new byte[]{4, 5, 6}); String second = hash();
        assertThrows(IOException.class, () -> NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, second, small, () -> true));
        assertThrows(IOException.class, () -> NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, second, new Limits(8, 5), () -> true));
        Files.delete(source); assertEquals(3, NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, first, small, () -> true).bytes());
        assertEquals(1, entries().size());
        Files.delete(path(directory, Kind.NATIVE, first));
        Path orphan = directory.resolve(".pending-" + UUID.randomUUID()); Files.write(orphan, new byte[]{9, 9, 9});
        Files.write(source, new byte[]{4, 5, 6});
        assertThrows(IOException.class, () -> NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, second, small, () -> true));
        assertArrayEquals(new byte[]{9, 9, 9}, Files.readAllBytes(orphan));
    }
    @Test void existingCorruptionIsNeverOverwrittenOrRecoveredFromOriginal() throws Exception {
        String expected = hash(); stage(expected); Path target = path(directory, Kind.NATIVE, expected);
        for (byte[] bad : new byte[][]{new byte[0], new byte[]{1, 2, 3}}) {
            Files.write(target, bad); assertThrows(IOException.class, () -> stage(expected));
            assertThrows(IOException.class, () -> verify(directory, Kind.NATIVE, expected, () -> true));
            assertArrayEquals(bad, Files.readAllBytes(target));
        }
    }
    @Test void crashWithFinalAndPendingHardLinksStaysWithinReservedQuota() throws Exception {
        Files.write(source, new byte[]{1, 2, 3}); String first = hash();
        Path target = path(directory, Kind.NATIVE, first);
        // Reconstruct the crash point after link publication but before pending unlink.
        NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, first, new Limits(2, 6), () -> true);
        Path pending = directory.resolve(".pending-" + UUID.randomUUID()); Files.createLink(pending, target);
        assertTrue(Files.isSameFile(pending, target)); assertEquals(2, entries().size());
        Files.write(source, new byte[]{4, 5, 6}); String second = hash();
        assertThrows(IOException.class, () -> NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, second, new Limits(3, 9), () -> true));
        assertThrows(IOException.class, () -> NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, second, new Limits(8, 11), () -> true));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(pending));
        Files.delete(source);
        assertEquals(3, NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, first, new Limits(2, 6), () -> true).bytes());
        assertEquals(2, entries().size()); // Exact retry must not reclaim an orphan it does not own.
        Files.delete(pending); Files.write(source, new byte[]{4, 5, 6});
        assertEquals(3, NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, second, new Limits(3, 9), () -> true).bytes());
        assertEquals(2, entries().size());
    }
    @Test void durabilityFailuresRetainOnlyPublishedBytesAndExactRetriesWork() throws Exception {
        String expected = hash(); byte[] bytes = Files.readAllBytes(source);
        for (Step failAt : Step.values()) {
            Path run = Files.createDirectory(root.resolve(failAt.name()));
            IOException failure = assertThrows(IOException.class, () -> NexusMapSourceStaging.stage(run, source, Kind.NATIVE, expected, LIMITS, () -> true,
                    step -> { if (step == failAt) throw new IOException("injected durability boundary failure"); }));
            if (failAt.ordinal() < Step.PUBLISHED.ordinal()) {
                assertFalse(failure instanceof UncertainCommit); assertFalse(Files.exists(path(run, Kind.NATIVE, expected)));
                try (var files = Files.list(run)) { assertEquals(0, files.count()); }
            } else {
                assertInstanceOf(UncertainCommit.class, failure);
                assertArrayEquals(bytes, Files.readAllBytes(path(run, Kind.NATIVE, expected)));
                assertEquals(bytes.length, NexusMapSourceStaging.stage(run, root.resolve("absent"), Kind.NATIVE, expected, LIMITS, () -> true).bytes());
                try (var files = Files.list(run)) { assertEquals(1, files.count()); }
            }
        }
    }
    @Test void cancellationAtEveryBoundaryNeverAcknowledgesAndPreservesPublishedBlob() throws Exception {
        String expected = hash();
        assertThrows(CancellationException.class, () -> NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, expected, LIMITS, () -> false));
        assertTrue(entries().isEmpty());
        for (Step cancelAt : Step.values()) {
            Path run = Files.createDirectory(root.resolve(cancelAt.name())); var alive = new AtomicBoolean(true);
            Exception failure = assertThrows(Exception.class, () -> NexusMapSourceStaging.stage(run, source, Kind.NATIVE, expected, LIMITS, alive::get,
                    step -> { if (step == cancelAt) alive.set(false); }));
            if (cancelAt.ordinal() < Step.PUBLISHED.ordinal()) {
                assertInstanceOf(CancellationException.class, failure); assertFalse(Files.exists(path(run, Kind.NATIVE, expected)));
            } else {
                assertInstanceOf(UncertainCommit.class, failure); assertInstanceOf(CancellationException.class, failure.getCause());
                assertEquals(expected, verify(run, Kind.NATIVE, expected, () -> true).hash());
            }
        }
    }
    @Test void postPublicationCorruptionDeletionAndConflictingLinkRefuse() throws Exception {
        String expected = hash();
        for (boolean delete : new boolean[]{false, true}) {
            Path run = Files.createDirectory(root.resolve("post-" + delete));
            assertThrows(UncertainCommit.class, () -> NexusMapSourceStaging.stage(run, source, Kind.NATIVE, expected, LIMITS, () -> true, step -> {
                if (step == Step.DIRECTORY_FORCED) {
                    if (delete) Files.delete(path(run, Kind.NATIVE, expected)); else Files.writeString(path(run, Kind.NATIVE, expected), "corrupt");
                }
            }));
        }
        assertThrows(UncertainCommit.class, () -> NexusMapSourceStaging.stage(directory, source, Kind.NATIVE, expected, LIMITS, () -> true, step -> {
            if (step == Step.FILE_FORCED) Files.writeString(path(directory, Kind.NATIVE, expected), "another writer");
        }));
        assertEquals("another writer", Files.readString(path(directory, Kind.NATIVE, expected)));
    }
    @Test void nonRegularMissingAndOversizedInputsNeverBootstrap() throws Exception {
        String expected = hash(); Files.delete(source); Files.createDirectory(source);
        assertThrows(IOException.class, () -> stage(expected)); Files.delete(source);
        Files.createSymbolicLink(source, root.resolve("missing")); assertThrows(IOException.class, () -> stage(expected)); Files.delete(source);
        assertThrows(IOException.class, () -> stage(expected));
        for (Kind kind : Kind.values()) {
            try (var file = new RandomAccessFile(source.toFile(), "rw")) { file.setLength(kind.maxBytes + 1); }
            assertThrows(IOException.class, () -> NexusMapSourceStaging.stage(directory, source, kind, expected, LIMITS, () -> true)); Files.delete(source);
        }
        assertTrue(entries().isEmpty());
    }
    @Test void invalidTargetsAndDirectoryEntriesArePreserved() throws Exception {
        String expected = hash(); Path target = path(directory, Kind.NATIVE, expected); Files.createDirectory(target);
        assertThrows(IOException.class, () -> stage(expected)); Files.delete(target);
        Files.createSymbolicLink(target, source); assertThrows(IOException.class, () -> stage(expected)); Files.delete(target);
        Files.createSymbolicLink(target, root.resolve("missing")); assertThrows(IOException.class, () -> stage(expected)); Files.delete(target);
        Path unknown = directory.resolve("unexpected"); Files.writeString(unknown, "keep");
        assertThrows(IOException.class, () -> stage(expected)); assertEquals("keep", Files.readString(unknown));
        Path linked = root.resolve("linked"); Files.createSymbolicLink(linked, directory);
        assertThrows(IOException.class, () -> NexusMapSourceStaging.stage(linked, source, Kind.NATIVE, expected, LIMITS, () -> true));
        Path missing = root.resolve("not-created");
        assertThrows(IOException.class, () -> NexusMapSourceStaging.stage(missing, source, Kind.NATIVE, expected, LIMITS, () -> true)); assertFalse(Files.exists(missing));
    }
    @Test void kindNamespacesRemainDistinctAndDescriptorsRejectInvalidValues() throws Exception {
        String expected = hash(); Blob nativeBlob = stage(expected);
        Blob detailBlob = NexusMapSourceStaging.stage(directory, source, Kind.DETAIL, expected, LIMITS, () -> true);
        assertNotEquals(nativeBlob.kind(), detailBlob.kind()); assertEquals(2, entries().size());
        for (String bad : List.of("../x", "A".repeat(64), "0".repeat(65), ""))
            assertThrows(IllegalArgumentException.class, () -> path(directory, Kind.NATIVE, bad));
        assertThrows(IllegalArgumentException.class, () -> new Blob(Kind.NATIVE, expected, Kind.NATIVE.maxBytes + 1));
        assertThrows(IllegalArgumentException.class, () -> new Limits(0, 1)); assertThrows(IllegalArgumentException.class, () -> new Limits(1, 0));
    }
}
