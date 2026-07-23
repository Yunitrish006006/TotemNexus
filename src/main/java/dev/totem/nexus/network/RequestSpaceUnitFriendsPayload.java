package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Stable {@code deadrecall:request_space_unit_friends} serverbound wire contract. */
public record RequestSpaceUnitFriendsPayload() implements CustomPacketPayload {
    public static final Type<RequestSpaceUnitFriendsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "request_space_unit_friends"));
    public static final StreamCodec<FriendlyByteBuf, RequestSpaceUnitFriendsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> { }, buf -> new RequestSpaceUnitFriendsPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
