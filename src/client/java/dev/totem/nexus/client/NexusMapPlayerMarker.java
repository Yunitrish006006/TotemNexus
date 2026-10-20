package dev.totem.nexus.client;

import dev.totem.nexus.mixin.client.NexusMapRendererInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import java.util.Optional;

/** Transient vanilla decoration shared by held-item, production Screen, and authorized Observer paths. */
public final class NexusMapPlayerMarker {
    private NexusMapPlayerMarker() { }

    /** Bounded map-local player presentation. It intentionally contains no raw world coordinates. */
    public record Marker(boolean offMap, int x, int y, byte rotation) {
        public Marker {
            if (Math.abs((long)x)>8000000 || Math.abs((long)y)>8000000 || (rotation & 0xFF) > 15) throw new IllegalArgumentException("Map marker rotation must be 0..15");
        }
    }

    public static Marker current(MapItemSavedData map) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.level.dimension().equals(map.dimension)) return null;
        return marker(mc.player.getX(), mc.player.getZ(), mc.player.getYRot(), map.centerX, map.centerZ, map.scale);
    }

    public static Marker currentForView(MapItemSavedData map,int effectiveScale) {
        if(effectiveScale<=map.scale) return current(map);
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || !mc.level.dimension().equals(map.dimension)) return null;
        double dx=mc.player.getX()-map.centerX,dz=mc.player.getZ()-map.centerZ;
        long half=64L<<effectiveScale;
        boolean inside=Math.abs(dx)<half && Math.abs(dz)<half;
        if(!Double.isFinite(dx) || !Double.isFinite(dz)) return null;
        int x=(int)Math.clamp(Math.round(Math.clamp(dx,-half,half)*2/(1<<map.scale)),-8000000L,8000000L);
        int z=(int)Math.clamp(Math.round(Math.clamp(dz,-half,half)*2/(1<<map.scale)),-8000000L,8000000L);
        return new Marker(!inside,x,z,inside?(byte)(((int)Math.floor(mc.player.getYRot()*16.0/360.0+0.5))&15):0);
    }

    public static MapRenderState.MapDecorationRenderState extract(MapItemSavedData map) {
        return extract(current(map));
    }

    public static MapRenderState.MapDecorationRenderState extract(Marker marker) {
        Minecraft mc = Minecraft.getInstance();
        MapDecoration decoration = decoration(marker);
        return decoration == null || mc == null
                ? null
                : ((NexusMapRendererInvoker) (Object) mc.getMapRenderer()).totem$extractDecorationRenderState(decoration);
    }

    public static Marker marker(double x, double z, float yaw, int centerX, int centerZ, int scale) {
        double px = (x - centerX) / (1 << scale), pz = (z - centerZ) / (1 << scale);
        if (!Double.isFinite(px) || !Double.isFinite(pz) || Math.abs(px) >= 320 || Math.abs(pz) >= 320) return null;
        boolean inside = px >= -63 && px <= 63 && pz >= -63 && pz <= 63;
        byte rotation = inside ? (byte) (((int) Math.floor(yaw * 16.0 / 360.0 + 0.5)) & 15) : 0;
        return new Marker(!inside,
                (byte) Math.clamp(Math.round(px * 2), -128, 127),
                (byte) Math.clamp(Math.round(pz * 2), -128, 127), rotation);
    }

    public static MapDecoration decoration(double x, double z, float yaw, int centerX, int centerZ, int scale) {
        return decoration(marker(x, z, yaw, centerX, centerZ, scale));
    }

    public static MapDecoration decoration(Marker marker) {
        if (marker == null) return null;
        return new MapDecoration(marker.offMap() ? MapDecorationTypes.PLAYER_OFF_MAP : MapDecorationTypes.PLAYER,
                (byte)Math.clamp(marker.x(),-128,127), (byte)Math.clamp(marker.y(),-128,127), marker.rotation(), Optional.empty());
    }
}
