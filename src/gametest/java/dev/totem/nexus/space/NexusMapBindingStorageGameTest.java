package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import dev.totem.nexus.space.NexusMapBindingSavedData.SparseImportIntent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/** Actual storage.get/computeIfAbsent/loadCanonical paths with the production mixin active. */
public final class NexusMapBindingStorageGameTest {
    private static final MapId ID = new MapId(921);
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000921");
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());

    @GameTest(maxTicks = 100)
    public void corruptCanonicalCannotFallbackOrCacheAbsence(GameTestHelper helper) {
        withDirectory(helper, directory -> {
            var fixture = fixture();
            write(directory, NexusMapBindingSavedData.LEGACY_COMPATIBILITY_TYPE, fixture.ledger());
            var file = file(directory, NexusMapBindingSavedData.TYPE);
            Files.createDirectories(file.getParent());
            byte[] broken = {1, 2, 3, 4};
            Files.write(file, broken);
            try (var storage = storage(directory)) {
                for (int retry = 0; retry < 2; retry++) {
                    refuses(() -> NexusMapBindingSavedData.loadCanonical(storage));
                    refuses(() -> storage.get(NexusMapBindingSavedData.TYPE));
                    refuses(() -> storage.computeIfAbsent(NexusMapBindingSavedData.TYPE));
                }
                storage.saveAndJoin();
                check(Arrays.equals(broken, Files.readAllBytes(file)), "Failure overwrote canonical bytes");
                // An explicit external repair becomes visible: failure never poisoned the cache.
                write(directory, NexusMapBindingSavedData.TYPE, fixture.ledger());
                var recovered = storage.get(NexusMapBindingSavedData.TYPE);
                check(fixture.intent().equals(recovered.sparseImportIntent(ID).orElseThrow()), "Repair lost ticket");
                Files.delete(file);
                check(storage.get(NexusMapBindingSavedData.TYPE) == recovered, "Cached reads touched disk");
            }
        });
    }

    @GameTest(maxTicks = 100)
    public void corruptLegacyCannotCreateEmptyCanonical(GameTestHelper helper) {
        withDirectory(helper, directory -> {
            var legacy = file(directory, NexusMapBindingSavedData.LEGACY_COMPATIBILITY_TYPE);
            Files.createDirectories(legacy.getParent());
            byte[] broken = {5, 6, 7};
            Files.write(legacy, broken);
            try (var storage = storage(directory)) {
                for (int retry = 0; retry < 2; retry++) refuses(() -> NexusMapBindingSavedData.loadCanonical(storage));
                storage.saveAndJoin();
                check(Files.notExists(file(directory, NexusMapBindingSavedData.TYPE)), "Created empty canonical file");
                check(Arrays.equals(broken, Files.readAllBytes(legacy)), "Legacy bytes changed");
            }
        });
    }

    @GameTest(maxTicks = 100)
    public void futureCanonicalCannotFallbackToOlderLedger(GameTestHelper helper) {
        withDirectory(helper, directory -> {
            var fixture = fixture();
            write(directory, NexusMapBindingSavedData.LEGACY_COMPATIBILITY_TYPE, fixture.ledger());
            var payload = payload(fixture.ledger());
            payload.putInt("data_version", NexusMapBindingSavedData.DATA_VERSION + 1);
            writePayload(directory, payload);
            var file = file(directory, NexusMapBindingSavedData.TYPE);
            var original = Files.readAllBytes(file);
            try (var storage = storage(directory)) {
                refuses(() -> NexusMapBindingSavedData.loadCanonical(storage));
                storage.saveAndJoin();
            }
            check(Arrays.equals(original, Files.readAllBytes(file)), "Future version was replaced");
        });
    }

    @GameTest(maxTicks = 100)
    public void partialLedgerCannotDropItsRejectedBinding(GameTestHelper helper) {
        withDirectory(helper, directory -> {
            var payload = payload(fixture().ledger());
            var maps = payload.getListOrEmpty("maps");
            var bad = new CompoundTag();
            bad.putString("map_id", "invalid");
            maps.add(bad);
            payload.put("maps", maps);
            writePayload(directory, payload);
            var file = file(directory, NexusMapBindingSavedData.TYPE);
            var original = Files.readAllBytes(file);
            try (var storage = storage(directory)) {
                refuses(() -> storage.get(NexusMapBindingSavedData.TYPE));
                refuses(() -> NexusMapBindingSavedData.loadCanonical(storage));
                storage.saveAndJoin();
            }
            check(Arrays.equals(original, Files.readAllBytes(file)), "Partial decode overwrote rejected binding");
        });
    }

    @GameTest(maxTicks = 100)
    public void absentFilesBootstrapAndValidLegacyMigrationRetainsTicket(GameTestHelper helper) {
        withDirectory(helper, directory -> {
            try (var storage = storage(directory.resolve("fresh"))) {
                var empty = NexusMapBindingSavedData.loadCanonical(storage);
                check(empty.get(ID).isEmpty(), "Fresh world did not start empty");
            }
            var fixture = fixture();
            write(directory, NexusMapBindingSavedData.LEGACY_COMPATIBILITY_TYPE, fixture.ledger());
            var legacy = file(directory, NexusMapBindingSavedData.LEGACY_COMPATIBILITY_TYPE);
            var original = Files.readAllBytes(legacy);
            try (var storage = storage(directory)) {
                var canonical = NexusMapBindingSavedData.loadCanonical(storage);
                check(fixture.intent().equals(canonical.sparseImportIntent(ID).orElseThrow()), "Migration lost ticket");
                storage.saveAndJoin();
            }
            check(Arrays.equals(original, Files.readAllBytes(legacy)), "Migration changed legacy bytes");
            Files.write(legacy, new byte[] {1, 2});
            try (var storage = storage(directory)) {
                check(fixture.intent().equals(NexusMapBindingSavedData.loadCanonical(storage)
                        .sparseImportIntent(ID).orElseThrow()), "Valid canonical consulted broken legacy");
            }
        });
    }

    @GameTest(maxTicks = 100)
    public void directoryAndSymlinkAreNotAbsentLedgerFiles(GameTestHelper helper) {
        withDirectory(helper, directory -> {
            var file = file(directory, NexusMapBindingSavedData.TYPE);
            Files.createDirectories(file);
            try (var storage = storage(directory)) {
                refuses(() -> NexusMapBindingSavedData.loadCanonical(storage));
            }
            Files.delete(file);
            var target = directory.resolve("missing-target");
            Files.createSymbolicLink(file, target);
            try (var storage = storage(directory)) {
                refuses(() -> NexusMapBindingSavedData.loadCanonical(storage));
            }
            check(Files.isSymbolicLink(file), "Dangling symlink was replaced");
        });
    }

    @GameTest(maxTicks = 100)
    public void disappearanceAfterMetadataCheckRemainsAReadFailure(GameTestHelper helper) {
        withDirectory(helper, directory -> {
            write(directory, NexusMapBindingSavedData.TYPE, fixture().ledger());
            try (var storage = new SavedDataStorage(directory, DataFixers.getDataFixer(), REGISTRIES) {
                @Override public CompoundTag readTagFromDisk(Path path, DataFixTypes type, int version) throws IOException {
                    throw new NoSuchFileException("injected read race");
                }
            }) {
                refuses(() -> storage.get(NexusMapBindingSavedData.TYPE));
                refuses(() -> storage.computeIfAbsent(NexusMapBindingSavedData.TYPE));
            }
        });
    }

    @GameTest(maxTicks = 100)
    public void unrelatedSavedDataRetainsNativeFailureHandling(GameTestHelper helper) {
        withDirectory(helper, directory -> {
            var type = new SavedDataType<>(Identifier.fromNamespaceAndPath("totem-nexus-gametest", "unrelated"),
                    Counter::new, Codec.INT.xmap(Counter::new, value -> value.value), DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
            var file = file(directory, type);
            Files.createDirectories(file.getParent());
            Files.write(file, new byte[] {1, 2, 3});
            try (var storage = storage(directory)) {
                check(storage.get(type) == null, "Changed unrelated native read failure policy");
                check(storage.computeIfAbsent(type).value == 0, "Changed unrelated native creation policy");
            }
        });
    }

    @GameTest(maxTicks = 100)
    public void durableWitnessBlocksDeletedCanonicalDespiteValidLegacy(GameTestHelper helper) {
        withDirectory(helper, directory -> {
            var fixture = fixture();
            write(directory, NexusMapBindingSavedData.TYPE, fixture.ledger());
            write(directory, NexusMapBindingSavedData.LEGACY_COMPATIBILITY_TYPE, fixture.ledger());
            establishWitness(directory, fixture);
            var canonical = file(directory, NexusMapBindingSavedData.TYPE);
            var marker = Files.readAllBytes(NexusMapBindingWitness.path(canonical));
            var legacy = file(directory, NexusMapBindingSavedData.LEGACY_COMPATIBILITY_TYPE);
            var originalLegacy = Files.readAllBytes(legacy);
            Files.delete(canonical);
            // A new storage instance models restart, with no prior native cache.
            try (var reopened = storage(directory)) {
                for (int retry = 0; retry < 2; retry++) {
                    refuses(() -> NexusMapBindingSavedData.loadCanonical(reopened));
                    refuses(() -> reopened.computeIfAbsent(NexusMapBindingSavedData.TYPE));
                }
                reopened.saveAndJoin();
            }
            check(Files.notExists(canonical), "Witness allowed empty or legacy replacement");
            check(Arrays.equals(originalLegacy, Files.readAllBytes(legacy)), "Deletion recovery changed legacy");
            check(Arrays.equals(marker, Files.readAllBytes(NexusMapBindingWitness.path(canonical))), "Witness changed");
        });
    }

    @GameTest(maxTicks = 100)
    public void witnessReloadKeepsTicketAndCorruptWitnessCannotDisappear(GameTestHelper helper) {
        withDirectory(helper, directory -> {
            var fixture = fixture();
            write(directory, NexusMapBindingSavedData.TYPE, fixture.ledger());
            establishWitness(directory, fixture);
            try (var reopened = storage(directory)) {
                check(fixture.intent().equals(NexusMapBindingSavedData.loadCanonical(reopened)
                        .sparseImportIntent(ID).orElseThrow()), "Witness reload lost import ticket");
            }
            var canonical = file(directory, NexusMapBindingSavedData.TYPE);
            var original = Files.readAllBytes(canonical);
            var marker = NexusMapBindingWitness.path(canonical);
            byte[] partial = {1, 2, 3};
            Files.write(marker, partial);
            try (var reopened = storage(directory)) {
                refuses(() -> NexusMapBindingSavedData.loadCanonical(reopened));
                reopened.saveAndJoin();
            }
            check(Arrays.equals(original, Files.readAllBytes(canonical)), "Corrupt witness replaced canonical");
            check(Arrays.equals(partial, Files.readAllBytes(marker)), "Corrupt witness was silently repaired");
        });
    }

    private static void establishWitness(Path directory, Fixture fixture) {
        var expected = NexusMapBindingWitness.capture(fixture.ledger());
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try (var reader = storage(directory)) {
                NexusMapBindingWitness.establish(reader, file(directory, NexusMapBindingSavedData.TYPE), REGISTRIES, expected);
            } catch (IOException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }).join();
    }

    private static final class Counter extends SavedData {
        private final int value;
        Counter() { this(0); }
        Counter(int value) { this.value = value; }
    }
    private record Fixture(NexusMapBindingSavedData ledger, SparseImportIntent intent) {}
    private static Fixture fixture() {
        var map = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD);
        var ledger = new NexusMapBindingSavedData();
        check(ledger.bind(ID, UNIT, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), map), "Fixture bind failed");
        return new Fixture(ledger, ledger.prepareSparseImport(ID, UNIT, map).orElseThrow());
    }
    private static SavedDataStorage storage(Path directory) {
        return new SavedDataStorage(directory, DataFixers.getDataFixer(), REGISTRIES);
    }
    private static Path file(Path directory, SavedDataType<?> type) {
        return type.id().withSuffix(".dat").resolveAgainst(directory);
    }
    private static CompoundTag payload(NexusMapBindingSavedData ledger) {
        return (CompoundTag) NexusMapBindingSavedData.CODEC.encodeStart(NbtOps.INSTANCE, ledger).getOrThrow();
    }
    private static void write(Path directory, SavedDataType<NexusMapBindingSavedData> type,
                              NexusMapBindingSavedData ledger) throws IOException {
        writeTag(file(directory, type), payload(ledger));
    }
    private static void writePayload(Path directory, CompoundTag payload) throws IOException {
        writeTag(file(directory, NexusMapBindingSavedData.TYPE), payload);
    }
    private static void writeTag(Path file, CompoundTag payload) throws IOException {
        var envelope = new CompoundTag();
        envelope.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        envelope.put("data", payload);
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(envelope, file);
    }
    private static void refuses(Runnable action) {
        try { action.run(); }
        catch (IllegalStateException failure) {
            check(failure.getMessage().startsWith("Refusing to recreate unreadable Nexus map bindings"),
                    "Unexpected refusal: " + failure.getClass().getSimpleName());
            return;
        }
        throw new AssertionError("Unreadable ledger was accepted or treated as absent");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void withDirectory(GameTestHelper helper, CheckedAction action) {
        try {
            Path directory = Files.createTempDirectory("nexus-binding-load-");
            try { action.run(directory); }
            finally {
                try (var paths = Files.walk(directory)) {
                    for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
            helper.succeed();
        } catch (Exception failure) {
            throw new IllegalStateException("Binding storage test failed", failure);
        }
    }
    @FunctionalInterface private interface CheckedAction { void run(Path directory) throws Exception; }
}
