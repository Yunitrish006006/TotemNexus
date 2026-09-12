package dev.totem.nexus.space;

import dev.totem.nexus.network.NexusMapDetailPayload;
import dev.totem.nexus.network.RequestNexusMapDetailPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Held-map authority, geometry metadata and bounded vanilla terrain packet scheduling. */
public final class NexusMapDetailNetworking {
    private static final AtomicBoolean REGISTERED=new AtomicBoolean();
    private static final Map<UUID,Long> LAST=new HashMap<>();
    private static final Map<UUID,Pending> PENDING=new HashMap<>();
    private static final Map<UUID,Map<Integer,Long>> SENT=new HashMap<>();
    private NexusMapDetailNetworking() { }
    public static void registerReceiver() {
        if(!REGISTERED.compareAndSet(false,true)) return;
        ServerPlayNetworking.registerGlobalReceiver(RequestNexusMapDetailPayload.TYPE,(p,c)->c.server().execute(()->send(c.player(),p)));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            NexusMapDetailSampling.tick(server);
            for(var player:server.getPlayerList().getPlayers()) flush(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->clear(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{LAST.clear();PENDING.clear();SENT.clear();NexusMapDetailSampling.clear();});
    }
    private static void clear(UUID id) { LAST.remove(id);PENDING.remove(id);SENT.remove(id); }
    static void send(ServerPlayer player,int id) { send(player,new RequestNexusMapDetailPayload(id)); }
    private static void send(ServerPlayer player,RequestNexusMapDetailPayload request) {
        sendTo(player,player,request,()->true);
    }
    /** Called only by the optional relay after its exact session/provider/access checks. */
    public static void enqueueObserved(ServerPlayer target,ServerPlayer observer,int mapId,int x,int z,int radius,
                                       java.util.function.BooleanSupplier stillAuthorized) {
        if(target==null || observer==null || stillAuthorized==null || !stillAuthorized.getAsBoolean()) return;
        sendTo(target,observer,new RequestNexusMapDetailPayload(mapId,x,z,radius),stillAuthorized);
    }
    private static void sendTo(ServerPlayer player,ServerPlayer recipient,RequestNexusMapDetailPayload request,
                               java.util.function.BooleanSupplier allowed) {
        long now=player.level().getServer().overworld().getGameTime();
        if(now-LAST.getOrDefault(recipient.getUUID(),Long.MIN_VALUE/2)<5) return;
        LAST.put(recipient.getUUID(),now);
        MapId mapId=new MapId(request.mapId());
        if(heldNexusMap(player,mapId)==null) return;
        MapItemSavedData base=MapItem.getSavedData(mapId,player.level());
        var bindings=NexusMapBindingSavedData.loadCanonical(player.level().getServer().overworld().getDataStorage());
        var binding=bindings.resolve(mapId,base).orElse(null);
        if(base==null || binding==null) return;
        var store=NexusMapDetailSavedData.get(player.level()); store.initialize(player.level(),mapId,base,binding);
        int x=request.radius()==0?base.centerX:request.centerX(), z=request.radius()==0?base.centerZ:request.centerZ();
        int radius=request.radius()==0?64<<base.scale:request.radius();
        int extent=64<<base.scale;
        if(Math.abs((long)x-base.centerX)>extent || Math.abs((long)z-base.centerZ)>extent) return;
        var selected=store.pages(mapId.id()).stream()
                .filter(p->Math.abs((long)p.x-x)<radius+(64<<p.scale) && Math.abs((long)p.z-z)<radius+(64<<p.scale))
                .sorted(Comparator.<NexusMapDetailSavedData.Page>comparingInt(p->-p.scale)
                        .thenComparingInt(p->p.historical?0:1)
                        .thenComparingLong(p->Math.abs((long)p.x-x)+Math.abs((long)p.z-z)))
                .limit(NexusMapDetailPayload.MAX_DETAIL_MAPS)
                .sorted(Comparator.<NexusMapDetailSavedData.Page>comparingInt(p->p.scale)
                        .thenComparingInt(p->p.historical?1:0)).toList();
        var packet=new net.minecraft.network.protocol.game.ClientboundMapItemDataPacket(mapId,base.scale,base.locked,
                List.of(),new MapItemSavedData.MapPatch(0,0,128,128,base.colors.clone()));
        var geometry=new ArrayList<NexusMapDetailPayload.Layer>();
        geometry.add(new NexusMapDetailPayload.Layer(mapId.id(),base.centerX,base.centerZ,base.scale,base.dimension.identifier().toString(),base.locked));
        for(var p:selected) geometry.add(new NexusMapDetailPayload.Layer(p.id,p.x,p.z,p.scale,base.dimension.identifier().toString(),true));
        ServerPlayNetworking.send(recipient,new NexusMapDetailPayload(mapId.id(),selected.stream().map(p->p.id).toList(),geometry));
        var sent=SENT.computeIfAbsent(recipient.getUUID(),ignored->new HashMap<>());
        sent.keySet().retainAll(selected.stream().map(p->p.id).toList());
        PENDING.put(recipient.getUUID(),new Pending(mapId,player.getUUID(),allowed,new ArrayDeque<>(selected),packet,now));
    }
    private static void flush(ServerPlayer player) {
        Pending pending=PENDING.get(player.getUUID());
        if(pending==null) return;
        var target=player.level().getServer().getPlayerList().getPlayer(pending.target);
        if(target==null || !pending.allowed.getAsBoolean() || heldNexusMap(target,pending.mapId)==null) { clear(player.getUUID());return; }
        var sent=SENT.computeIfAbsent(player.getUUID(),ignored->new HashMap<>());
        long now=player.level().getServer().overworld().getGameTime();
        int bytes=now==pending.created?4096:0; // Geometry envelope has an enforced <4 KiB bound.
        if(pending.base!=null) { player.connection.send(pending.base);pending.base=null;bytes+=16512; }
        while(!pending.pages.isEmpty() && bytes+16512<=32768) {
            var page=pending.pages.removeFirst();
            if(sent.getOrDefault(page.id,-1L)==page.revision()) continue;
            player.connection.send(page.packet()); bytes+=16512;sent.put(page.id,page.revision());
        }
        if(pending.pages.isEmpty()) PENDING.remove(player.getUUID());
    }
    static TeleportInterfaceItemResolver.ResolvedInterface heldNexusMap(ServerPlayer player,MapId id) {
        for(var hand:InteractionHand.values()) {
            var held=TeleportInterfaceItemResolver.resolve(player,hand).orElse(null);
            if(held!=null && held.type()==TeleportInterfaceType.FILLED_MAP && id.equals(held.mapId())) return held;
        }
        return null;
    }
    private static final class Pending {
        final MapId mapId; final UUID target; final java.util.function.BooleanSupplier allowed;
        final ArrayDeque<NexusMapDetailSavedData.Page> pages; final long created;
        net.minecraft.network.protocol.game.ClientboundMapItemDataPacket base;
        Pending(MapId mapId,UUID target,java.util.function.BooleanSupplier allowed,
                ArrayDeque<NexusMapDetailSavedData.Page> pages,net.minecraft.network.protocol.game.ClientboundMapItemDataPacket base,long created) {
            this.mapId=mapId;this.target=target;this.allowed=allowed;this.pages=pages;this.base=base;this.created=created;
        }
    }
}
