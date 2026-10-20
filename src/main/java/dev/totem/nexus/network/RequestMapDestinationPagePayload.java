package dev.totem.nexus.network;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Request correlation is not authorization; the server rechecks the held interface. */
public record RequestMapDestinationPagePayload(UUID requestId, String sourceType, UUID sourceId,
        int mapId, UUID after) implements CustomPacketPayload {
    public RequestMapDestinationPagePayload {
        Objects.requireNonNull(requestId); Objects.requireNonNull(sourceId);
        if (!("player".equals(sourceType) || "lodestone".equals(sourceType)) || mapId < 0)
            throw new IllegalArgumentException("Invalid map page source");
    }
    public static final Type<RequestMapDestinationPagePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("totem", "nexus/request_map_destination_page_v1"));
    public static final StreamCodec<FriendlyByteBuf, RequestMapDestinationPagePayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUUID(p.requestId()); buf.writeUtf(p.sourceType(), 32);
                buf.writeUUID(p.sourceId()); buf.writeInt(p.mapId()); buf.writeBoolean(p.after() != null);
                if (p.after() != null) buf.writeUUID(p.after()); },
            buf -> new RequestMapDestinationPagePayload(buf.readUUID(), buf.readUtf(32), buf.readUUID(),
                    buf.readInt(), buf.readBoolean() ? buf.readUUID() : null));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
