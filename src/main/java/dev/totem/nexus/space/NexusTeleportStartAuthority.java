package dev.totem.nexus.space;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** Starts and advances teleport sessions from server-owned context, endpoints and quotes. */
public final class NexusTeleportStartAuthority {
    private final NexusTeleportInterfaceAuthority interfaces;
    private final NexusTeleportExecutionAuthority execution = new NexusTeleportExecutionAuthority();

    public NexusTeleportStartAuthority(TeleportInterfaceSessionStore contexts) {
        this.interfaces = new NexusTeleportInterfaceAuthority(contexts);
    }

    public boolean start(ServerPlayer player, String sourceType, java.util.UUID sourceId, java.util.UUID targetId) {
        if (player == null) return false;
        TeleportInterfaceContext context = interfaces.require(player, sourceType, sourceId).orElse(null);
        if (context == null) return false;
        NexusTeleportQuoteCalculator.Source source = NexusTeleportResolver.source(
                player, sourceType, sourceId, context).orElse(null);
        NexusTeleportQuoteCalculator.Target target = NexusTeleportResolver.target(
                player, targetId, context).orElse(null);
        if (source == null || target == null) return false;
        NexusMapQuote quote = NexusTeleportQuoteCalculator.calculate(source, target, context.interfaceType(), NexusTeleportCost.resources(player), false);
        if (!quote.canTeleport()) return false;
        execution.start(new NexusTeleportExecutionSession(player.getUUID(), sourceType, sourceId, targetId, target.type(),
                player.level().dimension(), player.blockPosition(), context.interfaceType(),
                context.interactionHand(), context.mapId(), context.mapId() != null,
                quote.interfaceBonusActive(), quote.prepareTicks()));
        return true;
    }

    public NexusTeleportExecutionAuthority.Result tick(ServerPlayer player) {
        return execution.tick(player, session -> resolveFinal(player, session));
    }

    private Optional<NexusTeleportExecutionAuthority.Prepared> resolveFinal(ServerPlayer player, NexusTeleportExecutionSession session) {
        TeleportInterfaceContext context = interfaces.require(
                player, session.sourceType(), session.sourceId()).orElse(null);
        if (context == null || context.interactionHand() != session.interactionHand()
                || !java.util.Objects.equals(context.mapId(), session.mapId())) return Optional.empty();
        if (context.interfaceType() == TeleportInterfaceType.FILLED_MAP
                && (!NexusMapTeleportCommitAuthority.hasCurrentPlayerSource(
                        player, session.sourceType(), session.sourceId())
                || NexusMapTeleportCommitAuthority.resolveHeldRecord(
                        player, session.interactionHand(), session.mapId()).isEmpty())) return Optional.empty();
        NexusTeleportQuoteCalculator.Source source = NexusTeleportResolver.source(
                player, session.sourceType(), session.sourceId(), context).orElse(null);
        NexusTeleportQuoteCalculator.Target target = NexusTeleportResolver.target(
                player, session.targetId(), context).orElse(null);
        if (source == null || target == null) return Optional.empty();
        ServerLevel level = player.level().getServer().getLevel(target.dimension());
        if (level == null) return Optional.empty();
        NexusMapQuote quote = NexusTeleportQuoteCalculator.calculate(source, target, session.interfaceType(), NexusTeleportCost.resources(player), false);
        return Optional.of(new NexusTeleportExecutionAuthority.Prepared(level, target, quote));
    }
}
