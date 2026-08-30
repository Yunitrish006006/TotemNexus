package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/** Server-owned validation for a lodestone map source before opening a session. */
public final class NexusMapSourceAuthority {
    public static final double SOURCE_OPEN_RADIUS = 8.0D;

    public Optional<NexusSpaceUnitRecord> validateLodestone(ServerPlayer player, UUID sourceId) {
        if (player == null || sourceId == null) return Optional.empty();
        var storage = player.level().getServer().overworld().getDataStorage();
        NexusSpaceUnitSavedData units = storage.computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        NexusSpaceDiscoverySavedData discovery = storage.computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        NexusFriendSavedData friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        NexusSpaceUnitRecord source = units.get(sourceId).orElse(null);
        if (source == null || !source.isLodestoneAnchor() || source.status() != SpaceUnitStatus.ACTIVE
                || !source.canView(player.getUUID(), friends.areFriends(player.getUUID(), source.owner()))
                || !discovery.hasDiscovered(player.getUUID(), source.id())
                || !isWithinOpenRadius(player.level().dimension(), player.position(), source)
                || !player.level().isLoaded(source.pos())) return Optional.empty();
        if (!player.level().getBlockState(source.pos()).is(Blocks.LODESTONE)) {
            units.disableLodestone(source.id(), player.level().getGameTime());
            return Optional.empty();
        }
        return Optional.of(source);
    }

    static boolean isWithinOpenRadius(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> playerDimension,
                                      Vec3 playerPosition, NexusSpaceUnitRecord source) {
        if (playerDimension == null || playerPosition == null || source == null || !playerDimension.equals(source.dimension())) return false;
        BlockPos pos = source.pos();
        return isWithinOpenRadius(playerPosition.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
    }

    static boolean isWithinOpenRadius(double squaredDistance) {
        return squaredDistance >= 0.0D && squaredDistance <= SOURCE_OPEN_RADIUS * SOURCE_OPEN_RADIUS;
    }
}
