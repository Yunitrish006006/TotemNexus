package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Bounded, placement-driven material graph traversal around a lodestone. */
final class TeleportArrayMaterialScan {
    /** A larger builder-facing envelope, still bounded to 11×11×11 minus the lodestone. */
    static final int MAX_DISTANCE = 5;
    static final int INITIAL_RADIUS = 1;

    private TeleportArrayMaterialScan() {
    }

    static Result scan(
            ServerLevel level,
            BlockPos lodestonePos,
            Predicate<BlockState> legacyStructureMaterial,
            Predicate<BlockState> wornStructureMaterial) {
        Set<BlockPos> visited = new LinkedHashSet<>();
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        for (int dx = -INITIAL_RADIUS; dx <= INITIAL_RADIUS; dx++) {
            for (int dy = -INITIAL_RADIUS; dy <= INITIAL_RADIUS; dy++) {
                for (int dz = -INITIAL_RADIUS; dz <= INITIAL_RADIUS; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        pending.add(lodestonePos.offset(dx, dy, dz).immutable());
                    }
                }
            }
        }

        int rawBlocks = 0;
        int wornBlocks = 0;
        int maximumReachedDistance = 0;
        TeleportArrayMaterialAttributes totals = TeleportArrayMaterialAttributes.ZERO;
        Map<String, Integer> families = new LinkedHashMap<>();
        Map<String, TeleportArrayMaterialAttributes> familyContributions = new LinkedHashMap<>();
        Map<String, Integer> localExpansionPathCounts = new LinkedHashMap<>();
        Set<BlockPos> structuralPositions = new LinkedHashSet<>();

        while (!pending.isEmpty()) {
            BlockPos current = pending.removeFirst();
            if (!withinBounds(lodestonePos, current) || !visited.add(current) || !level.isLoaded(current)) {
                continue;
            }
            maximumReachedDistance = Math.max(maximumReachedDistance, chebyshevDistance(lodestonePos, current));
            BlockState state = level.getBlockState(current);
            TeleportArrayMaterialProfile profile = TeleportArrayMaterialProfiles.profileFor(state);
            boolean structural = profile.validStructureMaterial() || legacyStructureMaterial.test(state);
            if (structural) {
                if (!profile.validStructureMaterial()) {
                    TeleportArrayMaterialProfiles.logLegacyFallback(state);
                }
                rawBlocks++;
                structuralPositions.add(current);
                TeleportArrayMaterialAttributes contribution = profile.validStructureMaterial()
                        ? profile.attributes()
                        : new TeleportArrayMaterialAttributes(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of());
                totals = totals.plus(contribution);
                if (wornStructureMaterial.test(state)) {
                    wornBlocks++;
                }
                if (profile.validStructureMaterial()) {
                    families.merge(profile.family(), 1, Integer::sum);
                    familyContributions.merge(profile.family(), contribution, TeleportArrayMaterialAttributes::plus);
                } else {
                    families.merge("legacy", 1, Integer::sum);
                    familyContributions.merge("legacy", contribution, TeleportArrayMaterialAttributes::plus);
                }
            }

            int radius = profile.validStructureMaterial() ? profile.attributes().localScanExpansionRadius() : 0;
            if (radius == 0) {
                continue;
            }
            localExpansionPathCounts.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1, Integer::sum);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx != 0 || dy != 0 || dz != 0) {
                            BlockPos next = current.offset(dx, dy, dz);
                            if (withinBounds(lodestonePos, next) && !visited.contains(next)) {
                                pending.addLast(next.immutable());
                            }
                        }
                    }
                }
            }
        }

        int symmetricPairs = 0;
        int checkedPairs = 0;
        for (BlockPos position : visited) {
            int dx = position.getX() - lodestonePos.getX();
            int dz = position.getZ() - lodestonePos.getZ();
            if (dx < 0 || (dx == 0 && dz <= 0)) {
                continue;
            }
            BlockPos mirror = lodestonePos.offset(-dx, position.getY() - lodestonePos.getY(), -dz);
            if (!visited.contains(mirror) || !level.isLoaded(mirror)) {
                continue;
            }
            checkedPairs++;
            if (level.getBlockState(position).is(level.getBlockState(mirror).getBlock())) {
                symmetricPairs++;
            }
        }
        return new Result(rawBlocks, wornBlocks, symmetricPairs, checkedPairs, maximumReachedDistance, totals, families,
                familyContributions,
                localExpansionPathCounts,
                Set.copyOf(structuralPositions), Set.copyOf(visited));
    }

    private static boolean withinBounds(BlockPos origin, BlockPos position) {
        return chebyshevDistance(origin, position) <= MAX_DISTANCE && !origin.equals(position);
    }

    private static int chebyshevDistance(BlockPos origin, BlockPos position) {
        return Math.max(Math.abs(position.getX() - origin.getX()),
                Math.max(Math.abs(position.getY() - origin.getY()), Math.abs(position.getZ() - origin.getZ())));
    }

    record Result(
            int rawStructuralBlocks,
            int wornBlocks,
            int symmetricPairs,
            int checkedPairs,
            int maximumReachedDistance,
            TeleportArrayMaterialAttributes totals,
            Map<String, Integer> familyCounts,
            Map<String, TeleportArrayMaterialAttributes> familyContributions,
            Map<String, Integer> localExpansionPathCounts,
            Set<BlockPos> structuralPositions,
            Set<BlockPos> visitedPositions) {
        Result {
            familyCounts = Map.copyOf(familyCounts);
            familyContributions = Map.copyOf(familyContributions);
            localExpansionPathCounts = Map.copyOf(localExpansionPathCounts);
            structuralPositions = Set.copyOf(structuralPositions);
            visitedPositions = Set.copyOf(visitedPositions);
        }
    }
}
