package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Stable {@code deadrecall:rename_space_unit} serverbound wire contract. */
public record RenameSpaceUnitPayload(String sourceType, UUID sourceUnitId, UUID targetUnitId, String name)
        implements CustomPacketPayload {
    public static final Type<RenameSpaceUnitPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "rename_space_unit"));
    public static final StreamCodec<FriendlyByteBuf, RenameSpaceUnitPayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUtf(value.sourceType(), 32); buf.writeUUID(value.sourceUnitId()); buf.writeUUID(value.targetUnitId()); buf.writeUtf(value.name(), 64); },
            buf -> new RenameSpaceUnitPayload(buf.readUtf(32), buf.readUUID(), buf.readUUID(), buf.readUtf(64)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
