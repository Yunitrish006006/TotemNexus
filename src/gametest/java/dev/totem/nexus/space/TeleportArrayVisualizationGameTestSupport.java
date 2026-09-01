package dev.totem.nexus.space;

import dev.totem.nexus.network.TeleportArrayVisualizationPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Server-thread bridge that keeps Client GameTest visuals tied to the production material scan. */
public final class TeleportArrayVisualizationGameTestSupport {
    private TeleportArrayVisualizationGameTestSupport() {
    }

    public static TeleportArrayVisualizationPayload snapshot(
            ServerLevel level,
            UUID sourceId,
            BlockPos origin,
            boolean showArray,
            boolean showBuildSites) {
        TeleportArrayMaterialScan.Result scan = TeleportArrayMaterialScan.scan(
                level,
                origin,
                NexusSpaceUnitSavedData::isStructureBlock,
                NexusSpaceUnitSavedData::isWornStructureBlock
        );
        return new TeleportArrayVisualizationPayload(
                sourceId,
                level.dimension().identifier().toString(),
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                showArray,
                showBuildSites,
                NexusArrayVisualizationAuthority.relativeBlocks(origin, scan, showArray, showBuildSites)
        );
    }

    public static void assertSceneSemantics(
            ServerLevel level,
            BlockPos origin,
            TeleportArrayVisualizationPayload payload,
            Map<BlockPos, Block> expectedEmitters,
            Map<BlockPos, Block> expectedOrdinaryMaterials) {
        TeleportArrayMaterialScan.Result scan = TeleportArrayMaterialScan.scan(
                level,
                origin,
                NexusSpaceUnitSavedData::isStructureBlock,
                NexusSpaceUnitSavedData::isWornStructureBlock
        );
        if (!payload.blocks().equals(NexusArrayVisualizationAuthority.relativeBlocks(
                origin, scan, payload.showArray(), payload.showBuildSites()))) {
            throw new AssertionError("Complex screenshot payload diverged from the production material scan");
        }
        Set<BlockPos> actualEmitterOffsets = NexusArrayVisualizationAuthority.relativeBlocks(
                        origin, scan, true, false).stream()
                .filter(TeleportArrayVisualizationPayload.RelativeBlock::expansionEmitter)
                .map(block -> new BlockPos(block.dx(), block.dy(), block.dz()))
                .collect(Collectors.toSet());
        if (!actualEmitterOffsets.equals(expectedEmitters.keySet())) {
            throw new AssertionError("Complex array emitter offsets diverged from the real topology: "
                    + actualEmitterOffsets + " != " + expectedEmitters.keySet());
        }
        expectedEmitters.forEach((offset, expectedBlock) -> {
            Block actualBlock = level.getBlockState(origin.offset(offset)).getBlock();
            int radius = TeleportArrayMaterialProfiles.profileFor(actualBlock.defaultBlockState())
                    .attributes().localScanExpansionRadius();
            if (actualBlock != expectedBlock || radius <= 0) {
                throw new AssertionError("Gold outline mapped to a non-emitter at " + offset
                        + ": expected " + expectedBlock + ", found " + actualBlock + " radius=" + radius);
            }
        });
        expectedOrdinaryMaterials.forEach((offset, expectedBlock) -> {
            Block actualBlock = level.getBlockState(origin.offset(offset)).getBlock();
            boolean markedEmitter = actualEmitterOffsets.contains(offset);
            int radius = TeleportArrayMaterialProfiles.profileFor(actualBlock.defaultBlockState())
                    .attributes().localScanExpansionRadius();
            if (actualBlock != expectedBlock || radius > 0 || markedEmitter) {
                throw new AssertionError("Ordinary cyan outline was not backed by a non-emitter material at "
                        + offset + ": expected " + expectedBlock + ", found " + actualBlock
                        + " radius=" + radius + " markedEmitter=" + markedEmitter);
            }
        });

        for (TeleportArrayVisualizationPayload.RelativeBlock block : payload.blocks()) {
            if (block.buildable() && !level.getBlockState(origin.offset(block.dx(), block.dy(), block.dz())).canBeReplaced()) {
                throw new AssertionError("Build-site payload contains a non-replaceable scene position: " + block);
            }
        }
    }
}
