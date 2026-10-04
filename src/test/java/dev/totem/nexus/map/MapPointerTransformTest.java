package dev.totem.nexus.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapPointerTransformTest {
    @Test void interiorAnchorsSurviveZoomInAndOutWithinHalfAScreenPixel() {
        for (int oldScale : new int[]{1, 2, 4, 8}) {
            for (int newScale : new int[]{1, 2, 4, 8}) {
                for (double anchor : new double[]{110, 131.25, 163.5}) {
                    double before = (anchor - 47) / oldScale;
                    int pan = MapPointerTransform.anchoredPan(anchor, 47, oldScale, -91, newScale);
                    double after = (anchor - (-91 + pan)) / newScale;
                    assertEquals(before, after, 0.5 / newScale);
                }
            }
        }
    }

    @Test void bothEdgesClampExactlyAndInteriorPanRemainsUnchanged() {
        assertEquals(-61, MapPointerTransform.clampPan(-100, 512, 391));
        assertEquals(61, MapPointerTransform.clampPan(100, 512, 391));
        assertEquals(32, MapPointerTransform.clampPan(32, 512, 391));
        assertEquals(0, MapPointerTransform.clampPan(100, 128, 391));
        assertEquals(0, MapPointerTransform.clampPan(-100, 128, 391));
    }

    @Test void previousGuiScaleThreeFixtureMustClampRatherThanPromiseStableAnchor() {
        int pan = MapPointerTransform.anchoredPan(213 + 64, 85, 2, -43, 4);
        assertEquals(-64, pan);
        assertEquals(-61, MapPointerTransform.clampPan(pan, 512, 391));
    }
}
