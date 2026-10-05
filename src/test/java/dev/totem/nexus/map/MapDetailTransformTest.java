package dev.totem.nexus.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapDetailTransformTest {
    @Test void finestPageHasItsTrueWorldFootprintAtEveryZoomAndGuiBaseScale() {
        for (int guiBase : new int[]{1, 2, 3}) for (int zoom : new int[]{1, 2, 4, 8, 16}) {
            float pixelsPerBlock = guiBase * zoom / 16.0F;
            var page = MapDetailTransform.project(4, 0, guiBase * zoom, 300, 200, 192, -192);
            assertEquals(pixelsPerBlock, page.pixelScale());
            assertEquals(300 + 128 * pixelsPerBlock, page.left());
            assertEquals(200 - 256 * pixelsPerBlock, page.top());
            assertEquals(128 * pixelsPerBlock, page.pixelScale() * 128);
        }
    }

    @Test void adjacentPagesKeepSharedEdgesWithoutIndependentRounding() {
        for (int zoom : new int[]{1, 2, 4, 8, 16}) {
            var a = MapDetailTransform.project(4, 0, zoom, 300.5F, 200.5F, -129, -129);
            var b = MapDetailTransform.project(4, 0, zoom, 300.5F, 200.5F, -1, -1);
            assertEquals(a.left() + 128 * a.pixelScale(), b.left());
            assertEquals(a.top() + 128 * a.pixelScale(), b.top());
        }
    }

    @Test void everyHistoricalScaleUsesTheSameWorldTransform() {
        for (int base = 1; base <= 4; base++) for (int fine = 0; fine < base; fine++) {
            var layer = MapDetailTransform.project(base, fine, 3, 100, 100, 64, -64);
            float expected = 3.0F / (1 << (base - fine));
            assertEquals(expected, layer.pixelScale());
            assertEquals(100 + 64 * 3.0F / (1 << base), layer.left() + 64 * expected);
            assertEquals(100 - 64 * 3.0F / (1 << base), layer.top() + 64 * expected);
        }
    }
}
