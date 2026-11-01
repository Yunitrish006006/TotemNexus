package dev.totem.nexus.space;

import dev.totem.nexus.network.SpaceUnitMapPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** Projects an already validated map context into the stable clientbound payload. */
public final class NexusMapPayloadAuthority {
    private final BiConsumer<ServerPlayer, SpaceUnitMapPayload> sender;
    private final BiFunction<ServerPlayer, NexusSpaceUnitRecord, NexusMapQuote> quoteFor;

    public NexusMapPayloadAuthority(BiFunction<ServerPlayer, NexusSpaceUnitRecord, NexusMapQuote> quoteFor) {
        this(ServerPlayNetworking::send, quoteFor);
    }

    NexusMapPayloadAuthority(BiConsumer<ServerPlayer, SpaceUnitMapPayload> sender,
                             BiFunction<ServerPlayer, NexusSpaceUnitRecord, NexusMapQuote> quoteFor) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.quoteFor = Objects.requireNonNull(quoteFor, "quoteFor");
    }

    public void send(ServerPlayer player, TeleportInterfaceContext context, NexusSpaceUnitRecord source) {
        send(player, context, source, (ignored, target) -> quoteFor.apply(player, target));
    }

    /** Uses the complete server-owned quote path for a validated map context. */
    public void sendCalculated(ServerPlayer player, TeleportInterfaceContext context, NexusSpaceUnitRecord source,
                               NexusMapQuoteAuthority quotes) {
        Objects.requireNonNull(quotes, "quotes");
        if (player == null || context == null || source == null || !context.playerId().equals(player.getUUID())
                || !context.matchesSource(source.type().id(), source.id())) return;
        var storage = player.level().getServer().overworld().getDataStorage();
        NexusSpaceUnitSavedData units = NexusSpaceUnitSavedData.loadCanonical(storage);
        NexusSpaceDiscoverySavedData discovery = NexusSpaceDiscoverySavedData.loadCanonical(storage);
        NexusFriendSavedData friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        MapItemSavedData mapData = context.mapId() == null ? null : MapItem.getSavedData(context.mapId(), player.level());
        List<NexusSpaceUnitRecord> authorized = authorizedUnits(
                player, context, units, discovery, friends, mapData, storage);
        Set<UUID> authorizedIds = authorized.stream().map(NexusSpaceUnitRecord::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        SpaceUnitMapPayload payload = NexusMapPayloadFactory.build(player.getUUID(), source, context.interfaceType(), context.mapId(), mapData,
                authorized, List.of(), discovery, friends,
                target -> quotes.quote(player, context, target.id()),
                friend -> quotes.quote(player, context, friend.playerId()),
                unit -> authorizedIds.contains(unit.id()));
        sender.accept(player, payload);
    }

    private void send(ServerPlayer player, TeleportInterfaceContext context, NexusSpaceUnitRecord source,
                      BiFunction<ServerPlayer, NexusSpaceUnitRecord, NexusMapQuote> quotes) {
        if (player == null || context == null || source == null || !context.playerId().equals(player.getUUID())
                || !context.matchesSource(source.type().id(), source.id())) return;
        var storage = player.level().getServer().overworld().getDataStorage();
        NexusSpaceUnitSavedData units = NexusSpaceUnitSavedData.loadCanonical(storage);
        NexusSpaceDiscoverySavedData discovery = NexusSpaceDiscoverySavedData.loadCanonical(storage);
        NexusFriendSavedData friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        MapItemSavedData mapData = context.mapId() == null ? null : MapItem.getSavedData(context.mapId(), player.level());
        List<NexusSpaceUnitRecord> authorized = authorizedUnits(
                player, context, units, discovery, friends, mapData, storage);
        Set<UUID> authorizedIds = authorized.stream().map(NexusSpaceUnitRecord::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        SpaceUnitMapPayload payload = NexusMapPayloadFactory.build(player.getUUID(), source, context.interfaceType(), context.mapId(), mapData,
                authorized, List.of(), discovery, friends,
                target -> quotes.apply(player, target), ignored -> NexusMapQuote.unavailable(context.interfaceType(), "pending_authority"),
                unit -> authorizedIds.contains(unit.id()));
        sender.accept(player, payload);
    }

    private static List<NexusSpaceUnitRecord> authorizedUnits(ServerPlayer player, TeleportInterfaceContext context,
            NexusSpaceUnitSavedData units, NexusSpaceDiscoverySavedData discovery, NexusFriendSavedData friends,
            MapItemSavedData mapData, net.minecraft.world.level.storage.SavedDataStorage storage) {
        if (!context.interfaceType().hasMapVisualization()) return units.visibleDiscoveredUnits(player.getUUID(), discovery, friends);
        NexusMapRecordSavedData records;
        try {
            records = storage.get(NexusMapRecordSavedData.TYPE);
        } catch (RuntimeException exception) {
            return List.of();
        }
        if (records == null) return List.of();
        NexusMapRecordSavedData.Entry record = records.resolve(context.mapId(), mapData).orElse(null);
        if (record == null) return List.of();
        var candidates = new java.util.ArrayList<>(units.activeLodestones());
        candidates.addAll(units.deathNodes());
        return NexusMapNodeProjection.select(
                player.getUUID(), record, mapData, candidates,
                (viewer, owner) -> friends.areFriends(viewer, owner),
                unitId -> discovery.hasDiscovered(player.getUUID(), unitId),
                unit -> NexusMapRecordedCoverage.covers(
                        player, context.mapId(), unit.dimension(), unit.pos()));
    }

}
