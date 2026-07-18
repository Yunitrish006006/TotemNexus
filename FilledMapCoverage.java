package com.adaptor.deadrecall.space;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Pure filled-map coverage rules shared by quote calculation and boundary tests. */
public final class FilledMapCoverage {
    private static final int MAP_PIXELS_PER_SIDE = 128;

    private FilledMapCoverage() {
    }

    public static boolean covers(
            ResourceKey<Level> mapDimension,
            int centerX,
            int centerZ,
            int scale,
            ResourceKey<Level> targetDimension,
            BlockPos targetPos) {
        if (mapDimension == null || targetDimension == null || targetPos == null) {
            return false;
        }
        if (!mapDimension.equals(targetDimension)) {
            return false;
        }
        return bounds(centerX, centerZ, scale).contains(targetPos.getX(), targetPos.getZ());
    }

    public static Bounds bounds(int centerX, int centerZ, int scale) {
        if (scale < 0 || scale > MapItemSavedData.MAX_SCALE) {
            throw new IllegalArgumentException("Filled-map scale is outside the vanilla range: " + scale);
        }
        long blocksPerPixel = 1L << scale;
        long halfWidth = (MAP_PIXELS_PER_SIDE * blocksPerPixel) / 2L;
        return new Bounds(
                (long) centerX - halfWidth,
                (long) centerX + halfWidth,
                (long) centerZ - halfWidth,
                (long) centerZ + halfWidth
        );
    }

    /** Vanilla maps cover 128 pixels: minimum edges are inclusive and maximum edges exclusive. */
    public record Bounds(long minXInclusive, long maxXExclusive, long minZInclusive, long maxZExclusive) {
        public Bounds {
            if (maxXExclusive <= minXInclusive || maxZExclusive <= minZInclusive) {
                throw new IllegalArgumentException("Filled-map bounds must have positive area");
            }
        }

        public boolean contains(int x, int z) {
            return x >= minXInclusive && x < maxXExclusive
                    && z >= minZInclusive && z < maxZExclusive;
        }
    }
}
