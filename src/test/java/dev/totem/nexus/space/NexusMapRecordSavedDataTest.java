package dev.totem.nexus.space;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusMapRecordSavedDataTest {
    private static final String DETAIL_ROOT = "1".repeat(64);
    private static final String COVERAGE_ROOT = "a".repeat(64);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess.Frozen builtInLookup = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        Set<?> builtInRegistryKeys = builtInLookup.listRegistryKeys().collect(Collectors.toSet());
        HolderLookup.Provider lookup = HolderLookup.Provider.create(Stream.concat(
                builtInLookup.listRegistries(),
                VanillaRegistries.createWorldLookup().listRegistries()
                        .filter(registry -> !builtInRegistryKeys.contains(registry.key()))));
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(initializer -> initializer.apply());
        Bootstrap.validate();
    }

    @Test
    void recordsRoundTripThroughActualSavedDataStorage(@TempDir Path directory) {
        MapItemSavedData nativeData = map(0, 0, (byte) 4, false, Level.OVERWORLD);
        var entry = new NexusMapRecordSavedData.Entry(41, Level.OVERWORLD,
                nativeData.centerX, nativeData.centerZ, 4, false, 6,
                List.of(39, 40), Optional.of(DETAIL_ROOT), Optional.of(COVERAGE_ROOT), 7L);

        try (var storage = storage(directory)) {
            var records = new NexusMapRecordSavedData();
            assertEquals(NexusMapRecordSavedData.RegisterResult.INSERTED, records.register(entry));
            storage.set(NexusMapRecordSavedData.TYPE, records);
            storage.saveAndJoin();
        }
        try (var storage = storage(directory)) {
            var restored = storage.get(NexusMapRecordSavedData.TYPE);
            assertEquals(entry, restored.get(new MapId(41)).orElseThrow());
            assertEquals(6, restored.resolve(new MapId(41), nativeData).orElseThrow().extent(nativeData).scale());
        }
    }

    @Test
    void registrationIsIdempotentButRejectsSameMapIdConflict() {
        var nativeData = map(128, -256, (byte) 2, false, Level.OVERWORLD);
        var baseline = NexusMapRecordSavedData.Entry.baseline(new MapId(77), nativeData);
        var records = new NexusMapRecordSavedData();

        assertEquals(NexusMapRecordSavedData.RegisterResult.INSERTED, records.register(baseline));
        assertEquals(NexusMapRecordSavedData.RegisterResult.EXISTING, records.register(baseline));
        var conflict = new NexusMapRecordSavedData.Entry(77, Level.OVERWORLD,
                baseline.centerX(), baseline.centerZ(), 2, true, 2,
                List.of(), Optional.empty(), Optional.empty(), 0L);
        assertEquals(NexusMapRecordSavedData.RegisterResult.CONFLICT, records.register(conflict));
        assertEquals(baseline, records.get(new MapId(77)).orElseThrow());
    }

    @Test
    void nativeBootstrapIsAtomicAndIdempotent() {
        var id = new MapId(78);
        var nativeData = map(128, -256, (byte) 2, false, Level.OVERWORLD);
        var expected = NexusMapRecordSavedData.Entry.baseline(id, nativeData);
        var records = new NexusMapRecordSavedData();

        var inserted = records.bootstrap(id, nativeData);
        assertEquals(NexusMapRecordSavedData.RegisterResult.INSERTED, inserted.result());
        assertEquals(expected, inserted.entry().orElseThrow());

        var retry = records.bootstrap(id, nativeData);
        assertEquals(NexusMapRecordSavedData.RegisterResult.EXISTING, retry.result());
        assertEquals(expected, retry.entry().orElseThrow());
        assertEquals(expected, records.get(id).orElseThrow());
    }

    @Test
    void compatibleBootstrapPreservesExistingExtendedRecord() {
        var id = new MapId(79);
        var nativeData = map(0, 0, (byte) 4, false, Level.OVERWORLD);
        var extended = new NexusMapRecordSavedData.Entry(id.id(), Level.OVERWORLD,
                nativeData.centerX, nativeData.centerZ, 4, false, 6,
                List.of(77, 78), Optional.of(DETAIL_ROOT), Optional.of(COVERAGE_ROOT), 9L);
        var records = new NexusMapRecordSavedData();
        assertEquals(NexusMapRecordSavedData.RegisterResult.INSERTED, records.register(extended));

        var result = records.bootstrap(id, nativeData);

        assertEquals(NexusMapRecordSavedData.RegisterResult.EXISTING, result.result());
        assertEquals(extended, result.entry().orElseThrow());
        assertEquals(extended, records.get(id).orElseThrow());
    }

    @Test
    void scaleBeyondFourPublishesExactGeometryRootsAndLineage() {
        var sourceId = new MapId(83);
        var targetId = new MapId(84);
        var sourceNative = map(1024, -2048, (byte) 4, false, Level.NETHER);
        var source = new NexusMapRecordSavedData.Entry(sourceId.id(), Level.NETHER,
                sourceNative.centerX, sourceNative.centerZ, 4, false, 4,
                List.of(), Optional.of(DETAIL_ROOT), Optional.of(COVERAGE_ROOT), 7L);
        var targetNative = map(source.centerX(), source.centerZ(), (byte) 4, false, Level.NETHER);
        var records = new NexusMapRecordSavedData();
        assertEquals(NexusMapRecordSavedData.RegisterResult.INSERTED, records.register(source));

        var prepared = records.prepareDerivation(sourceId, sourceNative, targetId, targetNative, 5).orElseThrow();
        assertTrue(records.get(targetId).isEmpty());
        assertTrue(records.commitDerivation(prepared));

        var child = records.resolve(targetId, targetNative).orElseThrow();
        assertEquals(source.centerX(), child.centerX());
        assertEquals(source.centerZ(), child.centerZ());
        assertEquals(Level.NETHER, child.dimension());
        assertEquals(4, child.nativeScale());
        assertEquals(5, child.logicalScale());
        assertEquals(List.of(sourceId.id()), child.lineage());
        assertEquals(source.detailRoot(), child.detailRoot());
        assertEquals(source.coverageRoot(), child.coverageRoot());
        assertEquals(8L, child.revision());
    }

    @Test
    void scaleZeroLockCanRecordItsParentWithoutChangingExtent() {
        var sourceId = new MapId(85);
        var targetId = new MapId(86);
        var sourceNative = map(0, 0, (byte) 0, false, Level.OVERWORLD);
        var targetNative = sourceNative.locked();
        var records = new NexusMapRecordSavedData();
        records.bootstrap(sourceId, sourceNative);

        var prepared = records.prepareDerivation(sourceId, sourceNative, targetId, targetNative, 0).orElseThrow();
        assertTrue(records.commitDerivation(prepared));

        var child = records.resolve(targetId, targetNative).orElseThrow();
        assertTrue(child.locked());
        assertEquals(0, child.logicalScale());
        assertEquals(List.of(sourceId.id()), child.lineage());
    }

    @Test
    void contradictoryDerivationCannotPublishAChild() {
        var sourceId = new MapId(87);
        var targetId = new MapId(88);
        var sourceNative = map(0, 0, (byte) 1, false, Level.OVERWORLD);
        var wrongCenter = map(4096, 0, (byte) 2, false, Level.OVERWORLD);
        var records = new NexusMapRecordSavedData();
        records.bootstrap(sourceId, sourceNative);

        assertTrue(records.prepareDerivation(sourceId, sourceNative, targetId, wrongCenter, 2).isEmpty());
        assertTrue(records.get(targetId).isEmpty());
    }

    @Test
    void contradictoryOrInvalidBootstrapFailsWithoutReplacingRecord() {
        var id = new MapId(80);
        var snapshot = new NexusMapRecordSavedData.NativeSnapshot(
                id.id(), Level.OVERWORLD, 64, -64, 2, false);
        var baseline = snapshot.baseline();
        var records = new NexusMapRecordSavedData();
        assertEquals(NexusMapRecordSavedData.RegisterResult.INSERTED, records.bootstrap(snapshot).result());

        var conflicts = List.of(
                new NexusMapRecordSavedData.NativeSnapshot(id.id(), Level.NETHER, 64, -64, 2, false),
                new NexusMapRecordSavedData.NativeSnapshot(id.id(), Level.OVERWORLD, 512, -64, 2, false),
                new NexusMapRecordSavedData.NativeSnapshot(id.id(), Level.OVERWORLD, 64, -64, 3, false),
                new NexusMapRecordSavedData.NativeSnapshot(id.id(), Level.OVERWORLD, 64, -64, 2, true));
        for (var conflict : conflicts) {
            var result = records.bootstrap(conflict);
            assertEquals(NexusMapRecordSavedData.RegisterResult.CONFLICT, result.result());
            assertTrue(result.entry().isEmpty());
            assertEquals(baseline, records.get(id).orElseThrow());
        }

        assertEquals(NexusMapRecordSavedData.RegisterResult.CONFLICT,
                records.bootstrap(new MapId(-1), map(0, 0, (byte) 0, false, Level.OVERWORLD)).result());
        assertTrue(records.get(new MapId(-1)).isEmpty());
    }

    @Test
    void bootstrapUsesImmutableNativeSnapshot() {
        var nativeData = map(300, -700, (byte) 3, false, Level.OVERWORLD);
        var snapshot = NexusMapRecordSavedData.NativeSnapshot.capture(new MapId(81), nativeData);
        var subsequentlyLocked = nativeData.locked();

        assertEquals(nativeData.centerX, snapshot.centerX());
        assertEquals(nativeData.centerZ, snapshot.centerZ());
        assertEquals(3, snapshot.nativeScale());
        assertFalse(snapshot.locked());
        assertTrue(subsequentlyLocked.locked);
        assertFalse(snapshot.locked());
    }

    @Test
    void competingBootstrapCannotReplaceWinner() throws Exception {
        var id = new MapId(82);
        var first = new NexusMapRecordSavedData.NativeSnapshot(id.id(), Level.OVERWORLD, 0, 0, 1, false);
        var second = new NexusMapRecordSavedData.NativeSnapshot(id.id(), Level.NETHER, 256, 256, 2, true);
        var records = new NexusMapRecordSavedData();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return records.bootstrap(first);
            });
            var secondFuture = executor.submit(() -> {
                ready.countDown();
                start.await();
                return records.bootstrap(second);
            });
            ready.await();
            start.countDown();
            var firstResult = firstFuture.get();
            var secondResult = secondFuture.get();

            assertEquals(1, Stream.of(firstResult, secondResult)
                    .filter(result -> result.result() == NexusMapRecordSavedData.RegisterResult.INSERTED).count());
            assertEquals(1, Stream.of(firstResult, secondResult)
                    .filter(result -> result.result() == NexusMapRecordSavedData.RegisterResult.CONFLICT).count());
            var winner = Stream.of(firstResult, secondResult)
                    .filter(result -> result.result() == NexusMapRecordSavedData.RegisterResult.INSERTED)
                    .findFirst().orElseThrow().entry().orElseThrow();
            assertEquals(winner, records.get(id).orElseThrow());
        }
    }

    @Test
    void bootstrappedBaselineSurvivesActualSavedDataReload(@TempDir Path directory) {
        var id = new MapId(83);
        var nativeData = map(-512, 768, (byte) 3, true, Level.NETHER);
        var expected = NexusMapRecordSavedData.Entry.baseline(id, nativeData);

        try (var storage = storage(directory)) {
            var records = new NexusMapRecordSavedData();
            assertEquals(NexusMapRecordSavedData.RegisterResult.INSERTED,
                    records.bootstrap(id, nativeData).result());
            storage.set(NexusMapRecordSavedData.TYPE, records);
            storage.saveAndJoin();
        }
        try (var storage = storage(directory)) {
            var restored = storage.get(NexusMapRecordSavedData.TYPE);
            assertEquals(expected, restored.get(id).orElseThrow());
            assertEquals(NexusMapRecordSavedData.RegisterResult.EXISTING,
                    restored.bootstrap(id, nativeData).result());
        }
    }

    @Test
    void unsupportedLegacyClaimsCannotImportIdentityScaleOrDetail() {
        var unitId = UUID.randomUUID();
        var nativeData = map(0, 0, (byte) 4, false, Level.OVERWORLD);
        var legacy = new NexusMapBindingSavedData();
        var anchor = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);
        var scaleFour = new MapId(84);
        var scaleFive = new MapId(85);
        var scaleSix = new MapId(86);
        var scaleSeven = new MapId(87);
        assertTrue(legacy.bind(scaleFour, unitId, anchor, nativeData));
        assertTrue(legacy.derive(scaleFour, nativeData, scaleFive, nativeData, 5));
        assertTrue(legacy.derive(scaleFive, nativeData, scaleSix, nativeData, 6));
        assertTrue(legacy.derive(scaleSix, nativeData, scaleSeven, nativeData, 7));
        assertEquals(7, legacy.resolve(scaleSeven, nativeData).orElseThrow().extent(nativeData).scale());

        var oldItem = new ItemStack(Items.FILLED_MAP);
        oldItem.set(DataComponents.MAP_ID, scaleSeven);
        assertTrue(NexusInterfaceBinding.writeIdentity(oldItem, unitId));
        assertEquals(unitId, NexusInterfaceBinding.read(oldItem));

        var records = new NexusMapRecordSavedData();
        var result = records.bootstrap(scaleSeven, nativeData);

        assertEquals(NexusMapRecordSavedData.RegisterResult.INSERTED, result.result());
        var baseline = result.entry().orElseThrow();
        assertEquals(MapItemSavedData.MAX_SCALE, baseline.logicalScale());
        assertEquals(nativeData.scale, baseline.nativeScale());
        assertTrue(baseline.lineage().isEmpty());
        assertTrue(baseline.detailRoot().isEmpty());
        assertTrue(baseline.coverageRoot().isEmpty());
        assertEquals(0L, baseline.revision());
        assertEquals(unitId, NexusInterfaceBinding.read(oldItem));
        assertEquals(scaleSeven, oldItem.get(DataComponents.MAP_ID));
    }

    @Test
    void nativeGeometryMismatchFailsClosed() {
        var nativeData = map(0, 0, (byte) 1, false, Level.OVERWORLD);
        var id = new MapId(91);
        var records = new NexusMapRecordSavedData();
        assertEquals(NexusMapRecordSavedData.RegisterResult.INSERTED,
                records.register(NexusMapRecordSavedData.Entry.baseline(id, nativeData)));

        assertTrue(records.resolve(id, nativeData).isPresent());
        assertTrue(records.resolve(id, map(0, 0, (byte) 1, false, Level.NETHER)).isEmpty());
        assertTrue(records.resolve(id, map(512, 0, (byte) 1, false, Level.OVERWORLD)).isEmpty());
        assertTrue(records.resolve(id, map(0, 0, (byte) 2, false, Level.OVERWORLD)).isEmpty());
        var locked = nativeData.locked();
        assertTrue(records.resolve(id, locked).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> records.get(id).orElseThrow().extent(locked));
    }

    @Test
    void invalidScaleLineageRootsAndDuplicateCodecEntriesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> entry(1, 3, 2, List.of(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> entry(1, 3, 5, List.of(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> entry(1, 4, 5, List.of(2, 2), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> entry(1, 4, 5, List.of(1), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> entry(1, 4, 5, List.of(), Optional.of("BAD")));
        assertThrows(IllegalArgumentException.class, () -> new NexusMapRecordSavedData.Entry(
                1, Level.OVERWORLD, 0, 0, 4, false, 5,
                List.of(), Optional.empty(), Optional.empty(), -1L));

        var records = new NexusMapRecordSavedData();
        records.register(entry(1, 4, 5, List.of(0), Optional.of(DETAIL_ROOT)));
        var encoded = NexusMapRecordSavedData.CODEC.encodeStart(JsonOps.INSTANCE, records).getOrThrow().getAsJsonObject();
        encoded.getAsJsonArray("maps").add(encoded.getAsJsonArray("maps").get(0).deepCopy());
        assertFalse(NexusMapRecordSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).result().isPresent());

        var malformedRoot = NexusMapRecordSavedData.CODEC.encodeStart(JsonOps.INSTANCE, records)
                .getOrThrow().getAsJsonObject();
        malformedRoot.getAsJsonArray("maps").get(0).getAsJsonObject().addProperty("detail_root", "BAD");
        assertFalse(NexusMapRecordSavedData.CODEC.parse(JsonOps.INSTANCE, malformedRoot).result().isPresent());
    }

    private static NexusMapRecordSavedData.Entry entry(
            int id, int nativeScale, int logicalScale, List<Integer> lineage, Optional<String> root) {
        return new NexusMapRecordSavedData.Entry(id, Level.OVERWORLD, 0, 0,
                nativeScale, false, logicalScale, lineage, root, Optional.empty(), 0L);
    }

    private static MapItemSavedData map(
            int x, int z, byte scale, boolean locked, net.minecraft.resources.ResourceKey<Level> dimension) {
        var data = MapItemSavedData.createFresh(x, z, scale, false, false, dimension);
        return locked ? data.locked() : data;
    }

    private static SavedDataStorage storage(Path directory) {
        return new SavedDataStorage(directory, DataFixers.getDataFixer(),
                HolderLookup.Provider.create(Stream.empty()));
    }
}
