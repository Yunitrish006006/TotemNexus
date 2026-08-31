package dev.totem.nexus.space;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Short-lived server-only identity for the source that opened a teleport map. */
public record TeleportInterfaceContext(UUID playerId, TeleportInterfaceType interfaceType, String sourceType,
                                       UUID sourceId, InteractionHand interactionHand, MapId mapId, UUID boundUnitId,
                                       long createdGameTime, long expiresGameTime) {
    /** Compatibility constructor for server-only callers that do not validate a held item yet. */
    public TeleportInterfaceContext(UUID playerId, TeleportInterfaceType interfaceType, String sourceType,
                                    UUID sourceId, long createdGameTime, long expiresGameTime) {
        this(playerId, interfaceType, sourceType, sourceId, InteractionHand.MAIN_HAND, null, null,
                createdGameTime, expiresGameTime);
    }

    public TeleportInterfaceContext(UUID playerId, TeleportInterfaceType interfaceType, String sourceType,
                                    UUID sourceId, InteractionHand interactionHand, MapId mapId,
                                    long createdGameTime, long expiresGameTime) {
        this(playerId, interfaceType, sourceType, sourceId, interactionHand, mapId,
                SpaceUnitType.LODESTONE.id().equals(sourceType) ? sourceId : null,
                createdGameTime, expiresGameTime);
    }

    public TeleportInterfaceContext {
        if (playerId == null || interfaceType == null || sourceType == null || sourceId == null || interactionHand == null
                || expiresGameTime < createdGameTime)
            throw new IllegalArgumentException("Invalid teleport interface context identity");
        if ((interfaceType == TeleportInterfaceType.FILLED_MAP) != (mapId != null))
            throw new IllegalArgumentException("Only a filled-map context may carry a map ID");
        if (SpaceUnitType.LODESTONE.id().equals(sourceType) && !sourceId.equals(boundUnitId))
            throw new IllegalArgumentException("A lodestone context must match the held interface binding");
    }
    public boolean matchesSource(String sourceType, UUID sourceId) { return this.sourceType.equals(sourceType) && this.sourceId.equals(sourceId); }
    public boolean isExpired(long gameTime) { return gameTime > expiresGameTime; }
    public boolean isStillHeldBy(ServerPlayer player) {
        if (player == null || !playerId.equals(player.getUUID())) return false;
        Optional<TeleportInterfaceItemResolver.ResolvedInterface> resolved =
                TeleportInterfaceItemResolver.resolve(player, interactionHand);
        return resolved.isPresent()
                && resolved.get().type() == interfaceType
                && Objects.equals(resolved.get().mapId(), mapId)
                && Objects.equals(resolved.get().boundUnitId(), boundUnitId);
    }
}
