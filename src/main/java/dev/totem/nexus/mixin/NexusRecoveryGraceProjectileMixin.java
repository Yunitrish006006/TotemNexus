package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusRecoveryGrace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A launch, rather than chunk-loading an old projectile, ends the rescue. */
@Mixin(Projectile.class)
public abstract class NexusRecoveryGraceProjectileMixin {
    @Inject(method = "applyOnProjectileSpawned", at = @At("HEAD"))
    private void nexus$cancelRescueOnLaunch(ServerLevel level, ItemStack weapon, CallbackInfo ci) {
        NexusRecoveryGrace.projectileLaunched((Projectile) (Object) this);
    }
}
