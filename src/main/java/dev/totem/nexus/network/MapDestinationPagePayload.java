package dev.totem.nexus.network;

import java.util.Objects;
import java.util.UUID;
import dev.totem.nexus.space.TeleportInterfaceType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One bounded page, echoed request identity and exclusive continuation cursor. */
public record MapDestinationPagePayload(UUID requestId, UUID after, UUID nextAfter,
        SpaceUnitMapPayload map) implements CustomPacketPayload {
    public MapDestinationPagePayload {
        Objects.requireNonNull(requestId); Objects.requireNonNull(map);
        if (map.interfaceType() != TeleportInterfaceType.FILLED_MAP)
            throw new IllegalArgumentException("Destination pages require a filled map");
        if (nextAfter != null && (map.entries().isEmpty()
                || map.entries().stream().noneMatch(entry -> entry.id().equals(nextAfter))
                || (after != null && nextAfter.compareTo(after) <= 0)))
            throw new IllegalArgumentException("Invalid page continuation");
    }
    public boolean matches(RequestMapDestinationPagePayload request) {
        return request != null && requestId.equals(request.requestId()) && Objects.equals(after, request.after())
                && map.mapId() == request.mapId() && map.sourceType().equals(request.sourceType())
                && map.sourceUnitId().equals(request.sourceId());
    }
    public static final Type<MapDestinationPagePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("totem", "nexus/map_destination_page_v1"));
    public static final StreamCodec<FriendlyByteBuf, MapDestinationPagePayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUUID(p.requestId()); writeCursor(buf, p.after()); writeCursor(buf, p.nextAfter());
                SpaceUnitMapPayload.CODEC.encode(buf, p.map()); },
            buf -> new MapDestinationPagePayload(buf.readUUID(), readCursor(buf), readCursor(buf), SpaceUnitMapPayload.CODEC.decode(buf)));
    private static void writeCursor(FriendlyByteBuf buf, UUID cursor) { buf.writeBoolean(cursor != null); if (cursor != null) buf.writeUUID(cursor); }
    private static UUID readCursor(FriendlyByteBuf buf) { return buf.readBoolean() ? buf.readUUID() : null; }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
