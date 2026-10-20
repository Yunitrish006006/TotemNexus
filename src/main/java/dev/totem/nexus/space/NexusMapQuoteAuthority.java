package dev.totem.nexus.space;

import net.minecraft.server.level.ServerPlayer;

/** Calculates map quotes only from a server-owned interface context and saved endpoints. */
public final class NexusMapQuoteAuthority {
    public NexusMapQuote quote(ServerPlayer player, TeleportInterfaceContext context, NexusSpaceUnitRecord target) {
        return target == null
                ? unavailable(context)
                : quote(player, context, target.id());
    }

    /** Resolves both persisted units and online friends from server state. */
    public NexusMapQuote quote(ServerPlayer player, TeleportInterfaceContext context, java.util.UUID targetId) {
        if (player == null || context == null || targetId == null || !context.playerId().equals(player.getUUID()) || !context.isStillHeldBy(player)) {
            return unavailable(context);
        }
        return NexusTeleportResolver.source(player, context.sourceType(), context.sourceId(), context)
                .flatMap(source -> NexusTeleportResolver.target(player, targetId, context)
                        .map(resolvedTarget -> NexusTeleportQuoteCalculator.calculate(source, resolvedTarget, context.interfaceType(),
                                NexusTeleportCost.resources(player), context.interfaceType().hasMapVisualization()
                                        && NexusMapRecordedCoverage.covers(player, context.mapId(), resolvedTarget.dimension(), resolvedTarget.pos()))))
                .orElseGet(() -> unavailable(context));
    }

    private static NexusMapQuote unavailable(TeleportInterfaceContext context) {
        if (context == null) {
            return NexusMapQuote.unavailable(TeleportInterfaceType.COMPASS, "message.totem.space_unit.teleport_blocked.source");
        }
        return NexusMapQuote.unavailable(context.interfaceType(), "message.totem.space_unit.teleport_blocked.source");
    }
}
