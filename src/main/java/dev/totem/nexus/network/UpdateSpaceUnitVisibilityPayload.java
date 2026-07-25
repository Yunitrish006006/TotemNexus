package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record UpdateSpaceUnitVisibilityPayload(
        String sourceType,
        UUID sourceUnitId,
        UUID targetUnitId,
        String visibility)
        implements CustomPacketPayload {
    public static final Type<UpdateSpaceUnitVisibilityPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "update_space_unit_visibility"));

    public static final StreamCodec<FriendlyByteBuf, UpdateSpaceUnitVisibilityPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.sourceType(), 32);
                        buf.writeUUID(payload.sourceUnitId());
                        buf.writeUUID(payload.targetUnitId());
                        buf.writeUtf(payload.visibility(), 32);
                    },
                    buf -> new UpdateSpaceUnitVisibilityPayload(
                            buf.readUtf(32),
                            buf.readUUID(),
                            buf.readUUID(),
                            buf.readUtf(32))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
