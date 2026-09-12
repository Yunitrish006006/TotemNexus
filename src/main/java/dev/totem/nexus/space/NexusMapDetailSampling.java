package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import java.util.*;

/** Vanilla scale-zero colors with explicitly loaded-only, bounded sampling. */
public final class NexusMapDetailSampling {
    private static final Map<Integer,Integer> CURSORS = new HashMap<>();
    private static int rotation;
    private NexusMapDetailSampling() { }
    public static void clear() { CURSORS.clear(); rotation=0; }
    public static void tick(MinecraftServer server) {
        var players=server.getPlayerList().getPlayers();
        if (players.isEmpty()) { clear(); return; }
        Set<Integer> visited = new HashSet<>();
        int budget=2048;
        for (int n=0;n<players.size();n++) {
            ServerPlayer player=players.get(Math.floorMod(rotation+n,players.size()));
            for (InteractionHand hand:InteractionHand.values()) {
                var held=TeleportInterfaceItemResolver.resolve(player,hand).orElse(null);
                if (held==null || held.mapId()==null || !visited.add(held.mapId().id())) continue;
                if (budget==0) continue; // Retain every active map cursor, including this tick's deferred maps.
                var base=MapItem.getSavedData(held.mapId(),player.level());
                if (base==null || base.locked || !base.dimension.equals(player.level().dimension())) continue;
                var store=NexusMapDetailSavedData.get(player.level());
                var binding=NexusMapBindingSavedData.loadCanonical(server.overworld().getDataStorage()).resolve(held.mapId(),base).orElse(null);
                if (binding==null) continue;
                store.initialize(player.level(),held.mapId(),base,binding);
                int cursor=CURSORS.getOrDefault(held.mapId().id(),0);
                int count=Math.min(128,budget); budget-=count;
                int radius=player.level().dimensionType().hasCeiling()?64:128;
                for(int i=0;i<count;i++,cursor=(cursor+1)%65536) {
                    // A coprime stride spreads successive samples across the nearby square.
                    int at=(cursor*4051)&65535;
                    int dx=(at&255)-128, dz=(at>>>8)-128;
                    if(dx*dx+dz*dz>=radius*radius) continue;
                    int x=player.blockPosition().getX()+dx,z=player.blockPosition().getZ()+dz;
                    if(!FilledMapCoverage.isDrawn(base,base.dimension,new BlockPos(x,0,z))) continue;
                    Byte color=sample(player.level(),x,z);
                    if(color!=null && (color&255)/4!=0) store.record(player.level(),held.mapId().id(),x,z,color);
                }
                CURSORS.put(held.mapId().id(),cursor);
            }
        }
        CURSORS.keySet().retainAll(visited); rotation=(rotation+1)%players.size();
    }
    static Byte sample(ServerLevel level,int x,int z) {
        LevelChunk chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
        if(chunk==null || chunk.isEmpty()) return null;
        if(level.dimensionType().hasCeiling()) {
            int seed=x+z*231871; seed=seed*seed*31287121+seed*11;
            MapColor color=((seed>>20)&1)==0?MapColor.DIRT:MapColor.STONE;
            return color.getPackedId(MapColor.Brightness.NORMAL);
        }
        LevelChunk north=level.getChunkSource().getChunkNow(x>>4,(z-1)>>4);
        if(north==null) return null;
        Surface current=surface(level,chunk,x,z), previous=surface(level,north,x,z-1);
        MapColor.Brightness brightness;
        double shade=(current.height-previous.height)*4.0/5.0+(((x+z)&1)-0.5)*0.4;
        if(current.color==MapColor.WATER) {
            double depth=current.depth*0.1+((x+z)&1)*0.2;
            brightness=depth<0.5?MapColor.Brightness.HIGH:depth>0.9?MapColor.Brightness.LOW:MapColor.Brightness.NORMAL;
        } else brightness=shade>0.6?MapColor.Brightness.HIGH:shade< -0.6?MapColor.Brightness.LOW:MapColor.Brightness.NORMAL;
        return current.color.getPackedId(brightness);
    }
    private static Surface surface(ServerLevel level,LevelChunk chunk,int x,int z) {
        int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)+1;
        var pos=new BlockPos.MutableBlockPos(x,y,z);
        BlockState state=Blocks.BEDROCK.defaultBlockState();
        while(y>level.getMinY()) {
            pos.setY(--y); state=chunk.getBlockState(pos);
            if(state.getMapColor(chunk,pos)!=MapColor.NONE) break;
        }
        int depth=0;
        if(!state.getFluidState().isEmpty() && y>level.getMinY()) {
            var below=new BlockPos.MutableBlockPos(x,y-1,z);
            do { depth++; below.setY(y-depth); }
            while(below.getY()>level.getMinY() && !chunk.getBlockState(below).getFluidState().isEmpty());
            if(!state.isFaceSturdy(chunk,pos,Direction.UP)) state=state.getFluidState().createLegacyBlock();
        }
        return new Surface(state.getMapColor(chunk,pos),y,depth);
    }
    private record Surface(MapColor color,int height,int depth) { }
}
