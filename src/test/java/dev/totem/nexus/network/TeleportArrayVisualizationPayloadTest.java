package dev.totem.nexus.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TeleportArrayVisualizationPayloadTest {
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000701");

    @Test
    void roundTripsBoundedRelativeMembership() {
        TeleportArrayVisualizationPayload payload = new TeleportArrayVisualizationPayload(
                SOURCE,
                "minecraft:overworld",
                10,
                64,
                -8,
                600,
                List.of(
                        new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, true),
                        new TeleportArrayVisualizationPayload.RelativeBlock(2, 0, 0, false)
                )
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            TeleportArrayVisualizationPayload.CODEC.encode(buffer, payload);
            assertEquals(payload, TeleportArrayVisualizationPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsOriginOffsetsBoundsAndDuplicateCoordinates() {
        assertThrows(IllegalArgumentException.class, () ->
                new TeleportArrayVisualizationPayload.RelativeBlock(0, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () ->
                new TeleportArrayVisualizationPayload.RelativeBlock(6, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new TeleportArrayVisualizationPayload(
                SOURCE,
                "minecraft:overworld",
                0,
                64,
                0,
                600,
                List.of(
                        new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, false),
                        new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, true)
                )
        ));
    }

    @Test
    void codecRejectsAnOversizedCountBeforeAllocatingEntries() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUUID(SOURCE);
            buffer.writeUtf("minecraft:overworld", TeleportArrayVisualizationPayload.MAX_DIMENSION_LENGTH);
            buffer.writeInt(0);
            buffer.writeInt(64);
            buffer.writeInt(0);
            buffer.writeVarInt(600);
            buffer.writeVarInt(TeleportArrayVisualizationPayload.MAX_BLOCKS + 1);

            assertThrows(DecoderException.class, () -> TeleportArrayVisualizationPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void requestRejectsInvalidSourceIdentity() {
        assertThrows(IllegalArgumentException.class, () ->
                new RequestTeleportArrayVisualizationPayload("", SOURCE, true));
        assertThrows(NullPointerException.class, () ->
                new RequestTeleportArrayVisualizationPayload("lodestone", null, true));
    }
}
