package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Stable {@code deadrecall:start_space_unit_teleport} serverbound wire contract. */
public record StartSpaceUnitTeleportPayload(String sourceType, UUID sourceUnitId, UUID targetUnitId)
        implements CustomPacketPayload {
    public static final Type<StartSpaceUnitTeleportPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "start_space_unit_teleport"));
    public static final StreamCodec<FriendlyByteBuf, StartSpaceUnitTeleportPayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUtf(value.sourceType(), 32); buf.writeUUID(value.sourceUnitId()); buf.writeUUID(value.targetUnitId()); },
            buf -> new StartSpaceUnitTeleportPayload(buf.readUtf(32), buf.readUUID(), buf.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
