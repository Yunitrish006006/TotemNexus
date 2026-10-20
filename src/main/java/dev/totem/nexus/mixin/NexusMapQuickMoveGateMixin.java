package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusMapCraftingGuard;
import dev.totem.nexus.space.NexusMapCraftingTransaction;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Each quick-move iteration revalidates before post-processing or input consumption. */
@Mixin({CraftingMenu.class, InventoryMenu.class, CartographyTableMenu.class})
public abstract class NexusMapQuickMoveGateMixin {
    @WrapMethod(method = "quickMoveStack")
    private ItemStack totem$atomicMapQuickMove(Player player, int slotId, Operation<ItemStack> original) {
        return player instanceof ServerPlayer serverPlayer
                ? NexusMapCraftingTransaction.run((AbstractContainerMenu) (Object) this, serverPlayer, slotId,
                    () -> original.call(player, slotId), ItemStack.EMPTY)
                : original.call(player, slotId);
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void totem$validateMapResult(Player player, int slotId, CallbackInfoReturnable<ItemStack> cir) {
        if (player instanceof ServerPlayer serverPlayer
                && !NexusMapCraftingGuard.allowResultTake((AbstractContainerMenu) (Object) this,
                slotId, serverPlayer, true)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
