package dev.totem.nexus.gametest.mixin.client;

import dev.totem.nexus.client.ObserverPacketProbe;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ObserverPacketProbeMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void totem$recordObserverPacket(Packet<?> packet, CallbackInfo ci) {
        ObserverPacketProbe.record();
    }
}
