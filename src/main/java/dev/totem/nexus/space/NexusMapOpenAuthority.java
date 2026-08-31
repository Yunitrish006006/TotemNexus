package dev.totem.nexus.space;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import java.util.UUID;

/** Single server-side workflow for opening a validated lodestone map. */
public final class NexusMapOpenAuthority {
    private final NexusMapSourceAuthority sources;
    private final NexusTeleportInterfaceAuthority interfaces;
    private final NexusMapAuthority favorites;
    private final NexusMapPayloadAuthority payloads;
    private final NexusMapQuoteAuthority quotes;

    public NexusMapOpenAuthority(TeleportInterfaceSessionStore sessions, NexusMapPayloadAuthority payloads,
                                 NexusMapQuoteAuthority quotes) {
        this.sources = new NexusMapSourceAuthority();
        this.interfaces = new NexusTeleportInterfaceAuthority(sessions, sources);
        this.favorites = new NexusMapAuthority(sessions);
        this.payloads = payloads;
        this.quotes = quotes;
    }

    public boolean openLodestone(ServerPlayer player, InteractionHand hand, UUID sourceId) {
        var source = sources.validateLodestone(player, sourceId);
        if (source.isEmpty()) return false;
        var context = interfaces.establishLodestone(player, hand, sourceId);
        if (context.isEmpty()) return false;
        payloads.sendCalculated(player, context.get(), source.get(), quotes);
        return true;
    }

    public boolean openPlayerAnchor(ServerPlayer player, InteractionHand hand) {
        return false;
    }

    /** Refreshes an existing held-interface map without trusting a client map model. */
    public boolean refresh(ServerPlayer player, String sourceType, UUID sourceId) {
        var context = interfaces.require(player, sourceType, sourceId);
        if (context.isEmpty()) return false;
        var source = sourceFor(player, context.get());
        if (source.isEmpty()) return false;
        payloads.sendCalculated(player, context.get(), source.get(), quotes);
        return true;
    }

    /** Persists a validated favorite mutation and immediately resends the server map. */
    public boolean setFavorite(ServerPlayer player, String sourceType, UUID sourceId, UUID targetId, boolean favorite) {
        return favorites.setFavorite(player, sourceType, sourceId, targetId, favorite)
                && refresh(player, sourceType, sourceId);
    }

    private static java.util.Optional<NexusSpaceUnitRecord> sourceFor(ServerPlayer player, TeleportInterfaceContext context) {
        if (player == null || context == null) return java.util.Optional.empty();
        if (!SpaceUnitType.LODESTONE.id().equals(context.sourceType())) return java.util.Optional.empty();
        var storage = player.level().getServer().overworld().getDataStorage();
        NexusSpaceUnitSavedData units = storage.computeIfAbsent(NexusSpaceUnitSavedData.TYPE);
        NexusSpaceDiscoverySavedData discovery = storage.computeIfAbsent(NexusSpaceDiscoverySavedData.TYPE);
        NexusFriendSavedData friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        return units.get(context.sourceId()).filter(source -> source.isLodestoneAnchor()
                && source.status() == SpaceUnitStatus.ACTIVE
                && source.canView(player.getUUID(), friends.areFriends(player.getUUID(), source.owner()))
                && discovery.hasDiscovered(player.getUUID(), source.id()));
    }
}
