package dev.totem.nexus.network;

import java.util.UUID;
import dev.totem.nexus.space.NexusMapExtent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Map palette data only, never captured rendered pixels or authoritative coverage. */
public record MapOverviewPayload(UUID requestId,int mapId,int frameId,int centerX,int centerZ,int scale,String dimension,boolean locked,byte[] colors) implements CustomPacketPayload {
    public static final Type<MapOverviewPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("totem","nexus/map_overview_v1"));
    public MapOverviewPayload {
        new RequestMapOverviewPayload(requestId,mapId,frameId);
        new NexusMapExtent(centerX,centerZ,scale);
        if(dimension==null||dimension.length()>128||Identifier.tryParse(dimension)==null)throw new IllegalArgumentException("Invalid overview dimension");
        if(colors==null || colors.length!=16384) throw new IllegalArgumentException("Invalid overview palette length");
        colors=colors.clone();
    }
    @Override public byte[] colors(){return colors.clone();}
    public boolean matches(RequestMapOverviewPayload r){return r!=null&&requestId.equals(r.requestId())&&mapId==r.mapId()&&frameId==r.frameId();}
    public static final StreamCodec<FriendlyByteBuf,MapOverviewPayload> CODEC=StreamCodec.of(
            (b,p)->{b.writeUUID(p.requestId);b.writeInt(p.mapId);b.writeInt(p.frameId);b.writeInt(p.centerX);b.writeInt(p.centerZ);b.writeByte(p.scale);b.writeUtf(p.dimension,128);b.writeBoolean(p.locked);b.writeBytes(p.colors);},
            b->{UUID id=b.readUUID();int map=b.readInt(),frame=b.readInt(),x=b.readInt(),z=b.readInt(),s=b.readUnsignedByte();String dimension=b.readUtf(128);boolean locked=b.readBoolean();byte[] c=new byte[16384];b.readBytes(c);return new MapOverviewPayload(id,map,frame,x,z,s,dimension,locked,c);});
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
