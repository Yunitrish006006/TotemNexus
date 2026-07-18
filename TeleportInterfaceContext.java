package com.adaptor.deadrecall.space;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.Optional;
import java.util.UUID;

/** Short-lived Server-only identity for the item and source that opened the teleport map. */
public record TeleportInterfaceContext(
        UUID playerId,
        TeleportInterfaceType interfaceType,
        String sourceType,
        UUID sourceId,
        InteractionHand interactionHand,
        MapId mapId,
        long createdGameTime,
        long expiresGameTime) {

    public TeleportInterfaceContext {
        if (playerId == null
                || interfaceType == null
                || sourceType == null
                || sourceId == null
                || interactionHand == null) {
            throw new IllegalArgumentException("Teleport interface context identity cannot be null");
        }
        if ((interfaceType == TeleportInterfaceType.FILLED_MAP) != (mapId != null)) {
            throw new IllegalArgumentException("Only a filled-map context may carry a map ID");
        }
        if (expiresGameTime < createdGameTime) {
            throw new IllegalArgumentException("Teleport interface context expires before it was created");
        }
    }

    public boolean matchesSource(String sourceType, UUID sourceId) {
        return this.sourceType.equals(sourceType) && this.sourceId.equals(sourceId);
    }

    public boolean isExpired(long gameTime) {
        return gameTime > this.expiresGameTime;
    }

    public boolean isStillHeldBy(ServerPlayer player) {
        if (player == null || !this.playerId.equals(player.getUUID())) {
            return false;
        }
        Optional<TeleportInterfaceItemResolver.ResolvedInterface> resolved =
                TeleportInterfaceItemResolver.resolve(player, this.interactionHand);
        return resolved.isPresent()
                && resolved.get().type() == this.interfaceType
                && java.util.Objects.equals(resolved.get().mapId(), this.mapId);
    }
}
