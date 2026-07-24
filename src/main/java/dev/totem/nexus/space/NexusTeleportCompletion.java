package dev.totem.nexus.space;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

/** Final server-side teleport gate: quote, safe landing, then deduction and movement. */
public final class NexusTeleportCompletion {
    private NexusTeleportCompletion() { }
    public static boolean execute(ServerPlayer player, ServerLevel targetLevel, NexusTeleportQuoteCalculator.Target target, NexusMapQuote quote) {
        if (!quote.canTeleport()) return false;
        return NexusSafeLanding.find(targetLevel, target.pos(), target.lodestone(), quote.maxHorizontalDeviation()).map(landing -> {
            if (!NexusTeleportCost.deduct(player, quote)) return false;
            player.teleportTo(targetLevel, landing.getX()+.5D, landing.getY(), landing.getZ()+.5D, Relative.DELTA, player.getYRot(), player.getXRot(), false);
            return true;
        }).orElse(false);
    }
}
