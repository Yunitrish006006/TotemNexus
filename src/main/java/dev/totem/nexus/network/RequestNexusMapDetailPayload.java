package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests validated historical vanilla-map detail for the currently held Nexus map. */
public record RequestNexusMapDetailPayload(int mapId, int centerX, int centerZ, int radius, int scale) implements CustomPacketPayload {
    public RequestNexusMapDetailPayload(int mapId) { this(mapId,0,0,0,-1); }
    public RequestNexusMapDetailPayload(int mapId,int x,int z,int radius) { this(mapId,x,z,radius,-1); }
    public static final Type<RequestNexusMapDetailPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("totem", "nexus/request_map_detail_v3"));

    public RequestNexusMapDetailPayload {
        if (mapId < 0 || Math.abs((long)centerX)>30000128L || Math.abs((long)centerZ)>30000128L || radius<0 || radius>2048 || scale< -1 || scale>4) throw new IllegalArgumentException("Invalid Nexus map detail request");
    }

    public static final StreamCodec<FriendlyByteBuf, RequestNexusMapDetailPayload> CODEC = StreamCodec.of(
            (buf, payload) -> { buf.writeInt(payload.mapId());buf.writeInt(payload.centerX());buf.writeInt(payload.centerZ());buf.writeInt(payload.radius());buf.writeByte(payload.scale()); },
            buf -> new RequestNexusMapDetailPayload(buf.readInt(),buf.readInt(),buf.readInt(),buf.readInt(),buf.readByte())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
