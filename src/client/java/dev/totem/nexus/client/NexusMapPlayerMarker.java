package dev.totem.nexus.client;

import dev.totem.nexus.mixin.client.NexusMapRendererInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import java.util.Optional;

/** Transient vanilla decoration shared by the held-item and production Screen paths. */
public final class NexusMapPlayerMarker {
    private NexusMapPlayerMarker() { }
    public static MapRenderState.MapDecorationRenderState extract(MapItemSavedData map) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || !mc.level.dimension().equals(map.dimension)) return null;
        var decoration=decoration(mc.player.getX(),mc.player.getZ(),mc.player.getYRot(),map.centerX,map.centerZ,map.scale);
        return decoration==null?null:((NexusMapRendererInvoker)(Object)mc.getMapRenderer()).totem$extractDecorationRenderState(decoration);
    }
    public static MapDecoration decoration(double x,double z,float yaw,int centerX,int centerZ,int scale) {
        double px=(x-centerX)/(1<<scale), pz=(z-centerZ)/(1<<scale);
        if(!Double.isFinite(px) || !Double.isFinite(pz) || Math.abs(px)>=320 || Math.abs(pz)>=320) return null;
        boolean inside=px>=-63 && px<=63 && pz>=-63 && pz<=63;
        byte rotation=inside?(byte)(((int)Math.floor(yaw*16.0/360.0+0.5))&15):0;
        return new MapDecoration(inside?MapDecorationTypes.PLAYER:MapDecorationTypes.PLAYER_OFF_MAP,
                (byte)Math.clamp(Math.round(px*2),-128,127),
                (byte)Math.clamp(Math.round(pz*2),-128,127),rotation,Optional.empty());
    }
}
