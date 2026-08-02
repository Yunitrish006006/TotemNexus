package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusTeleportQuoteCalculatorTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final NexusTeleportQuoteCalculator.Resources RESOURCES =
            new NexusTeleportQuoteCalculator.Resources(PLAYER, false, 0, 20, 64, 64);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void materialQuoteAppliesFoodPhaseAccuracySafetyAndWearWithFinalClamps() {
        NexusMapQuote baseline = quote(Level.OVERWORLD, Level.OVERWORLD,
                TeleportArrayMaterialAttributes.ZERO, TeleportArrayMaterialAttributes.ZERO);
        TeleportArrayMaterialAttributes improvements = attributes(
                0, 0, 0, 100, 100, 100, 100, 0, 0, 100, 100, 0, 0, 0, Map.of());
        NexusMapQuote improved = quote(Level.OVERWORLD, Level.OVERWORLD, improvements,
                TeleportArrayMaterialAttributes.ZERO);

        assertTrue(improved.finalFoodCost() < baseline.finalFoodCost());
        assertEquals(40, improved.basePrepareTicks());
        assertEquals(1, improved.baseMaxHorizontalDeviation());
        assertEquals(0, improved.damageChancePercent());
        assertEquals(0, improved.structureWearChancePercent());
    }

    @Test
    void crossDimensionAffinityAndSignedCatalystUnitsChangeOnlyCrossDimensionQuotes() {
        TeleportArrayMaterialAttributes source = attributes(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8,
                Map.of("minecraft:the_nether", 5));
        TeleportArrayMaterialAttributes target = attributes(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                Map.of("minecraft:overworld", 5));

        NexusMapQuote baseline = quote(Level.OVERWORLD, Level.NETHER,
                TeleportArrayMaterialAttributes.ZERO, TeleportArrayMaterialAttributes.ZERO);
        NexusMapQuote tuned = quote(Level.OVERWORLD, Level.NETHER, source, target);

        assertTrue(tuned.resonance() > baseline.resonance());
        assertTrue(tuned.maxHorizontalDeviation() < baseline.maxHorizontalDeviation());
        assertTrue(tuned.damageChancePercent() < baseline.damageChancePercent());
        assertEquals(2, tuned.catalystDiscount());
        assertEquals(tuned.baseAmethystCost() - 2, tuned.amethystCost());
    }

    @Test
    void negativeCatalystUnitsUseTruncationTowardZeroAndCannotAffectSameDimensionRoutes() {
        TeleportArrayMaterialAttributes negativeUnits = attributes(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -7, Map.of());
        NexusMapQuote crossDimension = quote(Level.OVERWORLD, Level.NETHER, negativeUnits,
                TeleportArrayMaterialAttributes.ZERO);
        NexusMapQuote sameDimension = quote(Level.OVERWORLD, Level.OVERWORLD, negativeUnits,
                TeleportArrayMaterialAttributes.ZERO);

        assertEquals(-1, crossDimension.catalystDiscount());
        assertEquals(crossDimension.baseAmethystCost() + 1, crossDimension.amethystCost());
        assertEquals(0, sameDimension.catalystDiscount());
        assertEquals(0, sameDimension.amethystCost());
    }

    private static NexusMapQuote quote(
            net.minecraft.resources.ResourceKey<Level> sourceDimension,
            net.minecraft.resources.ResourceKey<Level> targetDimension,
            TeleportArrayMaterialAttributes sourceMaterials,
            TeleportArrayMaterialAttributes targetMaterials) {
        NexusTeleportQuoteCalculator.Source source = new NexusTeleportQuoteCalculator.Source(
                UUID.fromString("00000000-0000-0000-0000-000000000102"), "lodestone", sourceDimension,
                new BlockPos(0, 64, 0), .8D, 2, 0, sourceMaterials);
        NexusTeleportQuoteCalculator.Target target = new NexusTeleportQuoteCalculator.Target(
                UUID.fromString("00000000-0000-0000-0000-000000000103"), SpaceUnitType.LODESTONE,
                targetDimension, new BlockPos(1024, 64, 0), .8D, 2, .3D, true, PLAYER, 0, targetMaterials);
        return NexusTeleportQuoteCalculator.calculate(source, target, TeleportInterfaceType.COMPASS, RESOURCES, false);
    }

    private static TeleportArrayMaterialAttributes attributes(
            int structureCapacity, int scanExpansionRadius, int stability, int arrivalAccuracy, int targetLock,
            int arrivalSafety, int wearResistance, int maintenanceEfficiency, int interferenceResistance,
            int foodEfficiency, int phaseSpeed, int cooldownRecovery, int routeLoadCapacity,
            int catalystUnits, Map<String, Integer> affinity) {
        return new TeleportArrayMaterialAttributes(structureCapacity, scanExpansionRadius, stability,
                arrivalAccuracy, targetLock, arrivalSafety, wearResistance, maintenanceEfficiency,
                interferenceResistance, foodEfficiency, phaseSpeed, cooldownRecovery, routeLoadCapacity,
                catalystUnits, affinity);
    }
}
