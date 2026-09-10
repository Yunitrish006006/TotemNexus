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
        return validateLodestone(player, sourceId, (TeleportInterfaceContext) null);
    }

    public Optional<NexusSpaceUnitRecord> validateLodestone(ServerPlayer player, UUID sourceId, TeleportInterfaceContext context) {
        return validateLodestone(player, sourceId, source -> context == null
                ? NexusSpaceDiscoverySavedData.loadCanonical(player.level().getServer().overworld().getDataStorage())
                    .hasDiscovered(player.getUUID(), source.id())
                : NexusInterfaceAccess.allows(player, context, source));
    }

    /** Revalidates a server-issued preview session while the player holds building materials. */
    Optional<NexusSpaceUnitRecord> validateVisualizationLodestone(
            ServerPlayer player, UUID sourceId, net.minecraft.world.level.saveddata.maps.MapId mapId) {
        if (mapId == null) return validateLodestone(player, sourceId);
        var storage = player.level().getServer().overworld().getDataStorage();
        var data = net.minecraft.world.item.MapItem.getSavedData(mapId, player.level());
        var binding = NexusMapBindingSavedData.loadCanonical(storage).resolve(mapId, data).orElse(null);
        if (binding == null) return Optional.empty();
        var anchor = NexusSpaceUnitSavedData.loadCanonical(storage).get(binding.unitId()).orElse(null);
        var friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        if (!binding.matchesUnit(anchor) || !anchor.canView(player.getUUID(),
                friends.areFriends(player.getUUID(), anchor.owner()))) return Optional.empty();
        return validateLodestone(player, sourceId,
                source -> FilledMapCoverage.isDrawn(data, source.dimension(), source.pos()));
    }

    private Optional<NexusSpaceUnitRecord> validateLodestone(
            ServerPlayer player, UUID sourceId, java.util.function.Predicate<NexusSpaceUnitRecord> access) {
        if (player == null || sourceId == null) return Optional.empty();
        var storage = player.level().getServer().overworld().getDataStorage();
        NexusSpaceUnitSavedData units = NexusSpaceUnitSavedData.loadCanonical(storage);
        NexusFriendSavedData friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        NexusSpaceUnitRecord source = units.get(sourceId).orElse(null);
        if (source == null || !source.isLodestoneAnchor() || source.status() != SpaceUnitStatus.ACTIVE
                || !source.canView(player.getUUID(), friends.areFriends(player.getUUID(), source.owner()))
                || !access.test(source)
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
