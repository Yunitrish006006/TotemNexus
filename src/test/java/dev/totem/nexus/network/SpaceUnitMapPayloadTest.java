package dev.totem.nexus.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import dev.totem.nexus.space.TeleportInterfaceType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void acceptsMaterialAdjustedStructureWearAboveLegacyDamageCap() {
        assertDoesNotThrow(() -> new SpaceUnitMapPayload.Entry(
                UUID.randomUUID(), "lodestone", "A", "private", false, "minecraft:overworld", 0, 64, 0,
                0, 0, 0, 1, 1, 0, 0, 1, 1,
                1, 0, 1, 0, 0, 0,
                0, 0, 0, 0,
                60, 80, 75,
                false, "message.deadrecall.space_unit.interface_bonus.compass",
                false, false, true, 0, 0, true, ""));
    }

    @Test
    void rejectsStructureWearAboveOneHundredPercent() {
        assertThrows(IllegalArgumentException.class, () -> new SpaceUnitMapPayload.Entry(
                UUID.randomUUID(), "lodestone", "A", "private", false, "minecraft:overworld", 0, 64, 0,
                0, 0, 0, 1, 1, 0, 0, 1, 1,
                1, 0, 1, 0, 0, 0,
                0, 0, 0, 0,
                60, 101, 75,
                false, "message.deadrecall.space_unit.interface_bonus.compass",
                false, false, true, 0, 0, true, ""));
    }

    @Test
    void roundTripsServerCalculatedMaterialDiagnostics() {
        SpaceUnitMapPayload.MaterialSummary material = new SpaceUnitMapPayload.MaterialSummary(
                5, 9, 5, 1, 3, 2, 0, 1, 2, 1, 0, 2, 1, 0, 4, -1,
                Map.of("iron", 3, "gold", 2), Map.of("minecraft:the_nether", 1),
                List.of(new SpaceUnitMapPayload.FamilyContribution("iron", 3,
                        Map.of("structure_capacity", 6, "stability", 3))),
                2, List.of(new SpaceUnitMapPayload.MaintenanceTarget(4, 64, -2, "iron")));
        SpaceUnitMapPayload.Entry entry = new SpaceUnitMapPayload.Entry(
                UUID.randomUUID(), "lodestone", "A", "private", false, "minecraft:overworld", 0, 64, 0,
                0, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                false, "message.deadrecall.space_unit.interface_bonus.compass", false, false, true, 0, 0, true, "")
                .withMaterial(material);
        SpaceUnitMapPayload payload = new SpaceUnitMapPayload(
                UUID.randomUUID(), "lodestone", "Source", "minecraft:overworld", 4, 64, 4,
                TeleportInterfaceType.COMPASS, List.of(entry), material);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        SpaceUnitMapPayload.CODEC.encode(buffer, payload);
        SpaceUnitMapPayload decoded = SpaceUnitMapPayload.CODEC.decode(buffer);

        assertEquals(material, decoded.sourceMaterial());
        assertEquals(material, decoded.entries().getFirst().material());
    }

    @Test
    void acceptsSignedCatalystShardChangeAndPreservesThePaidMinimum() {
        assertDoesNotThrow(() -> new SpaceUnitMapPayload.Entry(
                UUID.randomUUID(), "lodestone", "A", "private", false, "minecraft:overworld", 0, 64, 0,
                0, 0, 0, 1, 1, 0, 0, 1, 1,
                5, 0, 4, -3, -2, -1,
                0, 0, 0, 0, 0, 0, 0,
                false, "message.deadrecall.space_unit.interface_bonus.compass",
                false, false, true, 0, 0, true, ""));
    }
}
