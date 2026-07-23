package dev.totem.nexus.space;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Thread-confined server session store used by future map/favorite authority. */
public final class TeleportInterfaceSessionStore {
    private final Map<UUID, TeleportInterfaceContext> contexts = new HashMap<>();
    public void put(TeleportInterfaceContext context) { contexts.put(context.playerId(), context); }
    public Optional<TeleportInterfaceContext> require(UUID playerId, String sourceType, UUID sourceId, long gameTime) {
        TeleportInterfaceContext context = contexts.get(playerId);
        if (context == null || context.isExpired(gameTime) || !context.matchesSource(sourceType, sourceId)) return Optional.empty();
        return Optional.of(context);
    }
    public Optional<TeleportInterfaceContext> require(ServerPlayer player, String sourceType, UUID sourceId, long gameTime) {
        if (player == null) return Optional.empty();
        TeleportInterfaceContext context = contexts.get(player.getUUID());
        if (context == null || !context.matchesSource(sourceType, sourceId)) return Optional.empty();
        if (context.isExpired(gameTime) || !context.isStillHeldBy(player)) {
            contexts.remove(player.getUUID());
            return Optional.empty();
        }
        return Optional.of(context);
    }
    public void remove(UUID playerId) { contexts.remove(playerId); }
    public void expire(long gameTime) { contexts.entrySet().removeIf(entry -> entry.getValue().isExpired(gameTime)); }
}
