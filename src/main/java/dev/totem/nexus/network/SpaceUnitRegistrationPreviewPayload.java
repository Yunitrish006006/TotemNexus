package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Stable {@code deadrecall:space_unit_registration_preview} clientbound wire contract. */
public record SpaceUnitRegistrationPreviewPayload(String dimension, int x, int y, int z, int tier,
                                                  int resonancePercent, int completenessPercent, int wearPercent,
                                                  int confirmSeconds) implements CustomPacketPayload {
    public static final Type<SpaceUnitRegistrationPreviewPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "space_unit_registration_preview"));
    public static final StreamCodec<FriendlyByteBuf, SpaceUnitRegistrationPreviewPayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUtf(value.dimension(), 128); buf.writeInt(value.x()); buf.writeInt(value.y()); buf.writeInt(value.z()); buf.writeInt(value.tier()); buf.writeInt(value.resonancePercent()); buf.writeInt(value.completenessPercent()); buf.writeInt(value.wearPercent()); buf.writeInt(value.confirmSeconds()); },
            buf -> new SpaceUnitRegistrationPreviewPayload(buf.readUtf(128), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
