package dev.totem.nexus.space;

/**
 * Server-calculated map quote. The future teleport authority supplies this
 * value; map serialization must not recalculate costs from client input.
 */
public record NexusMapQuote(
        double resonance, int tier, int distanceBlocks,
        int baseFoodCost, int finalFoodCost, int saturationCost, int hungerCost, int foodPointsNeeded, int safeFoodPointsAvailable,
        int amethystCost, int amethystAvailable, int baseAmethystCost, int sourceCatalysts, int targetCatalysts, int catalystDiscount,
        int basePrepareTicks, int prepareTicks, int baseMaxHorizontalDeviation, int maxHorizontalDeviation,
        int damageChancePercent, int baseStructureWearChancePercent, int structureWearChancePercent,
        boolean interfaceBonusActive, String interfaceBonusMessageKey, boolean canTeleport, String blockedReason) {
    public NexusMapQuote {
        interfaceBonusMessageKey = interfaceBonusMessageKey == null || interfaceBonusMessageKey.isBlank()
                ? "message.deadrecall.space_unit.interface_bonus.compass" : interfaceBonusMessageKey;
        blockedReason = blockedReason == null ? "" : blockedReason;
    }

    /** A valid non-teleportable placeholder for a map whose cost authority has not moved yet. */
    public static NexusMapQuote unavailable(TeleportInterfaceType interfaceType, String reason) {
        TeleportInterfaceQuotePolicy.Quote interfaceQuote = TeleportInterfaceQuotePolicy.specialize(
                interfaceType, SpaceUnitType.LODESTONE, false, false, 0, 0, 0, 0);
        return new NexusMapQuote(0, 0, -1, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, interfaceQuote.prepareTicks(), 0, interfaceQuote.maxHorizontalDeviation(),
                0, 0, interfaceQuote.structureWearChancePercent(), interfaceQuote.bonusActive(), interfaceQuote.bonusMessageKey(), false, reason);
    }
}
