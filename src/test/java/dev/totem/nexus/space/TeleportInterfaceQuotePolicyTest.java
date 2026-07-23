package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportInterfaceQuotePolicyTest {
    @Test
    void recoveryCompassImprovesOnlyOwnedDeathTargets() {
        TeleportInterfaceQuotePolicy.Quote quote = TeleportInterfaceQuotePolicy.specialize(
                TeleportInterfaceType.RECOVERY_COMPASS, SpaceUnitType.DEATH, true, false, 4, 50, 15, 20);
        assertTrue(quote.bonusActive());
        assertEquals(7, quote.maxHorizontalDeviation());
    }
    @Test
    void bookAndMapPreserveMinimumAndBounds() {
        TeleportInterfaceQuotePolicy.Quote book = TeleportInterfaceQuotePolicy.specialize(
                TeleportInterfaceType.BOOK, SpaceUnitType.LODESTONE, false, false, 1, 1, 2, 5);
        assertEquals(30, book.prepareTicks());
        TeleportInterfaceQuotePolicy.Quote map = TeleportInterfaceQuotePolicy.specialize(
                TeleportInterfaceType.FILLED_MAP, SpaceUnitType.LODESTONE, false, true, 1, 10, 1, 0);
        assertEquals(1, map.foodCost());
        assertEquals(0, map.maxHorizontalDeviation());
    }
}
