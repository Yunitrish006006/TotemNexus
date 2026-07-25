package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record CalibrateSpaceUnitPayload(String sourceType, UUID sourceUnitId, UUID targetUnitId)
        implements CustomPacketPayload {
    public static final Type<CalibrateSpaceUnitPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "calibrate_space_unit"));

    public static final StreamCodec<FriendlyByteBuf, CalibrateSpaceUnitPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.sourceType(), 32);
                        buf.writeUUID(payload.sourceUnitId());
                        buf.writeUUID(payload.targetUnitId());
                    },
                    buf -> new CalibrateSpaceUnitPayload(buf.readUtf(32), buf.readUUID(), buf.readUUID())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
