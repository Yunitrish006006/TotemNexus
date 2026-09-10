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
        NexusMapDetailPayload payload = new NexusMapDetailPayload(90, List.of(87, 88, 89));
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
                        .range(0, NexusMapBindingSavedData.MAX_DETAIL_ANCESTORS + 1)
                        .boxed().toList()));
    }

    @Test
    void requestRequiresNonNegativeMapIdentity() {
        assertEquals(4, new RequestNexusMapDetailPayload(4).mapId());
        assertThrows(IllegalArgumentException.class, () -> new RequestNexusMapDetailPayload(-1));
    }
}
