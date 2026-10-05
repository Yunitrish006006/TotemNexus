package dev.totem.nexus.map;

/** World resolution and palette-aware reduction; independent of GUI scaling. */
public final class MapResolution {
    private MapResolution() { }

    public static int selectedScale(int baseScale, int zoom) {
        if (baseScale < 0 || baseScale > 4 || zoom < 1 || zoom > (1 << baseScale)
                || Integer.bitCount(zoom) != 1) throw new IllegalArgumentException("Invalid map zoom");
        return baseScale - Integer.numberOfTrailingZeros(zoom);
    }

    /** Every level reduces original finest texels, never child winners. Zero means incomplete. */
    public static byte reduce(byte[] source, int x, int z, int scale, int[] counts) {
        if (source.length != 16384 || scale < 1 || scale > 3 || counts.length < 256)
            throw new IllegalArgumentException("Invalid reduction");
        java.util.Arrays.fill(counts, 0);
        int size = 1 << scale;
        if (x < 0 || z < 0 || x + size > 128 || z + size > 128)
            throw new IllegalArgumentException("Invalid footprint");
        for (int dz = 0; dz < size; dz++) for (int dx = 0; dx < size; dx++) {
            int color = Byte.toUnsignedInt(source[x + dx + (z + dz) * 128]);
            if ((color >>> 2) == 0) return 0;
            counts[color]++;
        }
        int bestColor = 1, bestCount = -1;
        for (int color = 1; color < 64; color++) {
            int total = 0;
            for (int shade = 0; shade < 4; shade++) total += counts[color * 4 + shade];
            if (total > bestCount) { bestCount = total; bestColor = color; }
        }
        int bestShade = 0;
        for (int shade = 1; shade < 4; shade++)
            if (counts[bestColor * 4 + shade] > counts[bestColor * 4 + bestShade]) bestShade = shade;
        return (byte) (bestColor * 4 + bestShade);
    }
}
