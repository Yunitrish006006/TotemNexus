package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.UUID;

/** Immutable server-side preparation session; no client packet can alter its source or target. */
public record NexusTeleportExecutionSession(UUID playerId, String sourceType, UUID sourceId, UUID targetId, SpaceUnitType targetType,
                                            ResourceKey<Level> startDimension, BlockPos startPos, TeleportInterfaceType interfaceType,
                                            InteractionHand interactionHand, MapId mapId,
                                            boolean filledMapDataValidAtStart, boolean filledMapBonusActiveAtStart, int remainingTicks) {
    public NexusTeleportExecutionSession {
        if (playerId == null || sourceType == null || sourceId == null || targetId == null || targetType == null
                || startDimension == null || startPos == null || interfaceType == null || interactionHand == null
                || (interfaceType == TeleportInterfaceType.FILLED_MAP) != (mapId != null)) {
            throw new IllegalArgumentException("Invalid teleport execution identity");
        }
        startPos = startPos.immutable(); remainingTicks = Math.max(0, remainingTicks);
    }
    public NexusTeleportExecutionSession tick() { return new NexusTeleportExecutionSession(playerId, sourceType, sourceId, targetId, targetType, startDimension, startPos, interfaceType, interactionHand, mapId, filledMapDataValidAtStart, filledMapBonusActiveAtStart, remainingTicks - 1); }
    public boolean ready() { return remainingTicks <= 0; }
    public String cancellationReason(PlayerState state) {
        if (!state.alive || state.removed) return "message.totem.space_unit.teleport_cancelled.generic";
        if (!startDimension.equals(state.dimension)) return "message.totem.space_unit.teleport_cancelled.dimension";
        long dx=(long)startPos.getX()-state.pos.getX(), dy=(long)startPos.getY()-state.pos.getY(), dz=(long)startPos.getZ()-state.pos.getZ();
        return dx*dx+dy*dy+dz*dz > 16 ? "message.totem.space_unit.teleport_cancelled.moved" : "";
    }
    public boolean filledMapQuoteStillValid(NexusMapQuote quote) { return interfaceType != TeleportInterfaceType.FILLED_MAP || (!filledMapBonusActiveAtStart || quote.interfaceBonusActive()); }
    public record PlayerState(boolean alive, boolean removed, ResourceKey<Level> dimension, BlockPos pos) { public PlayerState { pos = pos.immutable(); } }
}
