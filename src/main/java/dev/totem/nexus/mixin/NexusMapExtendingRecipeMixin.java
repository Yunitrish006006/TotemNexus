package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusMapLifecycleAuthority;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.MapExtendingRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents a missing/unloaded Nexus anchor from producing a workbench result. */
@Mixin(MapExtendingRecipe.class)
public abstract class NexusMapExtendingRecipeMixin {
    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At("RETURN"), cancellable = true)
    private void deadrecall$requireAvailableAnchor(
            CraftingInput input,
            Level level,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || !(level instanceof ServerLevel serverLevel)) return;
        ItemStack filledMap = ItemStack.EMPTY;
        for (int index = 0; index < input.size(); index++) {
            ItemStack candidate = input.getItem(index);
            if (candidate.has(net.minecraft.core.component.DataComponents.MAP_ID)) {
                filledMap = candidate;
                break;
            }
        }
        if (NexusMapLifecycleAuthority.expansionAnchorEligibility(filledMap, serverLevel)
                == NexusMapLifecycleAuthority.ScaleEligibility.DENIED) {
            cir.setReturnValue(false);
        }
    }
}
