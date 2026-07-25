package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record StartSpaceUnitTeleportPayload(String sourceType, UUID sourceUnitId, UUID targetUnitId)
        implements CustomPacketPayload {
    public static final Type<StartSpaceUnitTeleportPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "start_space_unit_teleport"));

    public static final StreamCodec<FriendlyByteBuf, StartSpaceUnitTeleportPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.sourceType(), 32);
                        buf.writeUUID(payload.sourceUnitId());
                        buf.writeUUID(payload.targetUnitId());
                    },
                    buf -> new StartSpaceUnitTeleportPayload(buf.readUtf(32), buf.readUUID(), buf.readUUID())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
