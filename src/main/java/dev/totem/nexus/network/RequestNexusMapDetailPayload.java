package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests validated historical vanilla-map detail for the currently held Nexus map. */
public record RequestNexusMapDetailPayload(int mapId, int centerX, int centerZ, int radius) implements CustomPacketPayload {
    public RequestNexusMapDetailPayload(int mapId) { this(mapId,0,0,0); }
    public static final Type<RequestNexusMapDetailPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("totem", "nexus/request_map_detail_v2"));

    public RequestNexusMapDetailPayload {
        if (mapId < 0 || Math.abs((long)centerX)>30000128L || Math.abs((long)centerZ)>30000128L || radius<0 || radius>2048) throw new IllegalArgumentException("Nexus map detail request requires a non-negative MapId");
    }

    public static final StreamCodec<FriendlyByteBuf, RequestNexusMapDetailPayload> CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeInt(payload.mapId());buf.writeInt(payload.centerX());buf.writeInt(payload.centerZ());buf.writeInt(payload.radius()); },
            buf -> new RequestNexusMapDetailPayload(buf.readInt(),buf.readInt(),buf.readInt(),buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
