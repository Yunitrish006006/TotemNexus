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
    @Test void extendedPanReachesNewTerrainWithoutStretchingNativeCoordinates() {
        assertEquals(-1,MapPointerTransform.clampPan(-384,256,256));
        assertEquals(-384,MapPointerTransform.clampExtendedPan(-384,256,256,64,4,7));
        for(int logical=5;logical<=20;logical++) for(int pan:new int[]{Integer.MIN_VALUE,0,Integer.MAX_VALUE}) {
            int result=MapPointerTransform.clampExtendedPan(pan,256,256,29999999,4,logical);
            double center=29999999-result*8.0;
            assertTrue(center+1016<=30000000 && center-1016>=-30000000);
        }
        assertThrows(IllegalArgumentException.class,()->MapPointerTransform.clampExtendedPan(0,256,256,0,4,21));
    }

}
