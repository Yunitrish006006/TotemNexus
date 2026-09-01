package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/** Requests bounded source-local visualization modes, or disables both modes. */
public record RequestTeleportArrayVisualizationPayload(
        String sourceType,
        UUID sourceUnitId,
        boolean showArray,
        boolean showBuildSites) implements CustomPacketPayload {
    public static final int MAX_SOURCE_TYPE_LENGTH = 32;
    public static final Type<RequestTeleportArrayVisualizationPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "request_teleport_array_visualization"));
    public static final StreamCodec<FriendlyByteBuf, RequestTeleportArrayVisualizationPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.sourceType(), MAX_SOURCE_TYPE_LENGTH);
                buf.writeUUID(payload.sourceUnitId());
                buf.writeBoolean(payload.showArray());
                buf.writeBoolean(payload.showBuildSites());
            },
            buf -> new RequestTeleportArrayVisualizationPayload(
                    buf.readUtf(MAX_SOURCE_TYPE_LENGTH),
                    buf.readUUID(),
                    buf.readBoolean(),
                    buf.readBoolean())
    );

    public RequestTeleportArrayVisualizationPayload {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceUnitId, "sourceUnitId");
        if (sourceType.isBlank() || sourceType.length() > MAX_SOURCE_TYPE_LENGTH) {
            throw new IllegalArgumentException("Teleport-array source type is invalid");
        }
    }

    public boolean enabled() {
        return showArray || showBuildSites;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
