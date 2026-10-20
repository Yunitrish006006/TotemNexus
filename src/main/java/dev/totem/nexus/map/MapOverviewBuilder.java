package dev.totem.nexus.map;

import dev.totem.nexus.space.NexusMapExtent;
import java.util.Arrays;

/** Incremental, presentation-only 128x128 overview. Never changes authoritative coverage. */
public final class MapOverviewBuilder {
    private final NexusMapExtent extent;
    private final byte[] colors = new byte[128 * 128];
    private final byte[] detail = new byte[128 * 128];
    private byte[] source;
    private long left, top;
    private int scale, cursor;

    public MapOverviewBuilder(NexusMapExtent extent) {
        this.extent = java.util.Objects.requireNonNull(extent);
        Arrays.fill(detail, (byte)127);
    }

    /** One immutable page snapshot at a time; callers retain ownership of page traversal. */
    public void beginLayer(int centerX, int centerZ, int nativeScale, byte[] pixels) {
        if (source != null) throw new IllegalStateException("Finish the current overview layer first");
        if (!NexusMapExtent.isSupportedCoordinate(centerX) || !NexusMapExtent.isSupportedCoordinate(centerZ)
                || nativeScale < 0 || nativeScale > 4 || nativeScale > extent.scale()
                || pixels == null || pixels.length != 16384)
            throw new IllegalArgumentException("Invalid overview layer");
        source = pixels.clone(); scale = nativeScale; cursor = 0;
        left = centerX - (64L << scale); top = centerZ - (64L << scale);
    }

    /** Consumes at most budget source pixels, each touching at most four output pixels. */
    public int advance(int budget) {
        if (budget < 0) throw new IllegalArgumentException("Negative overview budget");
        int used = 0;
        long unit = 1L << extent.scale();
        var bounds = extent.bounds();
        while (source != null && used < budget) {
            int at = cursor++; used++;
            byte color = source[at];
            if ((Byte.toUnsignedInt(color) >>> 2) != 0) {
                long x = left + ((long)(at & 127) << scale), z = top + ((long)(at >>> 7) << scale);
                long endX = Math.min(x + (1L << scale), NexusMapExtent.MAX_COORDINATE_EXCLUSIVE);
                long endZ = Math.min(z + (1L << scale), NexusMapExtent.MAX_COORDINATE_EXCLUSIVE);
                x = Math.max(x,NexusMapExtent.MIN_COORDINATE); z = Math.max(z,NexusMapExtent.MIN_COORDINATE);
                if (x < endX && z < endZ) {
                    long minX = Math.max(0,Math.floorDiv(x-bounds.minXInclusive(),unit));
                    long minZ = Math.max(0,Math.floorDiv(z-bounds.minZInclusive(),unit));
                    long maxX = Math.min(127,Math.floorDiv(endX-1-bounds.minXInclusive(),unit));
                    long maxZ = Math.min(127,Math.floorDiv(endZ-1-bounds.minZInclusive(),unit));
                    for (long py=minZ;py<=maxZ;py++) for(long px=minX;px<=maxX;px++) {
                        int index=(int)(py*128+px);
                        if (scale <= detail[index]) { colors[index]=color; detail[index]=(byte)scale; }
                    }
                }
            }
            if (cursor == 16384) source = null;
        }
        return used;
    }

    public boolean layerComplete() { return source == null; }
    public byte[] snapshot() { return colors.clone(); }
}
