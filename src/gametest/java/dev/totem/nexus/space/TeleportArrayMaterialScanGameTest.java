package dev.totem.nexus.space;

import dev.totem.nexus.network.TeleportArrayVisualizationPayload;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Covers the placement-driven material expansion graph around one lodestone. */
public final class TeleportArrayMaterialScanGameTest {
    private static final BlockPos LODESTONE = new BlockPos(4, 2, 4);

    @GameTest(maxTicks = 30)
    public void expansionModeRuleIsRegisteredWithStableDefaultAndCommandValues(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (!NexusTeleportArrayExpansionRules.EXPANSION_MODE_ID.equals(
                NexusTeleportArrayExpansionRules.EXPANSION_MODE.getIdentifier())
                || !"gamerule.deadrecall.teleport_array_expansion_mode".equals(
                NexusTeleportArrayExpansionRules.EXPANSION_MODE.getDescriptionId())
                || NexusTeleportArrayExpansionRules.EXPANSION_MODE.defaultValue()
                != NexusTeleportArrayExpansionRules.ExpansionMode.LOCAL
                || level.getGameRules().get(NexusTeleportArrayExpansionRules.EXPANSION_MODE)
                != NexusTeleportArrayExpansionRules.ExpansionMode.LOCAL
                || !"local".equals(NexusTeleportArrayExpansionRules.ExpansionMode.LOCAL.toString())
                || !"centered".equals(NexusTeleportArrayExpansionRules.ExpansionMode.CENTERED.toString())) {
            helper.fail("Expansion gamerule registration, default, or command values changed");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void visualizationInitialEnableStillRequiresHeldBoundInterface(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
        BlockPos sourcePos = helper.absolutePos(LODESTONE);
        UUID sourceId = UUID.randomUUID();
        level.setBlockAndUpdate(sourcePos, Blocks.LODESTONE.defaultBlockState());
        units(level).put(lodestone(sourceId, level, sourcePos, viewer.getUUID(), SpaceUnitVisibility.PRIVATE));
        discovery(level).markDiscovered(viewer.getUUID(), sourceId);
        viewer.setPos(sourcePos.getX() + 0.5D, sourcePos.getY() + 0.5D, sourcePos.getZ() + 0.5D);
        viewer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TUFF));

        try {
            if (refresh(viewer, sourceId, true, true).isPresent()
                    || NexusArrayVisualizationAuthority.hasSession(viewer.getUUID())) {
                helper.fail("Visualization enabled without a held bound interface and initial context");
                return;
            }
            helper.succeed();
        } finally {
            cleanup(viewer);
        }
    }

    @GameTest(maxTicks = 70)
    public void visualizationSessionRefreshesAfterSwitchingToBuildingMaterial(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
        BlockPos sourcePos = helper.absolutePos(LODESTONE);
        BlockPos changedPos = sourcePos.east();
        UUID sourceId = UUID.randomUUID();
        level.setBlockAndUpdate(sourcePos, Blocks.LODESTONE.defaultBlockState());
        units(level).put(lodestone(sourceId, level, sourcePos, viewer.getUUID(), SpaceUnitVisibility.PRIVATE));
        discovery(level).markDiscovered(viewer.getUUID(), sourceId);
        viewer.setPos(sourcePos.getX() + 0.5D, sourcePos.getY() + 0.5D, sourcePos.getZ() + 0.5D);
        viewer.setNoGravity(true);
        bindCompass(viewer, sourceId);
        establishContext(viewer, sourceId);

        if (refresh(viewer, sourceId, true, true).isEmpty()
                || !NexusArrayVisualizationAuthority.sessionMatches(
                viewer.getUUID(), SpaceUnitType.LODESTONE.id(), sourceId)) {
            cleanup(viewer);
            helper.fail("Legal initial visualization request did not establish its server session");
            return;
        }
        viewer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TUFF));
        NexusSpaceUnitAuthority.clearInterfaceContext(viewer.getUUID());

        helper.startSequence()
                // GameTest sequences begin between server game-time increments;
                // the extra boundary tick still proves the production 20-tick gate.
                .thenExecuteAfter(21, () -> {
                    level.setBlockAndUpdate(changedPos, Blocks.TUFF.defaultBlockState());
                    TeleportArrayVisualizationPayload placed = refresh(viewer, sourceId, true, true)
                            .orElseThrow(() -> helper.assertionException(
                                    "Placed material did not produce a changed visualization snapshot"));
                    TeleportArrayVisualizationPayload.RelativeBlock east =
                            new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, false, false);
                    if (!placed.blocks().contains(east)
                            || !NexusArrayVisualizationAuthority.hasSession(viewer.getUUID())) {
                        throw helper.assertionException(
                                "Session refresh after switching items did not classify placed tuff as counted");
                    }
                })
                .thenExecuteAfter(21, () -> {
                    level.setBlockAndUpdate(changedPos, Blocks.AIR.defaultBlockState());
                    TeleportArrayVisualizationPayload broken = refresh(viewer, sourceId, true, true)
                            .orElseThrow(() -> helper.assertionException(
                                    "Broken material did not produce a changed visualization snapshot"));
                    TeleportArrayVisualizationPayload.RelativeBlock east =
                            new TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, false, true);
                    if (!broken.blocks().contains(east)
                            || !NexusArrayVisualizationAuthority.hasSession(viewer.getUUID())) {
                        throw helper.assertionException(
                                "Session refresh after switching items did not restore the build site");
                    }
                })
                .thenExecute(() -> cleanup(viewer))
                .thenSucceed();
    }

    @GameTest(maxTicks = 50)
    public void visualizationSessionInvalidatesOnSourceDistanceAndPermissionChanges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NexusSpaceUnitSavedData units = units(level);
        NexusSpaceDiscoverySavedData discovery = discovery(level);
        BlockPos firstPos = helper.absolutePos(LODESTONE);
        BlockPos secondPos = firstPos.offset(0, 0, 3);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        level.setBlockAndUpdate(firstPos, Blocks.LODESTONE.defaultBlockState());
        level.setBlockAndUpdate(secondPos, Blocks.LODESTONE.defaultBlockState());
        units.put(lodestone(firstId, level, firstPos, UUID.randomUUID(), SpaceUnitVisibility.PUBLIC));
        units.put(lodestone(secondId, level, secondPos, UUID.randomUUID(), SpaceUnitVisibility.PUBLIC));

        ServerPlayer switchedSource = sessionViewer(helper, firstPos, firstId, discovery);
        ServerPlayer movedAway = sessionViewer(helper, firstPos, firstId, discovery);
        ServerPlayer lostPermission = sessionViewer(helper, firstPos, firstId, discovery);
        discovery.markDiscovered(switchedSource.getUUID(), secondId);
        switchToBuildingMaterial(switchedSource);
        switchToBuildingMaterial(movedAway);
        switchToBuildingMaterial(lostPermission);

        helper.startSequence()
                .thenExecuteAfter(21, () -> {
                    if (refresh(switchedSource, secondId, true, false).isPresent()
                            || NexusArrayVisualizationAuthority.hasSession(switchedSource.getUUID())) {
                        throw helper.assertionException(
                                "A different source reused a session without a newly held matching interface");
                    }

                    movedAway.setPos(firstPos.getX() + 20.5D, firstPos.getY() + 0.5D, firstPos.getZ() + 0.5D);
                    if (refresh(movedAway, firstId, true, false).isPresent()
                            || NexusArrayVisualizationAuthority.hasSession(movedAway.getUUID())) {
                        throw helper.assertionException("A session survived leaving the source-open radius");
                    }

                    units.put(lodestone(firstId, level, firstPos, UUID.randomUUID(), SpaceUnitVisibility.PRIVATE));
                    if (refresh(lostPermission, firstId, true, false).isPresent()
                            || NexusArrayVisualizationAuthority.hasSession(lostPermission.getUUID())) {
                        throw helper.assertionException("A session survived loss of source view permission");
                    }
                })
                .thenExecute(() -> {
                    cleanup(switchedSource);
                    cleanup(movedAway);
                    cleanup(lostPermission);
                })
                .thenSucceed();
    }

    @GameTest(maxTicks = 30)
    public void visualizationDisableAndDisconnectClearSessions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer viewer = helper.makeMockServerPlayerInLevel();
        BlockPos sourcePos = helper.absolutePos(LODESTONE);
        UUID sourceId = UUID.randomUUID();
        level.setBlockAndUpdate(sourcePos, Blocks.LODESTONE.defaultBlockState());
        units(level).put(lodestone(sourceId, level, sourcePos, viewer.getUUID(), SpaceUnitVisibility.PRIVATE));
        discovery(level).markDiscovered(viewer.getUUID(), sourceId);
        viewer.setPos(sourcePos.getX() + 0.5D, sourcePos.getY() + 0.5D, sourcePos.getZ() + 0.5D);

        try {
            enable(viewer, sourceId);
            refresh(viewer, sourceId, false, false);
            if (NexusArrayVisualizationAuthority.hasSession(viewer.getUUID())) {
                helper.fail("Explicit disable did not clear the visualization session");
                return;
            }

            enable(viewer, sourceId);
            NexusArrayVisualizationAuthority.disconnect(viewer.getUUID());
            if (NexusArrayVisualizationAuthority.hasSession(viewer.getUUID())) {
                helper.fail("Disconnect cleanup did not clear the visualization session");
                return;
            }

            helper.succeed();
        } finally {
            cleanup(viewer);
        }
    }

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
            bindCompass(viewer, validId);
            if (request(viewer, validId).isEmpty()) {
                helper.fail("Valid local visualization request did not produce a payload");
                return;
            }

            UUID forgedId = UUID.randomUUID();
            bindCompass(viewer, forgedId);
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
            bindCompass(viewer, unauthorizedId);
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
            bindCompass(viewer, otherDimensionId);
            if (request(viewer, otherDimensionId).isPresent()) {
                helper.fail("Different-dimension source produced a visualization payload");
                return;
            }

            UUID missingId = UUID.randomUUID();
            BlockPos missingPos = validPos.offset(0, 0, -2);
            level.setBlockAndUpdate(missingPos, Blocks.AIR.defaultBlockState());
            units.put(lodestone(missingId, level, missingPos, viewer.getUUID(), SpaceUnitVisibility.PRIVATE));
            discovery.markDiscovered(viewer.getUUID(), missingId);
            bindCompass(viewer, missingId);
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
            bindCompass(viewer, unloadedId);
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
                NexusArrayVisualizationAuthority.relativeBlocks(origin, scan, true, false);

        if (blocks.size() != 2
                || !blocks.contains(new dev.totem.nexus.network.TeleportArrayVisualizationPayload.RelativeBlock(1, 0, 0, true, false))
                || !blocks.contains(new dev.totem.nexus.network.TeleportArrayVisualizationPayload.RelativeBlock(2, 0, 0, false, false))
                || blocks.stream().anyMatch(block -> block.dx() == -3)) {
            helper.fail("Visualization did not match the authoritative connected structural set: " + blocks);
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void buildSitesMoveBetweenExactSetsAndExpansionRevealsNewSites(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        BlockPos firstSite = origin.east(2);
        BlockPos newlyReached = origin.east(3);
        BlockPos solidNonMaterial = origin.west();
        level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
        level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(solidNonMaterial, Blocks.DIRT.defaultBlockState());

        TeleportArrayMaterialScan.Result initial = scan(level, origin);
        if (!initial.buildablePositions().contains(firstSite)
                || initial.buildablePositions().contains(newlyReached)
                || initial.buildablePositions().contains(solidNonMaterial)) {
            helper.fail("Initial exact build-site set was incorrect");
            return;
        }

        level.setBlockAndUpdate(firstSite, Blocks.GOLD_BLOCK.defaultBlockState());
        TeleportArrayMaterialScan.Result counted = scan(level, origin);
        if (counted.buildablePositions().contains(firstSite)
                || !counted.structuralPositions().contains(firstSite)) {
            helper.fail("Placed material did not move from buildable to counted");
            return;
        }

        level.setBlockAndUpdate(firstSite, Blocks.IRON_BLOCK.defaultBlockState());
        TeleportArrayMaterialScan.Result expanded = scan(level, origin);
        if (!expanded.structuralPositions().contains(firstSite)
                || !expanded.expansionEmitterPositions().contains(firstSite)
                || !expanded.buildablePositions().contains(newlyReached)) {
            helper.fail("Placed expander did not expose the next buildable position");
            return;
        }

        level.setBlockAndUpdate(firstSite, Blocks.AIR.defaultBlockState());
        TeleportArrayMaterialScan.Result broken = scan(level, origin);
        if (!broken.buildablePositions().contains(firstSite)
                || broken.structuralPositions().contains(firstSite)) {
            helper.fail("Broken material did not return to the buildable set");
            return;
        }
        helper.succeed();
    }

    @GameTest(maxTicks = 30)
    public void localAndCenteredModesUseDifferentExpansionGeometry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        NexusTeleportArrayExpansionRules.ExpansionMode previous =
                level.getGameRules().get(NexusTeleportArrayExpansionRules.EXPANSION_MODE);
        try {
            level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
            level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(origin.west(2), Blocks.GOLD_BLOCK.defaultBlockState());

            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    NexusTeleportArrayExpansionRules.ExpansionMode.LOCAL,
                    null);
            TeleportArrayMaterialScan.Result local = scan(level, origin);
            if (!local.visitedPositions().contains(origin.east(2))
                    || local.visitedPositions().contains(origin.west(2))
                    || local.structuralPositions().contains(origin.west(2))) {
                helper.fail("Local mode stopped following only the emitter's placed path");
                return;
            }

            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    NexusTeleportArrayExpansionRules.ExpansionMode.CENTERED,
                    null);
            TeleportArrayMaterialScan.Result centered = scan(level, origin);
            if (!centered.structuralPositions().contains(origin.west(2))
                    || !containsLoadedCube(level, centered, origin, 2)) {
                helper.fail("Centered mode did not unlock the complete radius-two cube around the lodestone");
                return;
            }
            helper.succeed();
        } finally {
            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    previous,
                    null);
        }
    }

    @GameTest(maxTicks = 30)
    public void centeredModeReachesEmitterFixedPointWithoutAddingSameLayerRadii(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        NexusTeleportArrayExpansionRules.ExpansionMode previous =
                level.getGameRules().get(NexusTeleportArrayExpansionRules.EXPANSION_MODE);
        try {
            level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
            level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(origin.west(), Blocks.IRON_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(origin.east(2), Blocks.IRON_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(origin.east(3), Blocks.IRON_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(origin.west(4), Blocks.GOLD_BLOCK.defaultBlockState());

            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    NexusTeleportArrayExpansionRules.ExpansionMode.CENTERED,
                    null);
            TeleportArrayMaterialScan.Result centered = scan(level, origin);
            if (centered.maximumReachedDistance() != 4
                    || !centered.structuralPositions().contains(origin.west(4))
                    || !containsLoadedCube(level, centered, origin, 4)) {
                helper.fail("Centered emitter chain did not reach its radius-four fixed point");
                return;
            }
            // The two radius-one emitters in the initial layer must use max(), not sum().
            if (centered.visitedPositions().contains(origin.east(5))) {
                helper.fail("Same-layer emitters stacked their radii instead of taking the maximum");
                return;
            }
            helper.succeed();
        } finally {
            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    previous,
                    null);
        }
    }

    @GameTest(maxTicks = 30)
    public void centeredModeUsesMaximumInsteadOfStackingSameLayerEmitters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        NexusTeleportArrayExpansionRules.ExpansionMode previous =
                level.getGameRules().get(NexusTeleportArrayExpansionRules.EXPANSION_MODE);
        try {
            level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
            level.setBlockAndUpdate(origin.east(), Blocks.DIAMOND_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(origin.west(), Blocks.DIAMOND_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(origin.east(4), Blocks.GOLD_BLOCK.defaultBlockState());

            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    NexusTeleportArrayExpansionRules.ExpansionMode.CENTERED,
                    null);
            TeleportArrayMaterialScan.Result centered = scan(level, origin);
            if (centered.maximumReachedDistance() != 3
                    || centered.visitedPositions().contains(origin.east(4))
                    || centered.structuralPositions().contains(origin.east(4))) {
                helper.fail("Same-layer centered emitters stacked instead of selecting the maximum radius");
                return;
            }
            helper.succeed();
        } finally {
            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    previous,
                    null);
        }
    }

    @GameTest(maxTicks = 30)
    public void centeredModeStopsAtTheFiveBlockGlobalCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        NexusTeleportArrayExpansionRules.ExpansionMode previous =
                level.getGameRules().get(NexusTeleportArrayExpansionRules.EXPANSION_MODE);
        try {
            level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
            level.setBlockAndUpdate(origin.east(), Blocks.NETHERITE_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(origin.east(3), Blocks.NETHERITE_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(origin.east(5), Blocks.NETHERITE_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(origin.east(6), Blocks.GOLD_BLOCK.defaultBlockState());

            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    NexusTeleportArrayExpansionRules.ExpansionMode.CENTERED,
                    null);
            TeleportArrayMaterialScan.Result centered = scan(level, origin);
            if (centered.maximumReachedDistance() != TeleportArrayMaterialScan.MAX_DISTANCE
                    || !centered.expansionEmitterPositions().contains(origin.east(5))
                    || centered.visitedPositions().contains(origin.east(6))
                    || centered.structuralPositions().contains(origin.east(6))
                    || !containsLoadedCube(level, centered, origin, TeleportArrayMaterialScan.MAX_DISTANCE)) {
                helper.fail("Centered expansion exceeded or failed to fill the five-block cap");
                return;
            }
            helper.succeed();
        } finally {
            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    previous,
                    null);
        }
    }

    @GameTest(maxTicks = 30)
    public void centeredScanSkipsAnUnloadedBoundaryWithoutForceLoadingIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos testChunk = ChunkPos.containing(helper.absolutePos(LODESTONE));
        LoadedChunkBoundary boundary = findLoadedChunkBoundary(level, testChunk.x(), testChunk.z());
        if (boundary == null) {
            helper.fail("Could not establish a loaded-source/unloaded-neighbor scan fixture");
            return;
        }

        BlockPos origin = boundary.origin();
        BlockPos unloaded = origin.relative(boundary.direction());
        NexusTeleportArrayExpansionRules.ExpansionMode previousMode =
                level.getGameRules().get(NexusTeleportArrayExpansionRules.EXPANSION_MODE);
        try {
            if (level.isLoaded(unloaded)) {
                helper.fail("Unloaded scan-boundary fixture became loaded before evaluation");
                return;
            }

            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    NexusTeleportArrayExpansionRules.ExpansionMode.CENTERED,
                    null);
            TeleportArrayMaterialScan.Result centered = scan(level, origin);
            if (centered.visitedPositions().contains(unloaded) || level.isLoaded(unloaded)) {
                helper.fail("Centered scan visited or force-loaded an unavailable neighboring chunk");
                return;
            }
            helper.succeed();
        } finally {
            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    previousMode,
                    null);
        }
    }

    @GameTest(maxTicks = 30)
    public void visualizationSetsExactlyMatchTheProductionScanInBothModes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        NexusTeleportArrayExpansionRules.ExpansionMode previous =
                level.getGameRules().get(NexusTeleportArrayExpansionRules.EXPANSION_MODE);
        try {
            level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
            level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(origin.east(2), Blocks.TUFF.defaultBlockState());
            level.setBlockAndUpdate(origin.west(2), Blocks.OBSIDIAN.defaultBlockState());
            level.setBlockAndUpdate(origin.north(), Blocks.DIRT.defaultBlockState());

            for (NexusTeleportArrayExpansionRules.ExpansionMode mode
                    : NexusTeleportArrayExpansionRules.ExpansionMode.values()) {
                level.getGameRules().set(
                        NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                        mode,
                        null);
                TeleportArrayMaterialScan.Result scan = scan(level, origin);
                List<TeleportArrayVisualizationPayload.RelativeBlock> actual =
                        NexusArrayVisualizationAuthority.relativeBlocks(origin, scan, true, true);
                Set<TeleportArrayVisualizationPayload.RelativeBlock> expected =
                        expectedRelativeBlocks(origin, scan);
                if (actual.size() != expected.size() || !Set.copyOf(actual).equals(expected)) {
                    helper.fail(mode + " visualization blocks diverged from the production scan");
                    return;
                }
                boolean expectedWestObsidian = mode == NexusTeleportArrayExpansionRules.ExpansionMode.CENTERED;
                if (scan.structuralPositions().contains(origin.west(2)) != expectedWestObsidian) {
                    helper.fail(mode + " did not preserve its expected local/centered scan distinction");
                    return;
                }
            }
            helper.succeed();
        } finally {
            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    previous,
                    null);
        }
    }

    @GameTest(maxTicks = 30)
    public void gameruleChangeRefreshesLoadedSnapshotsAndWornTargetsUseTheSameMode(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(LODESTONE);
        BlockPos centeredWorn = origin.west(2);
        BlockPos remoteUnloaded = origin.offset(32_000, 0, 32_000);
        UUID unitId = UUID.randomUUID();
        UUID remoteUnitId = UUID.randomUUID();
        NexusSpaceUnitSavedData units = units(level);
        NexusTeleportArrayExpansionRules.ExpansionMode previous =
                level.getGameRules().get(NexusTeleportArrayExpansionRules.EXPANSION_MODE);
        try {
            level.setBlockAndUpdate(origin, Blocks.LODESTONE.defaultBlockState());
            level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(centeredWorn, Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
            units.put(lodestone(
                    unitId,
                    level,
                    origin,
                    UUID.randomUUID(),
                    SpaceUnitVisibility.PRIVATE));

            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    NexusTeleportArrayExpansionRules.ExpansionMode.LOCAL,
                    level.getServer());
            NexusSpaceUnitRecord local = units.rescanLodestone(level, unitId)
                    .orElseThrow(() -> helper.assertionException("Could not create local snapshot fixture"));
            if (local.structure().rawStructuralBlocks() != 1
                    || local.structure().teleportArrayExpansionModeCode() != 0
                    || units.wornLodestoneStructureBlocks(level, unitId).contains(centeredWorn)) {
                helper.fail("Local snapshot or worn-target scan included a centered-only block");
                return;
            }
            if (level.isLoaded(remoteUnloaded)) {
                helper.fail("Remote no-force-load fixture unexpectedly started loaded");
                return;
            }
            units.put(lodestone(
                    remoteUnitId,
                    level,
                    remoteUnloaded,
                    UUID.randomUUID(),
                    SpaceUnitVisibility.PRIVATE));

            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    NexusTeleportArrayExpansionRules.ExpansionMode.CENTERED,
                    level.getServer());
            NexusSpaceUnitRecord centered = units.get(unitId)
                    .orElseThrow(() -> helper.assertionException("Gamerule callback lost the lodestone fixture"));
            if (centered.structure().rawStructuralBlocks() != 2
                    || centered.structure().teleportArrayExpansionModeCode() != 1
                    || !units.wornLodestoneStructureBlocks(level, unitId).contains(centeredWorn)
                    || level.isLoaded(remoteUnloaded)
                    || !SpaceStructureSnapshot.EMPTY.equals(units.get(remoteUnitId)
                    .orElseThrow(() -> helper.assertionException("Gamerule callback lost remote fixture"))
                    .structure())) {
                helper.fail("Centered gamerule did not refresh the loaded snapshot and worn-target set");
                return;
            }
            helper.succeed();
        } finally {
            level.getGameRules().set(
                    NexusTeleportArrayExpansionRules.EXPANSION_MODE,
                    previous,
                    level.getServer());
        }
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
    public void tuffAndObsidianProfilesKeepApprovedIdentity(GameTestHelper helper) {
        TeleportArrayMaterialProfile tuff = TeleportArrayMaterialProfiles.profileFor(Blocks.TUFF.defaultBlockState());
        TeleportArrayMaterialProfile obsidian = TeleportArrayMaterialProfiles.profileFor(Blocks.OBSIDIAN.defaultBlockState());
        TeleportArrayMaterialProfile crying = TeleportArrayMaterialProfiles.profileFor(Blocks.CRYING_OBSIDIAN.defaultBlockState());

        if (!NexusSpaceUnitSavedData.isStructureBlock(Blocks.TUFF.defaultBlockState())
                || !NexusSpaceUnitSavedData.isStructureBlock(Blocks.OBSIDIAN.defaultBlockState())
                || !NexusSpaceUnitSavedData.isStructureBlock(Blocks.CRYING_OBSIDIAN.defaultBlockState())
                || !"tuff".equals(tuff.family())
                || tuff.attributes().structureCapacity() != 1
                || tuff.attributes().stability() != -1
                || tuff.attributes().maintenanceEfficiency() != 1
                || tuff.attributes().affinityFor("minecraft:overworld") != 1
                || !"obsidian".equals(obsidian.family())
                || obsidian.attributes().structureCapacity() != 2
                || obsidian.attributes().stability() != 3
                || obsidian.attributes().wearResistance() != 3
                || obsidian.attributes().interferenceResistance() != 3
                || obsidian.attributes().maintenanceEfficiency() != -3
                || obsidian.attributes().phaseSpeed() != -3
                || !"crying_obsidian".equals(crying.family())
                || crying.attributes().structureCapacity() != 2
                || crying.attributes().arrivalAccuracy() != 3
                || crying.attributes().targetLock() != 3
                || crying.attributes().arrivalSafety() != -2
                || crying.attributes().interferenceResistance() != -2
                || crying.attributes().affinityFor("minecraft:the_nether") != 3
                || tuff.attributes().localScanExpansionRadius() != 0
                || obsidian.attributes().localScanExpansionRadius() != 0
                || crying.attributes().localScanExpansionRadius() != 0) {
            helper.fail("Tuff/obsidian built-in profile identity or structure-tag membership changed");
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

    private static NexusSpaceDiscoverySavedData discovery(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
    }

    private static ServerPlayer sessionViewer(
            GameTestHelper helper,
            BlockPos sourcePos,
            UUID sourceId,
            NexusSpaceDiscoverySavedData discovery) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(sourcePos.getX() + 0.5D, sourcePos.getY() + 0.5D, sourcePos.getZ() + 0.5D);
        player.setNoGravity(true);
        discovery.markDiscovered(player.getUUID(), sourceId);
        enable(player, sourceId);
        return player;
    }

    private static void enable(ServerPlayer player, UUID sourceId) {
        bindCompass(player, sourceId);
        establishContext(player, sourceId);
        if (refresh(player, sourceId, true, false).isEmpty()) {
            throw new IllegalStateException("Could not establish visualization session fixture");
        }
    }

    private static void establishContext(ServerPlayer player, UUID sourceId) {
        if (NexusSpaceUnitAuthority.establishInterfaceContext(
                player,
                InteractionHand.MAIN_HAND,
                SpaceUnitType.LODESTONE.id(),
                sourceId
        ).isEmpty()) {
            throw new IllegalStateException("Could not establish held-interface fixture context");
        }
    }

    private static void switchToBuildingMaterial(ServerPlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TUFF));
        NexusSpaceUnitAuthority.clearInterfaceContext(player.getUUID());
    }

    private static java.util.Optional<TeleportArrayVisualizationPayload> refresh(
            ServerPlayer player,
            UUID sourceId,
            boolean showArray,
            boolean showBuildSites) {
        return NexusArrayVisualizationAuthority.createPayload(
                player,
                new dev.totem.nexus.network.RequestTeleportArrayVisualizationPayload(
                        SpaceUnitType.LODESTONE.id(), sourceId, showArray, showBuildSites));
    }

    private static void cleanup(ServerPlayer player) {
        NexusArrayVisualizationAuthority.disconnect(player.getUUID());
        NexusSpaceUnitAuthority.clearInterfaceContext(player.getUUID());
        player.discard();
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
                        true,
                        true
                )
        );
    }

    private static TeleportArrayMaterialScan.Result scan(ServerLevel level, BlockPos origin) {
        return TeleportArrayMaterialScan.scan(
                level,
                origin,
                NexusSpaceUnitSavedData::isStructureBlock,
                NexusSpaceUnitSavedData::isWornStructureBlock
        );
    }

    private static boolean containsLoadedCube(
            ServerLevel level,
            TeleportArrayMaterialScan.Result scan,
            BlockPos origin,
            int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos position = origin.offset(dx, dy, dz);
                    if (level.isLoaded(position) && !scan.visitedPositions().contains(position)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static LoadedChunkBoundary findLoadedChunkBoundary(
            ServerLevel level,
            int centerChunkX,
            int centerChunkZ) {
        for (int radius = 0; radius <= 8; radius++) {
            for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
                for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                    ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                    int middleX = chunk.getMiddleBlockX();
                    int middleZ = chunk.getMiddleBlockZ();
                    if (!level.isLoaded(new BlockPos(middleX, 80, middleZ))) {
                        continue;
                    }
                    LoadedChunkBoundary east = boundaryIfUnloaded(
                            level,
                            new BlockPos(chunk.getMaxBlockX(), 80, middleZ),
                            Direction.EAST);
                    if (east != null) {
                        return east;
                    }
                    LoadedChunkBoundary west = boundaryIfUnloaded(
                            level,
                            new BlockPos(chunk.getMinBlockX(), 80, middleZ),
                            Direction.WEST);
                    if (west != null) {
                        return west;
                    }
                    LoadedChunkBoundary south = boundaryIfUnloaded(
                            level,
                            new BlockPos(middleX, 80, chunk.getMaxBlockZ()),
                            Direction.SOUTH);
                    if (south != null) {
                        return south;
                    }
                    LoadedChunkBoundary north = boundaryIfUnloaded(
                            level,
                            new BlockPos(middleX, 80, chunk.getMinBlockZ()),
                            Direction.NORTH);
                    if (north != null) {
                        return north;
                    }
                }
            }
        }
        return null;
    }

    private static LoadedChunkBoundary boundaryIfUnloaded(
            ServerLevel level,
            BlockPos origin,
            Direction direction) {
        BlockPos boundary = origin.relative(direction);
        if (level.isLoaded(origin) && !level.isLoaded(boundary)) {
            return new LoadedChunkBoundary(origin, direction);
        }
        return null;
    }

    private static Set<TeleportArrayVisualizationPayload.RelativeBlock> expectedRelativeBlocks(
            BlockPos origin,
            TeleportArrayMaterialScan.Result scan) {
        Set<TeleportArrayVisualizationPayload.RelativeBlock> expected = new LinkedHashSet<>();
        for (BlockPos position : scan.structuralPositions()) {
            expected.add(relativeBlock(
                    origin,
                    position,
                    scan.expansionEmitterPositions().contains(position),
                    false));
        }
        for (BlockPos position : scan.buildablePositions()) {
            expected.add(relativeBlock(origin, position, false, true));
        }
        return Set.copyOf(expected);
    }

    private static TeleportArrayVisualizationPayload.RelativeBlock relativeBlock(
            BlockPos origin,
            BlockPos position,
            boolean expansionEmitter,
            boolean buildable) {
        return new TeleportArrayVisualizationPayload.RelativeBlock(
                position.getX() - origin.getX(),
                position.getY() - origin.getY(),
                position.getZ() - origin.getZ(),
                expansionEmitter,
                buildable);
    }

    private record LoadedChunkBoundary(BlockPos origin, Direction direction) {
    }

    private static void bindCompass(ServerPlayer player, UUID sourceId) {
        ItemStack compass = new ItemStack(Items.COMPASS);
        NexusInterfaceBinding.writeIdentity(compass, sourceId);
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);
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
