package com.adaptor.deadrecall.space;

public final class AmethystCatalystDiscount {
    public static final int CATALYST_BLOCKS_PER_SHARD = 4;
    public static final int MINIMUM_CROSS_DIMENSION_COST = 1;

    private AmethystCatalystDiscount() {
    }

    public static int catalystDiscount(int sourceCatalysts, int targetCatalysts) {
        int totalCatalysts = Math.max(0, sourceCatalysts) + Math.max(0, targetCatalysts);
        return totalCatalysts / CATALYST_BLOCKS_PER_SHARD;
    }

    public static int finalCost(int baseCost, int sourceCatalysts, int targetCatalysts) {
        if (baseCost <= 0) {
            return 0;
        }
        return Math.max(
                MINIMUM_CROSS_DIMENSION_COST,
                baseCost - catalystDiscount(sourceCatalysts, targetCatalysts)
        );
    }
}
