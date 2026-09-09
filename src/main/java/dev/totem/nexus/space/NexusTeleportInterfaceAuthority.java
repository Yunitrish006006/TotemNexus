package dev.totem.nexus.space;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import java.util.Optional;
import java.util.UUID;

/**
 * Establishes the short-lived, server-owned identity required before a map
 * payload can mutate or start teleport state. Callers must validate that the
 * supplied source is a visible active Space Unit before opening this context.
 */
public final class NexusTeleportInterfaceAuthority {
    private static final long CONTEXT_TICKS = 20L * 30L;
    private final TeleportInterfaceSessionStore sessions;
    private final NexusMapSourceAuthority mapSources;

    public NexusTeleportInterfaceAuthority(TeleportInterfaceSessionStore sessions) {
        this(sessions, new NexusMapSourceAuthority());
    }

    NexusTeleportInterfaceAuthority(TeleportInterfaceSessionStore sessions, NexusMapSourceAuthority mapSources) {
        this.sessions = sessions;
        this.mapSources = mapSources;
    }

    public Optional<TeleportInterfaceContext> establish(ServerPlayer player, InteractionHand hand,
                                                        String sourceType, UUID sourceId) {
        if (player == null || sourceId == null) return Optional.empty();
        Optional<TeleportInterfaceItemResolver.ResolvedInterface> resolved =
                TeleportInterfaceItemResolver.resolve(player, hand);
        if (resolved.isEmpty()) return Optional.empty();
        boolean portable = "player".equals(sourceType) && player.getUUID().equals(sourceId)
                && resolved.get().type().canSelectTeleportDestination();
        if (!portable && (!"lodestone".equals(sourceType)
                || (!resolved.get().type().hasMapVisualization() && !sourceId.equals(resolved.get().boundUnitId())))) return Optional.empty();
        long gameTime = player.level().getServer().overworld().getGameTime();
        TeleportInterfaceContext context = new TeleportInterfaceContext(player.getUUID(), resolved.get().type(),
                sourceType, sourceId, hand, resolved.get().mapId(), resolved.get().boundUnitId(),
                gameTime, gameTime + CONTEXT_TICKS);
        if (portable ? !validBoundAnchor(player, context) : mapSources.validateLodestone(player, sourceId, context).isEmpty()) return Optional.empty();
        sessions.put(context);
        return Optional.of(context);
    }

    /** Opens a lodestone session only after the server has validated the actual source unit. */
    public Optional<TeleportInterfaceContext> establishLodestone(ServerPlayer player, InteractionHand hand, UUID sourceId) {
        if (player == null) return Optional.empty();
        return establish(player, hand, SpaceUnitType.LODESTONE.id(), sourceId);
    }

    /** Opens a player-anchor session using the server player's own identity only. */
    public Optional<TeleportInterfaceContext> establishPlayerAnchor(ServerPlayer player, InteractionHand hand) {
        return player == null ? Optional.empty() : establish(player, hand, "player", player.getUUID());
    }

    public Optional<TeleportInterfaceContext> require(ServerPlayer player, String sourceType, UUID sourceId) {
        if (player == null) return Optional.empty();
        return sessions.require(player, sourceType, sourceId, player.level().getServer().overworld().getGameTime())
                .filter(context -> !"player".equals(sourceType) || (player.getUUID().equals(sourceId)
                        && context.interfaceType().canSelectTeleportDestination() && validBoundAnchor(player, context)));
    }

    private static boolean validBoundAnchor(ServerPlayer player, TeleportInterfaceContext context) {
        var storage = player.level().getServer().overworld().getDataStorage();
        var friends = storage.computeIfAbsent(NexusFriendSavedData.TYPE);
        return NexusSpaceUnitSavedData.loadCanonical(storage).get(context.boundUnitId())
                .filter(u -> u.isLodestoneAnchor() && u.status() == SpaceUnitStatus.ACTIVE
                        && u.canView(player.getUUID(), friends.areFriends(player.getUUID(), u.owner()))).isPresent();
    }

    public void disconnect(UUID playerId) {
        sessions.remove(playerId);
    }
}
