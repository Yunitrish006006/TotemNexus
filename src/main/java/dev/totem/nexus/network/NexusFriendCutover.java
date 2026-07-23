package dev.totem.nexus.network;

import dev.totem.nexus.space.NexusFriendPayloadAuthority;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit opt-in receiver cutover for the complete friends authority slice. */
public final class NexusFriendCutover {
    private static final AtomicBoolean ACTIVATED = new AtomicBoolean();
    private NexusFriendCutover() { }
    public static void activate() {
        if (!ACTIVATED.compareAndSet(false, true)) return;
        PayloadTypeRegistry.serverboundPlay().register(RequestSpaceUnitFriendsPayload.TYPE, RequestSpaceUnitFriendsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RemoveSpaceUnitFriendPayload.TYPE, RemoveSpaceUnitFriendPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SpaceUnitFriendsPayload.TYPE, SpaceUnitFriendsPayload.CODEC);
        NexusFriendPayloadAuthority authority = new NexusFriendPayloadAuthority();
        ServerPlayNetworking.registerGlobalReceiver(RequestSpaceUnitFriendsPayload.TYPE,
                (payload, context) -> context.server().execute(() -> authority.requestFriends(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(RemoveSpaceUnitFriendPayload.TYPE,
                (payload, context) -> context.server().execute(() -> authority.removeFriend(context.player(), payload.friendId())));
    }
}
