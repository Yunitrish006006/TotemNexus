package dev.totem.nexus.gametest.mixin;

import dev.totem.nexus.space.NexusTeleportTestMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class TeleportMessageProbeMixin {
    @Inject(method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void totem$recordTeleportMessage(Component message, CallbackInfo ci) {
        NexusTeleportTestMessages.record(((ServerPlayer) (Object) this).getUUID(), message);
    }
}
