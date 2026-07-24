package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Stable {@code deadrecall:update_space_unit_visibility} serverbound wire contract. */
public record UpdateSpaceUnitVisibilityPayload(String sourceType, UUID sourceUnitId, UUID targetUnitId, String visibility)
        implements CustomPacketPayload {
    public static final Type<UpdateSpaceUnitVisibilityPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "update_space_unit_visibility"));
    public static final StreamCodec<FriendlyByteBuf, UpdateSpaceUnitVisibilityPayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUtf(value.sourceType(), 32); buf.writeUUID(value.sourceUnitId()); buf.writeUUID(value.targetUnitId()); buf.writeUtf(value.visibility(), 32); },
            buf -> new UpdateSpaceUnitVisibilityPayload(buf.readUtf(32), buf.readUUID(), buf.readUUID(), buf.readUtf(32)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
