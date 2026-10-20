package dev.totem.nexus.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapDestinationProjectionTest {
    private MapDestinationProjection.Point point(int x, int z, boolean same, int panX, int panY) {
        return MapDestinationProjection.project(x,z,same,0,0,4,100+panX,50+panY,2,108,58,348,278);
    }
    @Test void extendedWorldPointReturnsToItsExactPositionAfterPan() {
        assertTrue(point(3072,0,true,0,0).edge());
        var moved=point(3072,0,true,-384,0);
        assertFalse(moved.edge()); assertEquals(228,moved.x()); assertEquals(178,moved.y());
    }
    @Test void distantTargetsStayInsideViewportAndPreserveTheirDirection() {
        var east=point(29999999,0,true,0,0); assertTrue(east.edge()); assertEquals(348,east.x());
        var west=point(-29999999,0,true,0,0); assertEquals(108,west.x());
        for(int x:new int[]{-29999999,0,29999999}) for(int z:new int[]{-29999999,0,29999999}) {
            var p=point(x,z,true,0,0); assertTrue(p.x()>=108&&p.x()<=348&&p.y()>=58&&p.y()<=278);
        }
    }
    @Test void otherDimensionIgnoresCoordinatesAndPanAndHasNoDirection() {
        var a=point(-29999999,29999999,false,-5000,5000);
        assertEquals(a,point(29999999,-29999999,false,5000,-5000));
        assertTrue(a.otherDimension()); assertFalse(a.edge()); assertEquals(348,a.x());assertEquals(58,a.y());
    }
    @Test void nativeWorldDifferencesDoNotOverflowAtSupportedExtremes() {
        var p=MapDestinationProjection.project(29999999,-29999999,true,-29999999,29999999,0,
                -536870912,536870912,16,8,8,600,300);
        assertTrue(p.edge()); assertTrue(p.x()>=8&&p.x()<=600&&p.y()>=8&&p.y()<=300);
    }
}
