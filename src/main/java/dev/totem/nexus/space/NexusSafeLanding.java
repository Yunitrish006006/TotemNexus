package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Server-side safe landing selection for a resolved teleport target. */
public final class NexusSafeLanding {
    private NexusSafeLanding() { }
    public static Optional<BlockPos> find(ServerLevel level, BlockPos anchor, boolean lodestoneTarget, int horizontalDeviation) {
        BlockPos start = lodestoneTarget ? anchor.above() : anchor;
        int radius = Math.max(0, Math.min(96, horizontalDeviation));
        for (int distance = 0; distance <= radius; distance++) for (int x = -distance; x <= distance; x++) for (int z = -distance; z <= distance; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != distance) continue;
            Optional<BlockPos> candidate = column(level, start.offset(x, 0, z));
            if (candidate.isPresent()) return candidate;
        }
        return Optional.empty();
    }
    private static Optional<BlockPos> column(ServerLevel level, BlockPos anchor) {
        for (int vertical = 0; vertical <= 4; vertical++) {
            BlockPos above = anchor.above(vertical); if (safe(level, above)) return Optional.of(above.immutable());
            if (vertical > 0) { BlockPos below = anchor.below(vertical); if (safe(level, below)) return Optional.of(below.immutable()); }
        }
        return Optional.empty();
    }
    private static boolean safe(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinY() || feet.getY() >= level.getMaxY() || !level.getWorldBorder().isWithinBounds(feet.getX() + .5D, feet.getZ() + .5D)) return false;
        BlockState floor = level.getBlockState(feet.below()), body = level.getBlockState(feet), head = level.getBlockState(feet.above());
        return !floor.isAir() && floor.getFluidState().isEmpty() && floor.blocksMotion() && open(body) && open(head)
                && !unsafe(floor) && !unsafe(body) && !unsafe(head);
    }
    private static boolean open(BlockState state) { return state.isAir() && state.getFluidState().isEmpty(); }
    private static boolean unsafe(BlockState state) { return state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
            || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.POWDER_SNOW); }
}
