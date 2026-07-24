package dev.totem.nexus.space;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;

import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in interaction wiring for the Nexus-owned bound-compass map workflow. */
public final class NexusMapOpenLifecycle {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private NexusMapOpenLifecycle() { }

    public static void register(NexusMapOpenAuthority maps) {
        if (!REGISTERED.compareAndSet(false, true)) return;
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!player.getItemInHand(hand).is(Items.COMPASS)) return InteractionResult.PASS;
            var sourceId = NexusCompassBinding.read(player.getItemInHand(hand));
            if (sourceId == null) return InteractionResult.PASS;
            if (world.isClientSide()) return InteractionResult.SUCCESS;
            maps.openLodestone((ServerPlayer) player, hand, sourceId);
            return InteractionResult.SUCCESS;
        });
    }
}
