package dev.totem.nexus.space;

import dev.totem.nexus.network.SpaceUnitFriendsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Complete server-side authority slice for the friends payload endpoints. */
public final class NexusFriendPayloadAuthority {
    private final NexusFriendAuthority friends;
    private final BiConsumer<ServerPlayer, SpaceUnitFriendsPayload> sender;

    public NexusFriendPayloadAuthority() {
        this(new NexusFriendAuthority(), ServerPlayNetworking::send);
    }

    NexusFriendPayloadAuthority(NexusFriendAuthority friends,
                                BiConsumer<ServerPlayer, SpaceUnitFriendsPayload> sender) {
        this.friends = Objects.requireNonNull(friends, "friends");
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    public void requestFriends(ServerPlayer player) {
        if (player == null) return;
        NexusFriendSavedData data = player.level().getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusFriendSavedData.TYPE);
        sender.accept(player, NexusFriendListAuthority.build(player.level().getServer(), player.getUUID(), data));
    }

    public boolean removeFriend(ServerPlayer player, UUID friendId) {
        if (player == null || friendId == null) return false;
        boolean removed = friends.removeFriend(player, friendId);
        requestFriends(player);
        return removed;
    }
}
