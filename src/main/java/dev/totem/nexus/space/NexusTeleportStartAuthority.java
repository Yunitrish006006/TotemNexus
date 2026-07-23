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
        NexusTeleportQuoteCalculator.Source source = NexusTeleportResolver.source(player, sourceType, sourceId).orElse(null);
        NexusTeleportQuoteCalculator.Target target = NexusTeleportResolver.target(player, targetId).orElse(null);
        if (source == null || target == null) return false;
        NexusMapQuote quote = NexusTeleportQuoteCalculator.calculate(source, target, context.interfaceType(), NexusTeleportCost.resources(player), false);
        if (!quote.canTeleport()) return false;
        execution.start(new NexusTeleportExecutionSession(player.getUUID(), sourceType, sourceId, targetId, target.type(),
                player.level().dimension(), player.blockPosition(), context.interfaceType(), context.mapId() != null,
                quote.interfaceBonusActive(), quote.prepareTicks()));
        return true;
    }

    public NexusTeleportExecutionAuthority.Result tick(ServerPlayer player) {
        return execution.tick(player, session -> resolveFinal(player, session));
    }

    private Optional<NexusTeleportExecutionAuthority.Prepared> resolveFinal(ServerPlayer player, NexusTeleportExecutionSession session) {
        NexusTeleportQuoteCalculator.Source source = NexusTeleportResolver.source(player, session.sourceType(), session.sourceId()).orElse(null);
        NexusTeleportQuoteCalculator.Target target = NexusTeleportResolver.target(player, session.targetId()).orElse(null);
        if (source == null || target == null) return Optional.empty();
        ServerLevel level = player.level().getServer().getLevel(target.dimension());
        if (level == null) return Optional.empty();
        NexusMapQuote quote = NexusTeleportQuoteCalculator.calculate(source, target, session.interfaceType(), NexusTeleportCost.resources(player), false);
        return Optional.of(new NexusTeleportExecutionAuthority.Prepared(level, target, quote));
    }
}
