package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Stable {@code deadrecall:request_space_unit_map} serverbound wire contract. */
public record RequestSpaceUnitMapPayload(String sourceType, UUID sourceUnitId) implements CustomPacketPayload {
    public static final Type<RequestSpaceUnitMapPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "request_space_unit_map"));
    public static final StreamCodec<FriendlyByteBuf, RequestSpaceUnitMapPayload> CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeUtf(payload.sourceType(), 32); buf.writeUUID(payload.sourceUnitId()); },
            buf -> new RequestSpaceUnitMapPayload(buf.readUtf(32), buf.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
