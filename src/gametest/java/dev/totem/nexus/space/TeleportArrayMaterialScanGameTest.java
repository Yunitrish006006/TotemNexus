package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Covers the placement-driven material expansion graph around one lodestone. */
public final class TeleportArrayMaterialScanGameTest {
    private static final BlockPos LODESTONE = new BlockPos(4, 2, 4);

    @GameTest(maxTicks = 30)
    public void visualizationRejectsForgedRemoteUnauthorizedAndInvalidSources(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
        BlockPos validPos = helper.absolutePos(LODESTONE);
        NexusSpaceUnitSavedData units = units(level);
        NexusSpaceDiscoverySavedData discovery = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        viewer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COMPASS));
        viewer.setPos(validPos.getX() + 0.5D, validPos.getY() + 0.5D, validPos.getZ() + 0.5D);
        level.setBlockAndUpdate(validPos, Blocks.LODESTONE.defaultBlockState());
        level.setBlockAndUpdate(validPos.east(), Blocks.IRON_BLOCK.defaultBlockState());

        try {
            UUID validId = UUID.randomUUID();
            units.put(lodestone(validId, level, validPos, viewer.getUUID(), SpaceUnitVisibility.PRIVATE));
            discovery.markDiscovered(viewer.getUUID(), validId);
            if (request(viewer, validId).isEmpty()) {
                helper.fail("Valid local visualization request did not produce a payload");
                return;
            }

            UUID forgedId = UUID.randomUUID();
            if (request(viewer, forgedId).isPresent()) {
                helper.fail("Forged source ID produced a visualization payload");
                return;
            }

            UUID unauthorizedId = UUID.randomUUID();
            BlockPos unauthorizedPos = validPos.offset(0, 0, 2);
            level.setBlockAndUpdate(unauthorizedPos, Blocks.LODESTONE.defaultBlockState());
            units.put(lodestone(unauthorizedId, level, unauthorizedPos, UUID.randomUUID(),
                    SpaceUnitVisibility.PRIVATE));
            discovery.markDiscovered(viewer.getUUID(), unauthorizedId);
            if (request(viewer, unauthorizedId).isPresent()) {
                helper.fail("Permission loss exposed a private source visualization");
                return;
            }

            UUID otherDimensionId = UUID.randomUUID();
            units.put(new NexusSpaceUnitRecord(
                    otherDimensionId,
                    SpaceUnitType.LODESTONE,
                    net.minecraft.world.level.Level.NETHER,
                    validPos,
                    viewer.getUUID(),
                    "Other dimension",
                    SpaceUnitVisibility.PRIVATE,
                    SpaceUnitStatus.ACTIVE,
                    java.util.Set.of(),
                    java.util.Set.of(),
                    SpaceStructureSnapshot.EMPTY,
                    0,
                    0
            ));
            discovery.markDiscovered(viewer.getUUID(), otherDimensionId);
            if (request(viewer, otherDimensionId).isPresent()) {
                helper.fail("Different-dimension source produced a visualization payload");
                return;
            }

            UUID missingId = UUID.randomUUID();
            BlockPos missingPos = validPos.offset(0, 0, -2);
            level.setBlockAndUpdate(missingPos, Blocks.AIR.defaultBlockState());
            units.put(lodestone(missingId, level, missingPos, viewer.getUUID(), SpaceUnitVisibility.PRIVATE));
            discovery.markDiscovered(viewer.getUUID(), missingId);
            if (request(viewer, missingId).isPresent()
                    || units.get(missingId).filter(unit -> unit.status() == SpaceUnitStatus.DISABLED).isEmpty()) {
                helper.fail("Missing lodestone produced a payload or remained active");
                return;
            }

            BlockPos unloadedPos = validPos.offset(32_000, 0, 32_000);
            if (level.isLoaded(unloadedPos)) {
                helper.fail("Unloaded-boundary fixture unexpectedly started loaded");
                return;
            }
            UUID unloadedId = UUID.randomUUID();
            units.put(lodestone(unloadedId, level, unloadedPos, viewer.getUUID(), SpaceUnitVisibility.PRIVATE));
            discovery.markDiscovered(viewer.getUUID(), unloadedId);
            if (request(viewer, unloadedId).isPresent() || level.isLoaded(unloadedPos)) {
                helper.fail("Unloaded source produced a payload or forced its chunk to load");
                return;
            }
            helper.succeed();
        } finally {
            NexusArrayVisualizationAuthority.disconnect(viewer.getUUID());
            NexusSpaceUnitAuthority.clearInterfaceContext(viewer.getUUID());
            viewer.discard();
        }
    }

    @GameTest(maxTicks = 30)
    public void visualizationContainsOnlyCountedBlocksAndMarksExtenders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
        level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(origin.east(2), Blocks.GOLD_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(origin.west(3), Blocks.DIAMOND_BLOCK.defaultBlockState());

        TeleportArrayMaterialScan.Result scan = TeleportArrayMaterialScan.scan(
                level,
                origin,
                NexusSpaceUnitSavedData::isStructureBlock,
                NexusSpaceUnitSavedData::isWornStructureBlock
        );
        List<dev.totem.nexus.network.TeleportArrayVisualizationPayload.RelativeBlock> blocks =
                NexusArrayVisualizationAuthority.relativeBlocks(origin, scan);

        if (blocks.size() != 2
                || !blocks.contains(new dev.totem.nexus.network.TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, true))
                || !blocks.contains(new dev.totem.nexus.network.TeleportArrayVisualizationPayload.RelativeBlock(2, 0, 0, false))
                || blocks.stream().anyMatch(block -> block.dx() == -3)) {
            helper.fail("Visualization did not match the authoritative connected structural set: " + blocks);
            return;
        }
        helper.succeed();
    }

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
    public void approvedMaterialDetailsKeepTheirDistinctTradeOffs(GameTestHelper helper) {
        TeleportArrayMaterialAttributes mossy = TeleportArrayMaterialProfiles.profileFor(
                Blocks.MOSSY_STONE_BRICKS.defaultBlockState()).attributes();
        TeleportArrayMaterialAttributes chiseledStone = TeleportArrayMaterialProfiles.profileFor(
                Blocks.CHISELED_STONE_BRICKS.defaultBlockState()).attributes();
        TeleportArrayMaterialAttributes tiles = TeleportArrayMaterialProfiles.profileFor(
                Blocks.DEEPSLATE_TILES.defaultBlockState()).attributes();
        TeleportArrayMaterialAttributes polishedDeepslate = TeleportArrayMaterialProfiles.profileFor(
                Blocks.POLISHED_DEEPSLATE.defaultBlockState()).attributes();
        TeleportArrayMaterialAttributes redNether = TeleportArrayMaterialProfiles.profileFor(
                Blocks.RED_NETHER_BRICKS.defaultBlockState()).attributes();
        TeleportArrayMaterialAttributes blackstoneBricks = TeleportArrayMaterialProfiles.profileFor(
                Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()).attributes();
        TeleportArrayMaterialAttributes rawGold = TeleportArrayMaterialProfiles.profileFor(
                Blocks.RAW_GOLD_BLOCK.defaultBlockState()).attributes();
        TeleportArrayMaterialAttributes emerald = TeleportArrayMaterialProfiles.profileFor(
                Blocks.EMERALD_BLOCK.defaultBlockState()).attributes();
        TeleportArrayMaterialAttributes lapis = TeleportArrayMaterialProfiles.profileFor(
                Blocks.LAPIS_BLOCK.defaultBlockState()).attributes();
        TeleportArrayMaterialAttributes coal = TeleportArrayMaterialProfiles.profileFor(
                Blocks.COAL_BLOCK.defaultBlockState()).attributes();
        TeleportArrayMaterialAttributes deepslateRedstone = TeleportArrayMaterialProfiles.profileFor(
                Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState()).attributes();
        TeleportArrayMaterialAttributes debris = TeleportArrayMaterialProfiles.profileFor(
                Blocks.ANCIENT_DEBRIS.defaultBlockState()).attributes();

        if (mossy.interferenceResistance() != 2 || mossy.arrivalAccuracy() != -1
                || chiseledStone.arrivalAccuracy() != 2 || chiseledStone.targetLock() != 2
                || chiseledStone.arrivalSafety() != 1 || chiseledStone.routeLoadCapacity() != -1
                || tiles.stability() != 1 || tiles.arrivalAccuracy() != 1 || tiles.targetLock() != 1
                || polishedDeepslate.foodEfficiency() != 1 || polishedDeepslate.maintenanceEfficiency() != 2
                || polishedDeepslate.phaseSpeed() != -2
                || redNether.phaseSpeed() != 1 || redNether.arrivalAccuracy() != 1
                || redNether.arrivalSafety() != 1 || redNether.foodEfficiency() != -1
                || blackstoneBricks.stability() != 2 || blackstoneBricks.arrivalSafety() != 2
                || blackstoneBricks.routeLoadCapacity() != 1
                || rawGold.foodEfficiency() != 1 || rawGold.wearResistance() != -2
                || rawGold.affinityFor("minecraft:the_nether") != 1
                || emerald.foodEfficiency() != 2 || emerald.cooldownRecovery() != 1
                || emerald.maintenanceEfficiency() != 2
                || lapis.arrivalAccuracy() != 2 || lapis.targetLock() != 2 || lapis.arrivalSafety() != -1
                || coal.phaseSpeed() != 1 || coal.stability() != -1 || coal.maintenanceEfficiency() != -1
                || deepslateRedstone.structureCapacity() != 2 || deepslateRedstone.phaseSpeed() != 0
                || deepslateRedstone.cooldownRecovery() != 1 || deepslateRedstone.interferenceResistance() != -3
                || debris.structureCapacity() != 2 || debris.stability() != 1 || debris.wearResistance() != 2
                || debris.arrivalSafety() != 2 || debris.phaseSpeed() != -2) {
            helper.fail("Approved material detail profiles were collapsed or changed");
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

    private static java.util.Optional<dev.totem.nexus.network.TeleportArrayVisualizationPayload> request(
            ServerPlayer player,
            UUID sourceId) {
        NexusArrayVisualizationAuthority.disconnect(player.getUUID());
        NexusSpaceUnitAuthority.establishInterfaceContext(
                player,
                InteractionHand.MAIN_HAND,
                SpaceUnitType.LODESTONE.id(),
                sourceId
        );
        return NexusArrayVisualizationAuthority.createPayload(
                player,
                new dev.totem.nexus.network.RequestTeleportArrayVisualizationPayload(
                        SpaceUnitType.LODESTONE.id(),
                        sourceId,
                        true
                )
        );
    }

    private static NexusSpaceUnitRecord lodestone(
            UUID id,
            ServerLevel level,
            BlockPos pos,
            UUID owner,
            SpaceUnitVisibility visibility) {
        return new NexusSpaceUnitRecord(
                id,
                SpaceUnitType.LODESTONE,
                level.dimension(),
                pos,
                owner,
                "Visualization fixture",
                visibility,
                SpaceUnitStatus.ACTIVE,
                java.util.Set.of(),
                java.util.Set.of(),
                SpaceStructureSnapshot.EMPTY,
                0,
                0
        );
    }

    private static net.minecraft.world.level.block.Block block(String path) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("minecraft", path));
    }
}
