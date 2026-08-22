package dev.totem.nexus.network;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterialCatalogPayloadTest {
    @Test
    void acceptsSignedMaterialAttributesAndAffinity() {
        MaterialCatalogPayload.Entry entry = new MaterialCatalogPayload.Entry(
                "minecraft:gold_block",
                "deadrecall:gold_block",
                "gold",
                true,
                Map.of(
                        "food_efficiency", 2,
                        "phase_speed", 2,
                        "wear_resistance", -1,
                        "arrival_safety", -1
                ),
                Map.of("minecraft:the_nether", 1)
        );

        MaterialCatalogPayload payload = new MaterialCatalogPayload(12L, List.of(entry));

        assertEquals(12L, payload.revision());
        assertEquals(2, payload.entries().getFirst().attribute("food_efficiency"));
        assertEquals(-1, payload.entries().getFirst().attribute("wear_resistance"));
        assertEquals(0, payload.entries().getFirst().attribute("stability"));
        assertEquals(1, payload.entries().getFirst().dimensionAffinity().get("minecraft:the_nether"));
    }

    @Test
    void rejectsInvalidBlockIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialCatalogPayload.Entry(
                "not a block id",
                "deadrecall:test",
                "test",
                true,
                Map.of(),
                Map.of()
        ));
    }

    @Test
    void rejectsUnsafeAttributeMagnitude() {
        assertThrows(IllegalArgumentException.class, () -> new MaterialCatalogPayload.Entry(
                "minecraft:stone",
                "deadrecall:test",
                "test",
                true,
                Map.of("stability", 65),
                Map.of()
        ));
    }

    @Test
    void rejectsNegativeRevision() {
        assertThrows(IllegalArgumentException.class,
                () -> new MaterialCatalogPayload(-1L, List.of()));
    }
}
