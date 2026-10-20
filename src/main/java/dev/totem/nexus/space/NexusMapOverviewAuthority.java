package dev.totem.nexus.space;

import dev.totem.nexus.map.MapOverviewBuilder;
import dev.totem.nexus.network.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.core.component.DataComponents;

/** Bounded server-thread overview generation. Each delivery rechecks the exact source. */
public final class NexusMapOverviewAuthority {
    private static final LinkedHashMap<UUID,Job> JOBS=new LinkedHashMap<>();
    private static final Map<UUID,Long> LAST=new HashMap<>();
    private static boolean registered;
    private NexusMapOverviewAuthority(){}
    public static void register(){
        if(registered)return;registered=true;
        ServerTickEvents.END_SERVER_TICK.register(NexusMapOverviewAuthority::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{JOBS.remove(handler.player.getUUID());LAST.remove(handler.player.getUUID());});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{JOBS.clear();LAST.clear();});
    }
    public static void request(ServerPlayer player,RequestMapOverviewPayload request){
        if(!ServerPlayNetworking.canSend(player,MapOverviewPayload.TYPE))return;
        long now=player.level().getServer().overworld().getGameTime();
        if(now-LAST.getOrDefault(player.getUUID(),Long.MIN_VALUE/2)<20)return;
        LAST.put(player.getUUID(),now);
        if(JOBS.containsKey(player.getUUID())||JOBS.size()>=8)return;
        Job job=create(player,request);
        if(job!=null)JOBS.put(player.getUUID(),job);
    }
    static Job create(ServerPlayer player,RequestMapOverviewPayload request){
        var access=access(player,request);if(access==null)return null;
        var store=NexusMapDetailSavedData.available(player.level()).orElse(null);if(store==null)return null;
        // Only this map's existing recorded pages. No remote chunks or ancestral inference.
        var pages=new ArrayList<>(store.pages(request.mapId()));
        pages.sort(Comparator.comparing((NexusMapDetailSavedData.Page p)->!p.historical));
        return new Job(request,access, pages);
    }
    private static Access access(ServerPlayer player,RequestMapOverviewPayload request){
        if(!player.isAlive()||player.isRemoved())return null;
        MapId id=new MapId(request.mapId());
        ItemStack stack;
        if(request.frameId()<0){
            var held=NexusMapDetailNetworking.heldNexusMap(player,id);if(held==null)return null;
            stack=ItemStack.EMPTY;
            for(var hand:net.minecraft.world.InteractionHand.values()) {
                var candidate=player.getItemInHand(hand);
                if(id.equals(candidate.get(DataComponents.MAP_ID)) && held.boundUnitId().equals(NexusInterfaceBinding.read(candidate))) {stack=candidate;break;}
            }
        }else{
            var entity=player.level().getEntity(request.frameId());
            if(!(entity instanceof ItemFrame frame)||frame.isRemoved()||player.distanceToSqr(frame)>64||!player.hasLineOfSight(frame))return null;
            stack=frame.getItem();
            if(!id.equals(stack.get(DataComponents.MAP_ID)))return null;
        }
        var base=MapItem.getSavedData(id,player.level());if(base==null)return null;
        var binding=NexusMapBindingSavedData.loadCanonical(player.level().getServer().overworld().getDataStorage()).resolve(id,base).orElse(null);
        if(binding==null||!binding.unitId().equals(NexusInterfaceBinding.read(stack)))return null;
        return new Access(base,binding,binding.extent(base));
    }
    private static void tick(MinecraftServer server){
        if(JOBS.isEmpty())return;
        // Round-robin one job; global work bound includes all players.
        var iterator=JOBS.entrySet().iterator();var entry=iterator.next();iterator.remove();
        var player=server.getPlayerList().getPlayer(entry.getKey());if(player==null)return;
        Job job=entry.getValue();
        // Admitted jobs keep progressing while authorized; a fixed age limit can
        // starve legitimate large maps when all eight slots are occupied.
        if(!job.valid(player))return;
        long deadline=System.nanoTime()+2_000_000L;int budget=32768;
        do {int used=job.advance(Math.min(256,budget));budget-=used;if(job.complete())break;}while(budget>0&&System.nanoTime()<deadline);
        if(job.complete()){
            if(job.valid(player)&&ServerPlayNetworking.canSend(player,MapOverviewPayload.TYPE))ServerPlayNetworking.send(player,job.payload());
        }else JOBS.put(entry.getKey(),job);
    }
    private record Access(MapItemSavedData base,NexusMapBindingSavedData.Entry binding,NexusMapExtent extent){}
    static final class Job {
        final RequestMapOverviewPayload request;final Access source;final List<NexusMapDetailSavedData.Page> pages;
        final MapOverviewBuilder builder;int next;boolean baseStarted;
        Job(RequestMapOverviewPayload request,Access source,List<NexusMapDetailSavedData.Page> pages){
            this.request=request;this.source=source;this.pages=pages;builder=new MapOverviewBuilder(source.extent);
        }
        boolean valid(ServerPlayer player){var current=access(player,request);return current!=null&&current.base==source.base&&current.binding.equals(source.binding);}
        int advance(int budget){
            if(builder.layerComplete()){
                if(next<pages.size()){
                    var page=pages.get(next++);builder.beginLayer(page.x,page.z,page.scale,page.overviewColors());
                }else if(!baseStarted){baseStarted=true;builder.beginLayer(source.base.centerX,source.base.centerZ,source.base.scale,source.base.colors);}
                else return 0;
            }
            return builder.advance(budget);
        }
        boolean complete(){return baseStarted&&builder.layerComplete();}
        MapOverviewPayload payload(){if(!complete())throw new IllegalStateException("Incomplete overview");var e=source.extent;return new MapOverviewPayload(request.requestId(),request.mapId(),request.frameId(),e.centerX(),e.centerZ(),e.scale(),source.base.dimension.identifier().toString(),source.base.locked,builder.snapshot());}
    }
}
