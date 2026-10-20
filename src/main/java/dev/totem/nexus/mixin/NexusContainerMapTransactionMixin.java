package dev.totem.nexus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.At;
import dev.totem.nexus.space.NexusMapCraftingTransaction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;

/** Wrap inside clicked's crash reporting so only our controlled refusal is intercepted. */
@Mixin(AbstractContainerMenu.class)
public abstract class NexusContainerMapTransactionMixin {
    @WrapMethod(method = "doClick")
    private void totem$atomicMapTake(int slotId, int button, ContainerInput input, Player player, Operation<Void> original) {
        if (player instanceof ServerPlayer serverPlayer) {
            NexusMapCraftingTransaction.run((AbstractContainerMenu) (Object) this, serverPlayer, slotId,
                    () -> original.call(slotId, button, input, player), null);
        } else original.call(slotId, button, input, player);
    }
    /** Native batch drop loops must commit one safeTake at a time, like shift-click. */
    @WrapOperation(method = "doClick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/inventory/Slot;safeTake(IILnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack totem$atomicSingleTake(Slot slot, int count, int max, Player player, Operation<ItemStack> original) {
        var menu = (AbstractContainerMenu) (Object) this;
        return player instanceof ServerPlayer serverPlayer
                ? NexusMapCraftingTransaction.run(menu, serverPlayer, slot.index,
                    () -> original.call(slot, count, max, player), ItemStack.EMPTY)
                : original.call(slot, count, max, player);
    }
}
