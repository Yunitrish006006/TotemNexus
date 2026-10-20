package dev.totem.nexus.space;

import dev.totem.nexus.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NexusMapPagePayloadTest {
    @Test void requestAndResponseRoundTripIncludingExclusiveCursor() {
        UUID source = UUID.randomUUID(), cursor = UUID.randomUUID();
        var request = new RequestMapDestinationPagePayload(UUID.randomUUID(), "player", source, 42, cursor);
        var response = new MapDestinationPagePayload(request.requestId(), cursor, null, map(source, 42));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RequestMapDestinationPagePayload.CODEC.encode(buf, request);
            assertEquals(request, RequestMapDestinationPagePayload.CODEC.decode(buf));
            MapDestinationPagePayload.CODEC.encode(buf, response);
            var decoded = MapDestinationPagePayload.CODEC.decode(buf);
            assertEquals(response, decoded);
            assertTrue(decoded.matches(request));
        } finally { buf.release(); }
    }

    @Test void oldRequestDifferentMapSourceOrCursorCannotMatchCurrentRequest() {
        var request = new RequestMapDestinationPagePayload(UUID.randomUUID(), "player", UUID.randomUUID(), 42, null);
        var response = new MapDestinationPagePayload(request.requestId(), null, null, map(request.sourceId(), 42));
        assertFalse(response.matches(new RequestMapDestinationPagePayload(UUID.randomUUID(), "player", request.sourceId(), 42, null)));
        assertFalse(response.matches(new RequestMapDestinationPagePayload(request.requestId(), "player", request.sourceId(), 43, null)));
        assertFalse(response.matches(new RequestMapDestinationPagePayload(request.requestId(), "player", UUID.randomUUID(), 42, null)));
        assertFalse(response.matches(new RequestMapDestinationPagePayload(request.requestId(), "player", request.sourceId(), 42, UUID.randomUUID())));
        assertFalse(response.matches(null));
    }

    @Test void malformedIdentityAndFabricatedContinuationAreRejected() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new RequestMapDestinationPagePayload(id, "player", id, -1, null));
        assertThrows(IllegalArgumentException.class, () -> new RequestMapDestinationPagePayload(id, "invalid", id, 42, null));
        assertThrows(IllegalArgumentException.class, () -> new MapDestinationPagePayload(id, null, id, map(id, 42)));
    }

    private static SpaceUnitMapPayload map(UUID source, int mapId) {
        return new SpaceUnitMapPayload(source, "player", "Player", "minecraft:overworld", 0, 64, 0,
                TeleportInterfaceType.FILLED_MAP, mapId, List.of(), SpaceUnitMapPayload.MaterialSummary.EMPTY);
    }
}
