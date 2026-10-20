package dev.totem.nexus.space;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusMapBindingAndPayloadPolicyTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void mapBindingRoundTripsAndRequiresItsPersistedMapAndCurrentAnchorIdentity() {
        MapId mapId = new MapId(7);
        UUID unitId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        BlockPos anchor = new BlockPos(24, 70, -40);
        MapItemSavedData mapData = MapItemSavedData.createFresh(
                anchor.getX(), anchor.getZ(), (byte) 1, false, false, Level.OVERWORLD);
        NexusMapBindingSavedData bindings = new NexusMapBindingSavedData();

        assertTrue(bindings.bind(mapId, unitId, GlobalPos.of(Level.OVERWORLD, anchor), mapData));
        assertTrue(bindings.validates(mapId, unitId, mapData));
        assertFalse(bindings.bind(mapId, UUID.randomUUID(), GlobalPos.of(Level.OVERWORLD, anchor), mapData));

        NexusMapBindingSavedData restored = NexusMapBindingSavedData.CODEC
                .parse(JsonOps.INSTANCE, NexusMapBindingSavedData.CODEC.encodeStart(JsonOps.INSTANCE, bindings)
                        .getOrThrow(IllegalArgumentException::new))
                .getOrThrow(IllegalArgumentException::new);
        NexusMapBindingSavedData.Entry entry = restored.resolve(mapId, mapData).orElseThrow();
        assertTrue(entry.matchesUnit(unit(unitId, Level.OVERWORLD, anchor, SpaceUnitStatus.ACTIVE)));
        assertFalse(entry.matchesUnit(unit(unitId, Level.NETHER, anchor, SpaceUnitStatus.ACTIVE)));
        assertFalse(entry.matchesUnit(unit(unitId, Level.OVERWORLD, anchor.offset(1, 0, 0), SpaceUnitStatus.ACTIVE)));
        assertFalse(entry.matchesUnit(unit(unitId, Level.OVERWORLD, anchor, SpaceUnitStatus.DISABLED)));
    }

    @Test
    void scaleAppendsDetailLineageWhileLockCopiesIt() {
        UUID unitId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        BlockPos anchor = new BlockPos(0, 70, 0);
        GlobalPos global = GlobalPos.of(Level.OVERWORLD, anchor);
        MapId scaleZeroId = new MapId(20);
        MapId scaleOneId = new MapId(21);
        MapId lockedId = new MapId(22);
        MapId scaleTwoId = new MapId(23);
        MapItemSavedData scaleZero = exactMap((byte) 0);
        MapItemSavedData scaleOne = exactMap((byte) 1);
        MapItemSavedData locked = scaleOne.locked();
        MapItemSavedData scaleTwo = exactMap((byte) 2);
        NexusMapBindingSavedData bindings = new NexusMapBindingSavedData();

        assertTrue(bindings.bind(scaleZeroId, unitId, global, scaleZero));
        assertTrue(bindings.derive(scaleZeroId, scaleZero, scaleOneId, scaleOne));
        assertEquals(List.of(scaleZeroId.id()), bindings.get(scaleOneId).orElseThrow().detailAncestors());

        assertTrue(bindings.derive(scaleOneId, scaleOne, lockedId, locked));
        assertEquals(List.of(scaleZeroId.id()), bindings.get(lockedId).orElseThrow().detailAncestors());

        assertTrue(bindings.derive(scaleOneId, scaleOne, scaleTwoId, scaleTwo));
        assertEquals(List.of(scaleZeroId.id(), scaleOneId.id()),
                bindings.get(scaleTwoId).orElseThrow().detailAncestors());
    }

    @Test
    void legacyBindingDecodesWithoutInventingDetailLineage() {
        MapId mapId = new MapId(30);
        UUID unitId = UUID.fromString("00000000-0000-0000-0000-000000000203");
        BlockPos anchor = new BlockPos(0, 70, 0);
        MapItemSavedData mapData = MapItemSavedData.createFresh(0, 0, (byte) 2, false, false, Level.OVERWORLD);
        NexusMapBindingSavedData bindings = new NexusMapBindingSavedData();
        assertTrue(bindings.bind(mapId, unitId, GlobalPos.of(Level.OVERWORLD, anchor), mapData));

        JsonElement encoded = NexusMapBindingSavedData.CODEC.encodeStart(JsonOps.INSTANCE, bindings)
                .getOrThrow(IllegalArgumentException::new);
        JsonObject legacy = encoded.getAsJsonObject();
        legacy.addProperty("data_version", 1);
        legacy.getAsJsonArray("maps").get(0).getAsJsonObject().remove("detail_ancestors");
        legacy.getAsJsonArray("maps").get(0).getAsJsonObject().remove("effective_scale");

        NexusMapBindingSavedData restored = NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, legacy)
                .getOrThrow(IllegalArgumentException::new);
        assertTrue(restored.get(mapId).orElseThrow().detailAncestors().isEmpty());
        assertEquals(mapData.scale, restored.resolve(mapId, mapData).orElseThrow().extent(mapData).scale());
    }

    @Test
    void bindingRejectsFabricatedOrInvalidDetailAncestry() {
        UUID unitId = UUID.fromString("00000000-0000-0000-0000-000000000204");
        GlobalPos anchor = GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 70, 0));
        assertThrows(IllegalArgumentException.class, () -> new NexusMapBindingSavedData.Entry(
                40, unitId, anchor, 0, 0, List.of(40)));
        assertThrows(IllegalArgumentException.class, () -> new NexusMapBindingSavedData.Entry(
                40, unitId, anchor, 0, 0, List.of(39, 39)));

        NexusMapBindingSavedData bindings = new NexusMapBindingSavedData();
        MapId sourceId = new MapId(41);
        MapItemSavedData source = exactMap((byte) 0);
        MapItemSavedData skippedScale = exactMap((byte) 2);
        assertTrue(bindings.bind(sourceId, unitId, anchor, source));
        assertFalse(bindings.derive(sourceId, source, new MapId(42), skippedScale));
    }

    @Test
    void newBindingsPersistTheirAuthoritativeScale() {
        NexusMapBindingSavedData bindings = new NexusMapBindingSavedData();
        MapId id = new MapId(50);
        assertTrue(bindings.bind(id, UUID.randomUUID(),
                GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), exactMap((byte) 4)));
        JsonObject entry = NexusMapBindingSavedData.CODEC.encodeStart(JsonOps.INSTANCE, bindings)
                .getOrThrow().getAsJsonObject().getAsJsonArray("maps").get(0).getAsJsonObject();
        assertTrue(entry.has("effective_scale"), "New records must not infer extent from mutable vanilla data");
        assertEquals(4, entry.get("effective_scale").getAsInt());
    }

    @Test
    void persistedScaleRejectsMismatchedVanillaBackingData() {
        NexusMapBindingSavedData bindings = new NexusMapBindingSavedData();
        MapId id = new MapId(51);
        assertTrue(bindings.bind(id, UUID.randomUUID(),
                GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), exactMap((byte) 4)));
        assertTrue(bindings.resolve(id, exactMap((byte) 3)).isEmpty());
    }

    @Test
    void malformedPersistedScaleIsRejectedInsteadOfIgnored() {
        NexusMapBindingSavedData bindings = new NexusMapBindingSavedData();
        assertTrue(bindings.bind(new MapId(52), UUID.randomUUID(),
                GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), exactMap((byte) 4)));
        JsonObject data = NexusMapBindingSavedData.CODEC.encodeStart(JsonOps.INSTANCE, bindings)
                .getOrThrow().getAsJsonObject();
        for (int scale : new int[]{-1, 31, Integer.MAX_VALUE}) {
            JsonObject invalid = data.deepCopy();
            invalid.getAsJsonArray("maps").get(0).getAsJsonObject().addProperty("effective_scale", scale);
            assertTrue(NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, invalid).isError(),
                    "Malformed explicit extent must fail closed: " + scale);
        }
    }

    @Test
    void extendedLineageRoundTripsBeyondTheVanillaCapAndStopsAtFullWorldCoverage() {
        NexusMapBindingSavedData bindings = new NexusMapBindingSavedData();
        UUID unit = UUID.randomUUID();
        GlobalPos anchor = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);
        MapId current = new MapId(100);
        MapItemSavedData backing = exactMap((byte) 4);
        assertTrue(bindings.bind(current, unit, anchor, backing));
        int scale = 4;
        while (bindings.resolve(current, backing).orElseThrow().extent(backing).expanded().isPresent()) {
            MapId next = new MapId(current.id() + 1);
            assertTrue(bindings.derive(current, backing, next, exactMap((byte) 4), scale + 1));
            scale++;
            assertEquals(scale, bindings.get(next).orElseThrow().effectiveScale());
            assertEquals(scale - 4, bindings.get(next).orElseThrow().detailAncestors().size());
            assertEquals(unit, bindings.get(next).orElseThrow().unitId());
            assertEquals(anchor, bindings.get(next).orElseThrow().anchor());
            current = next;
        }
        assertTrue(scale > 4);
        assertTrue(bindings.get(current).orElseThrow().extent(backing).coversSupportedWorld());
        assertFalse(bindings.derive(current, backing, new MapId(200), backing, scale + 1));
        assertTrue(bindings.get(new MapId(200)).isEmpty());

        MapId lockedId = new MapId(201);
        assertTrue(bindings.derive(current, backing, lockedId, backing.locked(), scale));
        NexusMapBindingSavedData restored = NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE,
                NexusMapBindingSavedData.CODEC.encodeStart(JsonOps.INSTANCE, bindings).getOrThrow()).getOrThrow();
        assertEquals(bindings.get(current), restored.resolve(current, backing));
        assertEquals(bindings.get(lockedId), restored.resolve(lockedId, backing.locked()));
        assertEquals(scale, restored.get(lockedId).orElseThrow().extent(backing.locked()).scale());
    }

    @Test
    void failedExtentTransitionsLeaveNoResultOrLineageMutation() {
        NexusMapBindingSavedData bindings = new NexusMapBindingSavedData();
        MapId source = new MapId(300);
        MapItemSavedData backing = exactMap((byte) 4);
        assertTrue(bindings.bind(source, UUID.randomUUID(), GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), backing));
        var original = bindings.get(source).orElseThrow();
        for (int scale : new int[]{-1, 3, 6, Integer.MAX_VALUE}) {
            assertFalse(bindings.derive(source, backing, new MapId(301), backing, scale));
            assertTrue(bindings.get(new MapId(301)).isEmpty());
            assertEquals(original, bindings.get(source).orElseThrow());
        }
        assertFalse(bindings.derive(source, backing, new MapId(301), backing, 4)); // Not a lock.
        assertFalse(bindings.derive(source, backing, new MapId(301), backing.locked(), 5));
        assertTrue(bindings.derive(source, backing, new MapId(302), backing.locked(), 4));
        assertFalse(bindings.derive(new MapId(302), backing.locked(), new MapId(303), backing, 5));
        assertFalse(bindings.derive(new MapId(302), backing.locked(), new MapId(303), backing, 4));
        assertTrue(bindings.get(new MapId(301)).isEmpty());
        assertTrue(bindings.get(new MapId(303)).isEmpty());
    }

    @Test
    void legacyScaleFourBindingCanAcquireExplicitExtendedExtentWithoutRebinding() {
        NexusMapBindingSavedData bindings = new NexusMapBindingSavedData();
        MapId source = new MapId(400);
        UUID unit = UUID.randomUUID();
        GlobalPos anchor = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);
        MapItemSavedData backing = exactMap((byte) 4);
        assertTrue(bindings.bind(source, unit, anchor, backing));
        JsonObject old = NexusMapBindingSavedData.CODEC.encodeStart(JsonOps.INSTANCE, bindings)
                .getOrThrow().getAsJsonObject();
        old.addProperty("data_version", 2);
        old.getAsJsonArray("maps").get(0).getAsJsonObject().remove("effective_scale");
        NexusMapBindingSavedData migrated = NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        assertEquals(-1, migrated.get(source).orElseThrow().effectiveScale());
        assertEquals(4, migrated.resolve(source, backing).orElseThrow().extent(backing).scale());
        assertTrue(migrated.bind(source, unit, anchor, backing));
        assertTrue(migrated.derive(source, backing, new MapId(401), backing, 5));
        assertEquals(5, migrated.get(new MapId(401)).orElseThrow().effectiveScale());
        assertFalse(migrated.validates(new MapId(401), UUID.randomUUID(), backing));
        assertFalse(migrated.bind(new MapId(401), unit, anchor, backing));
    }

    @Test
    void malformedLegacyLineageCannotPassPreflightThenFailDuringDerivation() {
        var malformed = new NexusMapBindingSavedData.Entry(600, UUID.randomUUID(),
                GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), 0, 0, List.of(599));
        assertFalse(malformed.matchesMapData(exactMap((byte) 0)));
        assertThrows(IllegalArgumentException.class, () -> malformed.extent(exactMap((byte) 0)));
    }

    @Test
    void extendedLedgerSurvivesDiskSaveAndNonDestructiveCanonicalMigration(@TempDir Path directory) {
        MapId source = new MapId(500);
        MapId extended = new MapId(501);
        UUID unit = UUID.randomUUID();
        MapItemSavedData backing = exactMap((byte) 4);
        NexusMapBindingSavedData original = new NexusMapBindingSavedData();
        assertTrue(original.bind(source, unit, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), backing));
        JsonObject old = NexusMapBindingSavedData.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow().getAsJsonObject();
        old.addProperty("data_version", 2);
        old.getAsJsonArray("maps").get(0).getAsJsonObject().remove("effective_scale");
        NexusMapBindingSavedData legacy = NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        try (var storage = mapStorage(directory)) {
            storage.set(NexusMapBindingSavedData.LEGACY_COMPATIBILITY_TYPE, legacy);
            NexusMapBindingSavedData canonical = NexusMapBindingSavedData.loadCanonical(storage);
            assertTrue(canonical.derive(source, backing, extended, backing, 5));
            assertTrue(legacy.get(extended).isEmpty());
            storage.saveAndJoin();
        }
        try (var storage = mapStorage(directory)) {
            NexusMapBindingSavedData restored = NexusMapBindingSavedData.loadCanonical(storage);
            var entry = restored.resolve(extended, backing).orElseThrow();
            assertEquals(5, entry.extent(backing).scale());
            assertEquals(0, entry.extent(backing).centerX());
            assertEquals(0, entry.extent(backing).centerZ());
            assertEquals(unit, entry.unitId());
            assertEquals(List.of(source.id()), entry.detailAncestors());
            assertTrue(restored.validates(extended, unit, backing));
            assertFalse(restored.validates(extended, UUID.randomUUID(), backing));
            assertTrue(storage.get(NexusMapBindingSavedData.LEGACY_COMPATIBILITY_TYPE).get(extended).isEmpty());
        }
    }

    private static net.minecraft.world.level.storage.SavedDataStorage mapStorage(Path directory) {
        return new net.minecraft.world.level.storage.SavedDataStorage(directory,
                net.minecraft.util.datafix.DataFixers.getDataFixer(),
                net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.empty()));
    }

    @Test
    void payloadPolicySeparatesManagementCompassAndMapProjection() {
        MapItemSavedData mapData = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD);
        java.util.Arrays.fill(mapData.colors, (byte) 4);
        BlockPos center = new BlockPos(mapData.centerX, 64, mapData.centerZ);
        UUID sourceId = UUID.fromString("00000000-0000-0000-0000-000000000211");
        NexusSpaceUnitRecord source = unit(sourceId, Level.OVERWORLD, center, SpaceUnitStatus.ACTIVE);
        NexusSpaceUnitRecord inside = unit(UUID.randomUUID(), Level.OVERWORLD, center.offset(63, 0, 0), SpaceUnitStatus.ACTIVE);
        NexusSpaceUnitRecord deathInside = new NexusSpaceUnitRecord(
                UUID.randomUUID(), SpaceUnitType.DEATH, Level.OVERWORLD, center.offset(0, 0, 10), sourceId,
                "Death Echo", SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE, Set.of(), Set.of(),
                SpaceStructureSnapshot.EMPTY, 1L, 1L);
        NexusSpaceUnitRecord edge = unit(UUID.randomUUID(), Level.OVERWORLD, center.offset(64, 0, 0), SpaceUnitStatus.ACTIVE);
        NexusSpaceUnitRecord crossDimension = unit(UUID.randomUUID(), Level.NETHER, center, SpaceUnitStatus.ACTIVE);
        NexusSpaceUnitRecord disabled = unit(UUID.randomUUID(), Level.OVERWORLD, center, SpaceUnitStatus.DISABLED);
        List<NexusSpaceUnitRecord> authorized = List.of(inside, deathInside, edge, crossDimension, disabled, source);

        assertEquals(List.of(source), NexusInterfacePayloadPolicy.selectAuthorizedUnits(
                TeleportInterfaceType.BOOK, sourceId, authorized, null));
        assertEquals(List.of(inside, deathInside, edge, crossDimension, source),
                NexusInterfacePayloadPolicy.selectAuthorizedUnits(
                        TeleportInterfaceType.COMPASS, sourceId, authorized, null));
        assertEquals(List.of(inside, deathInside, source), NexusInterfacePayloadPolicy.selectAuthorizedUnits(
                TeleportInterfaceType.FILLED_MAP, sourceId, authorized, mapData));
        assertTrue(NexusInterfacePayloadPolicy.selectAuthorizedUnits(
                TeleportInterfaceType.FILLED_MAP, sourceId, authorized, null).isEmpty());
    }

    /** Plain JUnit does not apply the constructor invoker mixin; reflect the same exact-center constructor. */
    private static MapItemSavedData exactMap(byte scale) {
        try {
            var constructor = MapItemSavedData.class.getDeclaredConstructor(
                    int.class,
                    int.class,
                    byte.class,
                    boolean.class,
                    boolean.class,
                    boolean.class,
                    net.minecraft.resources.ResourceKey.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(0, 0, scale, false, false, false, Level.OVERWORLD);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to create exact-centered Nexus map fixture", failure);
        }
    }

    private static NexusSpaceUnitRecord unit(
            UUID id, net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos, SpaceUnitStatus status) {
        return new NexusSpaceUnitRecord(
                id, SpaceUnitType.LODESTONE, dimension, pos, id, "Unit", SpaceUnitVisibility.PRIVATE, status,
                Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, 1L, 1L);
    }
}
