package dev.totem.nexus.space;

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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void payloadPolicySeparatesManagementCompassAndMapProjection() {
        MapItemSavedData mapData = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD);
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

    private static NexusSpaceUnitRecord unit(
            UUID id, net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos, SpaceUnitStatus status) {
        return new NexusSpaceUnitRecord(
                id, SpaceUnitType.LODESTONE, dimension, pos, id, "Unit", SpaceUnitVisibility.PRIVATE, status,
                Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, 1L, 1L);
    }
}
