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
        if (interfaceType == null || boundUnitId == null || serverAuthorizedUnits == null) return List.of();
        if (!interfaceType.hasMapVisualization()) {
            return serverAuthorizedUnits.stream()
                    .filter(unit -> unit.status() == SpaceUnitStatus.ACTIVE)
                    .filter(NexusSpaceUnitRecord::isLodestoneAnchor)
                    .filter(unit -> unit.id().equals(boundUnitId))
                    .limit(1)
                    .toList();
        }
        if (mapData == null) return List.of();
        return serverAuthorizedUnits.stream()
                .filter(unit -> unit.status() == SpaceUnitStatus.ACTIVE)
                .filter(unit -> FilledMapCoverage.covers(
                        mapData.dimension,
                        mapData.centerX,
                        mapData.centerZ,
                        mapData.scale,
                        unit.dimension(),
                        unit.pos()))
                .toList();
    }
}
