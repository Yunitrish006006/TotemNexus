package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/** Private world-storage fixtures exercise actual native snapshot/write/close wrappers. */
public final class NexusMapMigrationOwnerGameTest {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());
    private static final MapId ID = new MapId(981);
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000981");
    private static final GlobalPos ANCHOR = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);

    @GameTest(maxTicks = 300)
    public void earlierQueuedNativeSnapshotMustDrainBeforeWitness(GameTestHelper helper) {
        run(helper, Mode.NORMAL, fixture -> {
            var entered = new CompletableFuture<Void>();
            var tag = fixture.owner.stampSnapshot(fixture.ledger, envelope(fixture.ledger));
            var blocker = CompletableFuture.runAsync(() -> fixture.owner.nativeWrite(tag, () -> {
                entered.complete(null);
                fixture.ioGate.join();
            }));
            watch(helper, fixture, entered, () -> {
                var older = fixture.storage.scheduleSave();
                check(!older.isDone(), "Old native snapshot was not queued behind IO gate");
                check(fixture.ledger.bind(new MapId(982), UNIT, ANCHOR, fixture.map), "Could not prepare newer snapshot");
                var response = fixture.owner.begin();
                check(!response.isDone(), "Owner ignored queued native write");
                fixture.ioGate.complete(null);
                watch(helper, fixture, response, () -> {
                    var lease = response.join();
                    check(lease.valid() && older.isDone(), "Owner acknowledged before earlier IO drained");
                    try (var reopened = new SavedDataStorage(fixture.directory, DataFixers.getDataFixer(), REGISTRIES)) {
                        check(NexusMapBindingSavedData.loadCanonical(reopened).get(new MapId(982)).isPresent(),
                                "Earlier native snapshot replaced newer ledger");
                    }
                    lease.close(); done(helper, fixture);
                });
            });
        });
    }

    @GameTest(maxTicks = 300)
    public void pendingSaveFreezesMutationsWithoutBlockingServer(GameTestHelper helper) {
        run(helper, Mode.GATED, fixture -> {
            var candidate = new MapId(982);
            var prepared = fixture.ledger.prepareDerivation(ID, fixture.map, candidate, fixture.map.locked(), 0).orElseThrow();
            var response = fixture.owner.begin();
            check(!response.isDone(), "Preparation waited synchronously for native save");
            check(fixture.owner.begin().isCompletedExceptionally(), "Second owner session was admitted");
            check(fixture.storage.get(NexusMapBindingSavedData.TYPE) == fixture.ledger, "Read cache changed");
            check(fixture.ledger.validates(ID, UNIT, fixture.map), "Frozen map reads failed");
            check(!fixture.ledger.bind(candidate, UNIT, ANCHOR, fixture.map), "Frozen bind succeeded");
            check(fixture.ledger.prepareSparseImport(ID, UNIT, fixture.map).isEmpty(), "Frozen import preparation succeeded");
            check(!fixture.ledger.derive(ID, fixture.map, candidate, fixture.map.locked()), "Frozen derivation succeeded");
            refuses(() -> fixture.ledger.commitDerivation(prepared));
            refuses(() -> fixture.storage.set(NexusMapBindingSavedData.TYPE, new NexusMapBindingSavedData()));
            fixture.storage.gate.complete(null);
            watch(helper, fixture, response, () -> {
                var lease = response.join();
                check(lease.valid() && !fixture.ledger.acceptsMutation(), "Prepared lease did not retain freeze");
                check(NexusMapBindingWitness.present(fixture.canonical()), "Owner did not establish witness");
                lease.close();
                check(fixture.ledger.bind(candidate, UNIT, ANCHOR, fixture.map), "Lease release did not resume mutations");
                done(helper, fixture);
            });
        });
    }

    @GameTest(maxTicks = 300)
    public void heldWritesAndRetiredSnapshotsCannotOverwriteCanonical(GameTestHelper helper) {
        run(helper, Mode.GATED, fixture -> {
            var response = fixture.owner.begin(); fixture.storage.gate.complete(null);
            watch(helper, fixture, response, () -> {
                var lease = response.join();
                var original = Files.readAllBytes(fixture.canonical());
                var retired = fixture.owner.stampSnapshot(fixture.ledger, envelope(fixture.ledger));
                fixture.ledger.setDirty();
                fixture.storage.saveAndJoin();
                check(Arrays.equals(original, Files.readAllBytes(fixture.canonical())), "Held native save rewrote canonical");
                lease.close();
                check(fixture.ledger.bind(new MapId(982), UNIT, ANCHOR, fixture.map), "Mutation did not resume");
                var replayed = new AtomicBoolean();
                var replay = CompletableFuture.runAsync(() -> fixture.owner.nativeWrite(retired, () -> replayed.set(true)));
                watch(helper, fixture, replay, () -> {
                    replay.join();
                    check(!replayed.get(), "Retired snapshot replayed after release");
                    fixture.storage.saveAndJoin();
                    try (var reopened = new SavedDataStorage(fixture.directory, DataFixers.getDataFixer(), REGISTRIES)) {
                        check(NexusMapBindingSavedData.loadCanonical(reopened).get(new MapId(982)).isPresent(), "Fresh native snapshot was not saved");
                        var tag = reopened.readTagFromDisk(fixture.canonical(), NexusMapBindingSavedData.TYPE.dataFixType(),
                                SharedConstants.getCurrentVersion().dataVersion().version());
                        check(tag.get("totem_nexus_binding_snapshot_epoch") == null, "Transient epoch leaked to disk");
                    }
                    done(helper, fixture);
                });
            });
        });
    }

    @GameTest(maxTicks = 300)
    public void cancelledResponseDrainsAndRestoresOwnership(GameTestHelper helper) {
        run(helper, Mode.GATED, fixture -> {
            var response = fixture.owner.begin();
            check(response.cancel(false), "Could not cancel waiting response");
            fixture.storage.gate.complete(null);
            watchCondition(fixture, fixture.ledger::acceptsMutation, () -> {
                    var retried = fixture.owner.begin();
                    watch(helper, fixture, retried, () -> {
                        var lease = retried.join(); check(lease.valid(), "Cancelled response leaked owner slot");
                        lease.close(); done(helper, fixture);
                    });
            });
        });
    }

    @GameTest(maxTicks = 300)
    public void failedSaveFutureReleasesFreezeAndCanRetry(GameTestHelper helper) { saveFailure(helper, Mode.FAILED); }
    @GameTest(maxTicks = 300)
    public void synchronousSaveFailureReleasesFreezeAndCanRetry(GameTestHelper helper) { saveFailure(helper, Mode.THROWN); }
    private static void saveFailure(GameTestHelper helper, Mode mode) {
        run(helper, mode, fixture -> {
            var response = fixture.owner.begin();
            watch(helper, fixture, response, () -> {
                check(response.isCompletedExceptionally(), "Save failure was accepted");
                check(fixture.ledger.acceptsMutation(), "Save failure leaked freeze");
                var retried = fixture.owner.begin();
                watch(helper, fixture, retried, () -> {
                    var lease = retried.join(); check(lease.valid(), "Save failure prevented retry");
                    lease.close(); done(helper, fixture);
                });
            });
        });
    }

    @GameTest(maxTicks = 300)
    public void closingPendingStorageDoesNotRequireServerCallback(GameTestHelper helper) {
        run(helper, Mode.GATED, fixture -> {
            var response = fixture.owner.begin();
            check(!response.isDone(), "Close fixture was not waiting");
            CompletableFuture.runAsync(() -> fixture.storage.gate.complete(null));
            fixture.finishStorage();
            check(response.isDone(), "Storage close did not drain owner");
            if (!response.isCompletedExceptionally()) check(!response.join().valid(), "Shutdown left valid lease");
            check(fixture.ledger.acceptsMutation(), "Shutdown leaked freeze");
            check(fixture.owner.begin().isCompletedExceptionally(), "Closed owner admitted work");
            done(helper, fixture);
        });
    }

    @GameTest(maxTicks = 300)
    public void leaseReleaseRequiresServerThreadAndCloseReclaimsHeldLease(GameTestHelper helper) {
        run(helper, Mode.GATED, fixture -> {
            var response = fixture.owner.begin(); fixture.storage.gate.complete(null);
            watch(helper, fixture, response, () -> {
                var lease = response.join();
                var wrongThread = CompletableFuture.runAsync(lease::close);
                watch(helper, fixture, wrongThread, () -> {
                    check(wrongThread.isCompletedExceptionally(), "Off-thread lease release succeeded");
                    check(lease.valid(), "Off-thread release changed ownership");
                    fixture.finishStorage();
                    check(!lease.valid() && fixture.ledger.acceptsMutation(), "Close did not reclaim held lease");
                    lease.close(); done(helper, fixture);
                });
            });
        });
    }

    @GameTest(maxTicks = 300)
    public void mismatchingSealedSnapshotInvalidatesLeaseWithoutWriting(GameTestHelper helper) {
        run(helper, Mode.GATED, fixture -> {
            var response = fixture.owner.begin(); fixture.storage.gate.complete(null);
            watch(helper, fixture, response, () -> {
                var lease = response.join();
                var original = Files.readAllBytes(fixture.canonical());
                var invalid = fixture.owner.stampSnapshot(fixture.ledger, envelope(fixture.ledger));
                invalid.getCompoundOrEmpty("data").putInt("data_version", NexusMapBindingSavedData.DATA_VERSION + 1);
                var wrote = new AtomicBoolean();
                var attempted = CompletableFuture.runAsync(() -> fixture.owner.nativeWrite(invalid, () -> wrote.set(true)));
                watch(helper, fixture, attempted, () -> {
                    attempted.join();
                    check(!lease.valid() && !wrote.get(), "Mismatching frozen snapshot was accepted");
                    check(Arrays.equals(original, Files.readAllBytes(fixture.canonical())), "Mismatch modified canonical");
                    lease.close(); done(helper, fixture);
                });
            });
        });
    }

    private enum Mode { NORMAL, GATED, FAILED, THROWN }
    private static final class TestStorage extends SavedDataStorage {
        final CompletableFuture<Void> gate = new CompletableFuture<>();
        final Mode mode;
        boolean first = true;
        TestStorage(Path directory, Mode mode) { super(directory, DataFixers.getDataFixer(), REGISTRIES); this.mode = mode; }
        @Override public CompletableFuture<?> scheduleSave() {
            boolean initial = first; first = false;
            if (initial && mode == Mode.FAILED) return CompletableFuture.failedFuture(new IllegalStateException("injected save failure"));
            if (initial && mode == Mode.THROWN) throw new IllegalStateException("injected synchronous save failure");
            var pending = super.scheduleSave();
            return initial && mode == Mode.GATED ? pending.thenCompose(ignored -> gate) : pending;
        }
    }
    private static final class Fixture implements AutoCloseable {
        final Path directory;
        final TestStorage storage;
        final MapItemSavedData map = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD);
        final NexusMapBindingSavedData ledger = new NexusMapBindingSavedData();
        final NexusMapMigrationOwner owner;
        final CompletableFuture<Void> ioGate = new CompletableFuture<>();
        final List<Watch> watches = new ArrayList<>();
        boolean storageClosed, cleaned;
        Fixture(GameTestHelper helper, Mode mode) throws Exception {
            directory = Files.createTempDirectory("nexus-migration-owner-");
            storage = new TestStorage(directory, mode);
            check(ledger.bind(ID, UNIT, ANCHOR, map), "Fixture bind failed");
            ledger.prepareSparseImport(ID, UNIT, map).orElseThrow();
            storage.set(NexusMapBindingSavedData.TYPE, ledger);
            owner = NexusMapMigrationOwner.forStorage(storage, helper.getLevel().getServer());
        }
        Path canonical() { return NexusMapBindingSavedData.TYPE.id().withSuffix(".dat").resolveAgainst(directory); }
        void finishStorage() {
            if (storageClosed) return;
            ioGate.complete(null);
            storage.gate.complete(null);
            storage.close(); storageClosed = true;
        }
        @Override public void close() throws Exception {
            if (cleaned) return;
            finishStorage();
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
            cleaned = true;
        }
    }
    private static CompoundTag envelope(NexusMapBindingSavedData ledger) {
        var root = new CompoundTag();
        root.put("data", NexusMapBindingSavedData.CODEC.encodeStart(NbtOps.INSTANCE, ledger).getOrThrow());
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        return root;
    }
    private static void run(GameTestHelper helper, Mode mode, Action body) {
        try {
            var fixture = new Fixture(helper, mode);
            helper.runBeforeTestEnd(() -> guarded(fixture, fixture::close));
            // Register once before the native tick-action iterator starts. Nested
            // waits only update this fixture list, never Mojang's task map.
            helper.onEachTick(() -> {
                if (fixture.cleaned) return;
                for (var watch : List.copyOf(fixture.watches)) {
                    if (!watch.done && watch.ready.getAsBoolean()) {
                        watch.done = true;
                        guarded(fixture, watch.action);
                    }
                    if (fixture.cleaned) return;
                }
                fixture.watches.removeIf(watch -> watch.done);
            });
            guarded(fixture, () -> body.run(fixture));
        } catch (Exception failure) { throw new IllegalStateException("Migration owner fixture failed", failure); }
    }
    private static void watch(GameTestHelper helper, Fixture fixture, CompletableFuture<?> response, Checked body) {
        watchCondition(fixture, response::isDone, body);
    }
    private static void watchCondition(Fixture fixture, BooleanSupplier ready, Checked body) {
        fixture.watches.add(new Watch(ready, body));
    }
    private static final class Watch {
        final BooleanSupplier ready;
        final Checked action;
        boolean done;
        Watch(BooleanSupplier ready, Checked action) { this.ready = ready; this.action = action; }
    }
    private static void guarded(Fixture fixture, Checked body) {
        try { body.run(); }
        catch (Exception | AssertionError failure) {
            try { fixture.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw new IllegalStateException("Migration owner test failed", failure);
        }
    }
    private static void done(GameTestHelper helper, Fixture fixture) throws Exception { fixture.close(); helper.succeed(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void refuses(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Frozen mutation was admitted");
    }
    @FunctionalInterface private interface Action { void run(Fixture fixture) throws Exception; }
    @FunctionalInterface private interface Checked { void run() throws Exception; }
}
