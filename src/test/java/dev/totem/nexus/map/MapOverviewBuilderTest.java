package dev.totem.nexus.map;

import dev.totem.nexus.space.NexusMapExtent;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class MapOverviewBuilderTest {
    private static byte[] fill(int color) { byte[] p=new byte[16384];Arrays.fill(p,(byte)color);return p; }
    private static void finish(MapOverviewBuilder b) { while(!b.layerComplete()) assertTrue(b.advance(71)<=71); }

    @Test void expansionRetainsCenterAndLeavesUnknownOuterAreaBlank() {
        var b=new MapOverviewBuilder(new NexusMapExtent(0,0,5));
        b.beginLayer(0,0,4,fill(4)); finish(b);
        var pixels=b.snapshot();
        for(int z=0;z<128;z++) for(int x=0;x<128;x++)
            assertEquals(x>=32&&x<96&&z>=32&&z<96?4:0,pixels[z*128+x]);
    }
    @Test void offCenterRecordedRouteSurvivesMinificationAndTransparentLayers() {
        var b=new MapOverviewBuilder(new NexusMapExtent(0,0,5));
        byte[] route=new byte[16384]; route[64*128+64]=20;
        b.beginLayer(1536,0,0,route); finish(b);
        b.beginLayer(1536,0,4,fill(0)); finish(b);
        assertEquals(20,b.snapshot()[64*128+112]);
        assertEquals(0,b.snapshot()[64*128+111]);
    }
    @Test void fineTerrainWinsOverCoarseRegardlessOfLayerOrder() {
        for(boolean fineFirst:new boolean[]{true,false}) {
            var b=new MapOverviewBuilder(new NexusMapExtent(0,0,5));
            b.beginLayer(0,0,fineFirst?0:4,fill(fineFirst?20:8));finish(b);
            b.beginLayer(0,0,fineFirst?4:0,fill(fineFirst?8:20));finish(b);
            assertEquals(20,b.snapshot()[64*128+64]);
            assertEquals(8,b.snapshot()[40*128+40]);
        }
    }
    @Test void budgetSnapshotAndUnalignedPixelBoundariesAreSafe() {
        var b=new MapOverviewBuilder(new NexusMapExtent(0,0,4));var input=new byte[16384];input[0]=12;
        b.beginLayer(1,1,4,input); input[0]=0;
        assertEquals(0,b.advance(0));assertEquals(1,b.advance(1));assertFalse(b.layerComplete());
        var image=b.snapshot();assertEquals(12,image[0]);assertEquals(12,image[1]);assertEquals(12,image[128]);assertEquals(12,image[129]);
        image[0]=0;assertEquals(12,b.snapshot()[0]);
        assertThrows(IllegalStateException.class,()->b.beginLayer(0,0,0,fill(4)));
        finish(b);assertEquals(0,b.advance(1));
    }
    @Test void extremeExtentDoesNotWrapFarTerrainToOppositeEdge() {
        var b=new MapOverviewBuilder(new NexusMapExtent(29999999,29999999,NexusMapExtent.MAX_SCALE));
        b.beginLayer(-29999999,-29999999,4,fill(8));finish(b);
        assertTrue(java.util.stream.IntStream.range(0,16384).anyMatch(i->b.snapshot()[i]==8));
        assertEquals(0,b.snapshot()[127*128+127]);
        assertThrows(IllegalArgumentException.class,()->b.advance(-1));
    }
}
