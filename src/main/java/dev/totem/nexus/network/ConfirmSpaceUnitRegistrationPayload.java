package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Stable {@code deadrecall:confirm_space_unit_registration} serverbound wire contract. */
public record ConfirmSpaceUnitRegistrationPayload(String dimension, int x, int y, int z) implements CustomPacketPayload {
    public static final Type<ConfirmSpaceUnitRegistrationPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "confirm_space_unit_registration"));
    public static final StreamCodec<FriendlyByteBuf, ConfirmSpaceUnitRegistrationPayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUtf(value.dimension(), 128); buf.writeInt(value.x()); buf.writeInt(value.y()); buf.writeInt(value.z()); },
            buf -> new ConfirmSpaceUnitRegistrationPayload(buf.readUtf(128), buf.readInt(), buf.readInt(), buf.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
