package dev.totem.nexus.map;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

/**
 * Standalone, single-owner asynchronous page storage. No Minecraft objects or world IO
 * run on a caller thread. Integration must supply server-authorized map identities and
 * migrate/derive pages explicitly; a checksum is not an authorization credential.
 * Owner IDs name server-assigned storage namespaces, not native item MapIds. A later
 * snapshot index must freeze shared namespaces and allocate new ones for COW writes.
 * The owner must await closeAsync at controlled shutdown before terminating its JVM.
 */
public final class NexusSparseMapStore implements AutoCloseable {
    public static final int SIDE = 128;
    public static final int PIXELS = SIDE * SIDE;
    public static final int COVERAGE_BYTES = PIXELS / 8;
    private static final int MAGIC = 0x4e585350;
    private static final int VERSION = 1;
    private static final int FILE_BYTES = 5 * Integer.BYTES + Long.BYTES + PIXELS + COVERAGE_BYTES + Integer.BYTES;
    private static final int MIN_TILE = Math.floorDiv(-30_000_000, SIDE);
    private static final int MAX_TILE = Math.floorDiv(29_999_999, SIDE);

    public enum Status {
        OK, MISSING, CAPACITY, QUEUE_FULL, CORRUPT, IO_FAILURE, STALE, INVALID_UPDATE, CLOSED,
        /** Atomic replacement committed, but directory durability could not be confirmed. */
        COMMIT_UNCERTAIN
    }
    public record ReadResult(Status status, Optional<Page> page) {
        public ReadResult {
            if ((status == Status.OK) != page.isPresent()) throw new IllegalArgumentException("Invalid read result");
        }
    }
    public static final int MAX_BLOB_BYTES = PIXELS + COVERAGE_BYTES + 64;
    public static final int MAX_BATCH_RECORDS = 20;
    public static final int MAX_BATCH_BYTES = 65_536;
    public static final class Blob {
        private final byte[] bytes;
        private Blob(byte[] bytes) { this.bytes = bytes.clone(); }
        public byte[] bytes() { return bytes.clone(); }
    }
    public record BlobResult(Status status, Optional<Blob> blob) {
        public BlobResult {
            if ((status == Status.OK) != blob.isPresent()) throw new IllegalArgumentException("Invalid blob result");
        }
    }
    /** maxPages counts both legacy namespace pages and immutable page/index records. */
    public record Limits(long maxPages, int cachedPages, int pendingOperations) {
        public Limits {
            if (maxPages < 0 || cachedPages < 1 || cachedPages > 256
                    || pendingOperations < 1 || pendingOperations > 256)
                throw new IllegalArgumentException("Invalid sparse store limits");
        }
    }
    public record Key(int ownerId, int tileX, int tileZ) {
        public Key {
            if (ownerId < 0 || tileX < MIN_TILE || tileX > MAX_TILE || tileZ < MIN_TILE || tileZ > MAX_TILE)
                throw new IllegalArgumentException("Invalid sparse page key");
        }
        public static Key at(int ownerId, int worldX, int worldZ) {
            if (worldX < -30_000_000 || worldX >= 30_000_000 || worldZ < -30_000_000 || worldZ >= 30_000_000)
                throw new IllegalArgumentException("Outside supported world");
            return new Key(ownerId, Math.floorDiv(worldX, SIDE), Math.floorDiv(worldZ, SIDE));
        }
    }
    public static final class Page {
        private final long revision;
        private final byte[] colors;
        private final byte[] coverage;
        public Page(long revision, byte[] colors, byte[] coverage) {
            if (revision < 0 || colors.length != PIXELS || coverage.length != COVERAGE_BYTES)
                throw new IllegalArgumentException("Invalid sparse page");
            this.revision = revision; this.colors = colors.clone(); this.coverage = coverage.clone();
        }
        public long revision() { return revision; }
        public byte[] colors() { return colors.clone(); }
        public byte[] coverage() { return coverage.clone(); }
        public boolean known(int x, int z) {
            if (x < 0 || x >= SIDE || z < 0 || z >= SIDE) throw new IllegalArgumentException("Invalid page pixel");
            int index = x + z * SIDE;
            return (coverage[index / 8] & (1 << (index % 8))) != 0;
        }
        private boolean sameContent(Page other) {
            return Arrays.equals(colors, other.colors) && Arrays.equals(coverage, other.coverage);
        }
    }

    private static final java.util.Set<Path> OWNERS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Path root;
    private final Limits limits;
    enum CommitKind { BLOB, ROOT }
    @FunctionalInterface interface CommitObserver { void committed(CommitKind kind) throws IOException; }
    private final CommitObserver observer;
    private final ThreadPoolExecutor worker;
    private final AtomicBoolean closing = new AtomicBoolean();
    private final CompletableFuture<Void> closed = new CompletableFuture<>();
    // Worker-owned state only. The directory layout is the disk index; no global resident index.
    private final LinkedHashMap<Key, Page> cache = new LinkedHashMap<>(16, .75f, true);
    private final LinkedHashMap<String, Blob> blobCache = new LinkedHashMap<>(16, .75f, true);
    private long persistedPages;
    private boolean quotaKnown;
    private boolean writable;
    private FileChannel lockChannel;
    private FileLock fileLock;
    private Path ownedPath;

    public NexusSparseMapStore(Path root, Limits limits) {
        this(root, limits, runnable -> {
            Thread thread = new Thread(runnable, "nexus-sparse-map-io");
            thread.setDaemon(true);
            return thread;
        });
    }
    NexusSparseMapStore(Path root, Limits limits, ThreadFactory threads) {
        this(root, limits, threads, kind -> {});
    }
    NexusSparseMapStore(Path root, Limits limits, ThreadFactory threads, CommitObserver observer) {
        this.observer = java.util.Objects.requireNonNull(observer);
        this.root = root.toAbsolutePath().normalize(); this.limits = limits;
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(limits.pendingOperations()), threads) {
            @Override protected void terminated() {
                IOException error = null;
                try { if (fileLock != null) fileLock.release(); }
                catch (IOException failure) { error = failure; }
                try { if (lockChannel != null) lockChannel.close(); }
                catch (IOException failure) {
                    if (error == null) error = failure; else error.addSuppressed(failure);
                } finally {
                    cache.clear(); blobCache.clear();
                    if (ownedPath != null) OWNERS.remove(ownedPath);
                }
                // A completed close must permit immediate reopening by another owner.
                if (error == null) closed.complete(null); else closed.completeExceptionally(error);
            }
        };
        worker.execute(this::initialize);
    }

    private void initialize() {
        try {
            Files.createDirectories(root);
            rejectSymlink(root);
            Path realRoot = root.toRealPath();
            // Avoid opening a second JVM channel: closing it can release another channel's OS lock.
            if (!OWNERS.add(realRoot)) throw new IOException("Sparse store already owned in this JVM");
            ownedPath = realRoot;
            lockChannel = FileChannel.open(root.resolve(".lock"), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
            try { fileLock = lockChannel.tryLock(); }
            catch (OverlappingFileLockException failure) { throw new IOException("Sparse store already owned", failure); }
            if (fileLock == null) throw new IOException("Sparse store already owned");
            forceDirectory(root.getParent());
            writable = true;
            refreshQuotaNow();
        } catch (IOException failure) {
            // Existing known-path pages remain readable; never guess free capacity.
            quotaKnown = false;
        }
    }

    /** Retry initialization metadata after an IO problem without loading terrain or blocking callers. */
    public CompletableFuture<Status> refreshQuota() {
        return submit(this::refreshQuotaNow, Status.CLOSED, Status.QUEUE_FULL);
    }
    private Status refreshQuotaNow() {
        if (!writable) return Status.IO_FAILURE;
        try {
            // One fixed staging file bounds leftovers even if cleanup repeatedly fails.
            Files.deleteIfExists(root.resolve(".pending"));
            long count = 0;
            try (var paths = Files.walk(root, 1)) {
                var iterator = paths.iterator();
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    if (path.getFileName().toString().endsWith(".nmp") || path.getFileName().toString().endsWith(".nmb") || path.getFileName().toString().endsWith(".nmr")) {
                        // Do not let metadata errors or damaged/symlink page entries appear as free quota.
                        Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        count++;
                    }
                }
            }
            persistedPages = count; quotaKnown = true;
            return Status.OK;
        } catch (IOException | java.io.UncheckedIOException failure) {
            quotaKnown = false;
            return Status.IO_FAILURE;
        }
    }

    public CompletableFuture<ReadResult> read(Key key) {
        return submit(() -> readNow(key), new ReadResult(Status.CLOSED, Optional.empty()),
                new ReadResult(Status.QUEUE_FULL, Optional.empty()));
    }
    public CompletableFuture<Status> write(Key key, Page page) {
        java.util.Objects.requireNonNull(page);
        return submit(() -> writeNow(key, page), Status.CLOSED, Status.QUEUE_FULL);
    }
    public static String contentHash(byte[] bytes) {
        if (bytes.length < 1 || bytes.length > MAX_BLOB_BYTES) throw new IllegalArgumentException("Invalid blob length");
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void requireHash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid blob hash");
    }
    public CompletableFuture<BlobResult> readBlob(String hash) {
        requireHash(hash);
        return submit(() -> readBlobNow(hash), new BlobResult(Status.CLOSED, Optional.empty()),
                new BlobResult(Status.QUEUE_FULL, Optional.empty()));
    }
    /**
     * Capacity/corruption is checked for the complete deduplicated batch before writes.
     * IO failure may leave counted immutable records; it never means physical rollback.
     * Callers must publish a new root only after OK, and retry identical data to recover.
     */
    public CompletableFuture<Status> writeBlobs(java.util.List<byte[]> records) {
        if (records.isEmpty() || records.size() > MAX_BATCH_RECORDS) throw new IllegalArgumentException("Invalid batch size");
        int total = 0;
        var batch = new LinkedHashMap<String, Blob>();
        for (byte[] bytes : records) {
            total = Math.addExact(total, bytes.length);
            if (total > MAX_BATCH_BYTES) throw new IllegalArgumentException("Oversized blob batch");
            Blob immutable = new Blob(bytes);
            batch.put(contentHash(immutable.bytes), immutable);
        }
        return submit(() -> writeBlobsNow(batch), Status.CLOSED, Status.QUEUE_FULL);
    }
    private BlobResult readBlobNow(String hash) { return readBlobNow(hash, true); }
    private BlobResult readBlobNow(String hash, boolean useCache) {
        Blob hit = useCache ? blobCache.get(hash) : null;
        if (hit != null) return new BlobResult(Status.OK, Optional.of(hit));
        try {
            checkRoot();
            Path path = root.resolve(hash + ".nmb");
            var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) return new BlobResult(Status.CORRUPT, Optional.empty());
            byte[] bytes;
            try (FileChannel file = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                long size = file.size();
                if (size < 1 || size > MAX_BLOB_BYTES) return new BlobResult(Status.CORRUPT, Optional.empty());
                ByteBuffer data = ByteBuffer.allocate((int) size);
                while (data.hasRemaining()) if (file.read(data) < 0) return new BlobResult(Status.CORRUPT, Optional.empty());
                bytes = data.array();
            }
            if (!contentHash(bytes).equals(hash)) return new BlobResult(Status.CORRUPT, Optional.empty());
            Blob blob = new Blob(bytes); cacheBlob(hash, blob);
            return new BlobResult(Status.OK, Optional.of(blob));
        } catch (NoSuchFileException failure) { return new BlobResult(Status.MISSING, Optional.empty()); }
        catch (IOException failure) { return new BlobResult(Status.IO_FAILURE, Optional.empty()); }
    }
    private Status writeBlobsNow(LinkedHashMap<String, Blob> batch) {
        if (!writable) return Status.IO_FAILURE;
        var missing = new java.util.ArrayList<java.util.Map.Entry<String, Blob>>();
        for (var entry : batch.entrySet()) {
            BlobResult old = readBlobNow(entry.getKey(), false);
            if (old.status() == Status.MISSING) missing.add(entry);
            else if (old.status() != Status.OK) return old.status();
            else if (!Arrays.equals(old.blob().orElseThrow().bytes, entry.getValue().bytes)) return Status.CORRUPT;
        }
        if (!missing.isEmpty() && !quotaKnown) return Status.IO_FAILURE;
        if (missing.size() > Math.max(0L, limits.maxPages() - persistedPages)) return Status.CAPACITY;
        for (var entry : missing) {
            Status result = commitBlob(entry.getKey(), entry.getValue());
            if (result != Status.OK) return result;
        }
        // Existing deduplicated records also require durability confirmation before root publication.
        try { forceRoot(); return Status.OK; }
        catch (IOException failure) { return Status.COMMIT_UNCERTAIN; }
    }
    private Status commitBlob(String hash, Blob blob) {
        Path pending = root.resolve(".pending"), target = root.resolve(hash + ".nmb");
        boolean committed = false;
        try {
            checkRoot();
            ByteBuffer data = ByteBuffer.wrap(blob.bytes);
            try (FileChannel file = FileChannel.open(pending, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                while (data.hasRemaining()) file.write(data);
                file.force(true);
            }
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE);
            committed = true; persistedPages++; cacheBlob(hash, blob);
            observer.committed(CommitKind.BLOB); // Test-only fault observer; production is a no-op.
            forceRoot(); return Status.OK;
        } catch (IOException failure) { return committed ? Status.COMMIT_UNCERTAIN : Status.IO_FAILURE; }
        finally { try { Files.deleteIfExists(pending); } catch (IOException ignored) { /* Single staging path. */ } }
    }
    private void cacheBlob(String hash, Blob blob) {
        blobCache.put(hash, blob);
        while (blobCache.size() > limits.cachedPages()) blobCache.remove(blobCache.keySet().iterator().next());
    }

    CompletableFuture<NexusMapRootRegistry.Result> readRegistration(int mapId) {
        if (mapId < 0) throw new IllegalArgumentException("Invalid native MapId");
        return submit(() -> readRegistrationNow(mapId), NexusMapRootRegistry.failure(Status.CLOSED),
                NexusMapRootRegistry.failure(Status.QUEUE_FULL));
    }
    CompletableFuture<NexusMapRootRegistry.Result> compareRegistration(
            Optional<NexusMapRootRegistry.Entry> expected, NexusMapRootRegistry.Entry next) {
        return submit(() -> {
            if (!writable) return NexusMapRootRegistry.failure(Status.IO_FAILURE);
            if (!NexusMapRootRegistry.validCompare(expected, next)) return NexusMapRootRegistry.failure(Status.INVALID_UPDATE);
            var current = readRegistrationNow(next.identity().mapId());
            if (current.status() == Status.OK) {
                var actual = current.entry().orElseThrow();
                // An exact post-commit retry confirms durability; it cannot rewind newer data.
                if (actual.equals(next)) return confirmRegistration(next);
                if (!actual.identity().equals(next.identity())) return NexusMapRootRegistry.failure(Status.INVALID_UPDATE);
                if (expected.isEmpty() || !actual.equals(expected.get())) return NexusMapRootRegistry.failure(Status.STALE);
                return commitRegistration(next, false);
            }
            if (current.status() != Status.MISSING) return current;
            if (expected.isPresent()) return NexusMapRootRegistry.failure(Status.CORRUPT);
            return commitRegistration(next, true);
        }, NexusMapRootRegistry.failure(Status.CLOSED), NexusMapRootRegistry.failure(Status.QUEUE_FULL));
    }
    CompletableFuture<NexusMapRootRegistry.Result> forkRegistration(
            NexusMapRootRegistry.Entry source, NexusMapRootRegistry.Entry target) {
        return submit(() -> {
            if (!writable) return NexusMapRootRegistry.failure(Status.IO_FAILURE);
            if (!NexusMapRootRegistry.validFork(source, target)) return NexusMapRootRegistry.failure(Status.INVALID_UPDATE);
            var existing = readRegistrationNow(target.identity().mapId());
            if (existing.status() == Status.OK) return existing.entry().orElseThrow().equals(target)
                    ? confirmRegistration(target) : NexusMapRootRegistry.failure(Status.STALE);
            if (existing.status() != Status.MISSING) return existing;
            var current = readRegistrationNow(source.identity().mapId());
            if (current.status() != Status.OK) return current.status() == Status.MISSING
                    ? NexusMapRootRegistry.failure(Status.CORRUPT) : current;
            if (!current.entry().orElseThrow().equals(source)) return NexusMapRootRegistry.failure(Status.STALE);
            return commitRegistration(target, true);
        }, NexusMapRootRegistry.failure(Status.CLOSED), NexusMapRootRegistry.failure(Status.QUEUE_FULL));
    }
    private NexusMapRootRegistry.Result readRegistrationNow(int mapId) {
        try {
            checkRoot();
            Path path = root.resolve("root_" + mapId + ".nmr");
            var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) return NexusMapRootRegistry.failure(Status.CORRUPT);
            byte[] bytes;
            try (FileChannel file = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                long size = file.size();
                if (size < NexusMapRootRegistry.MIN_BYTES || size > NexusMapRootRegistry.MAX_BYTES)
                    return NexusMapRootRegistry.failure(Status.CORRUPT);
                ByteBuffer data = ByteBuffer.allocate((int) size);
                while (data.hasRemaining()) if (file.read(data) < 0) return NexusMapRootRegistry.failure(Status.CORRUPT);
                bytes = data.array();
            }
            var entry = NexusMapRootRegistry.decode(bytes, mapId);
            return entry == null ? NexusMapRootRegistry.failure(Status.CORRUPT)
                    : new NexusMapRootRegistry.Result(Status.OK, Optional.of(entry));
        } catch (NoSuchFileException failure) { return NexusMapRootRegistry.failure(Status.MISSING); }
        catch (IOException failure) { return NexusMapRootRegistry.failure(Status.IO_FAILURE); }
    }
    private NexusMapRootRegistry.Result confirmRegistration(NexusMapRootRegistry.Entry entry) {
        try { forceRoot(); return new NexusMapRootRegistry.Result(Status.OK, Optional.of(entry)); }
        catch (IOException failure) { return NexusMapRootRegistry.failure(Status.COMMIT_UNCERTAIN); }
    }
    private NexusMapRootRegistry.Result commitRegistration(NexusMapRootRegistry.Entry entry, boolean fresh) {
        if (fresh && !quotaKnown) return NexusMapRootRegistry.failure(Status.IO_FAILURE);
        if (fresh && persistedPages >= limits.maxPages()) return NexusMapRootRegistry.failure(Status.CAPACITY);
        Path pending = root.resolve(".pending"), target = root.resolve("root_" + entry.identity().mapId() + ".nmr");
        boolean committed = false;
        try {
            checkRoot();
            ByteBuffer bytes = ByteBuffer.wrap(NexusMapRootRegistry.encode(entry));
            try (FileChannel file = FileChannel.open(pending, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                while (bytes.hasRemaining()) file.write(bytes);
                file.force(true);
            }
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            committed = true;
            if (fresh) persistedPages++;
            observer.committed(CommitKind.ROOT);
            forceRoot();
            return new NexusMapRootRegistry.Result(Status.OK, Optional.of(entry));
        } catch (IOException failure) { return NexusMapRootRegistry.failure(committed ? Status.COMMIT_UNCERTAIN : Status.IO_FAILURE); }
        finally { try { Files.deleteIfExists(pending); } catch (IOException ignored) { /* Single bounded staging path. */ } }
    }

    private <T> CompletableFuture<T> submit(java.util.function.Supplier<T> operation, T closedValue, T fullValue) {
        if (closing.get()) return CompletableFuture.completedFuture(closedValue);
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try { result.complete(operation.get()); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (RejectedExecutionException failure) {
            result.complete(closing.get() ? closedValue : fullValue);
        }
        return result;
    }

    private Path pagePath(Key key) {
        return root.resolve(key.ownerId() + "_" + key.tileX() + "_" + key.tileZ() + ".nmp");
    }
    private static void rejectSymlink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Symlink in sparse map store");
    }
    private void checkRoot() throws IOException { rejectSymlink(root); }
    private ReadResult readNow(Key key) {
        Page hit = cache.get(key);
        if (hit != null) return new ReadResult(Status.OK, Optional.of(hit));
        Path path = pagePath(key);
        try {
            checkRoot();
            var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) return new ReadResult(Status.CORRUPT, Optional.empty());
            ByteBuffer data = ByteBuffer.allocate(FILE_BYTES);
            try (FileChannel file = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                if (file.size() != FILE_BYTES) return new ReadResult(Status.CORRUPT, Optional.empty());
                while (data.hasRemaining()) if (file.read(data) < 0) return new ReadResult(Status.CORRUPT, Optional.empty());
            }
            byte[] bytes = data.array();
            CRC32 checksum = new CRC32(); checksum.update(bytes, 0, FILE_BYTES - Integer.BYTES);
            data.flip();
            if (data.getInt() != MAGIC || data.getInt() != VERSION || data.getInt() != key.ownerId()
                    || data.getInt() != key.tileX() || data.getInt() != key.tileZ())
                return new ReadResult(Status.CORRUPT, Optional.empty());
            long revision = data.getLong();
            byte[] colors = new byte[PIXELS], coverage = new byte[COVERAGE_BYTES];
            data.get(colors); data.get(coverage);
            if (revision < 0 || data.getInt() != (int) checksum.getValue())
                return new ReadResult(Status.CORRUPT, Optional.empty());
            Page page = new Page(revision, colors, coverage);
            cache(key, page);
            return new ReadResult(Status.OK, Optional.of(page));
        } catch (NoSuchFileException failure) { return new ReadResult(Status.MISSING, Optional.empty()); }
        catch (IOException failure) { return new ReadResult(Status.IO_FAILURE, Optional.empty()); }
    }

    private Status writeNow(Key key, Page page) {
        if (!writable) return Status.IO_FAILURE;
        ReadResult old = readNow(key);
        if (old.status() != Status.OK && old.status() != Status.MISSING) return old.status();
        boolean fresh = old.status() == Status.MISSING;
        if (fresh && !quotaKnown) return Status.IO_FAILURE;
        if (fresh && persistedPages >= limits.maxPages()) return Status.CAPACITY;
        if (!fresh) {
            Page prior = old.page().orElseThrow();
            if (page.revision < prior.revision) return Status.STALE;
            if (page.revision == prior.revision) {
                if (!page.sameContent(prior)) return Status.STALE;
                try { forceRoot(); return Status.OK; }
                catch (IOException failure) { return Status.COMMIT_UNCERTAIN; }
            }
        }
        Path target = pagePath(key), pending = root.resolve(".pending");
        boolean committed = false;
        try {
            checkRoot();
            ByteBuffer data = ByteBuffer.allocate(FILE_BYTES);
            data.putInt(MAGIC).putInt(VERSION).putInt(key.ownerId()).putInt(key.tileX()).putInt(key.tileZ());
            data.putLong(page.revision).put(page.colors).put(page.coverage);
            CRC32 checksum = new CRC32(); checksum.update(data.array(), 0, FILE_BYTES - Integer.BYTES);
            data.putInt((int) checksum.getValue()).flip();
            try (FileChannel file = FileChannel.open(pending, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
                while (data.hasRemaining()) file.write(data);
                file.force(true);
            }
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            committed = true;
            if (fresh) persistedPages++;
            cache(key, page);
            // Rename is the commit point; a later durability error must not be reported as rollback.
            forceRoot();
            return Status.OK;
        } catch (IOException failure) { return committed ? Status.COMMIT_UNCERTAIN : Status.IO_FAILURE; }
        finally {
            try { Files.deleteIfExists(pending); } catch (IOException ignored) { /* Single bounded staging path. */ }
        }
    }
    private void forceRoot() throws IOException {
        forceDirectory(root);
    }
    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel file = FileChannel.open(directory, StandardOpenOption.READ)) { file.force(true); }
    }
    private void cache(Key key, Page page) {
        cache.put(key, page);
        while (cache.size() > limits.cachedPages()) cache.remove(cache.keySet().iterator().next());
    }
    /** Stops accepting work, drains accepted requests, and never waits on a caller/server thread. */
    public CompletableFuture<Void> closeAsync() {
        if (closing.compareAndSet(false, true)) worker.shutdown();
        return closed;
    }
    @Override public void close() { closeAsync(); }
}
