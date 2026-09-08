package dev.totem.nexus.space;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.MapItem;

/** Shared server-only endpoint policy; map identity is re-resolved from the recorded hand. */
final class NexusInterfaceAccess {
    private NexusInterfaceAccess() { }

    static boolean allows(ServerPlayer player, TeleportInterfaceContext context, NexusSpaceUnitRecord unit) {
        if (player == null || context == null || unit == null || !context.isStillHeldBy(player)
                || unit.status() != SpaceUnitStatus.ACTIVE) return false;
        var storage = player.level().getServer().overworld().getDataStorage();
        var friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        if (!unit.canView(player.getUUID(), friends.areFriends(player.getUUID(), unit.owner()))) return false;
        if (context.interfaceType().hasMapVisualization()) {
            return FilledMapCoverage.isDrawn(MapItem.getSavedData(context.mapId(), player.level()), unit.dimension(), unit.pos());
        }
        return NexusSpaceDiscoverySavedData.loadCanonical(storage).hasDiscovered(player.getUUID(), unit.id());
    }
}
