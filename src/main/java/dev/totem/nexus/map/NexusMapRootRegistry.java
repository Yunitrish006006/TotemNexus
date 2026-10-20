package dev.totem.nexus.map;

import dev.totem.nexus.space.NexusMapExtent;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.zip.CRC32;

import static dev.totem.nexus.map.NexusImmutableMapIndex.*;
import static dev.totem.nexus.map.NexusSparseMapStore.*;

/**
 * Server-privileged storage registry, not native-ledger or player authorization.
 * Integration must validate identities and distinguish new/migrating maps from lost rows.
 * This owner drains complete logical operations before closing its index and IO store.
 */
public final class NexusMapRootRegistry implements AutoCloseable {
    private static final int MAGIC = 0x4e585247, VERSION = 1, BASE_BYTES = 151;
    static final int MIN_BYTES = BASE_BYTES + 1, MAX_BYTES = BASE_BYTES + 256;
    public record Identity(int mapId, UUID unitId, String dimension, int centerX, int centerZ) {
        public Identity {
            if (mapId < 0 || unitId == null || dimension == null || dimension.length() > 256
                    || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                throw new IllegalArgumentException("Invalid map registry identity");
            new NexusMapExtent(centerX, centerZ, 0);
        }
        private boolean sameAnchor(Identity other) {
            return unitId.equals(other.unitId) && dimension.equals(other.dimension)
                    && centerX == other.centerX && centerZ == other.centerZ;
        }
    }
    public record Entry(Identity identity, int scale, boolean locked, long revision,
                        Root root, Root previousRoot, String operation) {
        public Entry {
            java.util.Objects.requireNonNull(identity); java.util.Objects.requireNonNull(root);
            java.util.Objects.requireNonNull(previousRoot); new Root(operation);
            if (revision < 0) throw new IllegalArgumentException("Invalid registry revision");
            new NexusMapExtent(identity.centerX(), identity.centerZ(), scale);
        }
    }
    public record Result(Status status, Optional<Entry> entry) {
        public Result {
            if ((status == Status.OK) != entry.isPresent()) throw new IllegalArgumentException("Invalid registry result");
        }
    }
    public enum Fork { SCALE, LOCK }

    private final NexusSparseMapStore store;
    private final NexusImmutableMapIndex index;
    private final int maxInFlight;
    private final Object gate = new Object();
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    private int inFlight;
    private boolean closing, closingIndex;

    public NexusMapRootRegistry(NexusSparseMapStore store, int maxInFlight) {
        if (maxInFlight < 1 || maxInFlight > 256) throw new IllegalArgumentException("Invalid registry operation bound");
        this.store = java.util.Objects.requireNonNull(store); this.maxInFlight = maxInFlight;
        index = new NexusImmutableMapIndex(store, maxInFlight);
    }
    public CompletableFuture<Result> read(Identity identity) {
        return bounded(() -> readIdentity(identity));
    }
    private CompletableFuture<Result> readIdentity(Identity identity) {
        return store.readRegistration(identity.mapId()).thenApply(result -> result.status() == Status.OK
                && !result.entry().orElseThrow().identity().equals(identity) ? failure(Status.INVALID_UPDATE) : result);
    }
    /** Only new, server-proven registrations may use this; never auto-create lost old-map rows. */
    public CompletableFuture<Result> create(Identity identity, int scale) {
        Entry provisional = new Entry(identity, scale, false, 0, Root.empty(), Root.empty(), Root.empty().hash());
        Entry initial = withOperation(provisional, fingerprint("CREATE", encode(provisional)));
        return bounded(() -> store.compareRegistration(Optional.empty(), initial));
    }
    public CompletableFuture<ReadResult> readPage(Identity identity, Tile tile) {
        return boundedRead(() -> readIdentity(identity).thenCompose(result -> result.status() == Status.OK
                ? index.read(result.entry().orElseThrow().root(), tile)
                : CompletableFuture.completedFuture(new ReadResult(result.status(), Optional.empty()))));
    }
    public CompletableFuture<Result> record(Entry expected, Tile tile, Page page) {
        java.util.Objects.requireNonNull(expected); java.util.Objects.requireNonNull(tile); java.util.Objects.requireNonNull(page);
        return bounded(() -> {
            if (expected.locked() || expected.revision() == Long.MAX_VALUE) return completed(Status.INVALID_UPDATE);
            byte[] point = ByteBuffer.allocate(8).putInt(tile.x()).putInt(tile.z()).array();
            byte[] terrain = ByteBuffer.allocate(8 + PIXELS + COVERAGE_BYTES)
                    .putLong(page.revision()).put(page.colors()).put(page.coverage()).array();
            String receipt = fingerprint("RECORD", encode(expected), point, terrain);
            return readIdentity(expected.identity()).thenCompose(current -> {
                if (current.status() != Status.OK)
                    return completed(current.status() == Status.MISSING ? Status.CORRUPT : current.status());
                Entry actual = current.entry().orElseThrow();
                boolean retry = actual.revision() == expected.revision() + 1
                        && actual.scale() == expected.scale() && actual.locked() == expected.locked()
                        && actual.previousRoot().equals(expected.root()) && actual.operation().equals(receipt);
                if (!actual.equals(expected) && !retry) return completed(Status.STALE);
                return index.put(expected.root(), tile, page).thenCompose(update -> {
                    if (update.status() != Status.OK) return completed(update.status());
                    Root nextRoot = update.root().orElseThrow();
                    if (nextRoot.equals(expected.root())) return store.compareRegistration(Optional.of(expected), expected);
                    Entry next = new Entry(expected.identity(), expected.scale(), false,
                            Math.incrementExact(expected.revision()), nextRoot, expected.root(), receipt);
                    return store.compareRegistration(Optional.of(expected), next);
                });
            });
        });
    }
    public CompletableFuture<Result> fork(Entry expectedSource, Identity target, Fork operation) {
        java.util.Objects.requireNonNull(expectedSource); java.util.Objects.requireNonNull(target); java.util.Objects.requireNonNull(operation);
        return bounded(() -> {
            if (expectedSource.locked() || expectedSource.identity().mapId() == target.mapId()
                    || !expectedSource.identity().sameAnchor(target)) return completed(Status.INVALID_UPDATE);
            int scale = expectedSource.scale();
            if (operation == Fork.SCALE) {
                var expanded = new NexusMapExtent(target.centerX(), target.centerZ(), scale).expanded();
                if (expanded.isEmpty()) return completed(Status.INVALID_UPDATE);
                scale = expanded.get().scale();
            }
            Entry provisional = new Entry(target, scale, operation == Fork.LOCK, 0,
                    expectedSource.root(), Root.empty(), Root.empty().hash());
            Entry next = withOperation(provisional, fingerprint("FORK_" + operation.name(), encode(expectedSource), encode(provisional)));
            return store.forkRegistration(expectedSource, next);
        });
    }
    private static Entry withOperation(Entry entry, String operation) {
        return new Entry(entry.identity(), entry.scale(), entry.locked(), entry.revision(), entry.root(), entry.previousRoot(), operation);
    }
    static boolean validCompare(Optional<Entry> expected, Entry next) {
        if (expected.isEmpty()) return next.revision() == 0 && !next.locked()
                && next.root().equals(Root.empty()) && next.previousRoot().equals(Root.empty());
        Entry old = expected.get();
        if (!old.identity().equals(next.identity()) || old.scale() != next.scale() || old.locked() != next.locked()) return false;
        if (old.equals(next)) return true;
        return !old.locked() && old.revision() != Long.MAX_VALUE && next.revision() == old.revision() + 1
                && next.previousRoot().equals(old.root());
    }
    static boolean validFork(Entry source, Entry target) {
        if (source.locked() || source.identity().mapId() == target.identity().mapId()
                || !source.identity().sameAnchor(target.identity()) || target.revision() != 0
                || !target.root().equals(source.root()) || !target.previousRoot().equals(Root.empty())) return false;
        if (target.locked()) return target.scale() == source.scale();
        return new NexusMapExtent(source.identity().centerX(), source.identity().centerZ(), source.scale())
                .expanded().filter(extent -> extent.scale() == target.scale()).isPresent();
    }
    static byte[] encode(Entry entry) {
        byte[] dimension = entry.identity().dimension().getBytes(StandardCharsets.US_ASCII);
        ByteBuffer data = ByteBuffer.allocate(BASE_BYTES + dimension.length);
        Identity identity = entry.identity();
        data.putInt(MAGIC).putInt(VERSION).putInt(identity.mapId());
        data.putLong(identity.unitId().getMostSignificantBits()).putLong(identity.unitId().getLeastSignificantBits());
        data.putInt(identity.centerX()).putInt(identity.centerZ()).putInt(entry.scale()).put((byte) (entry.locked() ? 1 : 0));
        data.putLong(entry.revision()).put(HexFormat.of().parseHex(entry.root().hash()))
                .put(HexFormat.of().parseHex(entry.previousRoot().hash())).put(HexFormat.of().parseHex(entry.operation()));
        data.putShort((short) dimension.length).put(dimension);
        CRC32 crc = new CRC32(); crc.update(data.array(), 0, data.capacity() - 4);
        data.putInt((int) crc.getValue()); return data.array();
    }
    static Entry decode(byte[] bytes, int mapId) {
        if (bytes.length < MIN_BYTES || bytes.length > MAX_BYTES) return null;
        CRC32 crc = new CRC32(); crc.update(bytes, 0, bytes.length - 4);
        ByteBuffer data = ByteBuffer.wrap(bytes);
        try {
            if (data.getInt() != MAGIC || data.getInt() != VERSION || data.getInt() != mapId) return null;
            UUID unit = new UUID(data.getLong(), data.getLong());
            int x = data.getInt(), z = data.getInt(), scale = data.getInt(); byte locked = data.get();
            if (locked != 0 && locked != 1) return null;
            long revision = data.getLong();
            String root = hash(data), previous = hash(data), operation = hash(data);
            int length = Short.toUnsignedInt(data.getShort());
            if (length < 1 || length > 256 || data.remaining() != length + 4) return null;
            byte[] dimension = new byte[length]; data.get(dimension);
            if (data.getInt() != (int) crc.getValue()) return null;
            return new Entry(new Identity(mapId, unit, new String(dimension, StandardCharsets.US_ASCII), x, z),
                    scale, locked == 1, revision, new Root(root), new Root(previous), operation);
        } catch (IllegalArgumentException | java.nio.BufferUnderflowException failure) { return null; }
    }
    private static String hash(ByteBuffer data) {
        byte[] hash = new byte[32]; data.get(hash); return HexFormat.of().formatHex(hash);
    }
    private static String fingerprint(String operation, byte[]... fields) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(new byte[]{'N', 'X', 'R', 'O', 1});
            byte[][] values = new byte[fields.length + 1][];
            values[0] = operation.getBytes(StandardCharsets.US_ASCII); System.arraycopy(fields, 0, values, 1, fields.length);
            for (byte[] field : values) {
                digest.update(ByteBuffer.allocate(4).putInt(field.length).array()); digest.update(field);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static Result failure(Status status) { return new Result(status, Optional.empty()); }
    private static CompletableFuture<Result> completed(Status status) { return CompletableFuture.completedFuture(failure(status)); }
    private CompletableFuture<Result> bounded(Supplier<CompletableFuture<Result>> operation) {
        return bounded(operation, failure(Status.CLOSED), failure(Status.QUEUE_FULL));
    }
    private CompletableFuture<ReadResult> boundedRead(Supplier<CompletableFuture<ReadResult>> operation) {
        return bounded(operation, new ReadResult(Status.CLOSED, Optional.empty()), new ReadResult(Status.QUEUE_FULL, Optional.empty()));
    }
    private <T> CompletableFuture<T> bounded(Supplier<CompletableFuture<T>> operation, T closedValue, T fullValue) {
        synchronized (gate) {
            if (closing) return CompletableFuture.completedFuture(closedValue);
            if (inFlight >= maxInFlight) return CompletableFuture.completedFuture(fullValue);
            inFlight++;
        }
        CompletableFuture<T> response = new CompletableFuture<>();
        CompletableFuture<T> work;
        try { work = operation.get(); }
        catch (Throwable failure) { response.completeExceptionally(failure); finish(); return response; }
        work.whenComplete((value, failure) -> {
            try { if (failure == null) response.complete(value); else response.completeExceptionally(failure); }
            finally { finish(); }
        });
        return response;
    }
    private void finish() {
        boolean close;
        synchronized (gate) { inFlight--; close = closing && inFlight == 0 && !closingIndex; if (close) closingIndex = true; }
        if (close) closeIndex();
    }
    private void closeIndex() {
        index.closeAsync().whenComplete((value, failure) -> {
            if (failure == null) closed.complete(null); else closed.completeExceptionally(failure);
        });
    }
    public CompletableFuture<Void> closeAsync() {
        boolean close;
        synchronized (gate) { closing = true; close = inFlight == 0 && !closingIndex; if (close) closingIndex = true; }
        if (close) closeIndex(); return closed;
    }
    @Override public void close() { closeAsync(); }
}
