package dev.totem.nexus.space;

import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Pure, current-holder projection of live Space Units through one MapId extent. */
final class NexusMapNodeProjection {
    private NexusMapNodeProjection() { }

    static List<NexusSpaceUnitRecord> select(
            UUID viewer,
            NexusMapRecordSavedData.Entry record,
            MapItemSavedData nativeData,
            Iterable<NexusSpaceUnitRecord> candidates,
            BiPredicate<UUID, UUID> areFriends,
            Predicate<UUID> isDiscovered,
            Predicate<NexusSpaceUnitRecord> isPainted) {
        if (viewer == null || record == null || nativeData == null || candidates == null
                || areFriends == null || isDiscovered == null || isPainted == null
                || !record.matchesNative(nativeData)) {
            return List.of();
        }
        NexusMapExtent.Bounds bounds = record.extent(nativeData).samplingBounds();
        var selected = new ArrayList<NexusSpaceUnitRecord>();
        for (NexusSpaceUnitRecord unit : candidates) {
            if (unit == null || unit.status() != SpaceUnitStatus.ACTIVE
                    || !record.dimension().equals(unit.dimension())
                    || !bounds.contains(unit.pos().getX(), unit.pos().getZ())
                    || !unit.canView(viewer, areFriends.test(viewer, unit.owner()))
                    || (!isDiscovered.test(unit.id()) && !isPainted.test(unit))) {
                continue;
            }
            selected.add(unit);
        }
        selected.sort(Comparator.comparing(NexusSpaceUnitRecord::id));
        return List.copyOf(selected);
    }
}
