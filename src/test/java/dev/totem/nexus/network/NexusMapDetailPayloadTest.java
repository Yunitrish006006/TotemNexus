package dev.totem.nexus.network;

import dev.totem.nexus.space.NexusMapBindingSavedData;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NexusMapDetailPayloadTest {
    @Test
    void roundTripsBoundedAncestorIdentitiesWithoutPixels() {
        NexusMapDetailPayload payload = new NexusMapDetailPayload(90, List.of(87, 88, 89), List.of(
                new NexusMapDetailPayload.Layer(90,-2048,1024,3,"minecraft:overworld",true),
                new NexusMapDetailPayload.Layer(87,-2112,1088,0,"minecraft:overworld",true)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NexusMapDetailPayload.CODEC.encode(buffer, payload);
            assertEquals(payload, NexusMapDetailPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsSelfDuplicateNegativeAndOversizedAncestors() {
        assertThrows(IllegalArgumentException.class,
                () -> new NexusMapDetailPayload(90, List.of(90)));
        assertThrows(IllegalArgumentException.class,
                () -> new NexusMapDetailPayload(90, List.of(89, 89)));
        assertThrows(IllegalArgumentException.class,
                () -> new NexusMapDetailPayload(90, List.of(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> new NexusMapDetailPayload(90, java.util.stream.IntStream
                        .range(0, NexusMapDetailPayload.MAX_DETAIL_MAPS + 1)
                        .boxed().toList()));
    }

    @Test
    void requestRequiresNonNegativeMapIdentity() {
        assertEquals(4, new RequestNexusMapDetailPayload(4).mapId());
        assertThrows(IllegalArgumentException.class, () -> new RequestNexusMapDetailPayload(-1));
    }
}
