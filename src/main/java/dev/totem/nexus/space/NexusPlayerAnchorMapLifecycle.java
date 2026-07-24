package dev.totem.nexus.space;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in interaction wiring for player-anchor interfaces that are not bound compasses. */
public final class NexusPlayerAnchorMapLifecycle {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private NexusPlayerAnchorMapLifecycle() { }

    public static void register(NexusMapOpenAuthority maps) {
        if (!REGISTERED.compareAndSet(false, true)) return;
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (TeleportInterfaceItemResolver.resolve(player.getItemInHand(hand)).isEmpty()
                    || NexusCompassBinding.read(player.getItemInHand(hand)) != null) return InteractionResult.PASS;
            if (world.isClientSide()) return InteractionResult.SUCCESS;
            maps.openPlayerAnchor((ServerPlayer) player, hand);
            return InteractionResult.SUCCESS;
        });
    }
}
