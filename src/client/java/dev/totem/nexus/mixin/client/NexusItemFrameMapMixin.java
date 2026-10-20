package dev.totem.nexus.mixin.client;

import dev.totem.nexus.client.NexusMapOverviewClient;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrameRenderer.class)
public abstract class NexusItemFrameMapMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/decoration/ItemFrame;Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;F)V",at=@At("RETURN"))
    private void totem$overview(ItemFrame frame, ItemFrameRenderState state, float partialTick, CallbackInfo ci) {
        if(state.mapId!=null)NexusMapOverviewClient.apply(frame.getItem(),frame.getId(),state.mapRenderState);
    }
}
