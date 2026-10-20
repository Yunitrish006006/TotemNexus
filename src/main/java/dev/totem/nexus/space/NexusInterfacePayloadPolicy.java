package dev.totem.nexus.space;

import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Privacy boundary for the Space Unit records projected through one held interface. */
final class NexusInterfacePayloadPolicy {
    private NexusInterfacePayloadPolicy() { }

    static List<NexusSpaceUnitRecord> selectAuthorizedUnits(
            TeleportInterfaceType interfaceType,
            UUID boundUnitId,
            Collection<NexusSpaceUnitRecord> serverAuthorizedUnits,
            MapItemSavedData mapData) {
        return selectWithMapAccess(interfaceType, boundUnitId, serverAuthorizedUnits,
                unit -> FilledMapCoverage.isDrawn(mapData, unit.dimension(), unit.pos()));
    }

    static List<NexusSpaceUnitRecord> selectWithMapAccess(
            TeleportInterfaceType interfaceType, UUID boundUnitId,
            Collection<NexusSpaceUnitRecord> serverAuthorizedUnits,
            java.util.function.Predicate<NexusSpaceUnitRecord> mapAccess) {
        if (interfaceType == null || boundUnitId == null || serverAuthorizedUnits == null) return List.of();
        return serverAuthorizedUnits.stream()
                .filter(unit -> allowsWithMapAccess(interfaceType, boundUnitId, unit, mapAccess))
                .limit(interfaceType.canSelectTeleportDestination() ? Long.MAX_VALUE : 1)
                .toList();
    }

    /** Single-record equivalent used by bounded page selection without materializing all matches. */
    static boolean allowsWithMapAccess(TeleportInterfaceType interfaceType, UUID boundUnitId,
            NexusSpaceUnitRecord unit, java.util.function.Predicate<NexusSpaceUnitRecord> mapAccess) {
        if (unit.status() != SpaceUnitStatus.ACTIVE) return false;
        if (!interfaceType.canSelectTeleportDestination()) {
            return unit.isLodestoneAnchor() && unit.id().equals(boundUnitId);
        }
        return !interfaceType.hasMapVisualization() || mapAccess.test(unit);
    }
}
