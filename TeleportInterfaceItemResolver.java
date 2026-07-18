package com.adaptor.deadrecall.space;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.Optional;

/** Resolves teleport interface identity exclusively from a Server-owned ItemStack. */
public final class TeleportInterfaceItemResolver {
    private TeleportInterfaceItemResolver() {
    }

    public static Optional<ResolvedInterface> resolve(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) {
            return Optional.empty();
        }
        return resolve(player.getItemInHand(hand));
    }

    public static Optional<ResolvedInterface> resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        if (stack.is(Items.COMPASS)) {
            return Optional.of(new ResolvedInterface(TeleportInterfaceType.COMPASS, null));
        }
        if (stack.is(Items.RECOVERY_COMPASS)) {
            return Optional.of(new ResolvedInterface(TeleportInterfaceType.RECOVERY_COMPASS, null));
        }
        if (stack.is(Items.BOOK)) {
            return Optional.of(new ResolvedInterface(TeleportInterfaceType.BOOK, null));
        }
        if (stack.is(Items.FILLED_MAP)) {
            MapId mapId = stack.get(DataComponents.MAP_ID);
            return mapId == null
                    ? Optional.empty()
                    : Optional.of(new ResolvedInterface(TeleportInterfaceType.FILLED_MAP, mapId));
        }
        return Optional.empty();
    }

    public record ResolvedInterface(
            TeleportInterfaceType type,
            MapId mapId) {

        public ResolvedInterface {
            if (type == null) {
                throw new IllegalArgumentException("Teleport interface type cannot be null");
            }
            if ((type == TeleportInterfaceType.FILLED_MAP) != (mapId != null)) {
                throw new IllegalArgumentException("Only a filled-map interface may carry a map ID");
            }
        }
    }
}
