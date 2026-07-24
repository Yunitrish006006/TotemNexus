package dev.totem.nexus.space;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Coordinates pending sessions with final server-resolved target, quote and movement. */
public final class NexusTeleportExecutionAuthority {
    private final NexusTeleportSessionStore sessions = new NexusTeleportSessionStore();
    public void start(NexusTeleportExecutionSession session) { sessions.start(session); }
    public void cancel(ServerPlayer player) { sessions.cancel(player.getUUID()); }
    public Result tick(ServerPlayer player, Function<NexusTeleportExecutionSession, Optional<Prepared>> resolveFinal) {
        Objects.requireNonNull(player, "player"); Objects.requireNonNull(resolveFinal, "resolveFinal");
        NexusTeleportExecutionSession current = sessions.get(player.getUUID()).orElse(null);
        if (current == null) return Result.NONE;
        String cancelled = current.cancellationReason(new NexusTeleportExecutionSession.PlayerState(player.isAlive(), player.isRemoved(), player.level().dimension(), player.blockPosition()));
        if (!cancelled.isEmpty()) { sessions.cancel(player.getUUID()); return new Result(false, false, cancelled); }
        NexusTeleportExecutionSession next = sessions.tick(player.getUUID()).orElseThrow();
        if (!next.ready()) return Result.PREPARING;
        Prepared prepared = resolveFinal.apply(next).orElse(null);
        if (prepared == null) return new Result(false, false, "message.deadrecall.space_unit.teleport_cancelled.target");
        if (!next.filledMapQuoteStillValid(prepared.quote)) return new Result(false, false, "message.deadrecall.space_unit.teleport_cancelled.interface_quote_changed");
        boolean completed = NexusTeleportCompletion.execute(player, prepared.level, prepared.target, prepared.quote);
        return completed ? Result.COMPLETED : new Result(false, false, prepared.quote.canTeleport() ? "message.deadrecall.space_unit.teleport_cancelled.no_landing" : prepared.quote.blockedReason());
    }
    public record Prepared(ServerLevel level, NexusTeleportQuoteCalculator.Target target, NexusMapQuote quote) { }
    public record Result(boolean preparing, boolean completed, String messageKey) {
        public static final Result NONE = new Result(false, false, "");
        public static final Result PREPARING = new Result(true, false, "");
        public static final Result COMPLETED = new Result(false, true, "");
    }
}
