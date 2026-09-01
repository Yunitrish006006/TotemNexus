package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.UUID;

/** Small refresh acknowledgement or invalidation; it never contains world positions. */
public record TeleportArrayVisualizationStatusPayload(
        UUID sourceUnitId,
        boolean accepted,
        boolean showArray,
        boolean showBuildSites) implements CustomPacketPayload {
    public static final Type<TeleportArrayVisualizationStatusPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "teleport_array_visualization_status"));
    public static final StreamCodec<FriendlyByteBuf, TeleportArrayVisualizationStatusPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.sourceUnitId());
                buf.writeBoolean(payload.accepted());
                buf.writeBoolean(payload.showArray());
                buf.writeBoolean(payload.showBuildSites());
            },
            buf -> new TeleportArrayVisualizationStatusPayload(
                    buf.readUUID(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean())
    );

    public TeleportArrayVisualizationStatusPayload {
        Objects.requireNonNull(sourceUnitId, "sourceUnitId");
        if (accepted && !showArray && !showBuildSites) {
            throw new IllegalArgumentException("Accepted visualization status has no enabled class");
        }
        if (!accepted && (showArray || showBuildSites)) {
            throw new IllegalArgumentException("Rejected visualization status cannot enable a class");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
