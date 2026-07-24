package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/** Stable {@code deadrecall:manage_death_node_admin} serverbound wire contract. */
public record ManageDeathNodeAdminPayload(UUID nodeId, String action) implements CustomPacketPayload {
    public static final Type<ManageDeathNodeAdminPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("deadrecall", "manage_death_node_admin"));
    public static final StreamCodec<FriendlyByteBuf, ManageDeathNodeAdminPayload> CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeUUID(value.nodeId()); buf.writeUtf(value.action(), 16); },
            buf -> new ManageDeathNodeAdminPayload(buf.readUUID(), buf.readUtf(16)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
