package dev.totem.nexus.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapResolutionTest {
    @Test void eachZoomSelectsItsOwnWorldResolution() {
        for(int base=0;base<=4;base++) for(int n=0;n<=base;n++)
            assertEquals(base-n,MapResolution.selectedScale(base,1<<n));
        assertThrows(IllegalArgumentException.class,()->MapResolution.selectedScale(4,3));
        assertThrows(IllegalArgumentException.class,()->MapResolution.selectedScale(2,8));
    }
    @Test void unknownFootprintNeverInventsCoverage() {
        byte[] pixels=new byte[16384]; java.util.Arrays.fill(pixels,(byte)22);
        for(int scale=1;scale<=3;scale++) {
            assertEquals(22,Byte.toUnsignedInt(MapResolution.reduce(pixels,0,0,scale,new int[256])));
            pixels[(1<<scale)-1]=0;
            assertEquals(0,MapResolution.reduce(pixels,0,0,scale,new int[256]));
            pixels[(1<<scale)-1]=22;
        }
    }
    @Test void directFootprintMajorityIsNotMajorityOfChildWinners() {
        byte[] pixels=new byte[16384];java.util.Arrays.fill(pixels,(byte)8);
        for(int[] origin:new int[][]{{0,0},{2,0},{0,2}}) {
            pixels[origin[0]+128*origin[1]]=4;pixels[origin[0]+1+128*origin[1]]=4;
            assertEquals(4,MapResolution.reduce(pixels,origin[0],origin[1],1,new int[256]));
        }
        assertEquals(8,MapResolution.reduce(pixels,0,0,2,new int[256]));
    }
    @Test void colorAndShadeTiesAreDeterministic() {
        byte[] pixels=new byte[16384]; pixels[0]=6;pixels[1]=5;pixels[128]=8;pixels[129]=9;
        assertEquals(5,MapResolution.reduce(pixels,0,0,1,new int[256]));
    }
}
