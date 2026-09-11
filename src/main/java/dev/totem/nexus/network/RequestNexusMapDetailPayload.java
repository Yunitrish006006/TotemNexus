package dev.totem.nexus.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Requests validated historical vanilla-map detail for the currently held Nexus map. */
public record RequestNexusMapDetailPayload(int mapId) implements CustomPacketPayload {
    public static final Type<RequestNexusMapDetailPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("totem", "nexus/request_map_detail"));

    public RequestNexusMapDetailPayload {
        if (mapId < 0) throw new IllegalArgumentException("Nexus map detail request requires a non-negative MapId");
    }

    public static final StreamCodec<FriendlyByteBuf, RequestNexusMapDetailPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeInt(payload.mapId()),
            buf -> new RequestNexusMapDetailPayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
