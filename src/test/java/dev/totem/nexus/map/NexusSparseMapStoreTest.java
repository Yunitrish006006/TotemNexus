package dev.totem.nexus.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static dev.totem.nexus.map.NexusSparseMapStore.*;
import static org.junit.jupiter.api.Assertions.*;

class NexusSparseMapStoreTest {
    @TempDir Path directory;
    private static final Key FIRST = Key.at(1, -1, -129);
    private static final Key SECOND = Key.at(1, 129, 0);

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
    private static Page page(long revision, int pixel) {
        byte[] colors = new byte[PIXELS], coverage = new byte[COVERAGE_BYTES];
        colors[pixel] = 20;
        coverage[pixel / 8] |= (byte) (1 << (pixel % 8));
        return new Page(revision, colors, coverage);
    }
    private static Path path(Path root, Key key) {
        return root.resolve(key.ownerId() + "_" + key.tileX() + "_" + key.tileZ() + ".nmp");
    }
    private NexusSparseMapStore open(long maxPages, int cache) {
        return new NexusSparseMapStore(directory, new Limits(maxPages, cache, 32));
    }

    @Test void saveReopenAndCacheEvictionRetainExactCoverage() throws Exception {
        var store = open(8, 1);
        try {
            assertEquals(Status.OK, await(store.write(FIRST, page(1, 5))));
            assertEquals(Status.OK, await(store.write(SECOND, page(1, 7))));
            assertTrue(await(store.read(FIRST)).page().orElseThrow().known(5, 0));
            assertFalse(await(store.read(FIRST)).page().orElseThrow().known(6, 0));
        } finally { await(store.closeAsync()); }
        store = open(8, 1);
        try {
            assertTrue(await(store.read(SECOND)).page().orElseThrow().known(7, 0));
            assertTrue(await(store.read(FIRST)).page().orElseThrow().known(5, 0));
        } finally { await(store.closeAsync()); }
    }

    @Test void colorsNeverAuthorizeUnknownCellsAndArraysRemainImmutable() throws Exception {
        byte[] colors = new byte[PIXELS], mask = new byte[COVERAGE_BYTES];
        java.util.Arrays.fill(colors, (byte) 24);
        Page page = new Page(0, colors, mask);
        mask[0] = -1; colors[0] = 0;
        assertFalse(page.known(0, 0));
        assertEquals(24, page.colors()[0]);
        page.coverage()[0] = -1; page.colors()[0] = 0;
        var store = open(1, 1);
        try {
            assertEquals(Status.OK, await(store.write(FIRST, page)));
            Page loaded = await(store.read(FIRST)).page().orElseThrow();
            assertFalse(loaded.known(0, 0));
            loaded.coverage()[0] = -1;
            assertFalse(await(store.read(FIRST)).page().orElseThrow().known(0, 0));
        } finally { await(store.closeAsync()); }
    }

    @Test void capacitySurvivesRestartAndDoesNotEvictExistingTerrain() throws Exception {
        var store = open(1, 1);
        try { assertEquals(Status.OK, await(store.write(FIRST, page(1, 0)))); }
        finally { await(store.closeAsync()); }
        store = open(1, 1);
        try {
            assertEquals(Status.CAPACITY, await(store.write(SECOND, page(1, 0))));
            assertTrue(await(store.read(FIRST)).page().orElseThrow().known(0, 0));
            assertEquals(Status.OK, await(store.write(FIRST, page(2, 1))));
        } finally { await(store.closeAsync()); }
        // Lowering quota must not make existing exploration unreadable.
        store = open(0, 1);
        try { assertTrue(await(store.read(FIRST)).page().orElseThrow().known(1, 0)); }
        finally { await(store.closeAsync()); }
        store = open(2, 1);
        try { assertEquals(Status.OK, await(store.write(SECOND, page(1, 0)))); }
        finally { await(store.closeAsync()); }
    }

    @Test void corruptExistingPageIsNeitherMissingNorFreeCapacity() throws Exception {
        var store = open(1, 1);
        try { assertEquals(Status.OK, await(store.write(FIRST, page(1, 0)))); }
        finally { await(store.closeAsync()); }
        Files.write(path(directory, FIRST), new byte[]{1, 2, 3});
        store = open(1, 1);
        try {
            assertEquals(Status.CORRUPT, await(store.read(FIRST)).status());
            assertEquals(Status.CORRUPT, await(store.write(FIRST, page(2, 1))));
            assertEquals(Status.CAPACITY, await(store.write(SECOND, page(1, 0))));
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(path(directory, FIRST)));
        } finally { await(store.closeAsync()); }
    }

    @Test void wrongKeyVersionRevisionAndChecksumFailClosedBeforeReturningCoverage() throws Exception {
        var store = open(8, 1);
        try { assertEquals(Status.OK, await(store.write(FIRST, page(1, 0)))); }
        finally { await(store.closeAsync()); }
        byte[] original = Files.readAllBytes(path(directory, FIRST));
        for (int offset : new int[]{0, 7, 11, 15, 19, 20, original.length - 1}) {
            byte[] broken = original.clone(); broken[offset] ^= (byte) 0x80;
            if (offset != original.length - 1) {
                var checksum = new java.util.zip.CRC32();
                checksum.update(broken, 0, broken.length - 4);
                java.nio.ByteBuffer.wrap(broken).putInt(broken.length - 4, (int) checksum.getValue());
            }
            Files.write(path(directory, FIRST), broken);
            store = open(8, 1);
            try {
                ReadResult result = await(store.read(FIRST));
                assertEquals(Status.CORRUPT, result.status());
                assertTrue(result.page().isEmpty());
            } finally { await(store.closeAsync()); }
        }
        Files.write(path(directory, FIRST), original);
        Files.createDirectories(path(directory, SECOND).getParent());
        Files.copy(path(directory, FIRST), path(directory, SECOND));
        store = open(8, 1);
        try { assertEquals(Status.CORRUPT, await(store.read(SECOND)).status()); }
        finally { await(store.closeAsync()); }
    }

    @Test void failedUpdateLeavesOldFileAndCachedProofUntouched() throws Exception {
        var store = open(2, 1);
        try {
            assertEquals(Status.OK, await(store.write(FIRST, page(1, 0))));
            byte[] previous = Files.readAllBytes(path(directory, FIRST));
            // Deterministic pre-commit IO failure, including failed staging cleanup.
            Path pending = directory.resolve(".pending");
            Files.createDirectory(pending); Files.write(pending.resolve("blocker"), new byte[]{1});
            for (int i = 0; i < 3; i++) assertEquals(Status.IO_FAILURE, await(store.write(FIRST, page(2, 1))));
            for (int i = 10; i < 40; i++)
                assertEquals(Status.IO_FAILURE, await(store.write(new Key(i, i, i), page(1, 0))));
            try (var paths = Files.list(directory)) {
                assertEquals(1, paths.filter(Files::isDirectory).count(), "Failed writes leaked per-key directories");
            }
            assertArrayEquals(previous, Files.readAllBytes(path(directory, FIRST)));
            Page old = await(store.read(FIRST)).page().orElseThrow();
            assertEquals(1, old.revision()); assertTrue(old.known(0, 0)); assertFalse(old.known(1, 0));
            Files.delete(pending.resolve("blocker")); Files.delete(pending);
            assertEquals(Status.OK, await(store.write(FIRST, page(2, 1))));
        } finally { await(store.closeAsync()); }
    }

    @Test void staleRevisionsCannotOverwriteNewerTerrain() throws Exception {
        var store = open(2, 1);
        try {
            assertEquals(Status.OK, await(store.write(FIRST, page(2, 0))));
            assertEquals(Status.STALE, await(store.write(FIRST, page(1, 1))));
            assertEquals(Status.STALE, await(store.write(FIRST, page(2, 1))));
            assertEquals(Status.OK, await(store.write(FIRST, page(2, 0))));
            assertTrue(await(store.read(FIRST)).page().orElseThrow().known(0, 0));
        } finally { await(store.closeAsync()); }
    }

    @Test void boundedQueueIsNonblockingAndCloseDrainsAcceptedRequests() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        Path root = directory.resolve("not-created-on-caller-thread");
        var store = new NexusSparseMapStore(root, new Limits(2, 1, 1), runnable -> new Thread(() -> {
            try { gate.await(); } catch (InterruptedException failure) { throw new AssertionError(failure); }
            runnable.run();
        }));
        try {
            assertFalse(Files.exists(root));
            var accepted = store.read(FIRST);
            assertFalse(accepted.isDone());
            assertEquals(Status.QUEUE_FULL, await(store.read(SECOND)).status());
            var close = store.closeAsync();
            assertSame(close, store.closeAsync()); assertFalse(close.isDone());
            gate.countDown();
            assertEquals(Status.MISSING, await(accepted).status());
            await(close);
            assertEquals(Status.CLOSED, await(store.read(FIRST)).status());
            assertEquals(Status.CLOSED, await(store.write(FIRST, page(1, 0))));
        } finally { gate.countDown(); await(store.closeAsync()); }
    }

    @Test void concurrentCloseAndSubmitCompleteEveryAcceptedFuture() throws Exception {
        var store = open(2, 1);
        await(store.read(FIRST));
        var futures = new ConcurrentLinkedQueue<CompletableFuture<ReadResult>>();
        try (var callers = Executors.newFixedThreadPool(4)) {
            List<java.util.concurrent.Future<?>> jobs = new ArrayList<>();
            for (int i = 0; i < 3; i++) jobs.add(callers.submit(() -> {
                for (int n = 0; n < 100; n++) futures.add(store.read(FIRST));
            }));
            jobs.add(callers.submit(store::closeAsync));
            for (var job : jobs) job.get(10, TimeUnit.SECONDS);
        } finally { await(store.closeAsync()); }
        assertEquals(300, futures.size());
        for (var future : futures) assertTrue(List.of(Status.MISSING, Status.QUEUE_FULL, Status.CLOSED)
                .contains(await(future).status()));
    }

    @Test void secondWriterCannotBypassQuotaOrOverwriteFirstWriter() throws Exception {
        var first = open(2, 1);
        await(first.read(FIRST));
        var second = open(2, 1);
        try {
            assertEquals(Status.OK, await(first.write(FIRST, page(1, 0))));
            assertEquals(Status.OK, await(second.read(FIRST)).status());
            assertEquals(Status.IO_FAILURE, await(second.write(FIRST, page(2, 1))));
            assertEquals(Status.IO_FAILURE, await(second.write(SECOND, page(1, 0))));
            assertTrue(await(first.read(FIRST)).page().orElseThrow().known(0, 0));
        } finally { await(second.closeAsync()); await(first.closeAsync()); }
    }

    @Test void failedStartupKeepsExistingTerrainReadableAndResumesAfterMetadataRetry() throws Exception {
        var store = open(8, 1);
        try { assertEquals(Status.OK, await(store.write(FIRST, page(1, 0)))); }
        finally { await(store.closeAsync()); }
        Path pending = directory.resolve(".pending");
        Files.createDirectory(pending); Files.write(pending.resolve("blocker"), new byte[]{1});
        store = open(8, 1);
        try {
            assertEquals(Status.OK, await(store.read(FIRST)).status());
            assertEquals(Status.IO_FAILURE, await(store.write(SECOND, page(1, 0))));
            Files.delete(pending.resolve("blocker")); Files.delete(pending);
            assertEquals(Status.OK, await(store.refreshQuota()));
            assertEquals(Status.OK, await(store.write(SECOND, page(1, 0))));
        } finally { await(store.closeAsync()); }
    }

    @Test void immutableBatchesDeduplicateAndSharePersistentQuotaWithNamespacePages() throws Exception {
        byte[] one = {1, 2, 3}, two = {4, 5, 6};
        String firstHash = contentHash(one);
        var store = open(2, 1);
        try {
            var pending = store.writeBlobs(List.of(one, one, two)); one[0] = 99;
            assertEquals(Status.OK, await(pending));
            var first = await(store.readBlob(firstHash)).blob().orElseThrow();
            assertArrayEquals(new byte[]{1, 2, 3}, first.bytes()); first.bytes()[0] = 88;
            assertArrayEquals(new byte[]{1, 2, 3}, await(store.readBlob(firstHash)).blob().orElseThrow().bytes());
            assertEquals(Status.CAPACITY, await(store.write(FIRST, page(1, 0))));
        } finally { await(store.closeAsync()); }
        store = open(2, 1);
        try {
            assertEquals(Status.OK, await(store.writeBlobs(List.of(new byte[]{1, 2, 3}, two))));
            assertEquals(Status.CAPACITY, await(store.writeBlobs(List.of(new byte[]{7}))));
        } finally { await(store.closeAsync()); }
    }

    @Test void blobBatchCapacityIsCheckedBeforeAnyPartialWrite() throws Exception {
        var store = open(1, 1);
        try {
            assertEquals(Status.CAPACITY, await(store.writeBlobs(List.of(new byte[]{1}, new byte[]{2}))));
            try (var files = Files.list(directory)) { assertEquals(0, files.filter(p -> p.toString().endsWith(".nmb")).count()); }
            assertEquals(Status.OK, await(store.writeBlobs(List.of(new byte[]{1}))));
        } finally { await(store.closeAsync()); }
    }

    @Test void corruptExistingBlobIsVerifiedFromDiskBeforeDeduplicatedWrite() throws Exception {
        var store = open(3, 1);
        byte[] original = {1, 2, 3}; String hash = contentHash(original);
        try {
            assertEquals(Status.OK, await(store.writeBlobs(List.of(original))));
            assertEquals(Status.OK, await(store.readBlob(hash)).status());
            Files.write(directory.resolve(hash + ".nmb"), new byte[]{9, 9, 9});
            assertEquals(Status.CORRUPT, await(store.writeBlobs(List.of(new byte[]{4}, original))));
            assertArrayEquals(new byte[]{9, 9, 9}, Files.readAllBytes(directory.resolve(hash + ".nmb")));
            assertEquals(Status.MISSING, await(store.readBlob(contentHash(new byte[]{4}))).status());
        } finally { await(store.closeAsync()); }
    }

    @Test void blobPayloadAndBatchLimitsAreValidatedBeforeIo() throws Exception {
        var store = open(10, 1);
        try {
            assertThrows(IllegalArgumentException.class, () -> store.readBlob("../invalid"));
            assertThrows(IllegalArgumentException.class, () -> store.writeBlobs(List.of(new byte[MAX_BLOB_BYTES + 1])));
            assertThrows(IllegalArgumentException.class, () -> store.writeBlobs(java.util.Collections.nCopies(21, new byte[]{1})));
            assertThrows(IllegalArgumentException.class, () -> store.writeBlobs(java.util.Collections.nCopies(4, new byte[MAX_BLOB_BYTES])));
            assertEquals(Status.MISSING, await(store.readBlob("f".repeat(64))).status());
        } finally { await(store.closeAsync()); }
    }

    @Test void invalidKeysPagesAndBoundsAreRejectedWithoutIo() {
        assertEquals(new Key(1, -1, -2), FIRST);
        assertThrows(IllegalArgumentException.class, () -> Key.at(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Key.at(1, 30_000_000, 0));
        assertThrows(IllegalArgumentException.class, () -> new Key(1, Integer.MAX_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> new Page(-1, new byte[PIXELS], new byte[COVERAGE_BYTES]));
        assertThrows(IllegalArgumentException.class, () -> new Page(0, new byte[1], new byte[COVERAGE_BYTES]));
        assertThrows(IllegalArgumentException.class, () -> new Limits(1, 257, 1));
        assertThrows(IllegalArgumentException.class, () -> new Limits(1, 1, 257));
    }
}
