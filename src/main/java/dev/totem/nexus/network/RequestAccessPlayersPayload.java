package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public record RequestAccessPlayersPayload(String sourceType, UUID sourceId, UUID targetId, String role,
                                         String query, int page, long requestId) implements CustomPacketPayload {
    public static final Type<RequestAccessPlayersPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("totem", "nexus/request_access_players"));
    public static final StreamCodec<FriendlyByteBuf, RequestAccessPlayersPayload> CODEC = StreamCodec.of((b,p) -> {
        b.writeUtf(p.sourceType,32); b.writeUUID(p.sourceId); b.writeUUID(p.targetId); b.writeUtf(p.role,32);
        b.writeUtf(p.query,64); b.writeVarInt(p.page); b.writeVarLong(p.requestId);
    }, b -> new RequestAccessPlayersPayload(b.readUtf(32),b.readUUID(),b.readUUID(),b.readUtf(32),b.readUtf(64),b.readVarInt(),b.readVarLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
