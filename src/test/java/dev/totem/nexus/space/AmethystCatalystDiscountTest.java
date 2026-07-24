package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmethystCatalystDiscountTest {
    @Test
    void discountsNeverMakeAPaidTeleportFree() {
        AmethystCatalystDiscount.Quote quote = AmethystCatalystDiscount.quote(3, 100, 100);
        assertEquals(50, quote.availableDiscount());
        assertEquals(2, quote.appliedDiscount());
        assertEquals(1, quote.finalCost());
    }

    @Test
    void onlyLodestoneEndpointsContributeCatalysts() {
        assertEquals(2, AmethystCatalystDiscount.quoteForEndpoints(8, true, 8, false, 100).availableDiscount());
        assertEquals(0, AmethystCatalystDiscount.quoteForEndpoints(0, true, 8, true, 8).finalCost());
    }
}
