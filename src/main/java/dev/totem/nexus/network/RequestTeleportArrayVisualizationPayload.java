package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/** Requests a fresh source-local teleport-array preview or disables the local view. */
public record RequestTeleportArrayVisualizationPayload(
        String sourceType,
        UUID sourceUnitId,
        boolean enable) implements CustomPacketPayload {
    public static final int MAX_SOURCE_TYPE_LENGTH = 32;
    public static final Type<RequestTeleportArrayVisualizationPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "request_teleport_array_visualization"));
    public static final StreamCodec<FriendlyByteBuf, RequestTeleportArrayVisualizationPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.sourceType(), MAX_SOURCE_TYPE_LENGTH);
                buf.writeUUID(payload.sourceUnitId());
                buf.writeBoolean(payload.enable());
            },
            buf -> new RequestTeleportArrayVisualizationPayload(
                    buf.readUtf(MAX_SOURCE_TYPE_LENGTH),
                    buf.readUUID(),
                    buf.readBoolean())
    );

    public RequestTeleportArrayVisualizationPayload {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceUnitId, "sourceUnitId");
        if (sourceType.isBlank() || sourceType.length() > MAX_SOURCE_TYPE_LENGTH) {
            throw new IllegalArgumentException("Teleport-array source type is invalid");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
