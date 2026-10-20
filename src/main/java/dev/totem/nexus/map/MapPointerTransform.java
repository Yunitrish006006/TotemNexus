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
    public static final int MAX_EXTENDED_PAN = 1 << 29;
    /** Native pixel coordinates stay unchanged; only the allowed world viewport grows. */
    public static int clampExtendedPan(int pan,int renderedSize,int viewportSize,int center,int nativeScale,int logicalScale) {
        if (nativeScale<0 || nativeScale>4 || logicalScale<nativeScale || logicalScale>dev.totem.nexus.space.NexusMapExtent.MAX_SCALE
                || renderedSize<1 || viewportSize<2 || !dev.totem.nexus.space.NexusMapExtent.isSupportedCoordinate(center))
            throw new IllegalArgumentException("Invalid extended map viewport");
        if (logicalScale==nativeScale) return clampPan(pan,renderedSize,viewportSize);
        double pixelsPerBlock=renderedSize/(128.0*(1<<nativeScale));
        long half=64L<<logicalScale;
        long minimum=Math.max((long)center-half,dev.totem.nexus.space.NexusMapExtent.MIN_COORDINATE);
        long maximum=Math.min((long)center+half,dev.totem.nexus.space.NexusMapExtent.MAX_COORDINATE_EXCLUSIVE);
        double low=(center-maximum)*pixelsPerBlock+(viewportSize-2)/2.0;
        double high=(center-minimum)*pixelsPerBlock-(viewportSize-2)/2.0;
        if(low>high) return (int)Math.round((center-(minimum+maximum)/2.0)*pixelsPerBlock);
        return (int)Math.clamp(Math.clamp((double)pan,Math.ceil(low),Math.floor(high)),-MAX_EXTENDED_PAN,MAX_EXTENDED_PAN);
    }

}
