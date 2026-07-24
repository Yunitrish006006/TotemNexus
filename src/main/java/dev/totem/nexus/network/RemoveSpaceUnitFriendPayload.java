package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Stable {@code deadrecall:remove_space_unit_friend} serverbound wire contract. */
public record RemoveSpaceUnitFriendPayload(UUID friendId) implements CustomPacketPayload {
    public static final Type<RemoveSpaceUnitFriendPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "remove_space_unit_friend"));
    public static final StreamCodec<FriendlyByteBuf, RemoveSpaceUnitFriendPayload> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeUUID(value.friendId()), buf -> new RemoveSpaceUnitFriendPayload(buf.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
