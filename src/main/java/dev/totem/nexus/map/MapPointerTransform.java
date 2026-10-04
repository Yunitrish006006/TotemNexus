package dev.totem.nexus.map;

/** Pure viewport arithmetic; no client classes or world access. */
public final class MapPointerTransform {
    private MapPointerTransform() { }

    public static int anchoredPan(double anchor, double oldOrigin, int oldScale,
                                  int newCenteredOrigin, int newScale) {
        double pixel = (anchor - oldOrigin) / oldScale;
        return (int) Math.round(anchor - pixel * newScale - newCenteredOrigin);
    }

    public static int clampPan(int pan, int renderedSize, int viewportSize) {
        int limit = Math.max(0, (renderedSize - (viewportSize - 2)) / 2);
        return Math.max(-limit, Math.min(limit, pan));
    }
}
