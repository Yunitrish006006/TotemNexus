package dev.totem.nexus.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.totem.nexus.client.NexusMapDetailClientState;
import dev.totem.nexus.client.NexusMapPlayerMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only the held-item production path; item frames and Observer Screens never enter this hook. */
@Mixin(ItemInHandRenderer.class)
public abstract class NexusHeldMapMarkerMixin implements dev.totem.nexus.client.NexusHeldMapVisualTestAccess {
    @org.spongepowered.asm.mixin.Unique private int totem$lastMap=-1;
    @org.spongepowered.asm.mixin.Unique private int totem$markers;
    public int totem$lastHeldMap() { return totem$lastMap; }
    public int totem$heldMarkerCount() { return totem$markers; }
    public void totem$resetHeldProbe() { totem$lastMap=-1;totem$markers=0; }
    @Shadow private MapRenderState mapRenderState;
    @Inject(method="renderMap",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/MapRenderer;extractRenderState(Lnet/minecraft/world/level/saveddata/maps/MapId;Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;Lnet/minecraft/client/renderer/state/MapRenderState;)V",shift=At.Shift.AFTER))
    private void totem$heldMarker(PoseStack pose,SubmitNodeCollector collector,int light,ItemStack stack,CallbackInfo ci) {
        var id=stack.get(DataComponents.MAP_ID);
        if(id==null || !NexusMapDetailClientState.recognized(id.id())) return;
        var data=MapItem.getSavedData(stack,Minecraft.getInstance().level);
        if(data==null) return;
        var marker=NexusMapPlayerMarker.extract(data);
        if(marker!=null) mapRenderState.decorations.add(marker);
        totem$lastMap=id.id();totem$markers=marker==null?0:1;
    }
}
