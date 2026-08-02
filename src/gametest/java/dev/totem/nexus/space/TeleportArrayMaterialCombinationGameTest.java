package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Representative, player-buildable material arrays.  These tests make the
 * intended advantages and trade-offs visible in the dedicated-server result
 * rather than testing profiles only one block at a time.
 */
public final class TeleportArrayMaterialCombinationGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("totem-nexus");
    private static final BlockPos LODESTONE = new BlockPos(4, 2, 4);
    private static final List<BlockPos> RING = List.of(
            new BlockPos(0, 0, -1), new BlockPos(0, 0, 1), new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
            new BlockPos(1, 0, -1), new BlockPos(-1, 0, -1), new BlockPos(1, 0, 1), new BlockPos(-1, 0, 1)
    );
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000401");

    @GameTest(maxTicks = 30)
    public void overworldStoneAndDeepslateReachTierOneWithDurability(GameTestHelper helper) {
        SpaceStructureSnapshot snapshot = scan(helper, "overworld_stone_deepslate",
                Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS,
                Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS);

        require(helper, snapshot.rawStructuralBlocks() == 6
                        && snapshot.effectiveStructureCapacity() == 8
                        && snapshot.tier() == 1
                        && snapshot.materialStability() == 8
                        && snapshot.arrivalSafety() == 2
                        && snapshot.wearResistance() == 4
                        && snapshot.phaseSpeed() == -2
                        && snapshot.dimensionAffinity().getOrDefault("minecraft:overworld", 0) == 8,
                "Stone/deepslate durable Tier 1 totals changed");
    }

    @GameTest(maxTicks = 30)
    public void ironAndRedstoneTradeInterferenceForFastHighLoadRouting(GameTestHelper helper) {
        SpaceStructureSnapshot snapshot = scan(helper, "iron_redstone_logistics",
                Blocks.IRON_BLOCK, Blocks.IRON_BLOCK, Blocks.IRON_BLOCK, Blocks.IRON_BLOCK,
                Blocks.REDSTONE_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.REDSTONE_BLOCK);

        require(helper, snapshot.effectiveStructureCapacity() == 12
                        && snapshot.tier() == 1
                        && snapshot.materialStability() == 4
                        && snapshot.interferenceResistance() == -12
                        && Math.round(snapshot.interference() * 100D) == 14
                        && snapshot.phaseSpeed() == 8
                        && snapshot.cooldownRecovery() == 8
                        && snapshot.routeLoadCapacity() == 12,
                "Iron/redstone logistics trade-off totals changed");
    }

    @GameTest(maxTicks = 30)
    public void copperOxidationCancelsBulbExpansionAndWaxOnlyProtectsWear(GameTestHelper helper) {
        TeleportArrayMaterialAttributes fresh = profile("copper_bulb").attributes();
        TeleportArrayMaterialAttributes oxidized = profile("oxidized_copper_bulb").attributes();
        TeleportArrayMaterialAttributes waxedOxidized = profile("waxed_oxidized_copper_bulb").attributes();

        LOGGER.info("傳送陣搭配 [copper_states]: fresh={}, oxidized={}, waxed_oxidized={}",
                fresh.scalarValues(), oxidized.scalarValues(), waxedOxidized.scalarValues());
        require(helper, fresh.localScanExpansionRadius() == 1
                        && oxidized.localScanExpansionRadius() == 0
                        && oxidized.stability() == fresh.stability() - 3
                        && waxedOxidized.stability() == oxidized.stability()
                        && waxedOxidized.wearResistance() == oxidized.wearResistance() + 1
                        && waxedOxidized.maintenanceEfficiency() == oxidized.maintenanceEfficiency() + 1,
                "Copper oxidation/wax trade-off changed");
    }

    @GameTest(maxTicks = 30)
    public void netherBrickAndGoldFavorFastNetherTravelAtSafetyCost(GameTestHelper helper) {
        SpaceStructureSnapshot snapshot = scan(helper, "nether_brick_gold_fast_route",
                Blocks.NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.NETHER_BRICKS,
                Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK, Blocks.GOLD_BLOCK);

        require(helper, snapshot.effectiveStructureCapacity() == 8
                        && snapshot.tier() == 1
                        && snapshot.arrivalAccuracy() == 0
                        && snapshot.targetLock() == 0
                        && snapshot.arrivalSafety() == -4
                        && snapshot.wearResistance() == -4
                        && snapshot.foodEfficiency() == 4
                        && snapshot.phaseSpeed() == 16
                        && snapshot.cooldownRecovery() == 12
                        && snapshot.dimensionAffinity().getOrDefault("minecraft:the_nether", 0) == 12,
                "Nether brick/gold fast-route totals changed");
    }

    @GameTest(maxTicks = 30)
    public void pairedAmethystArraysReachTierOneAndReduceCrossDimensionFuel(GameTestHelper helper) {
        SpaceStructureSnapshot snapshot = scan(helper, "paired_amethyst_cross_dimension",
                Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_BLOCK,
                Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_BLOCK, Blocks.AMETHYST_BLOCK);
        TeleportArrayMaterialAttributes materials = snapshot.materialAttributes();
        NexusMapQuote quote = NexusTeleportQuoteCalculator.calculate(
                new NexusTeleportQuoteCalculator.Source(
                        UUID.fromString("00000000-0000-0000-0000-000000000402"), "lodestone", Level.OVERWORLD,
                        BlockPos.ZERO, snapshot.resonance(), snapshot.tier(), 0, materials),
                new NexusTeleportQuoteCalculator.Target(
                        UUID.fromString("00000000-0000-0000-0000-000000000403"), SpaceUnitType.LODESTONE,
                        Level.NETHER, new BlockPos(128, 64, 0), snapshot.resonance(), snapshot.tier(), 0D,
                        true, PLAYER, 0, materials),
                TeleportInterfaceType.COMPASS,
                new NexusTeleportQuoteCalculator.Resources(PLAYER, false, 0, 20, 64, 64),
                false);

        LOGGER.info("傳送陣搭配 [paired_amethyst_cross_dimension]: catalyst_units={} + {} -> shard_change={}, cost={}",
                materials.crossDimensionCatalystUnits(), materials.crossDimensionCatalystUnits(),
                quote.catalystDiscount(), quote.amethystCost());
        require(helper, snapshot.effectiveStructureCapacity() == 8
                        && snapshot.tier() == 1
                        && snapshot.arrivalAccuracy() == 16
                        && snapshot.targetLock() == 8
                        && snapshot.arrivalSafety() == 8
                        && snapshot.phaseSpeed() == -8
                        && snapshot.crossDimensionCatalystUnits() == 8
                        && quote.catalystDiscount() == 4
                        && quote.amethystCost() == 1
                        && quote.canTeleport(),
                "Paired amethyst cross-dimension fuel calculation changed");
    }

    @GameTest(maxTicks = 30)
    public void crackedAndOreArrayStaysTierOneButCarriesASeverePenalty(GameTestHelper helper) {
        SpaceStructureSnapshot snapshot = scan(helper, "cracked_ore_temporary_array",
                Blocks.CRACKED_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
                Blocks.CRACKED_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
                Blocks.IRON_ORE, Blocks.IRON_ORE, Blocks.IRON_ORE, Blocks.IRON_ORE);

        require(helper, snapshot.effectiveStructureCapacity() == 8
                        && snapshot.tier() == 1
                        && snapshot.materialStability() == -12
                        && snapshot.arrivalSafety() == -8
                        && snapshot.wearResistance() == -16
                        && snapshot.maintenanceEfficiency() == -12
                        && snapshot.interferenceResistance() == -8
                        && Math.round(snapshot.interference() * 100D) == 10,
                "Cracked/ore temporary-array penalties changed");
    }

    @GameTest(maxTicks = 30)
    public void everyBuiltinProfileSupportsAHomogeneousTierOneArray(GameTestHelper helper) {
        List<MaterialSample> samples = representativeProfiles();
        CombinationSummary summary = new CombinationSummary("homogeneous", samples.size());
        for (MaterialSample sample : samples) {
            SpaceStructureSnapshot snapshot = scanSilently(helper, repeat(sample.block(), RING.size()));
            NexusMapQuote quote = crossDimensionQuote(snapshot);
            if (!validTierOneQuote(snapshot, quote)) {
                helper.fail("Invalid homogeneous array for " + sample.name() + ": " + describe(snapshot, quote));
                return;
            }
            summary.record(sample.name(), snapshot, quote);
        }
        summary.log();
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void everyBuiltinProfilePairHasABoundedServerQuote(GameTestHelper helper) {
        List<MaterialSample> samples = representativeProfiles();
        int expectedPairs = samples.size() * (samples.size() - 1) / 2;
        CombinationSummary summary = new CombinationSummary("four_plus_four_pairs", expectedPairs);
        for (int first = 0; first < samples.size(); first++) {
            for (int second = first + 1; second < samples.size(); second++) {
                MaterialSample left = samples.get(first);
                MaterialSample right = samples.get(second);
                Block[] materials = new Block[RING.size()];
                for (int index = 0; index < materials.length; index++) {
                    materials[index] = index < materials.length / 2 ? left.block() : right.block();
                }
                SpaceStructureSnapshot snapshot = scanSilently(helper, materials);
                NexusMapQuote quote = crossDimensionQuote(snapshot);
                if (!validTierOneQuote(snapshot, quote)) {
                    helper.fail("Invalid mixed array for " + left.name() + " + " + right.name() + ": "
                            + describe(snapshot, quote));
                    return;
                }
                summary.record(left.name() + " + " + right.name(), snapshot, quote);
            }
        }
        summary.log();
        helper.succeed();
    }

    private static SpaceStructureSnapshot scan(GameTestHelper helper, String name, Block... materials) {
        if (materials.length > RING.size()) {
            throw new IllegalArgumentException("The test ring contains only " + RING.size() + " positions");
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
        for (int index = 0; index < materials.length; index++) {
            level.setBlockAndUpdate(origin.offset(RING.get(index)), materials[index].defaultBlockState());
        }
        SpaceStructureSnapshot snapshot = units(level).previewLodestoneStructure(level, origin);
        LOGGER.info("傳送陣搭配 [{}]: blocks={}, capacity={}, tier={}, stability={}, interference={}, "
                        + "accuracy={}, lock={}, safety={}, wear={}, maintenance={}, food={}, phase={}, cooldown={}, load={}, "
                        + "catalyst={}, affinity={}",
                name, snapshot.rawStructuralBlocks(), snapshot.effectiveStructureCapacity(), snapshot.tier(),
                snapshot.materialStability(), Math.round(snapshot.interference() * 100D), snapshot.arrivalAccuracy(),
                snapshot.targetLock(), snapshot.arrivalSafety(), snapshot.wearResistance(), snapshot.maintenanceEfficiency(),
                snapshot.foodEfficiency(), snapshot.phaseSpeed(), snapshot.cooldownRecovery(), snapshot.routeLoadCapacity(),
                snapshot.crossDimensionCatalystUnits(), snapshot.dimensionAffinity());
        return snapshot;
    }

    private static SpaceStructureSnapshot scanSilently(GameTestHelper helper, Block... materials) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
        for (int index = 0; index < materials.length; index++) {
            level.setBlockAndUpdate(origin.offset(RING.get(index)), materials[index].defaultBlockState());
        }
        return units(level).previewLodestoneStructure(level, origin);
    }

    private static NexusMapQuote crossDimensionQuote(SpaceStructureSnapshot snapshot) {
        TeleportArrayMaterialAttributes materials = snapshot.materialAttributes();
        return NexusTeleportQuoteCalculator.calculate(
                new NexusTeleportQuoteCalculator.Source(
                        UUID.fromString("00000000-0000-0000-0000-000000000404"), "lodestone", Level.OVERWORLD,
                        BlockPos.ZERO, snapshot.resonance(), snapshot.tier(), 0, materials),
                new NexusTeleportQuoteCalculator.Target(
                        UUID.fromString("00000000-0000-0000-0000-000000000405"), SpaceUnitType.LODESTONE,
                        Level.NETHER, new BlockPos(128, 64, 0), snapshot.resonance(), snapshot.tier(), 0D,
                        true, PLAYER, 0, materials),
                TeleportInterfaceType.COMPASS,
                new NexusTeleportQuoteCalculator.Resources(PLAYER, false, 0, 20, 64, 64),
                false);
    }

    private static boolean validTierOneQuote(SpaceStructureSnapshot snapshot, NexusMapQuote quote) {
        return snapshot.rawStructuralBlocks() == RING.size()
                && snapshot.effectiveStructureCapacity() >= 8
                && snapshot.tier() >= 1
                && snapshot.resonance() >= 0D && snapshot.resonance() <= 1D
                && snapshot.interference() >= 0D && snapshot.interference() <= 1D
                && quote.basePrepareTicks() >= 40 && quote.basePrepareTicks() <= 300
                && quote.baseMaxHorizontalDeviation() >= 1 && quote.baseMaxHorizontalDeviation() <= 96
                && quote.damageChancePercent() >= 0 && quote.damageChancePercent() <= 60
                && quote.structureWearChancePercent() >= 0 && quote.structureWearChancePercent() <= 100
                && quote.amethystCost() >= 1;
    }

    private static String describe(SpaceStructureSnapshot snapshot, NexusMapQuote quote) {
        return "capacity=" + snapshot.effectiveStructureCapacity()
                + ", tier=" + snapshot.tier()
                + ", resonance=" + snapshot.resonance()
                + ", interference=" + snapshot.interference()
                + ", prepare=" + quote.basePrepareTicks()
                + ", drift=" + quote.baseMaxHorizontalDeviation()
                + ", damage=" + quote.damageChancePercent()
                + ", wear=" + quote.structureWearChancePercent()
                + ", shards=" + quote.amethystCost();
    }

    private static Block[] repeat(Block block, int count) {
        Block[] values = new Block[count];
        java.util.Arrays.fill(values, block);
        return values;
    }

    /** One representative block per built-in base profile; copper shape and state coverage remains explicit above. */
    private static List<MaterialSample> representativeProfiles() {
        List<MaterialSample> samples = new ArrayList<>();
        samples.add(new MaterialSample("stone_bricks", Blocks.STONE_BRICKS));
        samples.add(new MaterialSample("stone_detail", Blocks.CHISELED_STONE_BRICKS));
        samples.add(new MaterialSample("cracked_stone", Blocks.CRACKED_STONE_BRICKS));
        samples.add(new MaterialSample("deepslate", Blocks.DEEPSLATE_BRICKS));
        samples.add(new MaterialSample("cracked_deepslate", Blocks.CRACKED_DEEPSLATE_BRICKS));
        samples.add(new MaterialSample("nether_bricks", Blocks.NETHER_BRICKS));
        samples.add(new MaterialSample("cracked_nether", Blocks.CRACKED_NETHER_BRICKS));
        samples.add(new MaterialSample("blackstone", Blocks.POLISHED_BLACKSTONE));
        samples.add(new MaterialSample("cracked_blackstone", Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS));
        samples.add(new MaterialSample("copper_block", block("copper_block")));
        samples.add(new MaterialSample("cut_copper", block("cut_copper")));
        samples.add(new MaterialSample("chiseled_copper", block("chiseled_copper")));
        samples.add(new MaterialSample("copper_grate", block("copper_grate")));
        samples.add(new MaterialSample("copper_bulb", block("copper_bulb")));
        samples.add(new MaterialSample("raw_metal", Blocks.RAW_COPPER_BLOCK));
        samples.add(new MaterialSample("iron", Blocks.IRON_BLOCK));
        samples.add(new MaterialSample("gold", Blocks.GOLD_BLOCK));
        samples.add(new MaterialSample("netherite", Blocks.NETHERITE_BLOCK));
        samples.add(new MaterialSample("precision_mineral", Blocks.QUARTZ_BLOCK));
        samples.add(new MaterialSample("amethyst", Blocks.AMETHYST_BLOCK));
        samples.add(new MaterialSample("diamond", Blocks.DIAMOND_BLOCK));
        samples.add(new MaterialSample("redstone", Blocks.REDSTONE_BLOCK));
        samples.add(new MaterialSample("ore", Blocks.IRON_ORE));
        return List.copyOf(samples);
    }

    private static TeleportArrayMaterialProfile profile(String path) {
        return TeleportArrayMaterialProfiles.profileFor(block(path).defaultBlockState());
    }

    private static Block block(String path) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft", path));
    }

    private static NexusSpaceUnitSavedData units(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (condition) {
            helper.succeed();
        } else {
            helper.fail(message);
        }
    }

    private record MaterialSample(String name, Block block) {
    }

    private static final class CombinationSummary {
        private final String kind;
        private final int expected;
        private int count;
        private int crossDimensionViable;
        private final List<String> crossDimensionBlocked = new ArrayList<>();
        private double leastResonance = Double.MAX_VALUE;
        private String leastResonantName = "";
        private int highestInterference = Integer.MIN_VALUE;
        private String noisiestName = "";

        private CombinationSummary(String kind, int expected) {
            this.kind = kind;
            this.expected = expected;
        }

        private void record(String name, SpaceStructureSnapshot snapshot, NexusMapQuote quote) {
            count++;
            if (quote.canTeleport()) {
                crossDimensionViable++;
            } else {
                crossDimensionBlocked.add(name + " [" + quote.blockedReason() + ", resonance="
                        + snapshot.resonance() + "]");
            }
            if (snapshot.resonance() < leastResonance) {
                leastResonance = snapshot.resonance();
                leastResonantName = name;
            }
            int interference = (int) Math.round(snapshot.interference() * 100D);
            if (interference > highestInterference) {
                highestInterference = interference;
                noisiestName = name;
            }
        }

        private void log() {
            LOGGER.info("傳送陣搭配 [{}]: checked={}/{}, cross_dimension_viable={}, least_resonance={} ({})"
                            + ", highest_interference={} ({}), cross_dimension_blocked={}",
                    kind, count, expected, crossDimensionViable, leastResonance, leastResonantName,
                    highestInterference, noisiestName, crossDimensionBlocked);
        }
    }
}
