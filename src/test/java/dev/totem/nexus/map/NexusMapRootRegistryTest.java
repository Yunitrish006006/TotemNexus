package dev.totem.nexus.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

import static dev.totem.nexus.map.NexusSparseMapStore.*;
import static dev.totem.nexus.map.NexusImmutableMapIndex.*;
import static dev.totem.nexus.map.NexusMapRootRegistry.*;
import static org.junit.jupiter.api.Assertions.*;

class NexusMapRootRegistryTest {
    @TempDir Path directory;
    private static final UUID UNIT = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final Tile TILE = Tile.at(-1, -129);
    private static Identity identity(int id) { return new Identity(id, UNIT, "minecraft:overworld", -24, 40); }
    private NexusSparseMapStore store(long records) { return new NexusSparseMapStore(directory, new Limits(records, 2, 32)); }
    private NexusMapRootRegistry registry(long records) { return new NexusMapRootRegistry(store(records), 8); }
    private static <T> T await(CompletableFuture<T> future) throws Exception { return future.get(30, TimeUnit.SECONDS); }
    private static Entry entry(Result result) { assertEquals(Status.OK, result.status()); return result.entry().orElseThrow(); }
    private static Page page(long revision, int... pixels) {
        byte[] colors = new byte[PIXELS], mask = new byte[COVERAGE_BYTES];
        for (int pixel : pixels) { colors[pixel] = 20; mask[pixel / 8] |= (byte) (1 << (pixel % 8)); }
        return new Page(revision, colors, mask);
    }
    private Path row(int id) { return directory.resolve("root_" + id + ".nmr"); }
    private long blobs() throws Exception {
        try (var files = Files.list(directory)) { return files.filter(p -> p.toString().endsWith(".nmb")).count(); }
    }
    private NexusSparseMapStore gated(CountDownLatch gate) {
        return new NexusSparseMapStore(directory, new Limits(300, 2, 32), runnable -> new Thread(() -> {
            try { gate.await(); } catch (InterruptedException failure) { throw new AssertionError(failure); }
            runnable.run();
        }));
    }

    @Test void clonesShareOneNativeMapRegistrationAndSurviveReopen() throws Exception {
        var registry = registry(100); Entry next;
        try {
            Entry initial = entry(await(registry.create(identity(1), 0)));
            assertEquals(initial, entry(await(registry.create(identity(1), 0))));
            next = entry(await(registry.record(initial, TILE, page(1, 0))));
            assertEquals(1, next.revision()); assertEquals(initial.root(), next.previousRoot());
            assertEquals(next, entry(await(registry.read(identity(1)))));
            assertEquals(next, entry(await(registry.read(new Identity(1, UNIT, "minecraft:overworld", -24, 40)))));
            assertEquals(Status.STALE, await(registry.create(identity(1), 0)).status());
            try (var files = Files.list(directory)) { assertEquals(1, files.filter(p -> p.toString().endsWith(".nmr")).count()); }
        } finally { await(registry.closeAsync()); }
        registry = registry(100);
        try {
            assertEquals(next, entry(await(registry.read(identity(1)))));
            assertTrue(await(registry.readPage(identity(1), TILE)).page().orElseThrow().known(0, 0));
        } finally { await(registry.closeAsync()); }
    }

    @Test void concurrentFinalCasHasOneWinnerAndLoserDoesNotOverwriteExploration() throws Exception {
        var registry = registry(300); Entry initial;
        try { initial = entry(await(registry.create(identity(1), 0))); }
        finally { await(registry.closeAsync()); }
        CountDownLatch gate = new CountDownLatch(1);
        registry = new NexusMapRootRegistry(gated(gate), 8);
        try {
            Tile other = Tile.at(2048, 4096);
            var a = registry.record(initial, TILE, page(1, 0));
            var b = registry.record(initial, other, page(1, 1));
            gate.countDown();
            Result left = await(a), right = await(b);
            assertEquals(1, List.of(left, right).stream().filter(r -> r.status() == Status.OK).count());
            assertEquals(1, List.of(left, right).stream().filter(r -> r.status() == Status.STALE).count());
            Entry winner = entry(left.status() == Status.OK ? left : right);
            assertEquals(1, winner.revision());
            assertEquals(40, blobs(), "Final CAS loser must retain counted immutable records, not claim disk rollback");
            Tile missing = left.status() == Status.OK ? other : TILE;
            assertEquals(Status.MISSING, await(registry.readPage(identity(1), missing)).status());
            Page retry = left.status() == Status.OK ? page(1, 1) : page(1, 0);
            Entry merged = entry(await(registry.record(winner, missing, retry)));
            assertEquals(2, merged.revision());
            assertTrue(await(registry.readPage(identity(1), TILE)).page().orElseThrow().known(0, 0));
            assertTrue(await(registry.readPage(identity(1), other)).page().orElseThrow().known(1, 0));
        } finally { gate.countDown(); await(registry.closeAsync()); }
    }

    @Test void lockAndScaleForksRemainIndependentOfLaterSourceExploration() throws Exception {
        var registry = registry(200);
        try {
            Entry source = entry(await(registry.create(identity(1), 0)));
            source = entry(await(registry.record(source, TILE, page(1, 0))));
            long before = blobs();
            Entry locked = entry(await(registry.fork(source, identity(2), Fork.LOCK)));
            Entry scaled = entry(await(registry.fork(source, identity(3), Fork.SCALE)));
            assertEquals(before, blobs(), "Fork copied terrain");
            assertTrue(locked.locked()); assertEquals(0, locked.scale()); assertEquals(1, scaled.scale());
            assertEquals(source.root(), locked.root()); assertEquals(source.root(), scaled.root());
            Entry updated = entry(await(registry.record(source, TILE, page(2, 0, 1))));
            assertEquals(Status.INVALID_UPDATE, await(registry.record(locked, TILE, page(2, 0, 1))).status());
            assertFalse(await(registry.readPage(identity(2), TILE)).page().orElseThrow().known(1, 0));
            assertFalse(await(registry.readPage(identity(3), TILE)).page().orElseThrow().known(1, 0));
            entry(await(registry.record(scaled, TILE, page(2, 0, 2))));
            assertFalse(await(registry.readPage(identity(1), TILE)).page().orElseThrow().known(2, 0));
            assertEquals(Status.STALE, await(registry.fork(source, identity(4), Fork.LOCK)).status());
            assertEquals(Status.MISSING, await(registry.read(identity(4))).status());
            assertEquals(Status.INVALID_UPDATE, await(registry.fork(locked, identity(5), Fork.SCALE)).status());
            assertEquals(updated, entry(await(registry.read(identity(1)))));
        } finally { await(registry.closeAsync()); }
    }

    @Test void uncertainMetadataCommitRetriesExactlyAcrossReopenAndNeverRewindsNewerEntry() throws Exception {
        AtomicBoolean fail = new AtomicBoolean();
        var store = new NexusSparseMapStore(directory, new Limits(100, 2, 32), Thread::new, kind -> {
            if (kind == NexusSparseMapStore.CommitKind.ROOT && fail.getAndSet(false)) throw new java.io.IOException("Simulated metadata durability error");
        });
        var registry = new NexusMapRootRegistry(store, 8); Entry initial;
        try {
            initial = entry(await(registry.create(identity(1), 0)));
            fail.set(true);
            Result failed = await(registry.record(initial, TILE, page(1, 0)));
            assertEquals(Status.COMMIT_UNCERTAIN, failed.status()); assertTrue(failed.entry().isEmpty());
            assertEquals(1, entry(await(registry.read(identity(1)))).revision());
        } finally { await(registry.closeAsync()); }
        registry = registry(100);
        try {
            long before = blobs(); byte[] committed = Files.readAllBytes(row(1));
            Entry recovered = entry(await(registry.record(initial, TILE, page(1, 0))));
            assertEquals(1, recovered.revision()); assertEquals(before, blobs());
            assertArrayEquals(committed, Files.readAllBytes(row(1)));
            Entry newer = entry(await(registry.record(recovered, TILE, page(2, 0, 1))));
            long after = blobs();
            assertEquals(Status.STALE, await(registry.record(initial, TILE, page(1, 0))).status());
            assertEquals(newer, entry(await(registry.read(identity(1))))); assertEquals(after, blobs());
        } finally { await(registry.closeAsync()); }
    }

    @Test void failedFinalPublicationKeepsOldRowAndRetryReusesUnreachableBlocks() throws Exception {
        AtomicInteger written = new AtomicInteger();
        var store = new NexusSparseMapStore(directory, new Limits(100, 2, 32), Thread::new, kind -> {
            if (kind == NexusSparseMapStore.CommitKind.BLOB && written.incrementAndGet() == 20) {
                Path pending = directory.resolve(".pending");
                Files.createDirectory(pending); Files.write(pending.resolve("blocker"), new byte[]{1});
            }
        });
        var registry = new NexusMapRootRegistry(store, 8);
        try {
            Entry source = entry(await(registry.create(identity(1), 0))); byte[] before = Files.readAllBytes(row(1));
            Result failed = await(registry.record(source, TILE, page(1, 0)));
            assertEquals(Status.IO_FAILURE, failed.status()); assertTrue(failed.entry().isEmpty());
            assertArrayEquals(before, Files.readAllBytes(row(1))); assertEquals(source, entry(await(registry.read(identity(1)))));
            assertEquals(20, blobs());
            Files.delete(directory.resolve(".pending/blocker")); Files.delete(directory.resolve(".pending"));
            Entry retried = entry(await(registry.record(source, TILE, page(1, 0))));
            assertEquals(1, retried.revision()); assertEquals(20, blobs());
        } finally { await(registry.closeAsync()); }
    }

    @Test void uncertainForkRetryKeepsItsOriginalSnapshotEvenAfterSourceAdvances() throws Exception {
        AtomicBoolean fail = new AtomicBoolean();
        var store = new NexusSparseMapStore(directory, new Limits(100, 2, 32), Thread::new, kind -> {
            if (kind == NexusSparseMapStore.CommitKind.ROOT && fail.getAndSet(false)) throw new java.io.IOException("Simulated fork durability error");
        });
        var registry = new NexusMapRootRegistry(store, 8);
        try {
            Entry source = entry(await(registry.create(identity(1), 0)));
            source = entry(await(registry.record(source, TILE, page(1, 0))));
            fail.set(true);
            assertEquals(Status.COMMIT_UNCERTAIN, await(registry.fork(source, identity(2), Fork.LOCK)).status());
            entry(await(registry.record(source, TILE, page(2, 0, 1))));
            Entry recovered = entry(await(registry.fork(source, identity(2), Fork.LOCK)));
            assertEquals(source.root(), recovered.root());
            assertFalse(await(registry.readPage(identity(2), TILE)).page().orElseThrow().known(1, 0));
        } finally { await(registry.closeAsync()); }
    }

    @Test void malformedRowsAndWrongMapIdsAreNeverOverwrittenAsUnregistered() throws Exception {
        var registry = registry(100);
        try {
            Entry source = entry(await(registry.create(identity(1), 0)));
            byte[] original = Files.readAllBytes(row(1));
            for (int offset : new int[]{0, 7, 11, 36, 40, 41, 145, 147, original.length - 1}) {
                byte[] broken = original.clone(); broken[offset] ^= (byte) 0x80;
                if (offset != original.length - 1) {
                    CRC32 crc = new CRC32(); crc.update(broken, 0, broken.length - 4);
                    java.nio.ByteBuffer.wrap(broken).putInt(broken.length - 4, (int) crc.getValue());
                }
                Files.write(row(1), broken);
                assertEquals(Status.CORRUPT, await(registry.read(identity(1))).status());
                assertEquals(Status.CORRUPT, await(registry.create(identity(1), 0)).status());
                assertArrayEquals(broken, Files.readAllBytes(row(1)));
            }
            Files.write(row(1), original); Files.copy(row(1), row(2));
            assertEquals(Status.CORRUPT, await(registry.fork(source, identity(2), Fork.LOCK)).status());
            Files.delete(row(1));
            assertEquals(Status.CORRUPT, await(registry.record(source, TILE, page(1, 0))).status());
            assertFalse(Files.exists(row(1)));
        } finally { await(registry.closeAsync()); }
    }

    @Test void identityMismatchAndFullWorldExpansionRemainRestricted() throws Exception {
        var registry = registry(100);
        try {
            Entry source = entry(await(registry.create(identity(1), 20)));
            Identity forged = new Identity(1, UUID.randomUUID(), "minecraft:overworld", -24, 40);
            assertEquals(Status.INVALID_UPDATE, await(registry.read(forged)).status());
            assertEquals(Status.INVALID_UPDATE, await(registry.fork(source,
                    new Identity(2, UNIT, "minecraft:the_nether", -24, 40), Fork.LOCK)).status());
            assertEquals(Status.INVALID_UPDATE, await(registry.fork(source, identity(2), Fork.SCALE)).status());
            assertEquals(source, entry(await(registry.read(identity(1)))));
            assertThrows(IllegalArgumentException.class, () -> new Identity(-1, UNIT, "minecraft:overworld", 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new Identity(2, UNIT, "INVALID:dimension", 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new Identity(2, UNIT, "x:" + "a".repeat(255), 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new Identity(2, UNIT, "minecraft:overworld", 30_000_000, 0));
        } finally { await(registry.closeAsync()); }
    }

    @Test void registryRowsShareQuotaAndDoNotConsumeAnotherRecordOnCas() throws Exception {
        var registry = registry(21); Entry updated;
        try {
            Entry source = entry(await(registry.create(identity(1), 0)));
            updated = entry(await(registry.record(source, TILE, page(1, 0))));
            assertEquals(Status.CAPACITY, await(registry.fork(updated, identity(2), Fork.LOCK)).status());
            assertEquals(Status.MISSING, await(registry.read(identity(2))).status());
            assertEquals(Status.CAPACITY, await(registry.record(updated, TILE, page(2, 0, 1))).status());
            assertTrue(await(registry.readPage(identity(1), TILE)).page().orElseThrow().known(0, 0));
            assertEquals(20, blobs());
        } finally { await(registry.closeAsync()); }
        registry = registry(21);
        try {
            assertEquals(updated, entry(await(registry.read(identity(1)))));
            assertEquals(Status.CAPACITY, await(registry.create(identity(3), 0)).status());
        } finally { await(registry.closeAsync()); }
    }

    @Test void revisionOverflowIsRefusedBeforeIndexWrites() throws Exception {
        var registry = registry(100);
        try {
            Entry source = entry(await(registry.create(identity(1), 0)));
            Entry exhausted = new Entry(source.identity(), source.scale(), false, Long.MAX_VALUE,
                    source.root(), source.previousRoot(), source.operation());
            byte[] bytes = NexusMapRootRegistry.encode(exhausted); Files.write(row(1), bytes);
            assertEquals(exhausted, entry(await(registry.read(identity(1)))));
            assertEquals(Status.INVALID_UPDATE, await(registry.record(exhausted, TILE, page(1, 0))).status());
            assertArrayEquals(bytes, Files.readAllBytes(row(1))); assertEquals(0, blobs());
        } finally { await(registry.closeAsync()); }
    }

    @Test void closeDrainsRecordIncludingFinalCasAndBackpressureIsBounded() throws Exception {
        var registry = registry(100); Entry source;
        try { source = entry(await(registry.create(identity(1), 0))); }
        finally { await(registry.closeAsync()); }
        CountDownLatch gate = new CountDownLatch(1);
        registry = new NexusMapRootRegistry(gated(gate), 1);
        try {
            var accepted = registry.record(source, TILE, page(1, 0));
            assertEquals(Status.QUEUE_FULL, await(registry.read(identity(1))).status());
            var close = registry.closeAsync(); assertSame(close, registry.closeAsync()); assertFalse(close.isDone());
            gate.countDown();
            assertEquals(1, entry(await(accepted)).revision()); await(close);
            assertEquals(Status.CLOSED, await(registry.read(identity(1))).status());
        } finally { gate.countDown(); await(registry.closeAsync()); }
        registry = registry(100);
        try { assertTrue(await(registry.readPage(identity(1), TILE)).page().orElseThrow().known(0, 0)); }
        finally { await(registry.closeAsync()); }
    }

    @Test void cancelledResponseStillDrainsItsPublicationWithoutLeakingOperationPermit() throws Exception {
        var registry = registry(100); Entry source;
        try { source = entry(await(registry.create(identity(1), 0))); }
        finally { await(registry.closeAsync()); }
        CountDownLatch gate = new CountDownLatch(1);
        registry = new NexusMapRootRegistry(gated(gate), 1);
        try {
            var accepted = registry.record(source, TILE, page(1, 0)); assertTrue(accepted.cancel(false));
            var close = registry.closeAsync(); gate.countDown(); await(close); assertTrue(accepted.isCancelled());
        } finally { gate.countDown(); await(registry.closeAsync()); }
        registry = registry(100);
        try { assertEquals(1, entry(await(registry.read(identity(1)))).revision()); }
        finally { await(registry.closeAsync()); }
    }
}
