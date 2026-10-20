package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/** Actual native get/computeIfAbsent cache-miss behavior with production strict-read mixin. */
public final class NexusMapDetailStorageGameTest {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());

    @GameTest(maxTicks = 100)
    public void corruptTerrainRefusesRepeatedlyWithoutCachePoisoningOrOverwrite(GameTestHelper helper) {
        run(helper, directory -> {
            var file = file(directory); Files.createDirectories(file.getParent());
            var broken = new byte[] {1, 2, 3}; Files.write(file, broken);
            try (var storage = storage(directory)) {
                for (int i = 0; i < 2; i++) {
                    refuses(() -> storage.get(NexusMapDetailSavedData.TYPE));
                    refuses(() -> storage.computeIfAbsent(NexusMapDetailSavedData.TYPE));
                }
                storage.saveAndJoin();
                check(Arrays.equals(broken, Files.readAllBytes(file)), "Corrupt terrain was replaced");
                write(directory, fixture());
                var repaired = storage.get(NexusMapDetailSavedData.TYPE);
                check(repaired.known(31, 0, 0), "Explicit repair was not visible");
                Files.delete(file);
                check(storage.get(NexusMapDetailSavedData.TYPE) == repaired, "Cache hit read disk");
            }
        });
    }
    @GameTest(maxTicks = 100)
    public void futurePartialDuplicateAndMissingTerrainMustNeverBecomeEmpty(GameTestHelper helper) {
        run(helper, directory -> {
            for (int variant = 0; variant < 5; variant++) {
                var payload = fixture();
                if (variant == 0) payload.putInt("version", 2);
                if (variant == 1) {
                    var bad = new CompoundTag(); bad.putString("map_id", "invalid");
                    payload.getListOrEmpty("maps").add(bad);
                }
                if (variant == 2) payload.getListOrEmpty("pages").add(payload.getListOrEmpty("pages").getFirst().copy());
                if (variant == 3) payload.getListOrEmpty("maps").add(payload.getListOrEmpty("maps").getFirst().copy());
                if (variant == 4) {
                    var bad = new CompoundTag(); bad.putInt("map_id", 32); var refs = new ListTag(); refs.add(IntTag.valueOf(999));
                    bad.put("pages", refs); payload.getListOrEmpty("maps").add(bad);
                }
                write(directory, payload); var original = Files.readAllBytes(file(directory));
                try (var storage = storage(directory)) {
                    refuses(() -> storage.get(NexusMapDetailSavedData.TYPE));
                    refuses(() -> storage.computeIfAbsent(NexusMapDetailSavedData.TYPE));
                    storage.saveAndJoin();
                }
                check(Arrays.equals(original, Files.readAllBytes(file(directory))), "Rejected terrain bytes changed");
            }
        });
    }
    @GameTest(maxTicks = 100)
    public void absentTerrainBootstrapsAndValidOldFormatSurvivesSaveReload(GameTestHelper helper) {
        run(helper, directory -> {
            try (var storage = storage(directory)) {
                check(storage.get(NexusMapDetailSavedData.TYPE) == null, "Absent terrain was not absent");
                var empty = storage.computeIfAbsent(NexusMapDetailSavedData.TYPE);
                check(empty.pages(31).isEmpty(), "Fresh terrain not empty");
                storage.saveAndJoin();
            }
            var payload = fixture(); payload.remove("version"); write(directory, payload);
            try (var storage = storage(directory)) {
                var data = storage.get(NexusMapDetailSavedData.TYPE);
                check(data.known(31, 0, 0), "Legacy terrain was not retained");
                data.setDirty(); storage.saveAndJoin();
            }
            try (var reopened = storage(directory)) {
                var data = reopened.get(NexusMapDetailSavedData.TYPE);
                check(data.known(31, 0, 0), "Native save/reload lost terrain");
                try (var capture = data.beginMigrationSnapshot()) {
                    while (!capture.step(8)) { }
                    check(capture.result().maps().get(31).equals(java.util.List.of(1)), "Reload lost index");
                }
            }
        });
    }
    @GameTest(maxTicks = 100)
    public void directoryAndDanglingSymlinkAreNotAbsentTerrain(GameTestHelper helper) {
        run(helper, directory -> {
            var file = file(directory); Files.createDirectories(file);
            try (var storage = storage(directory)) { refuses(() -> storage.computeIfAbsent(NexusMapDetailSavedData.TYPE)); }
            Files.delete(file); Files.createSymbolicLink(file, directory.resolve("missing"));
            try (var storage = storage(directory)) { refuses(() -> storage.get(NexusMapDetailSavedData.TYPE)); }
            check(Files.isSymbolicLink(file), "Symlink was replaced");
        });
    }
    @GameTest(maxTicks = 100)
    public void readRaceAfterMetadataCheckCannotAuthorizeEmptyReplacement(GameTestHelper helper) {
        run(helper, directory -> {
            write(directory, fixture()); var original = Files.readAllBytes(file(directory));
            try (var storage = new SavedDataStorage(directory, DataFixers.getDataFixer(), REGISTRIES) {
                @Override public CompoundTag readTagFromDisk(Path file, DataFixTypes type, int version) throws IOException {
                    throw new NoSuchFileException("Injected terrain read race");
                }
            }) {
                refuses(() -> storage.get(NexusMapDetailSavedData.TYPE));
                refuses(() -> storage.computeIfAbsent(NexusMapDetailSavedData.TYPE));
            }
            check(Arrays.equals(original, Files.readAllBytes(file(directory))), "Read race replaced source");
        });
    }
    @GameTest(maxTicks = 100)
    public void gameplayFailureDoesNotRetryOrCreateEmptyDataAndReopenCanRecover(GameTestHelper helper) {
        run(helper, directory -> {
            var file = file(directory); Files.createDirectories(file.getParent());
            byte[] broken = {1, 2, 3}; Files.write(file, broken);
            int[] reads = {0};
            try (var storage = new SavedDataStorage(directory, DataFixers.getDataFixer(), REGISTRIES) {
                @Override public CompoundTag readTagFromDisk(Path path, DataFixTypes type, int version) throws IOException {
                    reads[0]++; return super.readTagFromDisk(path, type, version);
                }
            }) {
                check(NexusMapDetailSavedData.available(storage).isEmpty(), "Corrupt gameplay terrain accepted");
                var failure = ((NexusMapBindingStorage.TerrainFailureAccess) storage).totem$terrainFailure();
                check(failure != null, "Gameplay refusal not retained");
                for (int i = 0; i < 40; i++) check(NexusMapDetailSavedData.available(storage).isEmpty(), "Failure became empty terrain");
                check(reads[0] == 1, "Gameplay retried corrupt file repeatedly");
                storage.saveAndJoin();
                check(Arrays.equals(broken, Files.readAllBytes(file)), "Gameplay refusal replaced source");
                write(directory, fixture());
                check(NexusMapDetailSavedData.available(storage).isEmpty(), "Sticky failure cleared without reopening");
                check(((NexusMapBindingStorage.TerrainFailureAccess) storage).totem$terrainFailure() == failure,
                        "Refusal was logged/cached again");
            }
            try (var reopened = storage(directory)) {
                check(NexusMapDetailSavedData.available(reopened).orElseThrow().known(31, 0, 0), "Repair did not recover on reopen");
            }
        });
    }
    @GameTest(maxTicks = 100)
    public void unrelatedGameplayFailureIsNeverSwallowed(GameTestHelper helper) {
        run(helper, directory -> {
            var expected = new IllegalStateException("Injected unrelated operation failure");
            try (var storage = new SavedDataStorage(directory, DataFixers.getDataFixer(), REGISTRIES) {
                @Override public <T extends net.minecraft.world.level.saveddata.SavedData> T computeIfAbsent(
                        net.minecraft.world.level.saveddata.SavedDataType<T> type) { throw expected; }
            }) {
                boolean propagated = false;
                try { NexusMapDetailSavedData.available(storage); }
                catch (IllegalStateException failure) { propagated = failure == expected; }
                check(propagated, "Unrelated exception was swallowed");
                check(((NexusMapBindingStorage.TerrainFailureAccess) storage).totem$terrainFailure() == null,
                        "Unrelated exception disabled terrain");
            }
        });
    }
    private static CompoundTag fixture() {
        var data = new CompoundTag(); data.putInt("version", 1);
        var page = new CompoundTag(); page.putInt("id", 1); page.putInt("x", 64); page.putInt("z", 64);
        page.putInt("scale", 0); page.putBoolean("historical", false);
        var colors = new byte[16384]; colors[0] = 4; page.putByteArray("colors", colors);
        var pages = new ListTag(); pages.add(page); data.put("pages", pages);
        var map = new CompoundTag(); map.putInt("map_id", 31); var refs = new ListTag(); refs.add(IntTag.valueOf(1)); map.put("pages", refs);
        var maps = new ListTag(); maps.add(map); data.put("maps", maps); return data;
    }
    private static Path file(Path directory) { return NexusMapDetailSavedData.TYPE.id().withSuffix(".dat").resolveAgainst(directory); }
    private static SavedDataStorage storage(Path directory) { return new SavedDataStorage(directory, DataFixers.getDataFixer(), REGISTRIES); }
    private static void write(Path directory, CompoundTag data) throws IOException {
        var envelope = new CompoundTag(); envelope.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        envelope.put("data", data); Files.createDirectories(file(directory).getParent()); NbtIo.writeCompressed(envelope, file(directory));
    }
    private static void refuses(Runnable action) {
        try { action.run(); }
        catch (IllegalStateException failure) {
            check(failure.getMessage().startsWith("Refusing to recreate unreadable Nexus map terrain"), "Unexpected terrain refusal"); return;
        }
        throw new AssertionError("Unreadable terrain accepted or considered absent");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void run(GameTestHelper helper, Action action) {
        try {
            var directory = Files.createTempDirectory("nexus-terrain-load-");
            try { action.run(directory); }
            finally { try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            } }
            helper.succeed();
        } catch (Exception failure) { throw new IllegalStateException("Terrain storage test failed", failure); }
    }
    @FunctionalInterface private interface Action { void run(Path directory) throws Exception; }
}
