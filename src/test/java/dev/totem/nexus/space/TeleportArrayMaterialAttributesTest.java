package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportArrayMaterialAttributesTest {
    @Test
    void aggregationKeepsSignedValuesAndCombinesAffinity() {
        TeleportArrayMaterialAttributes first = new TeleportArrayMaterialAttributes(
                2, 1, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, Map.of("minecraft:the_nether", 2));
        TeleportArrayMaterialAttributes second = new TeleportArrayMaterialAttributes(
                1, -2, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -3, Map.of("minecraft:the_nether", -1));

        TeleportArrayMaterialAttributes total = first.plus(second);

        assertEquals(3, total.structureCapacity());
        assertEquals(-1, total.scanExpansionRadius());
        assertEquals(0, total.localScanExpansionRadius());
        assertEquals(-2, total.crossDimensionCatalystUnits());
        assertEquals(1, total.affinityFor("minecraft:the_nether"));
    }
}
