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
        return switch (NexusTeleportArrayExpansionRules.mode(level)) {
            case LOCAL -> scanLocal(level, lodestonePos, legacyStructureMaterial, wornStructureMaterial);
            case CENTERED -> scanCentered(level, lodestonePos, legacyStructureMaterial, wornStructureMaterial);
        };
    }

    private static Result scanLocal(
            ServerLevel level,
            BlockPos lodestonePos,
            Predicate<BlockState> legacyStructureMaterial,
            Predicate<BlockState> wornStructureMaterial) {
        ScanAccumulator scan = new ScanAccumulator(
                level, lodestonePos, legacyStructureMaterial, wornStructureMaterial);
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

        while (!pending.isEmpty()) {
            BlockPos current = pending.removeFirst();
            ScannedPosition scanned = scan.visit(current);
            if (!scanned.loaded()) {
                continue;
            }
            int radius = scanned.expansionRadius();
            if (radius == 0) {
                continue;
            }
            scan.recordExpansionPath(scanned.state());
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx != 0 || dy != 0 || dz != 0) {
                            BlockPos next = current.offset(dx, dy, dz);
                            if (withinBounds(lodestonePos, next) && !scan.seen(next)) {
                                pending.addLast(next.immutable());
                            }
                        }
                    }
                }
            }
        }

        return scan.finish();
    }

    private static Result scanCentered(
            ServerLevel level,
            BlockPos lodestonePos,
            Predicate<BlockState> legacyStructureMaterial,
            Predicate<BlockState> wornStructureMaterial) {
        ScanAccumulator scan = new ScanAccumulator(
                level, lodestonePos, legacyStructureMaterial, wornStructureMaterial);

        int reachedRadius = INITIAL_RADIUS;
        int scannedRadius;
        do {
            scannedRadius = reachedRadius;
            int nextRadius = scannedRadius;
            for (int dx = -scannedRadius; dx <= scannedRadius; dx++) {
                for (int dy = -scannedRadius; dy <= scannedRadius; dy++) {
                    for (int dz = -scannedRadius; dz <= scannedRadius; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos current = lodestonePos.offset(dx, dy, dz).immutable();
                        ScannedPosition scanned = scan.visit(current);
                        if (!scanned.loaded()) {
                            continue;
                        }
                        if (!scanned.structural() || scanned.expansionRadius() <= 0) {
                            continue;
                        }
                        scan.recordExpansionPath(scanned.state());
                        nextRadius = Math.max(nextRadius, Math.min(
                                MAX_DISTANCE, scanned.distanceFromLodestone() + scanned.expansionRadius()));
                    }
                }
            }
            reachedRadius = nextRadius;
        } while (reachedRadius > scannedRadius);

        return scan.finish();
    }

    private record ScannedPosition(
            boolean loaded,
            int distanceFromLodestone,
            BlockState state,
            boolean structural,
            int expansionRadius) {
        private static final ScannedPosition SKIPPED =
                new ScannedPosition(false, 0, null, false, 0);
    }

    private static final class ScanAccumulator {
        private final ServerLevel level;
        private final BlockPos lodestonePos;
        private final Predicate<BlockState> legacyStructureMaterial;
        private final Predicate<BlockState> wornStructureMaterial;
        private final Set<BlockPos> seen = new LinkedHashSet<>();
        private final Set<BlockPos> visited = new LinkedHashSet<>();
        private final Map<String, Integer> families = new LinkedHashMap<>();
        private final Map<String, TeleportArrayMaterialAttributes> familyContributions = new LinkedHashMap<>();
        private final Map<String, Integer> localExpansionPathCounts = new LinkedHashMap<>();
        private final Set<BlockPos> structuralPositions = new LinkedHashSet<>();
        private final Set<BlockPos> expansionEmitterPositions = new LinkedHashSet<>();
        private final Set<BlockPos> buildablePositions = new LinkedHashSet<>();
        private int rawBlocks;
        private int wornBlocks;
        private int maximumReachedDistance;
        private TeleportArrayMaterialAttributes totals = TeleportArrayMaterialAttributes.ZERO;

        private ScanAccumulator(
                ServerLevel level,
                BlockPos lodestonePos,
                Predicate<BlockState> legacyStructureMaterial,
                Predicate<BlockState> wornStructureMaterial) {
            this.level = level;
            this.lodestonePos = lodestonePos;
            this.legacyStructureMaterial = legacyStructureMaterial;
            this.wornStructureMaterial = wornStructureMaterial;
        }

        private ScannedPosition visit(BlockPos current) {
            if (!withinBounds(lodestonePos, current) || !seen.add(current) || !level.isLoaded(current)) {
                return ScannedPosition.SKIPPED;
            }
            visited.add(current);
            int distance = chebyshevDistance(lodestonePos, current);
            maximumReachedDistance = Math.max(maximumReachedDistance, distance);
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
                        : new TeleportArrayMaterialAttributes(
                        1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of());
                totals = totals.plus(contribution);
                if (wornStructureMaterial.test(state)) {
                    wornBlocks++;
                }
                if (profile.validStructureMaterial()) {
                    families.merge(profile.family(), 1, Integer::sum);
                    familyContributions.merge(
                            profile.family(), contribution, TeleportArrayMaterialAttributes::plus);
                } else {
                    families.merge("legacy", 1, Integer::sum);
                    familyContributions.merge("legacy", contribution, TeleportArrayMaterialAttributes::plus);
                }
            } else if (state.canBeReplaced()) {
                buildablePositions.add(current);
            }

            int radius = profile.validStructureMaterial()
                    ? profile.attributes().localScanExpansionRadius()
                    : 0;
            if (structural && radius > 0) {
                expansionEmitterPositions.add(current);
            }
            return new ScannedPosition(true, distance, state, structural, radius);
        }

        private boolean seen(BlockPos position) {
            return seen.contains(position);
        }

        private void recordExpansionPath(BlockState state) {
            localExpansionPathCounts.merge(
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), 1, Integer::sum);
        }

        private Result finish() {
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
            return new Result(
                    rawBlocks, wornBlocks, symmetricPairs, checkedPairs, maximumReachedDistance, totals, families,
                    familyContributions, localExpansionPathCounts,
                    structuralPositions, expansionEmitterPositions, buildablePositions, visited);
        }
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
            Set<BlockPos> expansionEmitterPositions,
            Set<BlockPos> buildablePositions,
            Set<BlockPos> visitedPositions) {
        Result {
            familyCounts = Map.copyOf(familyCounts);
            familyContributions = Map.copyOf(familyContributions);
            localExpansionPathCounts = Map.copyOf(localExpansionPathCounts);
            structuralPositions = Set.copyOf(structuralPositions);
            expansionEmitterPositions = Set.copyOf(expansionEmitterPositions);
            buildablePositions = Set.copyOf(buildablePositions);
            visitedPositions = Set.copyOf(visitedPositions);
        }
    }
}
