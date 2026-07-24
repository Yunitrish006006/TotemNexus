package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Stable {@code deadrecall:update_space_unit_access} serverbound wire contract. */
public record UpdateSpaceUnitAccessPayload(String sourceType, UUID sourceUnitId, UUID targetUnitId,
                                           String role, String playerName, boolean enabled)
        implements CustomPacketPayload {
    public static final Type<UpdateSpaceUnitAccessPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "update_space_unit_access"));
    public static final StreamCodec<FriendlyByteBuf, UpdateSpaceUnitAccessPayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUtf(value.sourceType(), 32); buf.writeUUID(value.sourceUnitId()); buf.writeUUID(value.targetUnitId()); buf.writeUtf(value.role(), 32); buf.writeUtf(value.playerName(), 64); buf.writeBoolean(value.enabled()); },
            buf -> new UpdateSpaceUnitAccessPayload(buf.readUtf(32), buf.readUUID(), buf.readUUID(), buf.readUtf(32), buf.readUtf(64), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
