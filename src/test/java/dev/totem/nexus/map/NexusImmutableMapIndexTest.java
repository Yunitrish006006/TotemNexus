package dev.totem.nexus.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.totem.nexus.map.NexusImmutableMapIndex.*;
import static dev.totem.nexus.map.NexusSparseMapStore.*;
import static org.junit.jupiter.api.Assertions.*;

class NexusImmutableMapIndexTest {
    @TempDir Path directory;
    private static final Tile TILE = Tile.at(-1, -129);
    private static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(15, TimeUnit.SECONDS); }
    private NexusSparseMapStore store(long records) { return new NexusSparseMapStore(directory, new Limits(records, 2, 32)); }
    private NexusImmutableMapIndex index(long records) { return new NexusImmutableMapIndex(store(records), 8); }
    private static Page page(long revision, int... pixels) {
        byte[] colors = new byte[PIXELS], mask = new byte[COVERAGE_BYTES];
        for (int pixel : pixels) { colors[pixel] = 20; mask[pixel / 8] |= (byte) (1 << (pixel % 8)); }
        return new Page(revision, colors, mask);
    }
    private static Root put(NexusImmutableMapIndex index, Root root, Tile tile, Page page) throws Exception {
        UpdateResult result = await(index.put(root, tile, page));
        assertEquals(Status.OK, result.status()); return result.root().orElseThrow();
    }
    private long records() throws Exception {
        try (var paths = Files.list(directory)) { return paths.filter(p -> p.toString().endsWith(".nmb")).count(); }
    }

    @Test void forksRetainOldPixelsAndCoverageAcrossUpdatesAndReopen() throws Exception {
        var index = index(200);
        Root old, expanded;
        try {
            old = put(index, Root.empty(), TILE, page(1, 0));
            long beforeFork = records();
            Root[] forks = new Root[1000]; Arrays.fill(forks, new Root(old.hash()));
            assertEquals(beforeFork, records(), "Fork copied terrain");
            expanded = put(index, old, TILE, page(2, 0, 1));
            assertNotEquals(old, expanded);
            assertFalse(await(index.read(forks[999], TILE)).page().orElseThrow().known(1, 0));
            assertTrue(await(index.read(expanded, TILE)).page().orElseThrow().known(1, 0));
            Files.writeString(directory.resolve("saved-roots"), old.hash() + "\n" + expanded.hash());
        } finally { await(index.closeAsync()); }
        index = index(200);
        try {
            var roots = Files.readAllLines(directory.resolve("saved-roots"));
            assertFalse(await(index.read(new Root(roots.get(0)), TILE)).page().orElseThrow().known(1, 0));
            assertTrue(await(index.read(new Root(roots.get(1)), TILE)).page().orElseThrow().known(1, 0));
        } finally { await(index.closeAsync()); }
    }

    @Test void independentBranchesAndWorldEdgesDoNotAliasTiles() throws Exception {
        var index = index(200);
        try {
            Root base = put(index, Root.empty(), TILE, page(1, 0));
            Root left = put(index, base, Tile.at(-30_000_000, -30_000_000), page(1, 3));
            Root right = put(index, base, Tile.at(29_999_999, 29_999_999), page(1, 4));
            assertEquals(Status.MISSING, await(index.read(left, Tile.at(29_999_999, 29_999_999))).status());
            assertEquals(Status.MISSING, await(index.read(right, Tile.at(-30_000_000, -30_000_000))).status());
            assertTrue(await(index.read(left, TILE)).page().orElseThrow().known(0, 0));
            assertTrue(await(index.read(right, TILE)).page().orElseThrow().known(0, 0));
        } finally { await(index.closeAsync()); }
        assertThrows(IllegalArgumentException.class, () -> new Tile(234_375, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tile(-234_376, 0));
        assertThrows(IllegalArgumentException.class, () -> Tile.at(30_000_000, 0));
    }

    @Test void updatesCopyOnlyFixedDepthAndLeaveAllHistoricalRootsUsable() throws Exception {
        var index = index(300);
        try {
            var roots = new java.util.ArrayList<Root>();
            Root root = Root.empty();
            for (int revision = 1; revision <= 8; revision++) {
                int[] known = java.util.stream.IntStream.range(0, revision).toArray();
                long before = records();
                root = put(index, root, TILE, page(revision, known));
                assertEquals(MAX_BLOCKS_PER_UPDATE, records() - before);
                roots.add(root);
            }
            for (int i = 0; i < roots.size(); i++) {
                Page stored = await(index.read(roots.get(i), TILE)).page().orElseThrow();
                assertTrue(stored.known(i, 0)); assertFalse(stored.known(i + 1, 0));
            }
        } finally { await(index.closeAsync()); }
    }

    @Test void quotaDenialPublishesNoRootOrPartialBatchAndExistingDataRemainUsable() throws Exception {
        var index = index(MAX_BLOCKS_PER_UPDATE);
        try {
            Root old = put(index, Root.empty(), TILE, page(1, 0));
            long before = records();
            UpdateResult failed = await(index.put(old, TILE, page(2, 0, 1)));
            assertEquals(Status.CAPACITY, failed.status()); assertTrue(failed.root().isEmpty());
            assertEquals(before, records());
            assertTrue(await(index.read(old, TILE)).page().orElseThrow().known(0, 0));
            assertEquals(old, put(index, old, TILE, page(1, 0)));
        } finally { await(index.closeAsync()); }
    }

    @Test void deduplicatedRetryAtFullQuotaUsesPreviouslyPersistedBlocks() throws Exception {
        var index = index(2L * MAX_BLOCKS_PER_UPDATE);
        try {
            Root old = put(index, Root.empty(), TILE, page(1, 0));
            Root next = put(index, old, TILE, page(2, 0, 1));
            long before = records();
            assertEquals(next, put(index, old, TILE, page(2, 0, 1)));
            assertEquals(before, records());
        } finally { await(index.closeAsync()); }
    }

    @Test void partialCommitUncertaintyRetainsQuotaAndRetryRecoversWithoutDuplicateRecords() throws Exception {
        AtomicInteger commits = new AtomicInteger();
        var store = new NexusSparseMapStore(directory, new Limits(20, 2, 32), Thread::new, kind -> {
            if (commits.incrementAndGet() == 2) throw new java.io.IOException("Simulated directory durability failure");
        });
        var index = new NexusImmutableMapIndex(store, 4);
        try {
            UpdateResult failed = await(index.put(Root.empty(), TILE, page(1, 0)));
            assertEquals(Status.COMMIT_UNCERTAIN, failed.status()); assertTrue(failed.root().isEmpty());
            assertEquals(2, records());
            assertEquals(Status.MISSING, await(index.read(Root.empty(), TILE)).status());
            Root recovered = put(index, Root.empty(), TILE, page(1, 0));
            assertEquals(20, records());
            assertTrue(await(index.read(recovered, TILE)).page().orElseThrow().known(0, 0));
        } finally { await(index.closeAsync()); }
    }

    @Test void staleRevisionOrLostCoverageCannotReplaceTheSuppliedRootsLeaf() throws Exception {
        var index = index(100);
        try {
            Root old = put(index, Root.empty(), TILE, page(2, 0, 1));
            long before = records();
            assertEquals(Status.STALE, await(index.put(old, TILE, page(1, 0, 1))).status());
            assertEquals(Status.STALE, await(index.put(old, TILE, page(2, 0, 1, 2))).status());
            assertEquals(Status.INVALID_UPDATE, await(index.put(old, TILE, page(3, 1))).status());
            assertEquals(before, records());
            assertTrue(await(index.read(old, TILE)).page().orElseThrow().known(0, 0));
            byte[] colors = new byte[PIXELS]; Arrays.fill(colors, (byte) 24);
            Root colored = put(index, Root.empty(), TILE, new Page(1, colors, new byte[COVERAGE_BYTES]));
            assertFalse(await(index.read(colored, TILE)).page().orElseThrow().known(0, 0));
        } finally { await(index.closeAsync()); }
    }

    @Test void missingReferencedBlockIsCorruptionAndCannotBeUpdatedAsEmptyTerrain() throws Exception {
        var index = index(100);
        Root root;
        try { root = put(index, Root.empty(), TILE, page(1, 0)); }
        finally { await(index.closeAsync()); }
        Files.delete(directory.resolve(root.hash() + ".nmb"));
        index = index(100);
        try {
            long before = records();
            assertEquals(Status.CORRUPT, await(index.read(root, TILE)).status());
            assertEquals(Status.CORRUPT, await(index.put(root, TILE, page(2, 0, 1))).status());
            assertEquals(before, records());
        } finally { await(index.closeAsync()); }
    }

    @Test void validHashDoesNotExcuseWrongTypeDepthOrLeafCoordinates() throws Exception {
        var store = store(100);
        var index = new NexusImmutableMapIndex(store, 4);
        try {
            for (int wrong : new int[]{0, 1, 2}) {
                byte[] bad = ByteBuffer.allocate(136).putInt(wrong == 0 ? 123 : 0x4e584e44)
                        .put((byte) 1).put((byte) (wrong == 1 ? 1 : 0)).putShort((short) 0).array();
                if (wrong == 2) bad[8] = 1; // Correct node header, missing nonempty child.
                assertEquals(Status.OK, await(store.writeBlobs(List.of(bad))));
                Root root = new Root(contentHash(bad));
                assertEquals(Status.CORRUPT, await(index.read(root, Tile.at(-30_000_000, -30_000_000))).status());
            }
            // Build a correctly hashed path whose terminal page names another tile.
            byte[] leaf = ByteBuffer.allocate(24 + PIXELS + COVERAGE_BYTES).putInt(0x4e585047).putInt(1)
                    .putInt(TILE.x() + 1).putInt(TILE.z()).putLong(1).array();
            var blobs = new java.util.ArrayList<byte[]>(); blobs.add(leaf);
            String hash = contentHash(leaf);
            int min = Key.at(0, -30_000_000, 0).tileX();
            for (int depth = DEPTH - 1; depth >= 0; depth--) {
                byte[] node = ByteBuffer.allocate(136).putInt(0x4e584e44).put((byte) 1).put((byte) depth).putShort((short) 0).array();
                int quadrant = (((TILE.x() - min) >>> (18 - depth)) & 1) * 2 + (((TILE.z() - min) >>> (18 - depth)) & 1);
                System.arraycopy(java.util.HexFormat.of().parseHex(hash), 0, node, 8 + quadrant * 32, 32);
                blobs.add(node); hash = contentHash(node);
            }
            assertEquals(Status.OK, await(store.writeBlobs(blobs)));
            assertEquals(Status.CORRUPT, await(index.read(new Root(contentHash(leaf)), TILE)).status());
            assertEquals(Status.CORRUPT, await(index.read(new Root(hash), TILE)).status());
        } finally { await(index.closeAsync()); }
    }

    @Test void closeDrainsWholeMultistageReadBeforeStoppingTheIoWorker() throws Exception {
        var index = index(100); Root root;
        try { root = put(index, Root.empty(), TILE, page(1, 0)); }
        finally { await(index.closeAsync()); }
        CountDownLatch gate = new CountDownLatch(1);
        var store = new NexusSparseMapStore(directory, new Limits(100, 1, 32), runnable -> new Thread(() -> {
            try { gate.await(); } catch (InterruptedException failure) { throw new AssertionError(failure); }
            runnable.run();
        }));
        index = new NexusImmutableMapIndex(store, 1);
        var accepted = index.read(root, TILE);
        try {
            assertEquals(Status.QUEUE_FULL, await(index.read(root, TILE)).status());
            var close = index.closeAsync(); assertSame(close, index.closeAsync()); assertFalse(close.isDone());
            gate.countDown();
            assertEquals(Status.OK, await(accepted).status()); await(close);
            assertEquals(Status.CLOSED, await(index.read(root, TILE)).status());
        } finally { gate.countDown(); await(index.closeAsync()); }
    }

    @Test void cancellingPublicResponseDoesNotLeakLogicalPermitOrHangClose() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        var store = new NexusSparseMapStore(directory, new Limits(100, 1, 32), runnable -> new Thread(() -> {
            try { gate.await(); } catch (InterruptedException failure) { throw new AssertionError(failure); }
            runnable.run();
        }));
        var index = new NexusImmutableMapIndex(store, 1);
        try {
            var accepted = index.put(Root.empty(), TILE, page(1, 0));
            assertTrue(accepted.cancel(false));
            var close = index.closeAsync(); gate.countDown(); await(close);
            assertTrue(accepted.isCancelled()); assertEquals(20, records());
        } finally { gate.countDown(); await(index.closeAsync()); }
    }
}
