package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusMapLifecycleAuthority;
import dev.totem.nexus.space.NexusMapRecipeInputs;
import net.minecraft.world.item.component.MapPostProcessing;
import java.util.ArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.MapExtendingRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents a missing/unloaded Nexus anchor from producing a workbench result. */
@Mixin(MapExtendingRecipe.class)
public abstract class NexusMapExtendingRecipeMixin {
    @Shadow @Final private ShapedRecipePattern pattern;
    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z",
            at = @At("RETURN"), cancellable = true)
    private void totem$requireAvailableAnchor(
            CraftingInput input,
            Level level,
            CallbackInfoReturnable<Boolean> cir) {
        if (!(level instanceof ServerLevel serverLevel) || !this.pattern.matches(input)) return;
        var cells = new ArrayList<ItemStack>();
        for (int i = 0; i < input.size(); i++) cells.add(input.getItem(i));
        var source = input.width() == 3 && input.height() == 3
                ? NexusMapRecipeInputs.workbenchSource(cells, MapPostProcessing.SCALE) : java.util.Optional.<ItemStack>empty();
        if (source.isEmpty()) return;
        var eligibility = NexusMapLifecycleAuthority.expansionAnchorEligibility(source.get(), serverLevel);
        if (eligibility != NexusMapLifecycleAuthority.ScaleEligibility.NOT_NEXUS)
            cir.setReturnValue(eligibility == NexusMapLifecycleAuthority.ScaleEligibility.ALLOWED);
    }
}
