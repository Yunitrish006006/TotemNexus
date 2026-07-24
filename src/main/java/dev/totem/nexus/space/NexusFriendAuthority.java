package dev.totem.nexus.space;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Server-side friend mutations backed by the preserved {@code space_friends} schema. */
public final class NexusFriendAuthority {
    public boolean removeFriend(ServerPlayer player, UUID friendId) {
        if (player == null || friendId == null) return false;
        NexusFriendSavedData friends = player.level().getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusFriendSavedData.TYPE);
        return friends.removeRelationship(player.getUUID(), friendId);
    }
}
