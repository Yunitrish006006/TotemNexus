package dev.totem.nexus.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.totem.nexus.effect.NexusEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
abstract class NexusPhasingNightVisionMixin {
    @WrapOperation(method = "nightVisionScale", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;getEffect(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/effect/MobEffectInstance;"))
    private static MobEffectInstance nexus$nightVision(LivingEntity entity, Holder<MobEffect> effect,
                                                       Operation<MobEffectInstance> original) {
        MobEffectInstance real = original.call(entity, effect);
        return effect.equals(MobEffects.NIGHT_VISION) ? NexusEffects.nightVision(entity, real) : real;
    }
}
