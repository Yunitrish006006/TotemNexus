package dev.totem.nexus.map;

/** One screen-space transform shared by destination rendering and hit testing. */
public final class MapDestinationProjection {
    private MapDestinationProjection() { }
    public record Point(int x, int y, boolean edge, boolean otherDimension) { }

    public static Point project(int worldX, int worldZ, boolean sameDimension,
            int centerX, int centerZ, int nativeScale, int mapLeft, int mapTop, int pixelScale,
            int left, int top, int right, int bottom) {
        if (nativeScale < 0 || nativeScale > 4 || pixelScale < 1 || right <= left || bottom <= top)
            throw new IllegalArgumentException("Invalid destination viewport");
        // Other dimensions have no direction in this terrain plane. Use a fixed dimension symbol.
        if (!sameDimension) return new Point(right, top, false, true);
        double x = mapLeft + (64 + ((long) worldX - centerX) / (double) (1 << nativeScale)) * pixelScale;
        double y = mapTop + (64 + ((long) worldZ - centerZ) / (double) (1 << nativeScale)) * pixelScale;
        if (x >= left && x <= right && y >= top && y <= bottom)
            return new Point((int) Math.round(x), (int) Math.round(y), false, false);
        double cx = (left + (double) right) / 2, cy = (top + (double) bottom) / 2;
        double dx = x - cx, dy = y - cy;
        double factor = Math.min(dx == 0 ? Double.POSITIVE_INFINITY : (right - left) / (2 * Math.abs(dx)),
                dy == 0 ? Double.POSITIVE_INFINITY : (bottom - top) / (2 * Math.abs(dy)));
        return new Point((int) Math.round(cx + dx * factor), (int) Math.round(cy + dy * factor), true, false);
    }
}
