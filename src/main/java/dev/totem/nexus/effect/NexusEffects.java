package dev.totem.nexus.effect;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** One synchronized Nexus effect; no vanilla potion instances are synthesized or mutated. */
public final class NexusEffects {
    public static final Holder<MobEffect> PHASING = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, Identifier.fromNamespaceAndPath("totem-nexus", "phasing"),
            new PhasingEffect());
    private static final Identifier WEAKNESS_MODIFIER = Identifier.fromNamespaceAndPath("totem-nexus", "phasing_weakness");

    private NexusEffects() { }
    public static void register() { }

    /** Reconcile only our modifier, allowing real Weakness of any strength to take precedence. */
    public static void updateWeakness(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        var attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) return;
        if (entity.hasEffect(PHASING) && !entity.hasEffect(MobEffects.WEAKNESS)) {
            attack.addOrUpdateTransientModifier(new AttributeModifier(WEAKNESS_MODIFIER, -4.0,
                    AttributeModifier.Operation.ADD_VALUE));
        } else {
            attack.removeModifier(WEAKNESS_MODIFIER);
        }
    }

    /** Select the longer night-vision source without changing either effect's lifetime. */
    public static MobEffectInstance nightVision(LivingEntity entity, MobEffectInstance vanilla) {
        MobEffectInstance phasing = entity.getEffect(PHASING);
        if (phasing == null) return vanilla;
        if (vanilla == null || phasing.isInfiniteDuration()
                || (!vanilla.isInfiniteDuration() && phasing.getDuration() > vanilla.getDuration())) return phasing;
        return vanilla;
    }

    private static final class PhasingEffect extends MobEffect {
        private PhasingEffect() { super(MobEffectCategory.BENEFICIAL, 0x92C7D6); }
    }
}
