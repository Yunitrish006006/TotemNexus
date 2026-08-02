package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportArrayMaterialProfileSelectionTest {
    @Test
    void exactBlockSelectorWinsOverHigherPriorityTagFallback() {
        assertTrue(TeleportArrayMaterialProfileSelection.compare(true, -8, false, 8) > 0);
        assertTrue(TeleportArrayMaterialProfileSelection.compare(false, 8, true, -8) < 0);
    }

    @Test
    void priorityOnlyBreaksTiesInsideTheSameSelectorClass() {
        assertTrue(TeleportArrayMaterialProfileSelection.compare(true, 2, true, 1) > 0);
        assertTrue(TeleportArrayMaterialProfileSelection.compare(false, -1, false, 1) < 0);
        assertEquals(0, TeleportArrayMaterialProfileSelection.compare(true, 0, true, 0));
    }

    @Test
    void equalSelectorClassAndPriorityIsAReloadError() {
        assertThrows(IllegalArgumentException.class,
                () -> TeleportArrayMaterialProfileSelection.requireUniqueWinner(0, "tie"));
        assertDoesNotThrow(() -> TeleportArrayMaterialProfileSelection.requireUniqueWinner(1, "winner"));
    }
}
