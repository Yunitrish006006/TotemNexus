package dev.totem.nexus.space;

import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/** Resolves teleport endpoints exclusively from Nexus SavedData and live server players. */
public final class NexusTeleportResolver {
    private NexusTeleportResolver() { }
    public static Optional<NexusTeleportQuoteCalculator.Source> source(ServerPlayer player, String sourceType, UUID sourceId) {
        if ("player".equals(sourceType) && player.getUUID().equals(sourceId))
            return Optional.of(new NexusTeleportQuoteCalculator.Source(player.getUUID(), "player", player.level().dimension(), player.blockPosition(), .6D, 0, 0));
        if (!"lodestone".equals(sourceType) || sourceId == null) return Optional.empty();
        NexusSpaceUnitSavedData units = player.level().getServer().overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        NexusSpaceDiscoverySavedData discovery = player.level().getServer().overworld().getDataStorage().computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        NexusFriendSavedData friends = player.level().getServer().overworld().getDataStorage().computeIfAbsent(NexusFriendSavedData.TYPE);
        return units.get(sourceId).filter(unit -> unit.isLodestoneAnchor() && unit.status() == SpaceUnitStatus.ACTIVE
                        && unit.canView(player.getUUID(), friends.areFriends(player.getUUID(), unit.owner())) && discovery.hasDiscovered(player.getUUID(), unit.id()))
                .map(unit -> new NexusTeleportQuoteCalculator.Source(unit.id(), "lodestone", unit.dimension(), unit.pos(), unit.structure().resonance(), unit.structure().tier(), unit.structure().amethystCatalystBlocks(), unit.structure().materialAttributes()));
    }
    public static Optional<NexusTeleportQuoteCalculator.Target> target(ServerPlayer player, UUID targetId) {
        if (targetId == null) return Optional.empty();
        var server = player.level().getServer();
        NexusSpaceUnitSavedData units = server.overworld().getDataStorage().computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        NexusSpaceDiscoverySavedData discovery = server.overworld().getDataStorage().computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        NexusFriendSavedData friends = server.overworld().getDataStorage().computeIfAbsent(NexusFriendSavedData.TYPE);
        Optional<NexusSpaceUnitRecord> unit = units.get(targetId);
        if (unit.isPresent()) return unit.filter(value -> value.status() == SpaceUnitStatus.ACTIVE
                        && value.canView(player.getUUID(), friends.areFriends(player.getUUID(), value.owner())) && discovery.hasDiscovered(player.getUUID(), value.id()))
                .map(value -> new NexusTeleportQuoteCalculator.Target(value.id(), value.type(), value.dimension(), value.pos(), value.structure().resonance(), value.structure().tier(), value.structure().wear(), value.isLodestoneAnchor(), value.owner(), value.structure().amethystCatalystBlocks(), value.structure().materialAttributes()));
        ServerPlayer friend = server.getPlayerList().getPlayer(targetId);
        if (friend == null || friend.getUUID().equals(player.getUUID()) || !friend.isAlive() || friend.isRemoved() || !friends.areFriends(player.getUUID(), friend.getUUID())) return Optional.empty();
        return Optional.of(new NexusTeleportQuoteCalculator.Target(friend.getUUID(), SpaceUnitType.PLAYER, friend.level().dimension(), friend.blockPosition(), .6D, 0, 0D, false, friend.getUUID(), 0));
    }
}
