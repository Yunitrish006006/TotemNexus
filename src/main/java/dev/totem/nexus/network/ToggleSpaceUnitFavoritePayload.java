package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Stable {@code deadrecall:toggle_space_unit_favorite} serverbound wire contract. */
public record ToggleSpaceUnitFavoritePayload(String sourceType, UUID sourceUnitId, UUID targetUnitId, boolean favorite)
        implements CustomPacketPayload {
    public static final Type<ToggleSpaceUnitFavoritePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "toggle_space_unit_favorite"));
    public static final StreamCodec<FriendlyByteBuf, ToggleSpaceUnitFavoritePayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUtf(value.sourceType(), 32); buf.writeUUID(value.sourceUnitId()); buf.writeUUID(value.targetUnitId()); buf.writeBoolean(value.favorite()); },
            buf -> new ToggleSpaceUnitFavoritePayload(buf.readUtf(32), buf.readUUID(), buf.readUUID(), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
