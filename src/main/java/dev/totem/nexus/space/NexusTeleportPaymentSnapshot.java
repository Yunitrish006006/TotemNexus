package dev.totem.nexus.space;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.ArrayList;

/** Rollback for the synchronous payment/teleport transaction, preserving stack components. */
final class NexusTeleportPaymentSnapshot {
    private final int food;
    private final float saturation;
    private final List<ItemStack> inventory = new ArrayList<>();
    NexusTeleportPaymentSnapshot(ServerPlayer player) {
        food = player.getFoodData().getFoodLevel();
        saturation = player.getFoodData().getSaturationLevel();
        for (int slot=0; slot<player.getInventory().getContainerSize(); slot++)
            inventory.add(player.getInventory().getItem(slot).copy());
    }
    void restore(ServerPlayer player) {
        player.getFoodData().setFoodLevel(food);
        player.getFoodData().setSaturation(saturation);
        for (int slot=0; slot<inventory.size(); slot++)
            player.getInventory().setItem(slot, inventory.get(slot).copy());
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
