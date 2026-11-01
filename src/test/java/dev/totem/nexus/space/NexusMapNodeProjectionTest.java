package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusMapNodeProjectionTest {
    private static final UUID VIEWER = new UUID(0, 1);
    private static final UUID FRIEND = new UUID(0, 2);
    private static final UUID STRANGER = new UUID(0, 3);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void recomputesCurrentHolderVisibilityDiscoveryCoverageDimensionAndExtent() {
        var nativeData = map(0, 0, (byte) 1);
        var record = NexusMapRecordSavedData.Entry.baseline(new MapId(301), nativeData);
        var ownedDiscovered = unit(1, VIEWER, Level.OVERWORLD, pos(nativeData, 0),
                SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE);
        var publicPainted = unit(2, STRANGER, Level.OVERWORLD, pos(nativeData, 8),
                SpaceUnitVisibility.PUBLIC, SpaceUnitStatus.ACTIVE);
        var friendDiscovered = unit(3, FRIEND, Level.OVERWORLD, pos(nativeData, 16),
                SpaceUnitVisibility.FRIENDS, SpaceUnitStatus.ACTIVE);
        var privateDiscovered = unit(4, STRANGER, Level.OVERWORLD, pos(nativeData, 24),
                SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE);
        var undiscovered = unit(5, STRANGER, Level.OVERWORLD, pos(nativeData, 32),
                SpaceUnitVisibility.PUBLIC, SpaceUnitStatus.ACTIVE);
        var disabledPainted = unit(6, STRANGER, Level.OVERWORLD, pos(nativeData, 40),
                SpaceUnitVisibility.PUBLIC, SpaceUnitStatus.DISABLED);
        var crossDimension = unit(7, STRANGER, Level.NETHER, pos(nativeData, 0),
                SpaceUnitVisibility.PUBLIC, SpaceUnitStatus.ACTIVE);
        var outsideExtent = unit(8, STRANGER, Level.OVERWORLD,
                new BlockPos(nativeData.centerX + 256, 64, nativeData.centerZ),
                SpaceUnitVisibility.PUBLIC, SpaceUnitStatus.ACTIVE);
        var candidates = List.of(outsideExtent, crossDimension, disabledPainted, undiscovered,
                privateDiscovered, friendDiscovered, publicPainted, ownedDiscovered);
        Set<UUID> discovered = Set.of(ownedDiscovered.id(), friendDiscovered.id(),
                privateDiscovered.id(), crossDimension.id(), outsideExtent.id());
        Set<UUID> painted = Set.of(publicPainted.id(), disabledPainted.id());

        var selected = NexusMapNodeProjection.select(
                VIEWER, record, nativeData, candidates,
                (viewer, owner) -> owner.equals(FRIEND),
                discovered::contains,
                unit -> painted.contains(unit.id()));

        assertEquals(List.of(ownedDiscovered, publicPainted, friendDiscovered), selected);
        assertEquals(1, record.logicalScale());
        assertTrue(record.lineage().isEmpty());
    }

    @Test
    void changingHolderRecomputesInsteadOfReusingPreviousPermissions() {
        var nativeData = map(0, 0, (byte) 0);
        var record = NexusMapRecordSavedData.Entry.baseline(new MapId(302), nativeData);
        var privateNode = unit(20, VIEWER, Level.OVERWORLD, pos(nativeData, 0),
                SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE);

        assertEquals(List.of(privateNode), NexusMapNodeProjection.select(
                VIEWER, record, nativeData, List.of(privateNode),
                (viewer, owner) -> false, id -> true, unit -> false));
        assertTrue(NexusMapNodeProjection.select(
                STRANGER, record, nativeData, List.of(privateNode),
                (viewer, owner) -> false, id -> true, unit -> false).isEmpty());
    }

    @Test
    void logicalExtentAboveVanillaScaleUsesRecordWithoutInventingPaintedPixels() {
        var nativeData = map(0, 0, (byte) 4);
        var record = new NexusMapRecordSavedData.Entry(
                303, Level.OVERWORLD, nativeData.centerX, nativeData.centerZ,
                4, false, 6, List.of(300, 301), Optional.of("a".repeat(64)), Optional.empty(), 2L);
        var beyondNativeButLogical = unit(30, STRANGER, Level.OVERWORLD,
                new BlockPos(nativeData.centerX + 3000, 64, nativeData.centerZ),
                SpaceUnitVisibility.PUBLIC, SpaceUnitStatus.ACTIVE);

        assertEquals(List.of(beyondNativeButLogical), NexusMapNodeProjection.select(
                VIEWER, record, nativeData, List.of(beyondNativeButLogical),
                (viewer, owner) -> false, id -> true, unit -> false));
        assertTrue(NexusMapNodeProjection.select(
                VIEWER, record, nativeData, List.of(beyondNativeButLogical),
                (viewer, owner) -> false, id -> false, unit -> false).isEmpty());
    }

    @Test
    void contradictoryNativeGeometryFailsClosed() {
        var nativeData = map(0, 0, (byte) 1);
        var record = NexusMapRecordSavedData.Entry.baseline(new MapId(304), nativeData);
        var node = unit(40, VIEWER, Level.OVERWORLD, pos(nativeData, 0),
                SpaceUnitVisibility.PUBLIC, SpaceUnitStatus.ACTIVE);

        assertTrue(NexusMapNodeProjection.select(
                VIEWER, record, map(512, 0, (byte) 1), List.of(node),
                (viewer, owner) -> true, id -> true, unit -> true).isEmpty());
    }

    private static MapItemSavedData map(int x, int z, byte scale) {
        return MapItemSavedData.createFresh(x, z, scale, false, false, Level.OVERWORLD);
    }

    private static BlockPos pos(MapItemSavedData map, int offsetX) {
        return new BlockPos(map.centerX + offsetX, 64, map.centerZ);
    }

    private static NexusSpaceUnitRecord unit(
            int id,
            UUID owner,
            net.minecraft.resources.ResourceKey<Level> dimension,
            BlockPos pos,
            SpaceUnitVisibility visibility,
            SpaceUnitStatus status) {
        return new NexusSpaceUnitRecord(
                new UUID(0, id), SpaceUnitType.LODESTONE, dimension, pos, owner, "Unit " + id,
                visibility, status, Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, 1L, 1L);
    }
}
