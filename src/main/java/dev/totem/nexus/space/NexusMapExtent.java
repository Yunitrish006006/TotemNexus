package dev.totem.nexus.space;

import net.minecraft.world.level.Level;

import java.util.Optional;

/** Logical map geometry, independent of vanilla's persisted scale limit. */
public record NexusMapExtent(int centerX, int centerZ, int scale) {
    public static final int MIN_COORDINATE = -Level.MAX_LEVEL_SIZE;
    public static final int MAX_COORDINATE_EXCLUSIVE = Level.MAX_LEVEL_SIZE;
    // Even an origin at a world edge needs no larger extent to contain the world.
    public static final int MAX_SCALE = Long.SIZE
            - Long.numberOfLeadingZeros(2L * Level.MAX_LEVEL_SIZE - 1L) - 6;

    public NexusMapExtent {
        if (!isSupportedCoordinate(centerX) || !isSupportedCoordinate(centerZ)
                || scale < 0 || scale > MAX_SCALE) {
            throw new IllegalArgumentException("Invalid Nexus map extent");
        }
    }

    public static boolean isSupportedCoordinate(long coordinate) {
        return coordinate >= MIN_COORDINATE && coordinate < MAX_COORDINATE_EXCLUSIVE;
    }

    /** Native map storage remains within Mojang's scale range. */
    public byte vanillaScale() {
        return (byte) Math.min(scale, net.minecraft.world.level.saveddata.maps.MapItemSavedData.MAX_SCALE);
    }

    public Bounds bounds() {
        long halfWidth = 64L << scale;
        return new Bounds(centerX - halfWidth, centerX + halfWidth,
                centerZ - halfWidth, centerZ + halfWidth);
    }

    /** A logical rectangle may cross a world edge; sampling never does. */
    public Bounds samplingBounds() {
        Bounds bounds = bounds();
        return new Bounds(Math.max(bounds.minXInclusive(), MIN_COORDINATE),
                Math.min(bounds.maxXExclusive(), MAX_COORDINATE_EXCLUSIVE),
                Math.max(bounds.minZInclusive(), MIN_COORDINATE),
                Math.min(bounds.maxZExclusive(), MAX_COORDINATE_EXCLUSIVE));
    }

    public boolean coversSupportedWorld() {
        Bounds bounds = bounds();
        return bounds.minXInclusive() <= MIN_COORDINATE
                && bounds.maxXExclusive() >= MAX_COORDINATE_EXCLUSIVE
                && bounds.minZInclusive() <= MIN_COORDINATE
                && bounds.maxZExclusive() >= MAX_COORDINATE_EXCLUSIVE;
    }

    public Optional<NexusMapExtent> expanded() {
        return coversSupportedWorld() ? Optional.empty()
                : Optional.of(new NexusMapExtent(centerX, centerZ, scale + 1));
    }

    public record Bounds(long minXInclusive, long maxXExclusive,
                         long minZInclusive, long maxZExclusive) {
        public Bounds {
            if (maxXExclusive <= minXInclusive || maxZExclusive <= minZInclusive) {
                throw new IllegalArgumentException("Map bounds must have positive area");
            }
        }

        public boolean contains(long x, long z) {
            return x >= minXInclusive && x < maxXExclusive
                    && z >= minZInclusive && z < maxZExclusive;
        }
    }
}
