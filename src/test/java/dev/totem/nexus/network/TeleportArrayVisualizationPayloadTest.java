package dev.totem.nexus.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportArrayVisualizationPayloadTest {
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000701");

    @Test
    void roundTripsBoundedRelativeCountedAndBuildableMembership() {
        TeleportArrayVisualizationPayload payload = snapshot(List.of(
                new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, true, false),
                new TeleportArrayVisualizationPayload.RelativeBlock(2, 0, 0, false, false),
                new TeleportArrayVisualizationPayload.RelativeBlock(-1, 0, 0, false, true)
        ));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            TeleportArrayVisualizationPayload.CODEC.encode(buffer, payload);
            assertEquals(payload, TeleportArrayVisualizationPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void statusAndDualModeRequestRoundTripWithoutPositions() {
        RequestTeleportArrayVisualizationPayload request =
                new RequestTeleportArrayVisualizationPayload("lodestone", SOURCE, true, true);
        TeleportArrayVisualizationStatusPayload status =
                new TeleportArrayVisualizationStatusPayload(SOURCE, true, true, true);
        FriendlyByteBuf requestBuffer = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf statusBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RequestTeleportArrayVisualizationPayload.CODEC.encode(requestBuffer, request);
            TeleportArrayVisualizationStatusPayload.CODEC.encode(statusBuffer, status);
            assertEquals(request, RequestTeleportArrayVisualizationPayload.CODEC.decode(requestBuffer));
            assertEquals(status, TeleportArrayVisualizationStatusPayload.CODEC.decode(statusBuffer));
            assertTrue(request.enabled());
        } finally {
            requestBuffer.release();
            statusBuffer.release();
        }
    }

    @Test
    void rejectsOriginBoundsDuplicateCoordinatesAndContradictoryClasses() {
        assertThrows(IllegalArgumentException.class, () ->
                new TeleportArrayVisualizationPayload.RelativeBlock(0, 0, 0, false, false));
        assertThrows(IllegalArgumentException.class, () ->
                new TeleportArrayVisualizationPayload.RelativeBlock(6, 0, 0, false, false));
        assertThrows(IllegalArgumentException.class, () ->
                new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, true, true));
        assertThrows(IllegalArgumentException.class, () -> snapshot(List.of(
                new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, false, false),
                new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, false, true)
        )));
        assertThrows(IllegalArgumentException.class, () -> new TeleportArrayVisualizationPayload(
                SOURCE, "minecraft:overworld", 0, 64, 0, true, false,
                List.of(new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, false, true))));
        assertThrows(IllegalArgumentException.class, () -> new TeleportArrayVisualizationPayload(
                SOURCE, "minecraft:overworld", 0, 64, 0, false, false, List.of()));
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
            buffer.writeBoolean(true);
            buffer.writeBoolean(true);
            buffer.writeVarInt(TeleportArrayVisualizationPayload.MAX_BLOCKS + 1);

            assertThrows(DecoderException.class, () -> TeleportArrayVisualizationPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void requestAndStatusRejectInvalidState() {
        assertThrows(IllegalArgumentException.class, () ->
                new RequestTeleportArrayVisualizationPayload("", SOURCE, true, false));
        assertThrows(NullPointerException.class, () ->
                new RequestTeleportArrayVisualizationPayload("lodestone", null, true, false));
        assertThrows(IllegalArgumentException.class, () ->
                new TeleportArrayVisualizationStatusPayload(SOURCE, true, false, false));
        assertThrows(IllegalArgumentException.class, () ->
                new TeleportArrayVisualizationStatusPayload(SOURCE, false, true, false));
    }

    private static TeleportArrayVisualizationPayload snapshot(
            List<TeleportArrayVisualizationPayload.RelativeBlock> blocks) {
        return new TeleportArrayVisualizationPayload(
                SOURCE, "minecraft:overworld", 10, 64, -8, true, true, blocks);
    }
}
