package dev.totem.nexus.space;

/** Stable catalyst discount math used by Space Unit teleport quotes. */
public final class AmethystCatalystDiscount {
    public static final int CATALYST_BLOCKS_PER_SHARD = 4;
    public static final int MINIMUM_CROSS_DIMENSION_COST = 1;
    public static final int MAXIMUM_CROSS_DIMENSION_COST = 64;
    private AmethystCatalystDiscount() { }
    public static int catalystDiscount(int sourceCatalysts, int targetCatalysts) {
        return catalystChange(sourceCatalysts, targetCatalysts);
    }
    /** Java integer division deliberately truncates toward zero for signed material units. */
    public static int catalystChange(int sourceUnits, int targetUnits) {
        return (sourceUnits + targetUnits) / CATALYST_BLOCKS_PER_SHARD;
    }
    public static int eligibleCatalysts(boolean lodestoneEndpoint, int catalystBlocks) {
        return lodestoneEndpoint ? catalystBlocks : 0;
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
        int source = sourceCatalysts;
        int target = targetCatalysts;
        int available = catalystChange(source, target);
        if (normalizedBase == 0) return new Quote(0, source, target, available, 0, 0);
        int finalCost = Math.max(MINIMUM_CROSS_DIMENSION_COST,
                Math.min(MAXIMUM_CROSS_DIMENSION_COST, normalizedBase - available));
        return new Quote(normalizedBase, source, target, available, normalizedBase - finalCost, finalCost);
    }
    public record Quote(int baseCost, int sourceCatalysts, int targetCatalysts, int availableDiscount,
                        int appliedDiscount, int finalCost) { }
}
