package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestDeathNodeAdminPayload(
        String ownerQuery,
        String dimensionId,
        String statusId,
        long createdAfterGameTime,
        long createdBeforeGameTime,
        int page) implements CustomPacketPayload {
    public static final Type<RequestDeathNodeAdminPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "request_death_node_admin"));

    public static final StreamCodec<FriendlyByteBuf, RequestDeathNodeAdminPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.ownerQuery(), 64);
                buf.writeUtf(payload.dimensionId(), 128);
                buf.writeUtf(payload.statusId(), 32);
                buf.writeLong(payload.createdAfterGameTime());
                buf.writeLong(payload.createdBeforeGameTime());
                buf.writeInt(payload.page());
            },
            buf -> new RequestDeathNodeAdminPayload(
                    buf.readUtf(64),
                    buf.readUtf(128),
                    buf.readUtf(32),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readInt()
            )
    );

    public RequestDeathNodeAdminPayload {
        ownerQuery = ownerQuery == null ? "" : ownerQuery.trim();
        dimensionId = dimensionId == null ? "" : dimensionId.trim();
        statusId = statusId == null ? "" : statusId.trim();
        createdAfterGameTime = Math.max(0L, createdAfterGameTime);
        createdBeforeGameTime = Math.max(0L, createdBeforeGameTime);
        page = Math.max(0, page);
    }

    public RequestDeathNodeAdminPayload() {
        this("", "", "", 0L, 0L, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
