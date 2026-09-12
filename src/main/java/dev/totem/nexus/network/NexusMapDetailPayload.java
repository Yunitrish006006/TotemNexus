package dev.totem.nexus.network;

import dev.totem.nexus.space.NexusMapBindingSavedData;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Bounded identities of server-validated finer vanilla maps; map pixels remain vanilla packets. */
public record NexusMapDetailPayload(int mapId, List<Integer> ancestorMapIds, List<Layer> geometries) implements CustomPacketPayload {
    public static final int MAX_DETAIL_MAPS = 20;
    public NexusMapDetailPayload(int mapId, List<Integer> ids) { this(mapId,ids,List.of()); }
    public record Layer(int id, int centerX, int centerZ, int scale, String dimension, boolean locked) {
        public Layer {
            if(id<0 || Math.abs((long)centerX)>30000128L || Math.abs((long)centerZ)>30000128L
                    || scale<0 || scale>4 || dimension==null || dimension.length()>128
                    || Identifier.tryParse(dimension)==null) throw new IllegalArgumentException("Invalid map geometry");
        }
    }
    public static final Type<NexusMapDetailPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("totem", "nexus/map_detail_v2"));

    public NexusMapDetailPayload {
        if (mapId < 0 || ancestorMapIds == null
                || ancestorMapIds.size() > MAX_DETAIL_MAPS) {
            throw new IllegalArgumentException("Invalid Nexus map detail payload");
        }
        java.util.LinkedHashSet<Integer> unique = new java.util.LinkedHashSet<>();
        for (Integer ancestor : ancestorMapIds) {
            if (ancestor == null || ancestor < 0 || ancestor == mapId || !unique.add(ancestor)) {
                throw new IllegalArgumentException("Invalid Nexus map detail ancestor identity");
            }
        }
        ancestorMapIds = List.copyOf(unique);
        if(geometries==null || geometries.size()>MAX_DETAIL_MAPS+1) throw new IllegalArgumentException("Invalid geometry count");
        var geometryIds = new java.util.HashSet<Integer>();
        for(var layer:geometries) if(!geometryIds.add(layer.id()) || (layer.id()!=mapId && !unique.contains(layer.id())))
            throw new IllegalArgumentException("Unlisted map geometry");
        geometries=List.copyOf(geometries);
    }

    public static final StreamCodec<FriendlyByteBuf, NexusMapDetailPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.mapId());
                buf.writeInt(payload.ancestorMapIds().size());
                for (int ancestor : payload.ancestorMapIds()) buf.writeInt(ancestor);
                buf.writeInt(payload.geometries().size());
                for(var l:payload.geometries()) { buf.writeInt(l.id());buf.writeInt(l.centerX());buf.writeInt(l.centerZ());buf.writeByte(l.scale());buf.writeUtf(l.dimension(),128);buf.writeBoolean(l.locked()); }
            },
            buf -> {
                int mapId = buf.readInt();
                int size = buf.readInt();
                if (size < 0 || size > MAX_DETAIL_MAPS) {
                    throw new DecoderException("Nexus map detail ancestor count out of range: " + size);
                }
                List<Integer> ancestors = new ArrayList<>(size);
                for (int index = 0; index < size; index++) ancestors.add(buf.readInt());
                int geometryCount=buf.readInt();
                if(geometryCount<0 || geometryCount>MAX_DETAIL_MAPS+1) throw new DecoderException("Invalid geometry count");
                List<Layer> layers=new ArrayList<>(geometryCount);
                for(int i=0;i<geometryCount;i++) layers.add(new Layer(buf.readInt(),buf.readInt(),buf.readInt(),buf.readUnsignedByte(),buf.readUtf(128),buf.readBoolean()));
                return new NexusMapDetailPayload(mapId, ancestors,layers);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
