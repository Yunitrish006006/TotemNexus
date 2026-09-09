package dev.totem.nexus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.totem.nexus.effect.NexusEffects;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
abstract class NexusPhasingFogMixin {
    @WrapOperation(method = "computeFogColor", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean nexus$nightVision(LivingEntity entity, Holder<MobEffect> effect, Operation<Boolean> original) {
        return original.call(entity, effect) || (effect.equals(MobEffects.NIGHT_VISION) && entity.hasEffect(NexusEffects.PHASING));
    }
}
