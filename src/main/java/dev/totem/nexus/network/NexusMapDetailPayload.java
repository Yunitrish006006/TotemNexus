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
public record NexusMapDetailPayload(int mapId, List<Integer> ancestorMapIds) implements CustomPacketPayload {
    public static final Type<NexusMapDetailPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("totem", "nexus/map_detail"));

    public NexusMapDetailPayload {
        if (mapId < 0 || ancestorMapIds == null
                || ancestorMapIds.size() > NexusMapBindingSavedData.MAX_DETAIL_ANCESTORS) {
            throw new IllegalArgumentException("Invalid Nexus map detail payload");
        }
        java.util.LinkedHashSet<Integer> unique = new java.util.LinkedHashSet<>();
        for (Integer ancestor : ancestorMapIds) {
            if (ancestor == null || ancestor < 0 || ancestor == mapId || !unique.add(ancestor)) {
                throw new IllegalArgumentException("Invalid Nexus map detail ancestor identity");
            }
        }
        ancestorMapIds = List.copyOf(unique);
    }

    public static final StreamCodec<FriendlyByteBuf, NexusMapDetailPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.mapId());
                buf.writeInt(payload.ancestorMapIds().size());
                for (int ancestor : payload.ancestorMapIds()) buf.writeInt(ancestor);
            },
            buf -> {
                int mapId = buf.readInt();
                int size = buf.readInt();
                if (size < 0 || size > NexusMapBindingSavedData.MAX_DETAIL_ANCESTORS) {
                    throw new DecoderException("Nexus map detail ancestor count out of range: " + size);
                }
                List<Integer> ancestors = new ArrayList<>(size);
                for (int index = 0; index < size; index++) ancestors.add(buf.readInt());
                return new NexusMapDetailPayload(mapId, ancestors);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
