package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Client requests repair of one server-advertised worn lodestone position. */
public record RepairSpaceUnitPayload(
        String sourceType,
        UUID sourceUnitId,
        UUID targetUnitId,
        int x,
        int y,
        int z) implements CustomPacketPayload {
    public static final Type<RepairSpaceUnitPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "repair_space_unit"));
    public static final StreamCodec<FriendlyByteBuf, RepairSpaceUnitPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.sourceType(), 32);
                buf.writeUUID(payload.sourceUnitId());
                buf.writeUUID(payload.targetUnitId());
                buf.writeInt(payload.x());
                buf.writeInt(payload.y());
                buf.writeInt(payload.z());
            },
            buf -> new RepairSpaceUnitPayload(
                    buf.readUtf(32), buf.readUUID(), buf.readUUID(), buf.readInt(), buf.readInt(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
