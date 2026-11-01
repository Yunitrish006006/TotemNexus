package dev.totem.nexus.space;

import net.minecraft.server.level.ServerPlayer;

/** Shared server-only endpoint policy; map identity is re-resolved from the recorded hand. */
final class NexusInterfaceAccess {
    private NexusInterfaceAccess() { }

    static boolean allows(ServerPlayer player, TeleportInterfaceContext context, NexusSpaceUnitRecord unit) {
        if (player == null || context == null || unit == null || !context.isStillHeldBy(player)
                || unit.status() != SpaceUnitStatus.ACTIVE) return false;
        var storage = player.level().getServer().overworld().getDataStorage();
        var friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        if (!unit.canView(player.getUUID(), friends.areFriends(player.getUUID(), unit.owner()))) return false;
        NexusSpaceDiscoverySavedData discovery = NexusSpaceDiscoverySavedData.loadCanonical(storage);
        if (!context.interfaceType().hasMapVisualization()) {
            return discovery.hasDiscovered(player.getUUID(), unit.id());
        }
        var nativeData = net.minecraft.world.item.MapItem.getSavedData(context.mapId(), player.level());
        var records = storage.get(NexusMapRecordSavedData.TYPE);
        var record = nativeData == null || records == null
                ? null : records.resolve(context.mapId(), nativeData).orElse(null);
        return record != null && !NexusMapNodeProjection.select(
                player.getUUID(), record, nativeData, java.util.List.of(unit),
                (viewer, owner) -> friends.areFriends(viewer, owner),
                id -> discovery.hasDiscovered(player.getUUID(), id),
                candidate -> NexusMapRecordedCoverage.covers(
                        player, context.mapId(), candidate.dimension(), candidate.pos())).isEmpty();
    }
}
