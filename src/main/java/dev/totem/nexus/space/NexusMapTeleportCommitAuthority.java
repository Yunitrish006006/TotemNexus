package dev.totem.nexus.space;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Server-only MapId and destination checks repeated immediately before teleport commit. */
final class NexusMapTeleportCommitAuthority {
    private NexusMapTeleportCommitAuthority() { }

    static boolean hasCurrentPlayerSource(ServerPlayer player, String sourceType, UUID sourceId) {
        return player != null && "player".equals(sourceType) && player.getUUID().equals(sourceId);
    }

    static Optional<HeldRecord> resolveHeldRecord(
            ServerPlayer player,
            InteractionHand hand,
            MapId expectedMapId) {
        if (player == null || hand == null || expectedMapId == null) return Optional.empty();
        NexusHeldMapResolver.ResolvedMap held = NexusHeldMapResolver.resolve(player, hand)
                .filter(resolved -> expectedMapId.equals(resolved.mapId()))
                .orElse(null);
        if (held == null) return Optional.empty();
        try {
            var storage = player.level().getServer().overworld().getDataStorage();
            NexusMapRecordSavedData records = storage.get(NexusMapRecordSavedData.TYPE);
            if (records == null) return Optional.empty();
            return records.resolve(expectedMapId, held.nativeData())
                    .map(record -> new HeldRecord(held.nativeData(), record));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    static boolean allowsDestination(
            ServerPlayer player,
            TeleportInterfaceContext context,
            NexusSpaceUnitRecord destination) {
        if (player == null || context == null || destination == null) return false;
        if (context.interfaceType() != TeleportInterfaceType.FILLED_MAP) {
            return NexusInterfaceAccess.allows(player, context, destination);
        }
        HeldRecord held = resolveHeldRecord(player, context.interactionHand(), context.mapId()).orElse(null);
        if (held == null) return false;
        var storage = player.level().getServer().overworld().getDataStorage();
        NexusSpaceDiscoverySavedData discovery = NexusSpaceDiscoverySavedData.loadCanonical(storage);
        NexusFriendSavedData friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        return !NexusMapNodeProjection.select(
                player.getUUID(), held.record(), held.nativeData(), List.of(destination),
                (viewer, owner) -> friends.areFriends(viewer, owner),
                unitId -> discovery.hasDiscovered(player.getUUID(), unitId),
                unit -> NexusMapRecordedCoverage.covers(
                        player,
                        context.mapId(),
                        unit.dimension(),
                        unit.pos()))
                .isEmpty();
    }

    record HeldRecord(MapItemSavedData nativeData, NexusMapRecordSavedData.Entry record) { }
}
