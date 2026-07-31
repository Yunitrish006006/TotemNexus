package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SpaceUnitRegistrationPreviewPayload(
        String dimension,
        int x,
        int y,
        int z,
        int tier,
        int resonancePercent,
        int completenessPercent,
        int wearPercent,
        int confirmSeconds)
        implements CustomPacketPayload {
    public static final Type<SpaceUnitRegistrationPreviewPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "space_unit_registration_preview"));

    public static final StreamCodec<FriendlyByteBuf, SpaceUnitRegistrationPreviewPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeUtf(payload.dimension(), 128);
                        buf.writeInt(payload.x());
                        buf.writeInt(payload.y());
                        buf.writeInt(payload.z());
                        buf.writeInt(payload.tier());
                        buf.writeInt(payload.resonancePercent());
                        buf.writeInt(payload.completenessPercent());
                        buf.writeInt(payload.wearPercent());
                        buf.writeInt(payload.confirmSeconds());
                    },
                    buf -> new SpaceUnitRegistrationPreviewPayload(
                            buf.readUtf(128),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
