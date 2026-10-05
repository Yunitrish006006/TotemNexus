package dev.totem.nexus.map;

/** Projects a real detail page into the base map's viewport, including subpixel scales. */
public record MapDetailTransform(float pixelScale, float left, float top) {
    public static MapDetailTransform project(int baseScale, int detailScale, int basePixelScale,
                                            float centerX, float centerY, int offsetX, int offsetZ) {
        if (baseScale < 0 || baseScale > 4 || detailScale < 0 || detailScale >= baseScale
                || basePixelScale < 1) throw new IllegalArgumentException("Invalid map detail transform");
        float pixelsPerBlock = basePixelScale / (float) (1 << baseScale);
        float pixelScale = pixelsPerBlock * (1 << detailScale);
        // Do not round separate page origins: adjacent pages must share the same edge.
        return new MapDetailTransform(pixelScale,
                centerX + offsetX * pixelsPerBlock - 64 * pixelScale,
                centerY + offsetZ * pixelsPerBlock - 64 * pixelScale);
    }
}
