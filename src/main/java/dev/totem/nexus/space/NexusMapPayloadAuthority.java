package dev.totem.nexus.space;

import dev.totem.nexus.network.SpaceUnitMapPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Objects;
import java.util.List;
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
        NexusSpaceUnitSavedData units = storage.computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        NexusSpaceDiscoverySavedData discovery = storage.computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        NexusFriendSavedData friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        MapItemSavedData mapData = context.mapId() == null ? null : MapItem.getSavedData(context.mapId(), player.level());
        SpaceUnitMapPayload payload = NexusMapPayloadFactory.build(player.getUUID(), source, context.interfaceType(), context.mapId(), mapData,
                units.visibleDiscoveredUnits(player.getUUID(), discovery, friends), List.of(), discovery, friends,
                target -> quotes.quote(player, context, target.id()),
                friend -> quotes.quote(player, context, friend.playerId()));
        sender.accept(player, payload);
    }

    private void send(ServerPlayer player, TeleportInterfaceContext context, NexusSpaceUnitRecord source,
                      BiFunction<ServerPlayer, NexusSpaceUnitRecord, NexusMapQuote> quotes) {
        if (player == null || context == null || source == null || !context.playerId().equals(player.getUUID())
                || !context.matchesSource(source.type().id(), source.id())) return;
        var storage = player.level().getServer().overworld().getDataStorage();
        NexusSpaceUnitSavedData units = storage.computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        NexusSpaceDiscoverySavedData discovery = storage.computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        NexusFriendSavedData friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        MapItemSavedData mapData = context.mapId() == null ? null : MapItem.getSavedData(context.mapId(), player.level());
        SpaceUnitMapPayload payload = NexusMapPayloadFactory.build(player.getUUID(), source, context.interfaceType(), context.mapId(), mapData,
                units.visibleDiscoveredUnits(player.getUUID(), discovery, friends), discovery, friends,
                target -> quotes.apply(player, target));
        sender.accept(player, payload);
    }

}
