package dev.totem.nexus.space;

import dev.totem.nexus.map.MapRecordingQueue;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.saveddata.maps.MapId;

public final class NexusMapResolutionGameTest {
    @GameTest public void fullSchedulerCompletesWalkingTurningAndSharedHolderRoutes(GameTestHelper helper) {
        schedulerRoute(helper,false);
        schedulerRoute(helper,true);
        helper.succeed();
    }

    private static void schedulerRoute(GameTestHelper helper,boolean shared) {
        var level=helper.getLevel();var server=level.getServer();
        var origin=helper.absolutePos(new net.minecraft.core.BlockPos(shared?14000:12000,2,12000));
        int x=Math.floorDiv(origin.getX(),128)*128-8,z=Math.floorDiv(origin.getZ(),8)*8;
        // Known base coverage and loaded terrain are prerequisites, not outcomes of the sampler.
        for(int cx=(x-32)>>4;cx<=(x+64)>>4;cx++) for(int cz=(z-32)>>4;cz<=(z+64)>>4;cz++) level.getChunk(cx,cz);
        for(int dx=-24;dx<64;dx++) for(int dz=-24;dz<64;dz++)
            level.setBlock(new net.minecraft.core.BlockPos(x+dx,origin.getY(),z+dz),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        var a=helper.makeMockServerPlayerInLevel();var b=helper.makeMockServerPlayerInLevel();
        var main=heldMap(helper,a,new net.minecraft.core.BlockPos(x,origin.getY(),z));
        var off=shared?heldMap(helper,a,new net.minecraft.core.BlockPos(x,origin.getY(),z)):null;
        a.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,main);
        if(shared) {
            a.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,off);
            b.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,main.copy());
        }
        var players=shared?java.util.List.of(a,b):java.util.List.of(a);
        var mainId=main.get(net.minecraft.core.component.DataComponents.MAP_ID);
        var ids=shared?java.util.List.of(mainId,off.get(net.minecraft.core.component.DataComponents.MAP_ID)):java.util.List.of(mainId);
        var store=NexusMapDetailSavedData.get(level);
        var routes=new java.util.HashMap<Integer,java.util.Map<net.minecraft.core.BlockPos,int[]>>();
        ids.forEach(id->routes.put(id.id(),new java.util.LinkedHashMap<>()));
        int[] first={-1,-1,-1,-1},last={-1,-1,-1,-1};
        int maxAttempts=0,maxReads=0,maxPending=0,maxDeferred=0,maxLatency=0,totalAttempts=0,finish=-1;
        NexusMapDetailSampling.clear();
        try {
            // Walk east 0.22 blocks/tick, sprint south then west 0.29 blocks/tick;
            // cross an actual 128-block fine-page boundary and retain both turns.
            for(int tick=0;tick<640;tick++) {
                int step=Math.min(tick,239);
                double dx=step<80?step*.22:step<160?80*.22:80*.22-(step-160)*.29;
                double dz=step<80?0:Math.min(step-80,80)*.29;
                a.setPos(x+dx+.5,origin.getY()+1,z+dz+.5);
                b.setPos(x+dx+32.5,origin.getY()+1,z+dz+.5);
                for(var player:players) for(var hand:net.minecraft.world.InteractionHand.values()) {
                    var id=player.getItemInHand(hand).get(net.minecraft.core.component.DataComponents.MAP_ID);
                    if(id==null) continue;
                    var pos=player.blockPosition();
                    var tile=new net.minecraft.core.BlockPos(Math.floorDiv(pos.getX(),8)*8,0,Math.floorDiv(pos.getZ(),8)*8);
                    final int admitted=tick;
                    routes.get(id.id()).computeIfAbsent(tile,ignored->new int[]{admitted,-1,-1,-1,-1});
                }
                var usage=NexusMapDetailSampling.tick(server,players,tick);
                if(usage.attempts()>2048 || usage.blockReads()>16384 || usage.blockReads()<0
                        || usage.pendingTiles()>1024*ids.size() || usage.deferredCells()>1024*ids.size())
                    helper.fail("Production scheduler exceeded global or queue bounds");
                maxAttempts=Math.max(maxAttempts,usage.attempts());maxReads=Math.max(maxReads,usage.blockReads());
                maxPending=Math.max(maxPending,usage.pendingTiles());maxDeferred=Math.max(maxDeferred,usage.deferredCells());
                totalAttempts+=usage.attempts();
                // Request during movement, then exactly one real 32768-read / 2 ms build slice.
                for(var id:ids) for(int scale=1;scale<=3;scale++) store.resolutionPages(level,id.id(),scale,x+24,z+16,128);
                store.buildResolutions();
                boolean complete=true;
                for(var id:ids) for(int scale=0;scale<=3;scale++) {
                    var pages=store.resolutionPages(level,id.id(),scale,x+24,z+16,128);
                    for(var entry:routes.get(id.id()).entrySet()) {
                        var times=entry.getValue();if(times[scale+1]>=0) continue;
                        var tile=entry.getKey();boolean covered=true;
                        for(int px=0;px<8;px+=1<<scale) for(int pz=0;pz<8;pz+=1<<scale)
                            covered &= covered(pages,scale,tile.getX()+px,tile.getZ()+pz);
                        if(covered) {
                            times[scale+1]=tick;if(first[scale]<0) first[scale]=tick;last[scale]=tick;
                            maxLatency=Math.max(maxLatency,tick-times[0]);
                        } else complete=false;
                    }
                }
                if(tick>=239 && complete) {finish=tick;break;}
            }
            if(finish<0) helper.fail("Eligible route footprints did not finish within 240 movement + 400 hold ticks: "+routes);
            for(int scale=0;scale<=3;scale++) if(first[scale]<0 || first[scale]>=240)
                helper.fail("Scale "+scale+" made no completed-footprint progress during movement");
            System.out.println("NEXUS_SCHEDULER_ROUTE shared="+shared+" finishTick="+finish
                    +" firstScale0to3="+java.util.Arrays.toString(first)+" lastScale0to3="+java.util.Arrays.toString(last)
                    +" maxFootprintLatency="+maxLatency+" totalAttempts="+totalAttempts+" maxAttempts="+maxAttempts
                    +" maxReads="+maxReads+" maxPending="+maxPending+" maxDeferred="+maxDeferred
                    +" routeTiles="+routes.values().stream().mapToInt(java.util.Map::size).sum());
        } finally {NexusMapDetailSampling.clear();}
    }

    private static boolean covered(java.util.List<NexusMapDetailSavedData.Page> pages,int scale,int x,int z) {
        for(var page:pages) {
            if(page.scale!=scale || page.historical) continue;
            int px=Math.floorDiv(x-page.x+(64<<scale),1<<scale),pz=Math.floorDiv(z-page.z+(64<<scale),1<<scale);
            if(px>=0&&px<128&&pz>=0&&pz<128&&(page.color(px,pz)&255)/4!=0) return true;
        }
        return false;
    }

    private static net.minecraft.world.item.ItemStack heldMap(GameTestHelper helper,net.minecraft.server.level.ServerPlayer owner,net.minecraft.core.BlockPos anchor) {
        var level=helper.getLevel();var unit=java.util.UUID.randomUUID();var id=level.getFreeMapId();
        var base=NexusMapLifecycleAuthority.exactData(anchor.getX(),anchor.getZ(),(byte)4,false,level.dimension());
        java.util.Arrays.fill(base.colors,(byte)22);level.setMapData(id,base);
        NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage()).put(new NexusSpaceUnitRecord(
                unit,SpaceUnitType.LODESTONE,level.dimension(),anchor,owner.getUUID(),"Scheduler fixture",SpaceUnitVisibility.PUBLIC,
                SpaceUnitStatus.ACTIVE,java.util.Set.of(),java.util.Set.of(),SpaceStructureSnapshot.EMPTY,1L,1L));
        NexusMapBindingSavedData.loadCanonical(level.getServer().overworld().getDataStorage()).bind(id,unit,net.minecraft.core.GlobalPos.of(level.dimension(),anchor),base);
        var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FILLED_MAP);
        stack.set(net.minecraft.core.component.DataComponents.MAP_ID,id);NexusInterfaceBinding.writeIdentity(stack,unit);
        return stack;
    }

    @GameTest public void eachDerivedLevelUsesOriginalCoverageAndRebuildsAfterReload(GameTestHelper helper) {
        var level=helper.getLevel();var store=new NexusMapDetailSavedData();var id=level.getFreeMapId();
        for(int z=0;z<128;z++) for(int x=0;x<128;x++) {
            int a=x&7,b=z&7;
            byte color=(byte)(a==0&&b==0?4:a<2&&b<2?8:a<4&&b<4?12:16);
            // Leave one 8x8 footprint incomplete, checking that it remains unknown at all LODs.
            if(x!=127 || z!=127) store.record(level,id.id(),x-128,z,color);
        }
        for(int scale=1;scale<=3;scale++) {
            var pages=store.resolutionPages(level,id.id(),scale,-64,64,128);drain(store);
            var page=pages.getFirst(); int size=1<<scale;
            int px=(-128-page.x+(64<<scale))/size,pz=(-page.z+(64<<scale))/size;
            int expected=scale==1?8:scale==2?12:16;
            if(Byte.toUnsignedInt(page.color(px,pz))!=expected) helper.fail("Wrong actual resolution at scale "+scale);
            if(page.color(px+127/size,pz+127/size)!=0) helper.fail("Incomplete footprint invented terrain");
        }
        var data=NexusMapDetailSavedData.CODEC.encodeStart(NbtOps.INSTANCE,store).getOrThrow();
        var restored=NexusMapDetailSavedData.CODEC.parse(NbtOps.INSTANCE,data).getOrThrow();
        if(restored.derivedCacheSize()!=0 || restored.pages(id.id()).size()!=1) helper.fail("Derived cache leaked into authoritative save");
        var rebuilt=restored.resolutionPages(level,id.id(),3,-64,64,128);drain(restored);
        if(rebuilt.getFirst().color(112,0)!=16) helper.fail("Old finest records did not rebuild scale-three detail");
        helper.succeed();
    }

    @GameTest public void lockedDerivedCacheRemainsIsolatedFromLiveSource(GameTestHelper helper) {
        var level=helper.getLevel();var store=new NexusMapDetailSavedData();MapId live=level.getFreeMapId(),locked=level.getFreeMapId();
        for(int z=0;z<8;z++) for(int x=0;x<8;x++) store.record(level,live.id(),x,z,(byte)22);
        store.derive(level,live,NexusMapLifecycleAuthority.exactData(0,0,(byte)4,false,level.dimension()),null,locked);
        var frozen=store.resolutionPages(level,locked.id(),3,0,0,128);drain(store);
        for(int z=0;z<8;z++) for(int x=0;x<8;x++) store.record(level,live.id(),x,z,(byte)30);
        var updated=store.resolutionPages(level,live.id(),3,0,0,128);drain(store);
        if(frozen.getFirst().color(0,0)!=22 || updated.getFirst().color(0,0)!=30) helper.fail("LOD cache crossed COW/lock boundary");
        helper.succeed();
    }

    @GameTest public void movingRouteCompletesLoadedFootprintsWithinBudget(GameTestHelper helper) {
        var level=helper.getLevel();var id=level.getFreeMapId();var store=new NexusMapDetailSavedData();
        // Keep the large walking fixture away from concurrently running neighboring test structures.
        var origin=helper.absolutePos(new net.minecraft.core.BlockPos(4096,2,4096));
        int tx=Math.floorDiv(origin.getX(),8),tz=Math.floorDiv(origin.getZ(),8);
        // Load only the fixture region before measurement; the production sampler never does so.
        for(int cx=Math.floorDiv(tx*8,16);cx<=Math.floorDiv((tx+8)*8-1,16);cx++)
            for(int cz=Math.floorDiv(tz*8-1,16);cz<=Math.floorDiv(tz*8+7,16);cz++) level.getChunk(cx,cz);
        for(int x=tx*8;x<(tx+8)*8;x++) for(int z=tz*8-1;z<tz*8+8;z++)
            level.setBlock(new net.minecraft.core.BlockPos(x,origin.getY(),z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        var base=NexusMapLifecycleAuthority.exactData(tx*8,tz*8,(byte)4,false,level.dimension());
        java.util.Arrays.fill(base.colors,(byte)22);
        var queue=new MapRecordingQueue();int attempts=0;
        // Move one block each test step, admitting each crossed 8x8 footprint once.
        for(int step=0;step<64;step++) {
            queue.offer(tx+step/8,tz,true,step);
            int[] reads={16384};
            int used=NexusMapDetailSampling.advance(level,id.id(),base,store,queue,step,128,reads,Long.MAX_VALUE);
            if(used>128 || reads[0]<0) helper.fail("Recording exceeded its deterministic budget");
            attempts+=used;
        }
        int known=0;for(int x=tx*8;x<(tx+8)*8;x++) for(int z=tz*8;z<tz*8+8;z++) if(store.known(id.id(),x,z)) known++;
        if(known!=512) helper.fail("Moving route left eligible holes: "+known+"/512");
        // Replay 0.3.27's relative stride with the same route and 128 attempts per step.
        var old=new java.util.HashSet<Long>();int cursor=0;
        for(int step=0;step<64;step++) for(int i=0;i<128;i++,cursor=(cursor+1)&65535) {
            int at=cursor*4051&65535,dx=(at&255)-128,dz=(at>>>8)-128;
            int x=tx*8+step+dx,z=tz*8+dz;
            if(dx*dx+dz*dz<128*128 && x>=tx*8 && x<(tx+8)*8 && z>=tz*8 && z<tz*8+8) old.add(((long)x<<32)|(z&0xffffffffL));
        }
        if(known<=old.size()) helper.fail("Route prioritization did not improve coverage against old candidate schedule");
        for(int scale=1;scale<=3;scale++) {
            var pages=store.resolutionPages(level,id.id(),scale,tx*8+32,tz*8+4,128);drain(store);
            for(int tile=0;tile<8;tile++) {
                int x=(tx+tile)*8,z=tz*8;boolean found=false;
                for(var p:pages) {int px=(x-p.x+(64<<scale))>>scale,pz=(z-p.z+(64<<scale))>>scale;
                    if(px>=0&&px<128&&pz>=0&&pz<128&&p.color(px,pz)!=0) found=true;}
                if(!found) helper.fail("Route has no completed derived coverage at scale "+scale);
            }
        }
        System.out.println("NEXUS_ROUTE_COVERAGE new="+known+"/512 oldCandidateUpperBound="+old.size()+"/512 attempts="+attempts+" steps=64 budget=128/step");
        helper.succeed();
    }

    private static void drain(NexusMapDetailSavedData store) {
        for(int n=0;n<2048 && store.pendingResolutionBuilds()>0;n++) store.buildResolutions();
        if(store.pendingResolutionBuilds()!=0) throw new AssertionError("LOD work failed to converge");
    }

    @GameTest public void unknownBaseCoverageRetriesWithoutInventingTerrain(GameTestHelper helper) {
        var level=helper.getLevel();var store=new NexusMapDetailSavedData();var id=level.getFreeMapId();
        var origin=helper.absolutePos(new net.minecraft.core.BlockPos(8192,2,8192));
        int x=Math.floorDiv(origin.getX(),8)*8,z=Math.floorDiv(origin.getZ(),8)*8;
        level.getChunk(x>>4,z>>4);level.getChunk(x>>4,(z-1)>>4);
        var base=NexusMapLifecycleAuthority.exactData(x,z,(byte)4,false,level.dimension());
        var queue=new MapRecordingQueue();queue.offer(x/8,z/8,true,0);
        NexusMapDetailSampling.advance(level,id.id(),base,store,queue,0,64,new int[]{16384},Long.MAX_VALUE);
        if(!store.pages(id.id()).isEmpty() || queue.deferredSize()!=64) helper.fail("Unknown base terrain was invented or pending coordinates lost");
        java.util.Arrays.fill(base.colors,(byte)22);
        if(NexusMapDetailSampling.advance(level,id.id(),base,store,queue,1,64,new int[]{0},Long.MAX_VALUE)!=0)
            helper.fail("Exhausted read budget consumed queued work");
        for(int tick=2;tick<10;tick++) NexusMapDetailSampling.advance(level,id.id(),base,store,queue,tick,64,new int[]{16384},Long.MAX_VALUE);
        for(int dx=0;dx<8;dx++) for(int dz=0;dz<8;dz++)
            if(!store.known(id.id(),x+dx,z+dz)) helper.fail("Newly valid base coverage failed to catch up");
        helper.succeed();
    }
}
