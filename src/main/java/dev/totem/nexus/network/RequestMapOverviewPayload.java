package dev.totem.nexus.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestMapOverviewPayload(UUID requestId,int mapId,int frameId) implements CustomPacketPayload {
    public static final Type<RequestMapOverviewPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("totem","nexus/request_map_overview_v1"));
    public RequestMapOverviewPayload {
        if(requestId==null || mapId<0 || frameId< -1) throw new IllegalArgumentException("Invalid overview request");
    }
    public static final StreamCodec<FriendlyByteBuf,RequestMapOverviewPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeUUID(p.requestId);b.writeInt(p.mapId);b.writeInt(p.frameId);},
            b->new RequestMapOverviewPayload(b.readUUID(),b.readInt(),b.readInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
