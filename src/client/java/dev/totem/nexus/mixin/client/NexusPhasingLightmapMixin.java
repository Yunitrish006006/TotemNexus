package dev.totem.nexus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.totem.nexus.effect.NexusEffects;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightmapRenderStateExtractor.class)
abstract class NexusPhasingLightmapMixin {
    @WrapOperation(method = "extract", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean nexus$nightVision(LocalPlayer player, Holder<MobEffect> effect, Operation<Boolean> original) {
        return original.call(player, effect) || (effect.equals(MobEffects.NIGHT_VISION) && player.hasEffect(NexusEffects.PHASING));
    }
}
