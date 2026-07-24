package dev.totem.nexus.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpaceUnitMapPayloadTest {
    @Test
    void acceptsConsistentMinimumPaidQuote() {
        assertDoesNotThrow(() -> new SpaceUnitMapPayload.Entry(UUID.randomUUID(), "lodestone", "A", "private", false, "minecraft:overworld", 0,64,0,0,0,0,1,1,0,0,1,1,1,0,1,0,0,0,0,0,0,0,0,0,0,false,"message.deadrecall.space_unit.interface_bonus.compass",false,false,true,0,0,true,""));
    }
    @Test
    void rejectsAnAmethystDiscountThatMakesPaidTravelFree() {
        assertThrows(IllegalArgumentException.class, () -> new SpaceUnitMapPayload.Entry(UUID.randomUUID(), "lodestone", "A", "private", false, "minecraft:overworld", 0,64,0,0,0,0,1,1,0,0,1,1,0,0,1,0,0,1,0,0,0,0,0,0,0,false,"message.deadrecall.space_unit.interface_bonus.compass",false,false,true,0,0,false,""));
    }
}
