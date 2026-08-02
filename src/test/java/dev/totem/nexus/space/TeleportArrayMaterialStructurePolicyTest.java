package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleportArrayMaterialStructurePolicyTest {
    @Test
    void capacityCompletenessAndTierUseTheDocumentedThresholdsAndBounds() {
        assertEquals(0, TeleportArrayMaterialStructurePolicy.effectiveCapacity(attributes(-100, 0, 0)));
        assertEquals(0.0D, TeleportArrayMaterialStructurePolicy.completeness(-1));
        assertEquals(1.0D, TeleportArrayMaterialStructurePolicy.completeness(100));
        assertEquals(0, TeleportArrayMaterialStructurePolicy.tier(7));
        assertEquals(1, TeleportArrayMaterialStructurePolicy.tier(8));
        assertEquals(2, TeleportArrayMaterialStructurePolicy.tier(24));
    }

    @Test
    void interferenceAndStabilityHonorSignedResistanceAndFinalClamps() {
        assertEquals(0, TeleportArrayMaterialStructurePolicy.interference(1, 100));
        assertEquals(100, TeleportArrayMaterialStructurePolicy.interference(100, -100));
        assertEquals(100, TeleportArrayMaterialStructurePolicy.stability(1.0D, 1.0D, 100, 0));
        assertEquals(0, TeleportArrayMaterialStructurePolicy.stability(0.0D, 0.0D, -100, 100));
        assertEquals(58, TeleportArrayMaterialStructurePolicy.stability(.5D, .5D, 20, 12));
    }

    private static TeleportArrayMaterialAttributes attributes(int structureCapacity, int stability,
                                                               int interferenceResistance) {
        return new TeleportArrayMaterialAttributes(structureCapacity, 0, stability, 0, 0,
                0, 0, 0, interferenceResistance, 0, 0, 0, 0, 0, Map.of());
    }
}
