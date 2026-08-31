package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusMapLifecycleAuthority;
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
    private void deadrecall$postProcessNexusMap(ItemStack stack, Level level, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        MapPostProcessing processing = stack.get(DataComponents.MAP_POST_PROCESSING);
        NexusMapLifecycleAuthority.PostProcessResult result =
                NexusMapLifecycleAuthority.postProcess(stack, serverLevel, processing);
        if (result == NexusMapLifecycleAuthority.PostProcessResult.NOT_NEXUS) return;
        stack.remove(DataComponents.MAP_POST_PROCESSING);
        // A late anchor/registry race must never turn the recipe output into a
        // second copy of the source MapId. Preview/take gates normally prevent
        // this path; emptying is the final defensive fallback.
        if (result == NexusMapLifecycleAuthority.PostProcessResult.DENIED) stack.setCount(0);
        ci.cancel();
    }
}
