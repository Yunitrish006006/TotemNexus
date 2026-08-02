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
                        && snapshot.wearResistance() == 6
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
                        && snapshot.arrivalAccuracy() == 4
                        && snapshot.targetLock() == 4
                        && snapshot.arrivalSafety() == -4
                        && snapshot.wearResistance() == -4
                        && snapshot.foodEfficiency() == 8
                        && snapshot.phaseSpeed() == 12
                        && snapshot.cooldownRecovery() == 8
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

    private static TeleportArrayMaterialProfile profile(String path) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft", path));
        return TeleportArrayMaterialProfiles.profileFor(block.defaultBlockState());
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
}
