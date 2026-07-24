package dev.totem.nexus.space;

/** Stable catalyst discount math used by Space Unit teleport quotes. */
public final class AmethystCatalystDiscount {
    public static final int CATALYST_BLOCKS_PER_SHARD = 4;
    public static final int MINIMUM_CROSS_DIMENSION_COST = 1;
    private AmethystCatalystDiscount() { }
    public static int catalystDiscount(int sourceCatalysts, int targetCatalysts) {
        return (Math.max(0, sourceCatalysts) + Math.max(0, targetCatalysts)) / CATALYST_BLOCKS_PER_SHARD;
    }
    public static int eligibleCatalysts(boolean lodestoneEndpoint, int catalystBlocks) {
        return lodestoneEndpoint ? Math.max(0, catalystBlocks) : 0;
    }
    public static int finalCost(int baseCost, int sourceCatalysts, int targetCatalysts) {
        return quote(baseCost, sourceCatalysts, targetCatalysts).finalCost();
    }
    public static Quote quoteForEndpoints(int baseCost, boolean sourceLodestone, int sourceCatalysts,
                                          boolean targetLodestone, int targetCatalysts) {
        return quote(baseCost, eligibleCatalysts(sourceLodestone, sourceCatalysts),
                eligibleCatalysts(targetLodestone, targetCatalysts));
    }
    public static Quote quote(int baseCost, int sourceCatalysts, int targetCatalysts) {
        int normalizedBase = Math.max(0, baseCost);
        int source = Math.max(0, sourceCatalysts);
        int target = Math.max(0, targetCatalysts);
        int available = catalystDiscount(source, target);
        if (normalizedBase == 0) return new Quote(0, source, target, available, 0, 0);
        int finalCost = Math.max(MINIMUM_CROSS_DIMENSION_COST, normalizedBase - available);
        return new Quote(normalizedBase, source, target, available, normalizedBase - finalCost, finalCost);
    }
    public record Quote(int baseCost, int sourceCatalysts, int targetCatalysts, int availableDiscount,
                        int appliedDiscount, int finalCost) { }
}
