package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusSafeLandingPolicyTest {
    @Test
    void cursorVisitsEveryColumnOnceInNearestFirstOrder() {
        NexusSafeLanding.ColumnCursor cursor =
                new NexusSafeLanding.ColumnCursor(NexusSafeLanding.MAX_HORIZONTAL_RADIUS);
        Set<Long> visited = new HashSet<>();
        int previousDistance = -1;

        while (cursor.hasNext()) {
            NexusSafeLanding.ColumnOffset offset = cursor.next();
            int distance = Math.max(Math.abs(offset.x()), Math.abs(offset.z()));
            assertTrue(distance >= previousDistance, "Safe-landing columns must remain nearest-first");
            assertTrue(visited.add(pack(offset.x(), offset.z())), "Safe-landing cursor repeated a column");
            previousDistance = distance;
        }

        int side = NexusSafeLanding.MAX_HORIZONTAL_RADIUS * 2 + 1;
        assertEquals(side * side, visited.size());
    }

    @Test
    void unreachableLegacyRadiusIsClampedToTheTeleportableMaximum() {
        NexusSafeLanding.ColumnCursor cursor = new NexusSafeLanding.ColumnCursor(96);
        int columns = 0;
        int furthest = 0;
        while (cursor.hasNext()) {
            NexusSafeLanding.ColumnOffset offset = cursor.next();
            columns++;
            furthest = Math.max(furthest, Math.max(Math.abs(offset.x()), Math.abs(offset.z())));
        }

        assertEquals(NexusSafeLanding.MAX_HORIZONTAL_RADIUS, furthest);
        assertEquals(9_409, columns);
    }

    @Test
    void perTickPolicyKeepsTheWorstCaseScanIncremental() {
        assertEquals(128, NexusSafeLanding.DEFAULT_COLUMN_BUDGET);
        assertEquals(200, NexusSafeLanding.MAX_SEARCH_TICKS);
        assertTrue(9_409 / NexusSafeLanding.DEFAULT_COLUMN_BUDGET > 1);
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
