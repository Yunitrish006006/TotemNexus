package dev.totem.nexus.space;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Map/favorite authority slice; receivers remain inactive until map UI cutover. */
public final class NexusMapAuthority {
    private final TeleportInterfaceSessionStore sessions;

    public NexusMapAuthority(TeleportInterfaceSessionStore sessions) { this.sessions = sessions; }

    public void openSession(TeleportInterfaceContext context) { sessions.put(context); }
    public void disconnect(UUID playerId) { sessions.remove(playerId); }
    public void tick(long gameTime) { sessions.expire(gameTime); }

    public boolean setFavorite(ServerPlayer player, String sourceType, UUID sourceId, UUID targetId, boolean favorite) {
        long time = player.level().getGameTime();
        if (sessions.require(player, sourceType, sourceId, time).isEmpty()) return false;
        var storage = player.level().getServer().overworld().getDataStorage();
        NexusSpaceUnitSavedData units = storage.computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        NexusSpaceDiscoverySavedData discovery = storage.computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        NexusFriendSavedData friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        NexusSpaceUnitRecord target = units.get(targetId).orElse(null);
        if (target == null || target.status() != SpaceUnitStatus.ACTIVE
                || !target.canView(player.getUUID(), friends.areFriends(player.getUUID(), target.owner()))
                || !discovery.hasDiscovered(player.getUUID(), targetId)) return false;
        return discovery.setFavorite(player.getUUID(), targetId, favorite);
    }
}
