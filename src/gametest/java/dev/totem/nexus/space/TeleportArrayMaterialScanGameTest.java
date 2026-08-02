package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Covers the placement-driven material expansion graph around one lodestone. */
public final class TeleportArrayMaterialScanGameTest {
    private static final BlockPos LODESTONE = new BlockPos(4, 2, 4);

    @GameTest(maxTicks = 30)
    public void extenderChainExpandsOnlyAlongItsPlacedPath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
        level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(origin.east(2), Blocks.DIAMOND_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(origin.east(3), Blocks.GOLD_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(origin.east(4), Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(origin.east(5), Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(origin.east(6), Blocks.GOLD_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(origin.west(3), Blocks.GOLD_BLOCK.defaultBlockState());

        SpaceStructureSnapshot snapshot = units(level).previewLodestoneStructure(level, origin);
        if (snapshot.rawStructuralBlocks() != 5) {
            helper.fail("Expected the eastward extension chain only, got " + snapshot.rawStructuralBlocks() + " blocks");
            return;
        }
        if (snapshot.maximumReachedDistance() != 5) {
            helper.fail("Expected the chained scan to reach the five-block bound, got " + snapshot.maximumReachedDistance());
            return;
        }
        if (snapshot.effectiveStructureCapacity() != 9) {
            helper.fail("Expected the five-block chain capacity 9, got " + snapshot.effectiveStructureCapacity());
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void initialSeedReadsAllTwentySixAdjacentPositionsOnly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        level.setBlockAndUpdate(origin.offset(dx, dy, dz), Blocks.STONE_BRICKS.defaultBlockState());
                    }
                }
            }
        }
        level.setBlockAndUpdate(origin.east(2), Blocks.GOLD_BLOCK.defaultBlockState());

        SpaceStructureSnapshot snapshot = units(level).previewLodestoneStructure(level, origin);
        if (snapshot.rawStructuralBlocks() != 26 || snapshot.maximumReachedDistance() != 1) {
            helper.fail("Initial scan must include exactly the centre-excluded 3x3x3 seed, got "
                    + snapshot.rawStructuralBlocks() + " blocks at reach " + snapshot.maximumReachedDistance());
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void disconnectedAndCancelledExtendersDoNotRevealMorePositions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
        // The exposed bulb's -1 oxidation modifier cancels its +1 local radius.
        level.setBlockAndUpdate(origin.east(), block("exposed_copper_bulb").defaultBlockState());
        level.setBlockAndUpdate(origin.east(2), Blocks.GOLD_BLOCK.defaultBlockState());
        // This valid material is outside the seed but has no connected expansion path.
        level.setBlockAndUpdate(origin.west(3), Blocks.DIAMOND_BLOCK.defaultBlockState());

        SpaceStructureSnapshot snapshot = units(level).previewLodestoneStructure(level, origin);
        if (snapshot.rawStructuralBlocks() != 1 || snapshot.maximumReachedDistance() != 1) {
            helper.fail("Cancelled or disconnected extenders exposed unrelated positions");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void existingAmethystArrayKeepsItsCatalystUnits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
        level.setBlockAndUpdate(origin.north(), Blocks.AMETHYST_BLOCK.defaultBlockState());

        SpaceStructureSnapshot snapshot = units(level).previewLodestoneStructure(level, origin);
        if (snapshot.rawStructuralBlocks() != 1 || snapshot.crossDimensionCatalystUnits() != 1
                || snapshot.amethystCatalystBlocks() != 1) {
            helper.fail("Existing amethyst catalyst behavior changed during material migration");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void materialFamiliesKeepTheirIdentityAndSignedContributions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
        level.setBlockAndUpdate(origin.north(), Blocks.STONE_BRICKS.defaultBlockState());
        level.setBlockAndUpdate(origin.south(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
        level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(origin.west(), Blocks.IRON_ORE.defaultBlockState());

        SpaceStructureSnapshot snapshot = units(level).previewLodestoneStructure(level, origin);
        if (snapshot.rawStructuralBlocks() != 4
                || snapshot.effectiveStructureCapacity() != 5
                || snapshot.materialStability() != -1
                || snapshot.materialFamilyCounts().getOrDefault("stone_brick", 0) != 2
                || snapshot.materialFamilyCounts().getOrDefault("iron", 0) != 1
                || snapshot.materialFamilyCounts().getOrDefault("ore", 0) != 1) {
            helper.fail("Mixed positive/negative materials lost their identity or signed totals: "
                    + snapshot.materialFamilyContributions());
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void copperAppliesShapeThenOxidationThenWax(GameTestHelper helper) {
        TeleportArrayMaterialProfile fresh = TeleportArrayMaterialProfiles.profileFor(
                block("copper_bulb").defaultBlockState());
        TeleportArrayMaterialProfile weathered = TeleportArrayMaterialProfiles.profileFor(
                block("weathered_copper_bulb").defaultBlockState());
        TeleportArrayMaterialProfile waxedWeathered = TeleportArrayMaterialProfiles.profileFor(
                block("waxed_weathered_copper_bulb").defaultBlockState());

        if (fresh.attributes().localScanExpansionRadius() != 1
                || weathered.attributes().localScanExpansionRadius() != 0
                || weathered.attributes().stability() >= fresh.attributes().stability()
                || waxedWeathered.attributes().stability() != weathered.attributes().stability()
                || waxedWeathered.attributes().wearResistance() != weathered.attributes().wearResistance() + 1
                || waxedWeathered.attributes().maintenanceEfficiency()
                != weathered.attributes().maintenanceEfficiency() + 1) {
            helper.fail("Copper state layering was not shape -> oxidation -> wax");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void cataloguePreservesCrackRefinementAndDimensionAffinityRules(GameTestHelper helper) {
        TeleportArrayMaterialProfile intact = TeleportArrayMaterialProfiles.profileFor(Blocks.STONE_BRICKS.defaultBlockState());
        TeleportArrayMaterialProfile cracked = TeleportArrayMaterialProfiles.profileFor(Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
        TeleportArrayMaterialProfile refined = TeleportArrayMaterialProfiles.profileFor(Blocks.IRON_BLOCK.defaultBlockState());
        TeleportArrayMaterialProfile ore = TeleportArrayMaterialProfiles.profileFor(Blocks.IRON_ORE.defaultBlockState());
        TeleportArrayMaterialProfile nether = TeleportArrayMaterialProfiles.profileFor(Blocks.NETHER_BRICKS.defaultBlockState());

        if (cracked.attributes().stability() > intact.attributes().stability()
                || cracked.attributes().arrivalSafety() > intact.attributes().arrivalSafety()
                || cracked.attributes().wearResistance() > intact.attributes().wearResistance()
                || refined.attributes().structureCapacity() <= ore.attributes().structureCapacity()
                || refined.attributes().stability() <= ore.attributes().stability()
                || intact.attributes().affinityFor("minecraft:overworld") <= 0
                || nether.attributes().affinityFor("minecraft:the_nether") <= 0) {
            helper.fail("Material identity profile invariant failed");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void everyInitialCatalogueFamilyIsAcceptedInOneArray(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
        Map<BlockPos, net.minecraft.world.level.block.Block> materials = new LinkedHashMap<>();
        materials.put(origin.north(), Blocks.STONE_BRICKS);
        materials.put(origin.south(), Blocks.DEEPSLATE_BRICKS);
        materials.put(origin.east(), Blocks.NETHER_BRICKS);
        materials.put(origin.west(), Blocks.POLISHED_BLACKSTONE);
        materials.put(origin.above(), block("copper_block"));
        materials.put(origin.below(), Blocks.RAW_IRON_BLOCK);
        materials.put(origin.north().east(), Blocks.IRON_BLOCK);
        materials.put(origin.north().west(), Blocks.GOLD_BLOCK);
        materials.put(origin.south().east(), Blocks.NETHERITE_BLOCK);
        materials.put(origin.south().west(), Blocks.QUARTZ_BLOCK);
        materials.put(origin.north().above(), Blocks.AMETHYST_BLOCK);
        materials.put(origin.south().above(), Blocks.DIAMOND_BLOCK);
        materials.put(origin.east().above(), Blocks.REDSTONE_BLOCK);
        materials.put(origin.west().above(), Blocks.IRON_ORE);
        materials.forEach((position, material) -> level.setBlockAndUpdate(position, material.defaultBlockState()));

        SpaceStructureSnapshot snapshot = units(level).previewLodestoneStructure(level, origin);
        String[] expectedFamilies = {"stone_brick", "deepslate", "nether_brick", "blackstone", "copper", "metal",
                "iron", "gold", "netherite", "mineral", "amethyst", "diamond", "redstone", "ore"};
        boolean everyFamilyPresent = java.util.Arrays.stream(expectedFamilies)
                .allMatch(family -> snapshot.materialFamilyCounts().getOrDefault(family, 0) == 1);
        if (snapshot.rawStructuralBlocks() != expectedFamilies.length || !everyFamilyPresent) {
            helper.fail("Initial catalogue family was not accepted: " + snapshot.materialFamilyCounts());
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void verifiedWornMaterialIsPhysicallyReplacedAndRescanned(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        BlockPos worn = origin.north();
        UUID unitId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
        level.setBlockAndUpdate(worn, Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
        NexusSpaceUnitSavedData data = units(level);
        data.put(new NexusSpaceUnitRecord(unitId, SpaceUnitType.LODESTONE, level.dimension(), origin,
                UUID.fromString("00000000-0000-0000-0000-000000000202"), "Repair test", SpaceUnitVisibility.PRIVATE,
                SpaceUnitStatus.ACTIVE, java.util.Set.of(), java.util.Set.of(), SpaceStructureSnapshot.EMPTY, 0, 0));

        if (data.repairLodestoneStructureBlock(level, unitId, origin.east(), Blocks.STONE_BRICKS.defaultBlockState())
                || !data.repairLodestoneStructureBlock(level, unitId, worn, Blocks.STONE_BRICKS.defaultBlockState())
                || !level.getBlockState(worn).is(Blocks.STONE_BRICKS)
                || !data.wornLodestoneStructureBlocks(level, unitId).isEmpty()) {
            helper.fail("Maintenance did not reject an unadvertised target and replace the selected worn block");
            return;
        }
        helper.succeed();
    }

    private static NexusSpaceUnitSavedData units(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
    }

    private static net.minecraft.world.level.block.Block block(String path) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft", path));
    }
}
