package dev.totem.nexus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.totem.nexus.effect.NexusEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collection;

@Mixin(LivingEntity.class)
abstract class NexusPhasingEffectMixin {
    @WrapOperation(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean nexus$hasResistance(LivingEntity entity, Holder<MobEffect> effect, Operation<Boolean> original) {
        return original.call(entity, effect) || (effect.equals(MobEffects.RESISTANCE) && entity.hasEffect(NexusEffects.PHASING));
    }

    @WrapOperation(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/LivingEntity;getEffect(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/effect/MobEffectInstance;"))
    private MobEffectInstance nexus$resistance(LivingEntity entity, Holder<MobEffect> effect, Operation<MobEffectInstance> original) {
        MobEffectInstance real = original.call(entity, effect);
        return real == null && effect.equals(MobEffects.RESISTANCE) ? entity.getEffect(NexusEffects.PHASING) : real;
    }

    @Inject(method = "onEffectAdded", at = @At("RETURN"))
    private void nexus$added(MobEffectInstance effect, Entity source, CallbackInfo ci) {
        NexusEffects.updateWeakness((LivingEntity) (Object) this);
    }

    @Inject(method = "onEffectUpdated", at = @At("RETURN"))
    private void nexus$updated(MobEffectInstance effect, boolean attributes, Entity source, CallbackInfo ci) {
        NexusEffects.updateWeakness((LivingEntity) (Object) this);
    }

    @Inject(method = "onEffectsRemoved", at = @At("RETURN"))
    private void nexus$removed(Collection<MobEffectInstance> effects, CallbackInfo ci) {
        NexusEffects.updateWeakness((LivingEntity) (Object) this);
    }
}
