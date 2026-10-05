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
    private static final Map<Integer,Work> WORK = new HashMap<>();
    private static int rotation;
    private NexusMapDetailSampling() { }
    public static void clear() { WORK.clear(); rotation=0; }
    public static void tick(MinecraftServer server) {
        tick(server,server.getPlayerList().getPlayers(),server.overworld().getGameTime());
    }
    // Testable input boundary only: production authorization, admission and real deadlines stay here.
    static TickUsage tick(MinecraftServer server,List<ServerPlayer> players,long tick) {
        if (players.isEmpty()) { clear(); return new TickUsage(0,0,0,0); }
        Set<Integer> visited = new HashSet<>();
        int budget=2048;
        int[] blockReads={16384};
        long deadline=System.nanoTime()+3_000_000L;
        for (int n=0;n<players.size();n++) {
            ServerPlayer player=players.get(Math.floorMod(rotation+n,players.size()));
            for (int h=0;h<2;h++) {
                InteractionHand hand=InteractionHand.values()[(h+(int)(tick&1))&1];
                var held=TeleportInterfaceItemResolver.resolve(player,hand).orElse(null);
                if (held==null || held.mapId()==null) continue;
                var base=MapItem.getSavedData(held.mapId(),player.level());
                if (base==null || base.locked || !base.dimension.equals(player.level().dimension())) continue;
                var store=NexusMapDetailSavedData.get(player.level());
                var binding=NexusMapBindingSavedData.loadCanonical(server.overworld().getDataStorage()).resolve(held.mapId(),base).orElse(null);
                if (binding==null) continue;
                boolean first=visited.add(held.mapId().id());
                store.initialize(player.level(),held.mapId(),base,binding);
                Work work=WORK.computeIfAbsent(held.mapId().id(),ignored->new Work());
                int radius=player.level().dimensionType().hasCeiling()?64:128;
                int tx=Math.floorDiv(player.blockPosition().getX(),8),tz=Math.floorDiv(player.blockPosition().getZ(),8);
                // Complete aligned footprints around the route before distant detail.
                for(int dz=-2;dz<=2;dz++) for(int dx=-2;dx<=2;dx++) work.queue.offer(tx+dx,tz+dz,true,tick);
                for(int i=0;i<4;i++) {
                    int at=work.cursor++&1023,dx=(at&31)-16,dz=(at>>>5)-16;
                    if(dx*dx+dz*dz<(radius/8)*(radius/8)) work.queue.offer(tx+dx,tz+dz,false,tick);
                }
                if(!first) continue; // Other holders still add their own legitimate nearby work.
                budget-=advance(player.level(),held.mapId().id(),base,store,work.queue,tick,Math.min(512,budget),blockReads,deadline);
            }
        }
        WORK.keySet().retainAll(visited); rotation=(rotation+1)%players.size();
        return new TickUsage(2048-budget,16384-blockReads[0],
                WORK.values().stream().mapToInt(w->w.queue.size()).sum(),
                WORK.values().stream().mapToInt(w->w.queue.deferredSize()).sum());
    }
    record TickUsage(int attempts,int blockReads,int pendingTiles,int deferredCells) { }
    static int advance(ServerLevel level,int owner,net.minecraft.world.level.saveddata.maps.MapItemSavedData base,
                       NexusMapDetailSavedData store,dev.totem.nexus.map.MapRecordingQueue queue,
                       long tick,int attempts,int[] blockReads,long deadline) {
        int used=0;
        Map<Long,Surface> surfaces=new HashMap<>(); // At most two columns per attempted sample; tick-local only.
        while(used<attempts && blockReads[0]>=2 && System.nanoTime()<deadline) {
            var candidate=queue.next(tick); if(candidate==null) break;
            used++;
            int x=candidate.x(),z=candidate.z();
            if(!candidate.refresh() && store.known(owner,x,z)) continue;
            var position=new BlockPos(x,0,z);
            if(!FilledMapCoverage.isDrawn(base,base.dimension,position)) {
                if(FilledMapCoverage.covers(base.dimension,base.centerX,base.centerZ,base.scale,base.dimension,position))
                    queue.defer(candidate,tick);
                continue;
            }
            Byte color=sample(level,x,z,blockReads,surfaces);
            if(color==null) queue.defer(candidate,tick,blockReads[0]==0);
            else if((color&255)/4!=0) store.record(level,owner,x,z,color);
        }
        return used;
    }
    private static final class Work {
        final dev.totem.nexus.map.MapRecordingQueue queue=new dev.totem.nexus.map.MapRecordingQueue();
        int cursor;
    }
    static Byte sample(ServerLevel level,int x,int z) {
        return sample(level,x,z,new int[]{16384},new HashMap<>());
    }
    private static Byte sample(ServerLevel level,int x,int z,int[] blockReads,Map<Long,Surface> surfaces) {
        LevelChunk chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
        if(chunk==null || chunk.isEmpty()) return null;
        if(level.dimensionType().hasCeiling()) {
            int seed=x+z*231871; seed=seed*seed*31287121+seed*11;
            MapColor color=((seed>>20)&1)==0?MapColor.DIRT:MapColor.STONE;
            return color.getPackedId(MapColor.Brightness.NORMAL);
        }
        LevelChunk north=level.getChunkSource().getChunkNow(x>>4,(z-1)>>4);
        if(north==null) return null;
        Surface current=cachedSurface(level,chunk,x,z,blockReads,surfaces), previous=cachedSurface(level,north,x,z-1,blockReads,surfaces);
        if(current==null || previous==null) return null;
        MapColor.Brightness brightness;
        double shade=(current.height-previous.height)*4.0/5.0+(((x+z)&1)-0.5)*0.4;
        if(current.color==MapColor.WATER) {
            double depth=current.depth*0.1+((x+z)&1)*0.2;
            brightness=depth<0.5?MapColor.Brightness.HIGH:depth>0.9?MapColor.Brightness.LOW:MapColor.Brightness.NORMAL;
        } else brightness=shade>0.6?MapColor.Brightness.HIGH:shade< -0.6?MapColor.Brightness.LOW:MapColor.Brightness.NORMAL;
        return current.color.getPackedId(brightness);
    }
    private static Surface cachedSurface(ServerLevel level,LevelChunk chunk,int x,int z,int[] reads,Map<Long,Surface> cache) {
        long key=((long)x<<32)|(z&0xffffffffL);
        Surface result=cache.get(key);
        if(result==null) {result=surface(level,chunk,x,z,reads);if(result!=null) cache.put(key,result);}
        return result;
    }
    private static Surface surface(ServerLevel level,LevelChunk chunk,int x,int z,int[] blockReads) {
        int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)+1;
        var pos=new BlockPos.MutableBlockPos(x,y,z);
        BlockState state=Blocks.BEDROCK.defaultBlockState();
        while(y>level.getMinY()) {
            if(blockReads[0]--<=0) { blockReads[0]=0;return null; }
            pos.setY(--y); state=chunk.getBlockState(pos);
            if(state.getMapColor(chunk,pos)!=MapColor.NONE) break;
        }
        int depth=0;
        if(!state.getFluidState().isEmpty() && y>level.getMinY()) {
            var below=new BlockPos.MutableBlockPos(x,y-1,z);
            do {
                depth++; below.setY(y-depth);
                if(below.getY()<=level.getMinY()) break;
                if(blockReads[0]--<=0) { blockReads[0]=0;return null; }
            } while(!chunk.getBlockState(below).getFluidState().isEmpty());
            if(!state.isFaceSturdy(chunk,pos,Direction.UP)) state=state.getFluidState().createLegacyBlock();
        }
        return new Surface(state.getMapColor(chunk,pos),y,depth);
    }
    private record Surface(MapColor color,int height,int depth) { }
}
