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
        if (player == null || !SpaceUnitType.LODESTONE.id().equals(sourceType) || sourceId == null) return Optional.empty();
        Optional<TeleportInterfaceItemResolver.ResolvedInterface> resolved =
                TeleportInterfaceItemResolver.resolve(player, hand);
        if (resolved.isEmpty()) return Optional.empty();
        if (!sourceId.equals(resolved.get().boundUnitId())
                || mapSources.validateLodestone(player, sourceId).isEmpty()) return Optional.empty();
        long gameTime = player.level().getServer().overworld().getGameTime();
        TeleportInterfaceContext context = new TeleportInterfaceContext(player.getUUID(), resolved.get().type(),
                sourceType, sourceId, hand, resolved.get().mapId(), resolved.get().boundUnitId(),
                gameTime, gameTime + CONTEXT_TICKS);
        sessions.put(context);
        return Optional.of(context);
    }

    /** Opens a lodestone session only after the server has validated the actual source unit. */
    public Optional<TeleportInterfaceContext> establishLodestone(ServerPlayer player, InteractionHand hand, UUID sourceId) {
        if (player == null) return Optional.empty();
        return mapSources.validateLodestone(player, sourceId)
                .flatMap(source -> establish(player, hand, SpaceUnitType.LODESTONE.id(), source.id()));
    }

    /** Opens a player-anchor session using the server player's own identity only. */
    public Optional<TeleportInterfaceContext> establishPlayerAnchor(ServerPlayer player, InteractionHand hand) {
        return Optional.empty();
    }

    public Optional<TeleportInterfaceContext> require(ServerPlayer player, String sourceType, UUID sourceId) {
        if (player == null) return Optional.empty();
        return sessions.require(player, sourceType, sourceId, player.level().getServer().overworld().getGameTime());
    }

    public void disconnect(UUID playerId) {
        sessions.remove(playerId);
    }
}
