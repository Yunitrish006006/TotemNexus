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
        if (NexusSpaceDiscoverySavedData.loadCanonical(storage).hasDiscovered(player.getUUID(), unit.id())) return true;
        return context.interfaceType().hasMapVisualization()
                && NexusMapRecordedCoverage.covers(player, context.mapId(), unit.dimension(), unit.pos());
    }
}
