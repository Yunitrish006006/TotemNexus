package dev.totem.nexus.mixin;

import dev.totem.nexus.effect.NexusEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep vanilla potion processing intact and layer the server-owned rescue flag afterward. */
@Mixin(LivingEntity.class)
public abstract class NexusRecoveryGraceVisibilityMixin {
    @Inject(method = "updateInvisibilityStatus", at = @At("RETURN"))
    private void nexus$recoveryVisibility(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && player.hasEffect(NexusEffects.PHASING)) player.setInvisible(true);
    }
}
