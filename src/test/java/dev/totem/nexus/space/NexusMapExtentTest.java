package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NexusMapExtentTest {
    @Test
    void expansionBeyondVanillaKeepsAnExactNegativeOffGridCenter() {
        NexusMapExtent old = new NexusMapExtent(-24, 40, 4);
        NexusMapExtent expanded = old.expanded().orElseThrow();
        assertEquals(5, expanded.scale());
        assertEquals(-24, expanded.centerX());
        assertEquals(40, expanded.centerZ());
        assertEquals(4096, expanded.bounds().maxXExclusive() - expanded.bounds().minXInclusive());
        assertEquals(2048, old.bounds().maxXExclusive() - old.bounds().minXInclusive());
        assertEquals(4, expanded.vanillaScale());
        assertTrue(expanded.bounds().contains(-2072, -2008));
        assertFalse(expanded.bounds().contains(2024, 2088));
    }

    @Test
    void centeredAndWorldEdgeMapsStopOnlyWhenTheyContainTheWholeWorld() {
        for (int x : new int[]{NexusMapExtent.MIN_COORDINATE, -24, 0,
                NexusMapExtent.MAX_COORDINATE_EXCLUSIVE - 1}) {
            for (int z : new int[]{NexusMapExtent.MIN_COORDINATE, 0,
                    NexusMapExtent.MAX_COORDINATE_EXCLUSIVE - 1}) {
                NexusMapExtent extent = new NexusMapExtent(x, z, 0);
                while (extent.expanded().isPresent()) {
                    assertFalse(extent.coversSupportedWorld());
                    extent = extent.expanded().orElseThrow();
                }
                assertTrue(extent.coversSupportedWorld());
                assertTrue(extent.scale() > 4);
                assertTrue(extent.scale() <= NexusMapExtent.MAX_SCALE);
                assertEquals(x, extent.centerX());
                assertEquals(z, extent.centerZ());
                assertEquals(new NexusMapExtent.Bounds(NexusMapExtent.MIN_COORDINATE,
                        NexusMapExtent.MAX_COORDINATE_EXCLUSIVE, NexusMapExtent.MIN_COORDINATE,
                        NexusMapExtent.MAX_COORDINATE_EXCLUSIVE), extent.samplingBounds());
                assertFalse(new NexusMapExtent(x, z, extent.scale() - 1).coversSupportedWorld());
            }
        }
    }

    @Test
    void samplingClipsBothWorldEdgesWithoutChangingTheLogicalRectangle() {
        NexusMapExtent extent = new NexusMapExtent(NexusMapExtent.MIN_COORDINATE,
                NexusMapExtent.MAX_COORDINATE_EXCLUSIVE - 1, 4);
        assertTrue(extent.bounds().minXInclusive() < NexusMapExtent.MIN_COORDINATE);
        assertTrue(extent.bounds().maxZExclusive() > NexusMapExtent.MAX_COORDINATE_EXCLUSIVE);
        assertEquals(NexusMapExtent.MIN_COORDINATE, extent.samplingBounds().minXInclusive());
        assertEquals(NexusMapExtent.MAX_COORDINATE_EXCLUSIVE, extent.samplingBounds().maxZExclusive());
        assertTrue(extent.samplingBounds().contains(extent.centerX(), extent.centerZ()));
        assertFalse(extent.samplingBounds().contains(NexusMapExtent.MIN_COORDINATE - 1L, extent.centerZ()));
        assertFalse(extent.samplingBounds().contains(extent.centerX(), NexusMapExtent.MAX_COORDINATE_EXCLUSIVE));
    }

    @Test
    void malformedExtentsAreRejectedBeforeAnyShiftOrCoordinateArithmetic() {
        for (int scale : new int[]{-1, NexusMapExtent.MAX_SCALE + 1, 31, 63, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new NexusMapExtent(0, 0, scale));
        }
        for (int coordinate : new int[]{Integer.MIN_VALUE, NexusMapExtent.MIN_COORDINATE - 1,
                NexusMapExtent.MAX_COORDINATE_EXCLUSIVE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new NexusMapExtent(coordinate, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new NexusMapExtent(0, coordinate, 0));
        }
    }
}
