package dev.totem.nexus.mixin.client;

import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuses Minecraft's decoration atlas lookup for transient Nexus map overlays. */
@Mixin(MapRenderer.class)
public interface NexusMapRendererInvoker {
    @Invoker("extractDecorationRenderState")
    MapRenderState.MapDecorationRenderState totem$extractDecorationRenderState(MapDecoration decoration);
}
