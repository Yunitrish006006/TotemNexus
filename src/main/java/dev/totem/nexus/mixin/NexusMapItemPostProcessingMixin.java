package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusMapLifecycleAuthority;
import dev.totem.nexus.space.NexusMapCraftingTransaction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Nexus SCALE/LOCK results on their server-owned exact anchor. */
@Mixin(MapItem.class)
public abstract class NexusMapItemPostProcessingMixin {
    @Inject(method = "onCraftedPostProcess", at = @At("HEAD"), cancellable = true)
    private void totem$postProcessNexusMap(ItemStack stack, Level level, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (NexusMapCraftingTransaction.reuseProcessed(stack, level)) { ci.cancel(); return; }
        var sourceMapId = stack.get(DataComponents.MAP_ID);
        MapPostProcessing processing = stack.get(DataComponents.MAP_POST_PROCESSING);
        NexusMapLifecycleAuthority.PostProcessResult result =
                NexusMapLifecycleAuthority.postProcess(stack, serverLevel, processing);
        if (result == NexusMapLifecycleAuthority.PostProcessResult.NOT_NEXUS) return;
        if (result == NexusMapLifecycleAuthority.PostProcessResult.DENIED)
            NexusMapCraftingTransaction.abortIfActive(stack, level);
        if (result == NexusMapLifecycleAuthority.PostProcessResult.PROCESSED)
            NexusMapCraftingTransaction.rememberProcessed(stack, level, sourceMapId);
        stack.remove(DataComponents.MAP_POST_PROCESSING);
        // Native menus abort and restore above. Non-menu callers retain the fail-closed fallback.
        if (result == NexusMapLifecycleAuthority.PostProcessResult.DENIED) stack.setCount(0);
        ci.cancel();
    }
}
