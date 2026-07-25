package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record ManageDeathNodeAdminPayload(UUID nodeId, String action, UUID confirmationToken) implements CustomPacketPayload {
    private static final int MAX_ACTION_LENGTH = 32;
    public static final Type<ManageDeathNodeAdminPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "manage_death_node_admin"));

    public static final StreamCodec<FriendlyByteBuf, ManageDeathNodeAdminPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.nodeId());
                buf.writeUtf(payload.action(), MAX_ACTION_LENGTH);
                buf.writeBoolean(payload.confirmationToken() != null);
                if (payload.confirmationToken() != null) {
                    buf.writeUUID(payload.confirmationToken());
                }
            },
            buf -> new ManageDeathNodeAdminPayload(
                    buf.readUUID(),
                    buf.readUtf(MAX_ACTION_LENGTH),
                    buf.readBoolean() ? buf.readUUID() : null
            )
    );

    public ManageDeathNodeAdminPayload(UUID nodeId, String action) {
        this(nodeId, action, null);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
