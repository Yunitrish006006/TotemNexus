package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayDeque;
import java.util.HashSet;

/** A small loaded-only walk search, including corners, rather than proximity across walls or floors. */
final class NexusRecoveryLanding {
    static final int RADIUS = 6;
    private static final int MAX_WALK = 10;
    private static final int MAX_VISITED = 256;
    private NexusRecoveryLanding() { }

    static boolean near(BlockPos feet, BlockPos backpack) {
        long dx = (long) feet.getX() - backpack.getX(), dz = (long) feet.getZ() - backpack.getZ();
        return dx * dx + dz * dz <= RADIUS * RADIUS && Math.abs(feet.getY() - backpack.getY()) <= 3;
    }

    private static boolean clearPickup(ServerLevel level, BlockPos feet, BlockPos backpack) {
        if (!level.isLoaded(backpack)) return false;
        var from = net.minecraft.world.phys.Vec3.atCenterOf(feet);
        var to = net.minecraft.world.phys.Vec3.atCenterOf(backpack);
        return level.clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()))
                .getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    static boolean reachable(ServerLevel level, BlockPos feet, BlockPos backpack) {
        if (!near(feet, backpack) || !NexusSafeLanding.isSafeLoaded(level, feet)) return false;
        record Step(BlockPos pos, int distance) { }
        var queue = new ArrayDeque<Step>();
        var visited = new HashSet<BlockPos>();
        queue.add(new Step(feet, 0)); visited.add(feet);
        while (!queue.isEmpty() && visited.size() <= MAX_VISITED) {
            Step step = queue.removeFirst();
            long dx = step.pos().getX() - backpack.getX(), dz = step.pos().getZ() - backpack.getZ();
            if (dx * dx + dz * dz <= 1 && Math.abs(step.pos().getY() - backpack.getY()) <= 1
                    && clearPickup(level, step.pos(), backpack)) return true;
            if (step.distance() >= MAX_WALK) continue;
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos adjacent = step.pos().relative(direction);
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos next = adjacent.offset(0, dy, 0);
                    if (!near(next, backpack) || visited.contains(next)
                            || !NexusSafeLanding.isSafeLoaded(level, next)) continue;
                    // A step up needs clearance above the departure head; a step down above the arrival head.
                    BlockPos clearance = dy > 0 ? step.pos().above(2) : dy < 0 ? next.above(2) : null;
                    if (clearance != null) {
                        var chunk = level.getChunkSource().getChunkNow(clearance.getX() >> 4, clearance.getZ() >> 4);
                        if (chunk == null || !chunk.getBlockState(clearance).isAir()) continue;
                    }
                    visited.add(next); queue.addLast(new Step(next, step.distance() + 1));
                    break;
                }
            }
        }
        return false;
    }
}
