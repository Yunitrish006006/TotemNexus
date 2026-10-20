package dev.totem.nexus.map;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static dev.totem.nexus.map.NexusSparseMapStore.*;

/**
 * Persistent immutable quadtree. A root is a storage snapshot, never authorization.
 * Native MapId/root persistence, root CAS and orphan collection belong to integration.
 * This index owns its store lifecycle; callbacks must not block the IO worker.
 */
public final class NexusImmutableMapIndex implements AutoCloseable {
    public static final int DEPTH = 19;
    public static final int MAX_BLOCKS_PER_UPDATE = DEPTH + 1;
    private static final String EMPTY_HASH = "0".repeat(64);
    private static final int MIN_TILE = Key.at(0, -30_000_000, 0).tileX();
    private static final int NODE_MAGIC = 0x4e584e44, PAGE_MAGIC = 0x4e585047, VERSION = 1;
    private static final int NODE_BYTES = 8 + 4 * 32;
    private static final int PAGE_BYTES = 24 + PIXELS + COVERAGE_BYTES;

    public record Root(String hash) {
        public Root {
            if (hash == null || !hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid snapshot root");
        }
        public static Root empty() { return new Root(EMPTY_HASH); }
    }
    public record Tile(int x, int z) {
        public Tile { new Key(0, x, z); }
        public static Tile at(int worldX, int worldZ) {
            Key key = Key.at(0, worldX, worldZ);
            return new Tile(key.tileX(), key.tileZ());
        }
        private int quadrant(int depth) {
            int shift = DEPTH - 1 - depth;
            return (((x - MIN_TILE) >>> shift) & 1) * 2 + (((z - MIN_TILE) >>> shift) & 1);
        }
    }
    public record UpdateResult(Status status, Optional<Root> root) {
        public UpdateResult {
            if ((status == Status.OK) != root.isPresent()) throw new IllegalArgumentException("Invalid update result");
        }
    }
    private record Walk(Status status, List<String[]> parents, Optional<Page> page) {}

    private final NexusSparseMapStore store;
    private final int maxInFlight;
    private final Object gate = new Object();
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private int inFlight;
    private boolean closing, closingStore;

    public NexusImmutableMapIndex(NexusSparseMapStore store, int maxInFlight) {
        if (maxInFlight < 1 || maxInFlight > 256) throw new IllegalArgumentException("Invalid logical operation bound");
        this.store = java.util.Objects.requireNonNull(store); this.maxInFlight = maxInFlight;
    }

    public CompletableFuture<ReadResult> read(Root root, Tile tile) {
        java.util.Objects.requireNonNull(root); java.util.Objects.requireNonNull(tile);
        return bounded(() -> walk(root.hash(), tile, 0, new ArrayList<>()).thenApply(walk ->
                        new ReadResult(walk.status(), walk.page())),
                new ReadResult(Status.CLOSED, Optional.empty()), new ReadResult(Status.QUEUE_FULL, Optional.empty()));
    }
    /**
     * Each update copies at most 19 index nodes and one page, regardless of snapshot count.
     * Lost coverage and stale same-leaf revisions are refused before blob writes.
     */
    public CompletableFuture<UpdateResult> put(Root root, Tile tile, Page page) {
        java.util.Objects.requireNonNull(root); java.util.Objects.requireNonNull(tile); java.util.Objects.requireNonNull(page);
        return bounded(() -> walk(root.hash(), tile, 0, new ArrayList<>()).thenCompose(walk -> {
            if (walk.status() != Status.OK && walk.status() != Status.MISSING) return updateFailure(walk.status());
            if (walk.page().isPresent()) {
                Page prior = walk.page().get();
                if (page.revision() < prior.revision()) return updateFailure(Status.STALE);
                byte[] beforeColors = prior.colors(), afterColors = page.colors();
                byte[] beforeMask = prior.coverage(), afterMask = page.coverage();
                if (page.revision() == prior.revision()) {
                    if (!Arrays.equals(beforeColors, afterColors) || !Arrays.equals(beforeMask, afterMask))
                        return updateFailure(Status.STALE);
                    // The root's blocks were read and verified; confirm directory durability too.
                    return store.writeBlobs(List.of(encodePage(tile, page))).thenApply(status ->
                            status == Status.OK ? new UpdateResult(status, Optional.of(root)) : new UpdateResult(status, Optional.empty()));
                }
                for (int i = 0; i < beforeMask.length; i++)
                    if ((beforeMask[i] & ~afterMask[i]) != 0) return updateFailure(Status.INVALID_UPDATE);
            }
            List<byte[]> blocks = new ArrayList<>(MAX_BLOCKS_PER_UPDATE);
            byte[] leaf = encodePage(tile, page);
            blocks.add(leaf);
            String hash = contentHash(leaf);
            for (int depth = DEPTH - 1; depth >= 0; depth--) {
                String[] children = walk.parents().get(depth).clone();
                children[tile.quadrant(depth)] = hash;
                byte[] node = encodeNode(depth, children);
                blocks.add(node); hash = contentHash(node);
            }
            Root next = new Root(hash);
            return store.writeBlobs(blocks).thenApply(status -> new UpdateResult(status,
                    status == Status.OK ? Optional.of(next) : Optional.empty()));
        }), new UpdateResult(Status.CLOSED, Optional.empty()), new UpdateResult(Status.QUEUE_FULL, Optional.empty()));
    }

    private CompletableFuture<Walk> walk(String hash, Tile tile, int depth, List<String[]> parents) {
        if (hash.equals(EMPTY_HASH)) {
            while (parents.size() < DEPTH) {
                String[] children = new String[4]; Arrays.fill(children, EMPTY_HASH); parents.add(children);
            }
            return CompletableFuture.completedFuture(new Walk(Status.MISSING, parents, Optional.empty()));
        }
        return store.readBlob(hash).thenCompose(result -> {
            if (result.status() != Status.OK) {
                // A missing referenced block is corruption, distinct from an explicit empty child.
                Status status = result.status() == Status.MISSING ? Status.CORRUPT : result.status();
                return CompletableFuture.completedFuture(new Walk(status, parents, Optional.empty()));
            }
            byte[] bytes = result.blob().orElseThrow().bytes();
            if (depth == DEPTH) {
                Page page = decodePage(tile, bytes);
                return CompletableFuture.completedFuture(new Walk(page == null ? Status.CORRUPT : Status.OK,
                        parents, Optional.ofNullable(page)));
            }
            String[] children = decodeNode(depth, bytes);
            if (children == null) return CompletableFuture.completedFuture(new Walk(Status.CORRUPT, parents, Optional.empty()));
            parents.add(children);
            return walk(children[tile.quadrant(depth)], tile, depth + 1, parents);
        });
    }
    private static byte[] encodeNode(int depth, String[] children) {
        ByteBuffer data = ByteBuffer.allocate(NODE_BYTES);
        data.putInt(NODE_MAGIC).put((byte) VERSION).put((byte) depth).putShort((short) 0);
        for (String child : children) data.put(HexFormat.of().parseHex(child));
        return data.array();
    }
    private static String[] decodeNode(int depth, byte[] bytes) {
        if (bytes.length != NODE_BYTES) return null;
        ByteBuffer data = ByteBuffer.wrap(bytes);
        if (data.getInt() != NODE_MAGIC || data.get() != VERSION || data.get() != depth || data.getShort() != 0) return null;
        String[] children = new String[4]; boolean any = false;
        for (int i = 0; i < children.length; i++) {
            byte[] hash = new byte[32]; data.get(hash); children[i] = HexFormat.of().formatHex(hash);
            any |= !children[i].equals(EMPTY_HASH);
        }
        return any ? children : null;
    }
    private static byte[] encodePage(Tile tile, Page page) {
        return ByteBuffer.allocate(PAGE_BYTES).putInt(PAGE_MAGIC).putInt(VERSION).putInt(tile.x()).putInt(tile.z())
                .putLong(page.revision()).put(page.colors()).put(page.coverage()).array();
    }
    private static Page decodePage(Tile tile, byte[] bytes) {
        if (bytes.length != PAGE_BYTES) return null;
        ByteBuffer data = ByteBuffer.wrap(bytes);
        if (data.getInt() != PAGE_MAGIC || data.getInt() != VERSION || data.getInt() != tile.x() || data.getInt() != tile.z()) return null;
        long revision = data.getLong();
        if (revision < 0) return null;
        byte[] colors = new byte[PIXELS], coverage = new byte[COVERAGE_BYTES];
        data.get(colors); data.get(coverage);
        return new Page(revision, colors, coverage);
    }
    private static CompletableFuture<UpdateResult> updateFailure(Status status) {
        return CompletableFuture.completedFuture(new UpdateResult(status, Optional.empty()));
    }
    private <T> CompletableFuture<T> bounded(Supplier<CompletableFuture<T>> action, T closedValue, T fullValue) {
        synchronized (gate) {
            if (closing) return CompletableFuture.completedFuture(closedValue);
            if (inFlight >= maxInFlight) return CompletableFuture.completedFuture(fullValue);
            inFlight++;
        }
        CompletableFuture<T> response = new CompletableFuture<>();
        CompletableFuture<T> operation;
        try { operation = action.get(); }
        catch (Throwable failure) { response.completeExceptionally(failure); finish(); return response; }
        // The caller may cancel response; that must not cancel draining or leak the logical permit.
        operation.whenComplete((value, error) -> {
            try {
                if (error == null) response.complete(value); else response.completeExceptionally(error);
            } finally { finish(); }
        });
        return response;
    }
    private void finish() {
        boolean closeStore;
        synchronized (gate) {
            inFlight--;
            closeStore = closing && inFlight == 0 && !closingStore;
            if (closeStore) closingStore = true;
        }
        if (closeStore) closeStore();
    }
    private void closeStore() {
        store.closeAsync().whenComplete((value, error) -> {
            if (error == null) closed.complete(null); else closed.completeExceptionally(error);
        });
    }
    /** Drain complete logical operations before stopping the shared IO worker. */
    public CompletableFuture<Void> closeAsync() {
        boolean closeStore;
        synchronized (gate) {
            closing = true;
            closeStore = inFlight == 0 && !closingStore;
            if (closeStore) closingStore = true;
        }
        if (closeStore) closeStore();
        return closed;
    }
    @Override public void close() { closeAsync(); }
}
