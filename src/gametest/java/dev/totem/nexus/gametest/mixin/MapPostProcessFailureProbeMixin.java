package dev.totem.nexus.gametest.mixin;

import dev.totem.nexus.space.NexusMapLifecycleAuthority;
import dev.totem.nexus.space.NexusMapPostProcessFailureProbe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapPostProcessing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NexusMapLifecycleAuthority.class)
public abstract class MapPostProcessFailureProbeMixin {
    @Inject(method = "postProcess", at = @At("HEAD"), cancellable = true)
    private static void totem$failAfterPreflight(ItemStack stack, ServerLevel level, MapPostProcessing processing,
            CallbackInfoReturnable<NexusMapLifecycleAuthority.PostProcessResult> cir) {
        var failure=NexusMapPostProcessFailureProbe.take(stack);
        if (failure == NexusMapPostProcessFailureProbe.Failure.REMOVE_ORIGIN) {
            var entry = dev.totem.nexus.space.NexusMapBindingSavedData.loadCanonical(level.getServer().overworld().getDataStorage())
                    .get(stack.get(net.minecraft.core.component.DataComponents.MAP_ID)).orElseThrow();
            level.setBlockAndUpdate(entry.anchor().pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        if(failure==NexusMapPostProcessFailureProbe.Failure.ERROR) throw new IllegalStateException("Expected test callback failure");
        if(failure==NexusMapPostProcessFailureProbe.Failure.DENY) cir.setReturnValue(NexusMapLifecycleAuthority.PostProcessResult.DENIED);
    }
}
