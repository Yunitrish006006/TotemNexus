package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

/** A portable interface always starts at the player; a constructed array adds stability. */
final class NexusPortableSource {
    private NexusPortableSource() { }

    private record Cached(BlockPos pos, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                          TeleportInterfaceContext context, long tick, long revision, Optional<NexusSpaceUnitRecord> array) { }
    private static final java.util.Map<ServerPlayer, Cached> cache = new java.util.WeakHashMap<>();

    static Optional<NexusSpaceUnitRecord> array(ServerPlayer player, TeleportInterfaceContext context) {
        return array(player, context, false);
    }

    static Optional<NexusSpaceUnitRecord> array(ServerPlayer player, TeleportInterfaceContext context, boolean fresh) {
        var level = player.level();
        long tick = level.getServer().overworld().getGameTime();
        var old = cache.get(player);
        if (!fresh && old != null && old.pos().equals(player.blockPosition()) && old.dimension().equals(level.dimension())
                && old.context().equals(context) && tick >= old.tick() && tick - old.tick() < 20
                && old.revision() == TeleportArrayMaterialProfiles.revision()
                && old.array().map(u -> NexusInterfaceAccess.allows(player, context, u)).orElse(true)) return old.array();
        var selected = scan(player, context);
        cache.put(player, new Cached(player.blockPosition().immutable(), level.dimension(), context, tick,
                TeleportArrayMaterialProfiles.revision(), selected));
        return selected;
    }

    private static Optional<NexusSpaceUnitRecord> scan(ServerPlayer player, TeleportInterfaceContext context) {
        var level = player.level();
        var units = NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
        return units.activeLodestones().stream()
                .filter(u -> u.dimension().equals(level.dimension()))
                .filter(u -> Math.abs(u.pos().getX() - player.blockPosition().getX()) <= 5
                        && Math.abs(u.pos().getZ() - player.blockPosition().getZ()) <= 5
                        && Math.abs(u.pos().getY() - player.blockPosition().getY()) <= 7)
                .filter(u -> level.isLoaded(u.pos()) && level.getBlockState(u.pos()).is(Blocks.LODESTONE))
                .filter(u -> NexusInterfaceAccess.allows(player, context, u))
                .sorted(Comparator.comparingDouble(u -> u.pos().distSqr(player.blockPosition())))
                .limit(8)
                .filter(u -> contains(player.blockPosition(), u.pos(), TeleportArrayMaterialScan.scan(level, u.pos(),
                        NexusSpaceUnitSavedData::isStructureBlock, NexusSpaceUnitSavedData::isWornStructureBlock).structuralPositions()))
                .findFirst().flatMap(u -> units.rescanLodestone(level, u.id()));
    }

    static NexusSpaceUnitRecord playerRecord(ServerPlayer player, TeleportInterfaceContext context) {
        var array = array(player, context);
        var structure = array.map(NexusSpaceUnitRecord::structure).orElse(SpaceStructureSnapshot.EMPTY);
        var snapshot = new SpaceStructureSnapshot(structure.completeness(), structure.symmetry(), stability(array),
                structure.interference(), structure.environmentStability(), structure.wear(), structure.tier(),
                structure.amethystCatalystBlocks(), structure.material());
        return new NexusSpaceUnitRecord(player.getUUID(), SpaceUnitType.PLAYER, player.level().dimension(),
                player.blockPosition(), player.getUUID(), player.getName().getString(), SpaceUnitVisibility.PRIVATE,
                SpaceUnitStatus.ACTIVE, Set.of(), Set.of(), snapshot, 0, 0);
    }

    static boolean contains(BlockPos feet, BlockPos anchor, Set<BlockPos> structure) {
        if (structure.isEmpty()) return false;
        int minX = anchor.getX(), maxX = minX, minY = anchor.getY(), maxY = minY;
        int minZ = anchor.getZ(), maxZ = minZ;
        for (BlockPos p : structure) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        return feet.getX() >= minX && feet.getX() <= maxX && feet.getZ() >= minZ && feet.getZ() <= maxZ
                && feet.getY() >= minY && feet.getY() <= maxY + 2;
    }

    static double stability(Optional<NexusSpaceUnitRecord> array) {
        return .6D + .35D * array.map(u -> Math.clamp(u.structure().resonance(), 0D, 1D)).orElse(0D);
    }
}
