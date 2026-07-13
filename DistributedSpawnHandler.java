package com.adaptor.deadrecall.space;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class DistributedSpawnHandler {
    public static final GameRule<Boolean> DISTRIBUTED_SPAWNING =
            GameRuleBuilder.forBoolean(false)
                    .category(GameRuleCategory.SPAWNING)
                    .buildAndRegister(Identifier.fromNamespaceAndPath("deadrecall", "dead_recall_distributed_spawning"));

    private static final int CANDIDATE_SAMPLES = 56;
    private static final int MIN_DISTANCE_BETWEEN_SPAWNS = 1_200;
    private static final int NEAR_DENSITY_RADIUS = 512;
    private static final int MID_DENSITY_RADIUS = 2_048;
    private static final int FAR_DENSITY_RADIUS = 8_192;
    private static final int MIN_RADIUS_FROM_WORLD_SPAWN = 900;
    private static final int MAX_RADIUS_FROM_WORLD_SPAWN = 6_000;
    private static final int PREFERRED_RADIUS_FROM_WORLD_SPAWN = 2_800;
    private static final int STORED_POINT_REPAIR_RADIUS = 32;
    private static final int WORLD_SPAWN_FALLBACK_RADIUS = 96;

    private DistributedSpawnHandler() {
    }

    public static void register() {
        // Static initialization registers the game rule.
    }

    public static Optional<TeleportTransition> findRespawnTransition(
            ServerPlayer player,
            TeleportTransition.PostTeleportTransition postTeleportTransition) {
        MinecraftServer server = player.level().getServer();
        ServerLevel overworld = server.overworld();
        if (!(Boolean) overworld.getGameRules().get(DISTRIBUTED_SPAWNING)) {
            return Optional.empty();
        }
        if (player.getRespawnConfig() != null) {
            return Optional.empty();
        }

        DeadRecallDistributedSpawnSavedData data = data(server);
        DeadRecallDistributedSpawnSavedData.PlayerSpawn spawn = data.get(player.getUUID())
                .orElseGet(() -> createPersonalSpawn(player, overworld, data).orElse(null));
        if (spawn == null) {
            return Optional.empty();
        }

        ServerLevel spawnLevel = server.getLevel(spawn.dimension());
        if (spawnLevel == null) {
            data.remove(player.getUUID());
            return Optional.empty();
        }

        Optional<BlockPos> safePos = safeSpawnNear(spawnLevel, spawn.pos(), STORED_POINT_REPAIR_RADIUS);
        if (safePos.isEmpty()) {
            data.remove(player.getUUID());
            spawn = createPersonalSpawn(player, overworld, data).orElse(null);
            if (spawn == null) {
                return Optional.empty();
            }
            spawnLevel = server.getLevel(spawn.dimension());
            if (spawnLevel == null) {
                return Optional.empty();
            }
            safePos = safeSpawnNear(spawnLevel, spawn.pos(), STORED_POINT_REPAIR_RADIUS);
        }
        if (safePos.isEmpty()) {
            return Optional.empty();
        }

        BlockPos pos = safePos.get();
        if (!pos.equals(spawn.pos())) {
            spawn = data.put(player.getUUID(), spawnLevel.dimension(), pos, spawn.yaw(), spawnLevel.getGameTime());
        }

        return Optional.of(new TeleportTransition(
                spawnLevel,
                Vec3.atBottomCenterOf(pos),
                Vec3.ZERO,
                spawn.yaw(),
                0.0F,
                postTeleportTransition
        ));
    }

    private static Optional<DeadRecallDistributedSpawnSavedData.PlayerSpawn> createPersonalSpawn(
            ServerPlayer player,
            ServerLevel level,
            DeadRecallDistributedSpawnSavedData data) {
        RandomSource random = level.getRandom();
        BlockPos worldSpawn = level.getRespawnData().pos();
        Optional<ScoredSpawn> best = Optional.empty();

        for (int i = 0; i < CANDIDATE_SAMPLES; i++) {
            Optional<BlockPos> pos = sampleCandidate(level, worldSpawn, random);
            if (pos.isEmpty()) {
                continue;
            }

            double score = scoreCandidate(data, level, worldSpawn, pos.get(), random);
            if (!Double.isFinite(score)) {
                continue;
            }

            ScoredSpawn scored = new ScoredSpawn(pos.get(), score);
            if (best.isEmpty() || scored.score() < best.get().score()) {
                best = Optional.of(scored);
            }
        }

        Optional<BlockPos> pos = best.map(ScoredSpawn::pos)
                .or(() -> safeSpawnNear(level, worldSpawn, WORLD_SPAWN_FALLBACK_RADIUS));
        return pos.map(spawnPos -> data.put(
                player.getUUID(),
                level.dimension(),
                spawnPos,
                random.nextFloat() * 360.0F - 180.0F,
                level.getGameTime()
        ));
    }

    private static Optional<BlockPos> sampleCandidate(ServerLevel level, BlockPos center, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int radius = MIN_RADIUS_FROM_WORLD_SPAWN
                + random.nextInt(Math.max(1, MAX_RADIUS_FROM_WORLD_SPAWN - MIN_RADIUS_FROM_WORLD_SPAWN + 1));
        int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
        return safeSurfaceAt(level, x, z);
    }

    private static Optional<BlockPos> safeSpawnNear(ServerLevel level, BlockPos anchor, int radius) {
        if (isSafeSpawn(level, anchor)) {
            return Optional.of(anchor.immutable());
        }

        Optional<BlockPos> sameColumn = safeSurfaceAt(level, anchor.getX(), anchor.getZ());
        if (sameColumn.isPresent()) {
            return sameColumn;
        }

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int horizontal = 1; horizontal <= radius; horizontal++) {
            for (int dx = -horizontal; dx <= horizontal; dx++) {
                best = nearestSafeColumn(level, anchor, anchor.getX() + dx, anchor.getZ() - horizontal, best, bestDistance);
                bestDistance = best == null ? bestDistance : best.distSqr(anchor);
                best = nearestSafeColumn(level, anchor, anchor.getX() + dx, anchor.getZ() + horizontal, best, bestDistance);
                bestDistance = best == null ? bestDistance : best.distSqr(anchor);
            }
            for (int dz = -horizontal + 1; dz <= horizontal - 1; dz++) {
                best = nearestSafeColumn(level, anchor, anchor.getX() - horizontal, anchor.getZ() + dz, best, bestDistance);
                bestDistance = best == null ? bestDistance : best.distSqr(anchor);
                best = nearestSafeColumn(level, anchor, anchor.getX() + horizontal, anchor.getZ() + dz, best, bestDistance);
                bestDistance = best == null ? bestDistance : best.distSqr(anchor);
            }
            if (best != null) {
                return Optional.of(best);
            }
        }
        return Optional.empty();
    }

    private static BlockPos nearestSafeColumn(
            ServerLevel level,
            BlockPos anchor,
            int x,
            int z,
            BlockPos best,
            double bestDistance) {
        Optional<BlockPos> safe = safeSurfaceAt(level, x, z);
        if (safe.isEmpty()) {
            return best;
        }

        double distance = safe.get().distSqr(anchor);
        return distance < bestDistance ? safe.get() : best;
    }

    private static Optional<BlockPos> safeSurfaceAt(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        return isSafeSpawn(level, pos) ? Optional.of(pos.immutable()) : Optional.empty();
    }

    private static boolean isSafeSpawn(ServerLevel level, BlockPos pos) {
        WorldBorder border = level.getWorldBorder();
        if (!border.isWithinBounds(pos)) {
            return false;
        }
        if (pos.getY() <= level.getMinY() + 1 || pos.getY() >= level.getMinY() + level.getHeight() - 2) {
            return false;
        }

        BlockPos floorPos = pos.below();
        BlockState floor = level.getBlockState(floorPos);
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        return floor.blocksMotion()
                && floor.getFluidState().isEmpty()
                && !isDangerousFloor(floor)
                && isSafeAir(feet)
                && isSafeAir(head);
    }

    private static boolean isSafeAir(BlockState state) {
        return state.isAir()
                && state.getFluidState().isEmpty()
                && !isDangerousSpace(state);
    }

    private static boolean isDangerousFloor(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.LAVA_CAULDRON);
    }

    private static boolean isDangerousSpace(BlockState state) {
        return state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.END_PORTAL);
    }

    private static double scoreCandidate(
            DeadRecallDistributedSpawnSavedData data,
            ServerLevel level,
            BlockPos worldSpawn,
            BlockPos candidate,
            RandomSource random) {
        double densityScore = 0.0D;
        for (DeadRecallDistributedSpawnSavedData.PlayerSpawn spawn : data.spawns()) {
            if (!spawn.dimension().equals(level.dimension())) {
                continue;
            }

            double distanceSquared = horizontalDistanceSquared(candidate, spawn.pos());
            if (distanceSquared < (double) MIN_DISTANCE_BETWEEN_SPAWNS * MIN_DISTANCE_BETWEEN_SPAWNS) {
                return Double.POSITIVE_INFINITY;
            }
            if (distanceSquared <= (double) NEAR_DENSITY_RADIUS * NEAR_DENSITY_RADIUS) {
                densityScore += 4.0D;
            } else if (distanceSquared <= (double) MID_DENSITY_RADIUS * MID_DENSITY_RADIUS) {
                densityScore += 2.0D;
            } else if (distanceSquared <= (double) FAR_DENSITY_RADIUS * FAR_DENSITY_RADIUS) {
                densityScore += 1.0D;
            }
        }

        double radius = Math.sqrt(horizontalDistanceSquared(candidate, worldSpawn));
        double radiusScore = Math.abs(radius - PREFERRED_RADIUS_FROM_WORLD_SPAWN) / 512.0D;
        double lowTerrainPenalty = candidate.getY() < 64 ? 2.0D : candidate.getY() < 72 ? 0.5D : 0.0D;
        return densityScore + radiusScore + lowTerrainPenalty + biomePenalty(level, candidate) + random.nextDouble() * 0.2D;
    }

    private static double biomePenalty(ServerLevel level, BlockPos candidate) {
        var biome = level.getBiome(candidate);
        if (biome.is(BiomeTags.IS_DEEP_OCEAN)) {
            return 8.0D;
        }
        if (biome.is(BiomeTags.IS_OCEAN)) {
            return 5.0D;
        }
        if (biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
            return 2.0D;
        }
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_SAVANNA)) {
            return -0.5D;
        }
        return 0.0D;
    }

    private static double horizontalDistanceSquared(BlockPos first, BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return (double) dx * dx + (double) dz * dz;
    }

    private static DeadRecallDistributedSpawnSavedData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DeadRecallDistributedSpawnSavedData.TYPE);
    }

    private record ScoredSpawn(BlockPos pos, double score) {
    }
}
