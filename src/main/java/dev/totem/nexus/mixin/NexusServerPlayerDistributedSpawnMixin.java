package dev.totem.nexus.mixin;

import dev.totem.nexus.space.NexusDistributedSpawnAuthority;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class NexusServerPlayerDistributedSpawnMixin {
    @Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true)
    private void deadrecall$findDistributedRespawnPosition(
            boolean useSpawnBlock,
            TeleportTransition.PostTeleportTransition postTeleportTransition,
            CallbackInfoReturnable<TeleportTransition> cir) {
        NexusDistributedSpawnAuthority.findRespawnTransition((ServerPlayer) (Object) this, postTeleportTransition)
                .ifPresent(cir::setReturnValue);
    }
}
