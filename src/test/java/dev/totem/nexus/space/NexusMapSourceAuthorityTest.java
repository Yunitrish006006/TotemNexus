package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusMapSourceAuthorityTest {
    @Test void acceptsOnlyDistancesInsideTheEightBlockRadius() {
        assertTrue(NexusMapSourceAuthority.isWithinOpenRadius(64.0D));
        assertFalse(NexusMapSourceAuthority.isWithinOpenRadius(64.0001D));
        assertFalse(NexusMapSourceAuthority.isWithinOpenRadius(-1.0D));
    }
}
