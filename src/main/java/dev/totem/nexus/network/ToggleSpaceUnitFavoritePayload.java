package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record ToggleSpaceUnitFavoritePayload(
        String sourceType,
        UUID sourceUnitId,
        UUID targetUnitId,
        boolean favorite)
        implements CustomPacketPayload {
    public static final Type<ToggleSpaceUnitFavoritePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "toggle_space_unit_favorite"));

    public static final StreamCodec<FriendlyByteBuf, ToggleSpaceUnitFavoritePayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.sourceType(), 32);
                        buf.writeUUID(payload.sourceUnitId());
                        buf.writeUUID(payload.targetUnitId());
                        buf.writeBoolean(payload.favorite());
                    },
                    buf -> new ToggleSpaceUnitFavoritePayload(
                            buf.readUtf(32),
                            buf.readUUID(),
                            buf.readUUID(),
                            buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
