package dev.totem.nexus.space;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/** One explicit metadata migration session per world storage; no terrain cutover. */
public final class NexusMapMigrationOwner {
    private static final String EPOCH = "totem_nexus_binding_snapshot_epoch";
    private static final NexusMapSourceStaging.Limits SOURCE_LIMITS = new NexusMapSourceStaging.Limits(65536, 1L << 30);
    public interface StorageAccess {
        NexusMapMigrationOwner totem$mapMigrationOwner(MinecraftServer server);
        SavedData totem$cachedData(SavedDataType<?> type);
        NexusMapDiskSourceVerifier.Source totem$sourceFile(SavedDataType<?> type);
    }
    public static CompletableFuture<Lease> prepare(ServerLevel level) {
        return forStorage(level.getServer().overworld().getDataStorage(), level.getServer()).begin();
    }
    static NexusMapMigrationOwner forStorage(SavedDataStorage storage, MinecraftServer server) {
        return ((StorageAccess) storage).totem$mapMigrationOwner(server);
    }

    private final SavedDataStorage storage;
    private final MinecraftServer server;
    private final Path canonical;
    private final HolderLookup.Provider registries;
    private final ReentrantLock io = new ReentrantLock();
    private final AtomicBoolean retrySave = new AtomicBoolean();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), action -> {
                var thread = new Thread(action, "nexus-map-migration");
                thread.setDaemon(true);
                return thread;
            });
    private volatile Session active;
    private volatile boolean closing, retiredUnstamped;
    private long nextEpoch;
    private CompletableFuture<Void> pendingDisk = CompletableFuture.completedFuture(null);

    public NexusMapMigrationOwner(SavedDataStorage storage, MinecraftServer server, Path canonical,
                                  HolderLookup.Provider registries) {
        if (!server.isSameThread()) throw new IllegalStateException("Migration owner requires server thread");
        this.storage = storage; this.server = server; this.canonical = canonical; this.registries = registries;
    }
    public void checkServer(MinecraftServer expected) {
        checkThread();
        if (server != expected) throw new IllegalStateException("Wrong migration world owner");
    }
    private void checkThread() {
        if (!server.isSameThread()) throw new IllegalStateException("Migration operation requires server thread");
    }
    Path sourceManifestDirectory() {
        checkThread();
        return canonical.resolveSibling("nexus_map_sources");
    }
    Path sourceBlobDirectory() {
        checkThread();
        return canonical.resolveSibling("nexus_map_source_blobs");
    }
    Path inventoryReceiptDirectory() { checkThread(); return canonical.resolveSibling("nexus_map_inventory_receipts"); }
    /** Reserve a full maximum-size record; partial/corrupt regular files remain quota charges. */
    private static void inventoryReceiptQuota(Path directory, Path target, java.util.function.BooleanSupplier alive) throws IOException {
        int count = 0; long bytes = 0; boolean existing = false;
        try (var entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                if (!alive.getAsBoolean()) throw new CancellationException("Inventory receipt publication stopped");
                String name = entry.getFileName().toString();
                var attributes = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!name.matches("inventory-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.receipt")
                        || !attributes.isRegularFile() || attributes.size() > NexusMapInventoryReceipt.MAX_BYTES)
                    throw new IOException("Invalid inventory receipt directory entry");
                bytes += attributes.size(); existing |= entry.getFileName().equals(target.getFileName());
                if (++count > 64 || bytes > (256L << 20)) throw new IOException("Inventory receipt directory quota exceeded");
            }
        }
        if (!existing && (count >= 64 || bytes > (256L << 20) - NexusMapInventoryReceipt.MAX_BYTES))
            throw new IOException("Inventory receipt publication exceeds reserved quota");
    }
    /** Worker-only: the held metadata witness has already made the namespace durable. */
    private static void prepareManifestDirectory(Path directory) throws IOException {
        try { Files.createDirectory(directory); }
        catch (FileAlreadyExistsException existing) { /* Validate, never follow/replace an existing entry. */ }
        if (!Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isDirectory())
            throw new IOException("Invalid map source manifest directory");
        try (var parent = FileChannel.open(directory.toAbsolutePath().getParent(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            parent.force(true);
        }
    }

    /** Never join this response on the server thread. Cancellation still drains internal IO. */
    synchronized CompletableFuture<Lease> begin() {
        checkThread();
        if (closing || active != null || !pendingDisk.isDone()) return CompletableFuture.failedFuture(new IllegalStateException("Migration owner unavailable"));
        var ledger = NexusMapBindingSavedData.loadCanonical(storage);
        Object token = ledger.freezeForMigration();
        final Session session;
        try {
            session = new Session(ledger, token, NexusMapBindingWitness.capture(ledger), Math.incrementExact(nextEpoch));
            nextEpoch = session.epoch;
            active = session;
        } catch (RuntimeException failure) {
            ledger.releaseMigrationFreeze(token);
            return CompletableFuture.failedFuture(failure);
        }
        try {
            ledger.setDirty();
            // Native chaining includes all prior queued writes; no server-thread wait.
            storage.scheduleSave().whenComplete((ignored, failure) -> {
                if (failure != null) finishFailure(session, failure);
                else {
                    try { worker.execute(() -> establish(session)); }
                    catch (RuntimeException rejected) { finishFailure(session, rejected); }
                }
            });
        } catch (RuntimeException failure) { finishFailure(session, failure); }
        return session.response;
    }

    private void establish(Session session) {
        try {
            io.lock();
            try {
                session.sealed = true;
                retiredUnstamped = true;
                if (closing || session.response.isCancelled() || session.failure.get() != null)
                    throw new CancellationException("Migration preparation stopped");
                // Native save can create world/data for the first time. Persist
                // that entry before the witness forces data/namespace children.
                Path world = canonical.toAbsolutePath().getParent().getParent().getParent();
                if (world == null) throw new IOException("Missing native data directory parent");
                try (var directory = FileChannel.open(world, StandardOpenOption.READ)) { directory.force(true); }
                NexusMapBindingWitness.establish(storage, canonical, registries, session.expected);
            } finally { io.unlock(); }
            boolean deliver;
            synchronized (this) {
                deliver = active == session && !closing && !session.response.isCancelled() && session.failure.get() == null;
                session.held = deliver;
            }
            if (!deliver) release(session);
            session.drained.complete(null);
            if (deliver) {
                if (!session.response.complete(new Lease(this, session))) release(session);
            } else session.response.completeExceptionally(new CancellationException("Migration preparation stopped"));
        } catch (IOException | RuntimeException failure) {
            finishFailure(session, failure);
        } finally {
            // Cleanup also runs when an unexpected Error escapes the worker.
            if (!session.drained.isDone()) finishFailure(session, new IllegalStateException("Migration worker did not finish"));
        }
    }
    private void finishFailure(Session session, Throwable failure) {
        session.failure.compareAndSet(null, failure);
        release(session);
        session.drained.complete(null);
        session.response.completeExceptionally(failure);
    }
    private synchronized void release(Session session) {
        if (active != session) return;
        if (session.capture != null) session.capture.close();
        session.ledger.releaseMigrationFreeze(session.token);
        retrySave.set(true);
        active = null;
    }

    public void beforeSet() {
        if (active != null) throw new IllegalStateException("Canonical map bindings are frozen for migration");
    }
    public void afterGet(SavedData result) {
        Session session = active;
        if (session != null && result != session.ledger) throw new IllegalStateException("Canonical migration ledger was replaced");
    }
    public void beforeSave() {
        checkThread();
        if (active == null && retrySave.getAndSet(false)) {
            var ledger = storage.get(NexusMapBindingSavedData.TYPE);
            if (ledger != null) ledger.setDirty();
        }
    }
    /** Epoch is transient envelope metadata, removed before every native disk write. */
    public CompoundTag stampSnapshot(SavedData source, CompoundTag envelope) {
        Session session = active;
        if (session != null && source != session.ledger) throw new IllegalStateException("Wrong frozen canonical instance");
        envelope.putLong(EPOCH, session == null ? 0 : session.epoch);
        return envelope;
    }
    public void nativeWrite(CompoundTag envelope, Runnable original) {
        io.lock();
        try {
            long epoch = envelope.getLongOr(EPOCH, -1);
            envelope.remove(EPOCH);
            Session session = active;
            boolean matchingEpoch = session != null && epoch == session.epoch;
            if (session != null && (matchingEpoch || session.sealed) && !matches(envelope, session.expected))
                session.failure.compareAndSet(null, new IllegalStateException("Frozen native snapshot changed"));
            if ((epoch > 0 && (!matchingEpoch || session.sealed)) || (epoch < 0 && retiredUnstamped)
                    || (session != null && (session.sealed || session.failure.get() != null))) {
                retrySave.set(true);
                return;
            }
            original.run();
        } finally { io.unlock(); }
    }
    private boolean matches(CompoundTag envelope, NexusMapBindingWitness.Expected expected) {
        try {
            if (!(envelope.get("data") instanceof CompoundTag data)) return false;
            return NexusMapBindingSavedData.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), data)
                    .result().map(NexusMapBindingWitness::capture).filter(expected::equals).isPresent();
        } catch (RuntimeException malformed) { return false; }
    }

    /** Shutdown may wait for IO, but never requires a queued server callback to finish. */
    public void closeForStorage() {
        checkThread();
        closing = true;
        Session session = active;
        if (session != null) {
            session.failure.compareAndSet(null, new CancellationException("Storage closing"));
            session.response.completeExceptionally(new CancellationException("Storage closing"));
            session.drained.join();
            release(session);
        }
        worker.shutdown();
        pendingDisk.join();
        beforeSave();
    }
    public static final class Lease implements AutoCloseable {
        private final NexusMapMigrationOwner owner;
        private final Session session;
        private Lease(NexusMapMigrationOwner owner, Session session) { this.owner = owner; this.session = session; }
        public boolean valid() { return !owner.closing && owner.active == session && session.held && session.failure.get() == null; }
        /** Cache-only, server-thread capture. A controller must stage/verify disk sources separately. */
        SourceCapture captureSource(MapId id) { return captureSource(id, false); }
        SourceCapture captureFrozenSource(MapId id) { return captureSource(id, true); }
        private SourceCapture captureSource(MapId id, boolean freezeNative) {
            owner.checkThread();
            synchronized (owner) {
                if (!valid() || session.capture != null || !owner.pendingDisk.isDone()) throw new IllegalStateException("Source capture unavailable");
                var binding = session.ledger.get(id).orElseThrow(() -> new IllegalStateException("Missing source binding"));
                var intent = session.ledger.sparseImportIntent(id).orElseThrow(() -> new IllegalStateException("Missing import ticket"));
                var base = cached(owner.server.getDataStorage(), MapItemSavedData.type(id));
                var detail = cached(owner.storage, NexusMapDetailSavedData.TYPE);
                if (base == null || detail == null || !intent.matches(binding, base)
                        || ((NexusMapBindingStorage.TerrainFailureAccess) owner.storage).totem$terrainFailure() != null)
                    throw new IllegalStateException("Unstaged or mismatched source data");
                // Both holds and the base copy are acquired consecutively on the server thread.
                var nativeHold = freezeNative ? NexusMapTerrainFreeze.acquire(owner.server, base) : null;
                NexusMapDetailSavedData.MigrationSnapshot freeze = null;
                boolean attached = false;
                try {
                    freeze = detail.beginMigrationSnapshot();
                    var baseCopy = new NexusMapSourceSnapshot.Base(id, base);
                    var capture = new SourceCapture(this, id, base, detail, freeze, intent, baseCopy, nativeHold);
                    session.capture = capture;
                    attached = true;
                    return capture;
                } finally {
                    if (!attached) {
                        if (freeze != null) freeze.close();
                        if (nativeHold != null) nativeHold.close();
                    }
                }
            }
        }
        @Override public void close() {
            owner.checkThread();
            owner.release(session);
            owner.beforeSave();
        }
    }
    private static <T extends SavedData> T cached(SavedDataStorage storage, SavedDataType<T> type) {
        SavedData value = ((StorageAccess) storage).totem$cachedData(type);
        // Native cache keys include the CODEC/type identity; do not issue get/computeIfAbsent here.
        @SuppressWarnings("unchecked") T result = (T) value;
        return result;
    }
    static final class SourceCapture implements AutoCloseable {
        private final Lease lease;
        private final MapId id;
        private final MapItemSavedData nativeSource;
        private final NexusMapDetailSavedData detailSource;
        private final NexusMapDetailSavedData.MigrationSnapshot detailCapture;
        private final NexusMapBindingSavedData.SparseImportIntent intent;
        private final NexusMapSourceSnapshot.Base base;
        private final NexusMapTerrainFreeze.Hold nativeHold;
        private NexusMapSourceSnapshot result;
        private volatile boolean closed;
        private CompletableFuture<Void> diskResponse;
        private volatile NexusMapDiskSourceVerifier.Observation diskObservation;
        private CompletableFuture<Void> manifestResponse;
        private volatile NexusMapSourceManifest.Manifest manifest;
        private NexusMapMigrationInventory inventory;
        private CompletableFuture<Void> stagingResponse;
        private volatile StagedSources stagedSources;
        private BatchPreparation batchPreparation;
        private CompletableFuture<Void> batchResponse;
        private volatile StagedInventory stagedInventory;
        private CompletableFuture<Void> receiptResponse;
        private volatile NexusMapInventoryReceipt.Receipt inventoryReceipt;
        private CompletableFuture<Void> recoveryResponse;
        private volatile NexusMapInventoryRecovery.Verified recoveredInventory;
        record StagedSources(NexusMapSourceManifest.Manifest manifest, NexusMapSourceStaging.Blob nativeSource,
                             NexusMapSourceStaging.Blob detailSource) { }
        record StagedInventory(StagedSources primary, NexusMapNativeBatchStaging.Result natives) { }
        private static final class BatchPreparation {
            final NexusMapMigrationInventory.Snapshot inventory;
            final java.util.Iterator<Integer> remaining;
            final java.util.Map<Integer, NexusMapDiskSourceVerifier.Source> descriptors = new java.util.TreeMap<>();
            boolean complete;
            BatchPreparation(NexusMapMigrationInventory.Snapshot inventory) {
                this.inventory = inventory; remaining = inventory.nativeSources().iterator();
            }
        }
        private SourceCapture(Lease lease, MapId id, MapItemSavedData nativeSource,
                              NexusMapDetailSavedData detailSource, NexusMapDetailSavedData.MigrationSnapshot detailCapture,
                              NexusMapBindingSavedData.SparseImportIntent intent, NexusMapSourceSnapshot.Base base, NexusMapTerrainFreeze.Hold nativeHold) {
            this.lease = lease; this.id = id; this.nativeSource = nativeSource; this.detailSource = detailSource;
            this.detailCapture = detailCapture; this.intent = intent; this.base = base; this.nativeHold = nativeHold;
        }
        private void check() {
            lease.owner.checkThread();
            if (closed) throw new IllegalStateException("Source capture released");
            if ((nativeHold != null && (!nativeHold.valid() || nativeSource.colors == null
                    || java.nio.ByteBuffer.wrap(nativeSource.colors).mismatch(base.colors()) != -1))
                    || !lease.valid() || cached(lease.owner.server.getDataStorage(), MapItemSavedData.type(id)) != nativeSource
                    || cached(lease.owner.storage, NexusMapDetailSavedData.TYPE) != detailSource
                    || ((NexusMapBindingStorage.TerrainFailureAccess) lease.owner.storage).totem$terrainFailure() != null) {
                close();
                throw new IllegalStateException("Source capture invalidated");
            }
        }
        /** Selected native base plus full detail are held; other native sources still need reconciliation. */
        NexusMapSourceSnapshot frozenSnapshot() {
            var snapshot = result();
            if (nativeHold == null) throw new IllegalStateException("Native recording is not frozen");
            return snapshot;
        }
        boolean step(int budget) {
            check();
            try {
                if (!detailCapture.step(budget)) return false;
                if (result == null) result = new NexusMapSourceSnapshot(intent, base, detailCapture.result());
                return true;
            } catch (RuntimeException failure) { close(); throw failure; }
        }
        NexusMapSourceSnapshot result() {
            check();
            if (result == null) throw new IllegalStateException("Source capture incomplete");
            return result;
        }
        /** Enumerates every held binding and all detail indexes, including unprepared/orphan sources. */
        NexusMapMigrationInventory inventory() {
            var snapshot = result();
            if (inventory != null) return inventory;
            try {
                inventory = lease.session.ledger.migrationInventory(lease.session.token, snapshot.detail(),
                        lease.session.expected, this::check, this::close);
                return inventory;
            } catch (RuntimeException failure) { close(); throw failure; }
        }
        /** Async readiness only; retrieve evidence through the checked server-thread accessor. */
        CompletableFuture<Void> verifyDisk() {
            return verifyDisk(() -> { });
        }
        CompletableFuture<Void> verifyDisk(NexusMapDiskSourceVerifier.AfterDecode beforeRead) {
            NexusMapSourceSnapshot snapshot = result();
            if (recoveryResponse != null) throw new IllegalStateException("Capture is recovering saved inventory");
            if (diskResponse != null) return diskResponse;
            var owner = lease.owner;
            var nativeFile = ((StorageAccess) owner.server.getDataStorage()).totem$sourceFile(MapItemSavedData.type(id));
            var detailFile = ((StorageAccess) owner.storage).totem$sourceFile(NexusMapDetailSavedData.TYPE);
            diskResponse = new CompletableFuture<>();
            var response = diskResponse;
            var drained = new CompletableFuture<Void>();
            owner.pendingDisk = drained;
            try {
                owner.worker.execute(() -> {
                    try {
                        beforeRead.run();
                        var observed = NexusMapDiskSourceVerifier.verify(nativeFile, detailFile, owner.registries, snapshot,
                                () -> !closed && lease.valid() && !response.isCancelled());
                        if (closed || !lease.valid() || response.isCancelled()) throw new CancellationException("Source verification stopped");
                        diskObservation = observed;
                        response.complete(null);
                    } catch (IOException | RuntimeException failure) { response.completeExceptionally(failure); }
                    finally {
                        if (!response.isDone()) response.completeExceptionally(new IllegalStateException("Source verification worker did not finish"));
                        drained.complete(null);
                    }
                });
            } catch (RuntimeException rejected) { response.completeExceptionally(rejected); drained.complete(null); }
            return response;
        }
        NexusMapDiskSourceVerifier.Observation diskObservation() {
            check();
            if (diskResponse == null || !diskResponse.isDone()) throw new IllegalStateException("Disk verification incomplete");
            try { diskResponse.join(); } // Already done: never a server-thread IO wait.
            catch (RuntimeException failure) { close(); throw failure; }
            if (diskObservation == null) throw new IllegalStateException("No disk source observation");
            return diskObservation;
        }
        /** Persist the checked observation; readiness is not a transferable migration authority. */
        CompletableFuture<Void> persistManifest() { return persistManifest(stage -> { }); }
        CompletableFuture<Void> persistManifest(NexusMapSourceManifest.Probe probe) {
            return persistManifest(() -> { }, probe);
        }
        CompletableFuture<Void> persistManifest(NexusMapDiskSourceVerifier.AfterDecode beforePublish,
                                                NexusMapSourceManifest.Probe probe) {
            var observed = diskObservation();
            if (manifestResponse != null) return manifestResponse;
            var owner = lease.owner;
            var expected = NexusMapSourceManifest.capture(lease.session.expected, intent, observed);
            Path directory = owner.sourceManifestDirectory();
            manifestResponse = new CompletableFuture<>();
            var response = manifestResponse;
            var drained = new CompletableFuture<Void>();
            // The same single worker orders this after the disk reader's final drain.
            owner.pendingDisk = drained;
            try {
                owner.worker.execute(() -> {
                    try {
                        if (closed || !lease.valid() || response.isCancelled()) throw new CancellationException("Manifest publication stopped");
                        beforePublish.run();
                        if (closed || !lease.valid() || response.isCancelled()) throw new CancellationException("Manifest publication stopped");
                        prepareManifestDirectory(directory);
                        NexusMapSourceManifest.publish(directory, expected,
                                () -> !closed && lease.valid() && !response.isCancelled(), probe);
                        if (closed || !lease.valid() || response.isCancelled()) throw new CancellationException("Manifest publication stopped");
                        manifest = expected;
                        response.complete(null);
                    } catch (IOException | RuntimeException failure) { response.completeExceptionally(failure); }
                    finally {
                        if (!response.isDone()) response.completeExceptionally(new IllegalStateException("Manifest worker did not finish"));
                        drained.complete(null);
                    }
                });
            } catch (RuntimeException rejected) { response.completeExceptionally(rejected); drained.complete(null); }
            return response;
        }
        NexusMapSourceManifest.Manifest manifest() {
            check();
            if (manifestResponse == null || !manifestResponse.isDone()) throw new IllegalStateException("Manifest publication incomplete");
            try { manifestResponse.join(); } // Done already; never waits for worker IO on the server thread.
            catch (RuntimeException failure) { close(); throw failure; }
            if (manifest == null) throw new IllegalStateException("No source manifest");
            return manifest;
        }
        /** Save both observed sources and strictly decode the saved pair before exposing checked evidence. */
        CompletableFuture<Void> stageSources() { return stageSources(() -> { }, step -> { }); }
        CompletableFuture<Void> stageSources(NexusMapDiskSourceVerifier.AfterDecode beforeStage,
                                              NexusMapSourceStaging.Probe probe) {
            var expected = manifest();
            if (stagingResponse != null) return stagingResponse;
            var snapshot = result(); var observed = diskObservation(); var owner = lease.owner;
            var nativeFile = ((StorageAccess) owner.server.getDataStorage()).totem$sourceFile(MapItemSavedData.type(id));
            var detailFile = ((StorageAccess) owner.storage).totem$sourceFile(NexusMapDetailSavedData.TYPE);
            Path directory = owner.sourceBlobDirectory();
            stagingResponse = new CompletableFuture<>(); var response = stagingResponse;
            var drained = new CompletableFuture<Void>(); owner.pendingDisk = drained;
            try {
                owner.worker.execute(() -> {
                    try {
                        java.util.function.BooleanSupplier alive = () -> !closed && lease.valid() && !response.isCancelled();
                        if (!alive.getAsBoolean()) throw new CancellationException("Source staging stopped");
                        // Test gates stay outside the staging helper's publication monitor.
                        beforeStage.run();
                        if (!alive.getAsBoolean()) throw new CancellationException("Source staging stopped");
                        prepareManifestDirectory(directory);
                        var nativeBlob = NexusMapSourceStaging.stage(directory, nativeFile.path(), NexusMapSourceStaging.Kind.NATIVE,
                                expected.nativeFileHash(), SOURCE_LIMITS, alive, probe);
                        var detailBlob = NexusMapSourceStaging.stage(directory, detailFile.path(), NexusMapSourceStaging.Kind.DETAIL,
                                expected.detailFileHash(), SOURCE_LIMITS, alive, probe);
                        var stagedNative = new NexusMapDiskSourceVerifier.Source(
                                NexusMapSourceStaging.path(directory, nativeBlob.kind(), nativeBlob.hash()), nativeFile.fixType(), nativeFile.fixer());
                        var stagedDetail = new NexusMapDiskSourceVerifier.Source(
                                NexusMapSourceStaging.path(directory, detailBlob.kind(), detailBlob.hash()), detailFile.fixType(), detailFile.fixer());
                        var verified = NexusMapDiskSourceVerifier.verify(stagedNative, stagedDetail, owner.registries, snapshot, alive);
                        if (!verified.equals(observed)) throw new IOException("Staged sources differ from held observation");
                        if (!alive.getAsBoolean()) throw new CancellationException("Source staging stopped");
                        stagedSources = new StagedSources(expected, nativeBlob, detailBlob);
                        response.complete(null);
                    } catch (IOException | RuntimeException failure) { response.completeExceptionally(failure); }
                    finally {
                        if (!response.isDone()) response.completeExceptionally(new IllegalStateException("Source staging worker did not finish"));
                        drained.complete(null);
                    }
                });
            } catch (RuntimeException rejected) { response.completeExceptionally(rejected); drained.complete(null); }
            return response;
        }
        StagedSources stagedSources() {
            check();
            if (stagingResponse == null || !stagingResponse.isDone()) throw new IllegalStateException("Source staging incomplete");
            try { stagingResponse.join(); } // Already done: never waits for disk IO on the server thread.
            catch (RuntimeException failure) { close(); throw failure; }
            if (stagedSources == null) throw new IllegalStateException("No staged source evidence");
            return stagedSources;
        }
        /** Descriptor construction is cache-free/path-only and bounded to 1..8 IDs per server tick call. */
        boolean prepareBatch(int budget) {
            check();
            if (budget < 1 || budget > 8) throw new IllegalArgumentException("Batch descriptor budget must be 1..8");
            var primary = stagedSources(); var snapshot = inventory().result();
            if (batchPreparation == null) batchPreparation = new BatchPreparation(snapshot);
            var preparation = batchPreparation;
            if (preparation.complete) return true;
            var access = (StorageAccess) lease.owner.server.getDataStorage();
            try {
                while (budget-- > 0 && preparation.remaining.hasNext()) {
                    int sourceId = preparation.remaining.next();
                    var source = access.totem$sourceFile(MapItemSavedData.type(new MapId(sourceId)));
                    if (sourceId == id.id()) source = new NexusMapDiskSourceVerifier.Source(
                            NexusMapSourceStaging.path(lease.owner.sourceBlobDirectory(), primary.nativeSource().kind(), primary.nativeSource().hash()),
                            source.fixType(), source.fixer());
                    preparation.descriptors.put(sourceId, source);
                }
            } catch (RuntimeException failure) { close(); throw failure; }
            preparation.complete = !preparation.remaining.hasNext();
            return preparation.complete;
        }
        CompletableFuture<Void> stageBatch() { return stageBatch(() -> { }, (sourceId, phase) -> { }); }
        CompletableFuture<Void> stageBatch(NexusMapDiskSourceVerifier.AfterDecode beforeBatch, NexusMapNativeBatchStaging.Probe probe) {
            var primary = stagedSources(); var snapshot = result(); var observed = diskObservation();
            if (batchPreparation == null || !batchPreparation.complete) throw new IllegalStateException("Batch descriptors incomplete");
            if (batchResponse != null) return batchResponse;
            var owner = lease.owner; var preparation = batchPreparation;
            var nativeType = ((StorageAccess) owner.server.getDataStorage()).totem$sourceFile(MapItemSavedData.type(id));
            var detailType = ((StorageAccess) owner.storage).totem$sourceFile(NexusMapDetailSavedData.TYPE);
            Path directory = owner.sourceBlobDirectory();
            batchResponse = new CompletableFuture<>(); var response = batchResponse;
            var drained = new CompletableFuture<Void>(); owner.pendingDisk = drained;
            try {
                owner.worker.execute(() -> {
                    try {
                        java.util.function.BooleanSupplier alive = () -> !closed && lease.valid() && !response.isCancelled();
                        if (!alive.getAsBoolean()) throw new CancellationException("Inventory staging stopped");
                        beforeBatch.run();
                        if (!alive.getAsBoolean()) throw new CancellationException("Inventory staging stopped");
                        var batch = NexusMapNativeBatchStaging.stage(directory, preparation.inventory,
                                preparation.descriptors, owner.registries, SOURCE_LIMITS, alive, probe);
                        var selected = batch.sources().get(id.id());
                        if (!batch.metadata().equals(lease.session.expected) || selected == null
                                || !selected.blob().equals(primary.nativeSource())
                                || !selected.terrainHash().equals(primary.manifest().nativeTerrainHash()))
                            throw new IOException("Inventory staging lost held primary identity");
                        var stagedNative = new NexusMapDiskSourceVerifier.Source(
                                NexusMapSourceStaging.path(directory, primary.nativeSource().kind(), primary.nativeSource().hash()), nativeType.fixType(), nativeType.fixer());
                        var stagedDetail = new NexusMapDiskSourceVerifier.Source(
                                NexusMapSourceStaging.path(directory, primary.detailSource().kind(), primary.detailSource().hash()), detailType.fixType(), detailType.fixer());
                        if (!NexusMapDiskSourceVerifier.verify(stagedNative, stagedDetail, owner.registries, snapshot, alive).equals(observed))
                            throw new IOException("Saved detail/primary pair changed during inventory staging");
                        if (!alive.getAsBoolean()) throw new CancellationException("Inventory staging stopped");
                        stagedInventory = new StagedInventory(primary, batch); response.complete(null);
                    } catch (IOException | RuntimeException failure) { response.completeExceptionally(failure); }
                    finally {
                        if (!response.isDone()) response.completeExceptionally(new IllegalStateException("Inventory staging worker did not finish"));
                        drained.complete(null);
                    }
                });
            } catch (RuntimeException rejected) { response.completeExceptionally(rejected); drained.complete(null); }
            return response;
        }
        StagedInventory stagedInventory() {
            check();
            if (batchResponse == null || !batchResponse.isDone()) throw new IllegalStateException("Inventory staging incomplete");
            try { batchResponse.join(); }
            catch (RuntimeException failure) { close(); throw failure; }
            if (stagedInventory == null) throw new IllegalStateException("No staged inventory evidence");
            return stagedInventory;
        }
        CompletableFuture<Void> publishInventory() { return publishInventory(() -> { }, step -> { }); }
        CompletableFuture<Void> publishInventory(NexusMapDiskSourceVerifier.AfterDecode beforePublish, NexusMapInventoryReceipt.Probe probe) {
            var proof = stagedInventory();
            if (receiptResponse != null) return receiptResponse;
            var snapshot = result(); var observed = diskObservation(); var owner = lease.owner;
            var nativeType = ((StorageAccess) owner.server.getDataStorage()).totem$sourceFile(MapItemSavedData.type(id));
            var detailType = ((StorageAccess) owner.storage).totem$sourceFile(NexusMapDetailSavedData.TYPE);
            Path directory = owner.inventoryReceiptDirectory(), blobs = owner.sourceBlobDirectory();
            receiptResponse = new CompletableFuture<>(); var response = receiptResponse;
            var drained = new CompletableFuture<Void>(); owner.pendingDisk = drained;
            try {
                owner.worker.execute(() -> {
                    try {
                        java.util.function.BooleanSupplier alive = () -> !closed && lease.valid() && !response.isCancelled();
                        if (!alive.getAsBoolean()) throw new CancellationException("Inventory publication stopped");
                        beforePublish.run();
                        if (!alive.getAsBoolean()) throw new CancellationException("Inventory publication stopped");
                        var receipt = NexusMapInventoryReceipt.capture(proof);
                        // Revalidate physical sources after the earlier batch readiness, before claiming durable completion.
                        for (var row : receipt.natives().values()) {
                            var blob = row.blob();
                            if (!NexusMapSourceStaging.verify(blobs, blob.kind(), blob.hash(), alive).equals(blob))
                                throw new IOException("Inventory native blob length changed");
                            var source = new NexusMapDiskSourceVerifier.Source(NexusMapSourceStaging.path(blobs, blob.kind(), blob.hash()), nativeType.fixType(), nativeType.fixer());
                            var verified = NexusMapDiskSourceVerifier.observeNative(source, new MapId(row.mapId()), owner.registries, alive);
                            var base = verified.base();
                            if (!verified.fileHash().equals(blob.hash()) || !verified.terrainHash().equals(row.terrainHash()) || base.centerX != row.centerX() || base.centerZ != row.centerZ()
                                    || base.scale != row.scale() || base.locked != row.locked() || !base.dimension.identifier().toString().equals(row.dimension()))
                                throw new IOException("Inventory native metadata changed");
                        }
                        var detail = receipt.detail();
                        if (!NexusMapSourceStaging.verify(blobs, detail.kind(), detail.hash(), alive).equals(detail))
                            throw new IOException("Inventory detail blob length changed");
                        var selected = receipt.natives().get(id.id()).blob();
                        var savedNative = new NexusMapDiskSourceVerifier.Source(NexusMapSourceStaging.path(blobs, selected.kind(), selected.hash()), nativeType.fixType(), nativeType.fixer());
                        var savedDetail = new NexusMapDiskSourceVerifier.Source(NexusMapSourceStaging.path(blobs, detail.kind(), detail.hash()), detailType.fixType(), detailType.fixer());
                        if (!NexusMapDiskSourceVerifier.verify(savedNative, savedDetail, owner.registries, snapshot, alive).equals(observed))
                            throw new IOException("Inventory saved pair no longer matches held snapshot");
                        if (!alive.getAsBoolean()) throw new CancellationException("Inventory publication stopped");
                        prepareManifestDirectory(directory);
                        inventoryReceiptQuota(directory, NexusMapInventoryReceipt.path(directory, receipt.primary().ticket()), alive);
                        NexusMapInventoryReceipt.publish(directory, receipt, alive, probe);
                        if (!alive.getAsBoolean()) throw new CancellationException("Inventory publication stopped");
                        inventoryReceipt = receipt; response.complete(null);
                    } catch (IOException | RuntimeException failure) { response.completeExceptionally(failure); }
                    finally {
                        if (!response.isDone()) response.completeExceptionally(new IllegalStateException("Inventory publication worker did not finish"));
                        drained.complete(null);
                    }
                });
            } catch (RuntimeException rejected) { response.completeExceptionally(rejected); drained.complete(null); }
            return response;
        }
        NexusMapInventoryReceipt.Receipt inventoryReceipt() {
            check();
            if (receiptResponse == null || !receiptResponse.isDone()) throw new IllegalStateException("Inventory publication incomplete");
            try { receiptResponse.join(); } catch (RuntimeException failure) { close(); throw failure; }
            if (inventoryReceipt == null) throw new IllegalStateException("No inventory receipt");
            return inventoryReceipt;
        }
        CompletableFuture<Void> recoverInventory() { return recoverInventory(() -> { }); }
        /** Recovery and new disk observation are exclusive branches of a fresh source capture. */
        CompletableFuture<Void> recoverInventory(NexusMapDiskSourceVerifier.AfterDecode beforeRecovery) {
            var held = inventory().result();
            if (recoveryResponse != null) return recoveryResponse;
            var owner = lease.owner;
            if (diskResponse != null || !owner.pendingDisk.isDone())
                throw new IllegalStateException("Recovery requires a fresh idle source capture");
            var nativeType = ((StorageAccess) owner.server.getDataStorage()).totem$sourceFile(MapItemSavedData.type(id));
            var detailType = ((StorageAccess) owner.storage).totem$sourceFile(NexusMapDetailSavedData.TYPE);
            Path receipts = owner.inventoryReceiptDirectory(), blobs = owner.sourceBlobDirectory();
            recoveryResponse = new CompletableFuture<>(); var response = recoveryResponse;
            var drained = new CompletableFuture<Void>(); owner.pendingDisk = drained;
            try {
                owner.worker.execute(() -> {
                    try {
                        java.util.function.BooleanSupplier alive = () -> !closed && lease.valid() && !response.isCancelled();
                        if (!alive.getAsBoolean()) throw new CancellationException("Inventory recovery stopped");
                        beforeRecovery.run();
                        if (!alive.getAsBoolean()) throw new CancellationException("Inventory recovery stopped");
                        var verified = NexusMapInventoryRecovery.verify(receipts, blobs, intent.ticket(), held,
                                nativeType.fixer(), detailType.fixer(), owner.registries, alive);
                        if (!alive.getAsBoolean()) throw new CancellationException("Inventory recovery stopped");
                        recoveredInventory = verified; response.complete(null);
                    } catch (IOException | RuntimeException failure) { response.completeExceptionally(failure); }
                    finally {
                        if (!response.isDone()) response.completeExceptionally(new IllegalStateException("Inventory recovery worker did not finish"));
                        drained.complete(null);
                    }
                });
            } catch (RuntimeException rejected) { response.completeExceptionally(rejected); drained.complete(null); }
            return response;
        }
        NexusMapInventoryRecovery.Verified recoveredInventory() {
            check();
            if (recoveryResponse == null || !recoveryResponse.isDone()) throw new IllegalStateException("Inventory recovery incomplete");
            try { recoveryResponse.join(); } catch (RuntimeException failure) { close(); throw failure; }
            if (recoveredInventory == null) throw new IllegalStateException("No recovered inventory evidence");
            return recoveredInventory;
        }
        @Override public void close() {
            lease.owner.checkThread();
            if (closed) return;
            closed = true;
            if (diskResponse != null) diskResponse.completeExceptionally(new CancellationException("Source capture released"));
            if (manifestResponse != null) manifestResponse.completeExceptionally(new CancellationException("Source capture released"));
            if (stagingResponse != null) stagingResponse.completeExceptionally(new CancellationException("Source capture released"));
            if (batchResponse != null) batchResponse.completeExceptionally(new CancellationException("Source capture released"));
            if (receiptResponse != null) receiptResponse.completeExceptionally(new CancellationException("Source capture released"));
            if (recoveryResponse != null) recoveryResponse.completeExceptionally(new CancellationException("Source capture released"));
            detailCapture.close();
            if (nativeHold != null) nativeHold.close();
            if (lease.session.capture == this) lease.session.capture = null;
        }
    }
    private static final class Session {
        final NexusMapBindingSavedData ledger;
        final Object token;
        final NexusMapBindingWitness.Expected expected;
        final long epoch;
        final CompletableFuture<Lease> response = new CompletableFuture<>();
        final CompletableFuture<Void> drained = new CompletableFuture<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        volatile boolean sealed, held;
        SourceCapture capture;
        Session(NexusMapBindingSavedData ledger, Object token, NexusMapBindingWitness.Expected expected, long epoch) {
            this.ledger = ledger; this.token = token; this.expected = expected; this.epoch = epoch;
        }
    }
}
