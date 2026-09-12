package dev.totem.nexus.client;

import dev.totem.nexus.network.NexusMapDetailPayload;
import dev.totem.nexus.network.RequestNexusMapDetailPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Session-only identities of historical vanilla map layers approved by the server. */
public final class NexusMapDetailClientState {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Map<Integer, List<Integer>> BY_MAP_ID = new HashMap<>();
    private static final Map<Integer, RequestNexusMapDetailPayload> VIEWS = new HashMap<>();
    private static int ticks;
    private static long revision;
    private static final java.util.LinkedHashMap<Integer,Boolean> DETAIL_CACHE=new java.util.LinkedHashMap<>(64,0.75f,true);
    private static final java.util.LinkedHashMap<Integer,Boolean> OWNERS=new java.util.LinkedHashMap<>(16,0.75f,true);
    public static long revision() { return revision; }
    public static RequestNexusMapDetailPayload view(int id) { return VIEWS.getOrDefault(id,new RequestNexusMapDetailPayload(id)); }
    public static boolean recognized(int id) { return BY_MAP_ID.containsKey(id); }
    public static void viewport(int id,int x,int z,int radius) {
        VIEWS.put(id,new RequestNexusMapDetailPayload(id,x,z,Math.clamp(radius,1,2048)));
    }

    private NexusMapDetailClientState() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(client.player==null || ++ticks%10!=0
                    || (client.gui.screen() instanceof dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen ro && ro.totem$isObserverReadOnly())) return;
            var heldIds=new java.util.HashSet<Integer>();
            for(var hand:net.minecraft.world.InteractionHand.values()) {
                var stack=client.player.getItemInHand(hand);
                var id=stack.get(net.minecraft.core.component.DataComponents.MAP_ID);
                if(id!=null && dev.totem.nexus.space.NexusInterfaceBinding.read(stack)!=null) heldIds.add(id.id());
            }
            if(client.gui.screen() instanceof NexusSpaceUnitMapScreen screen && screen.observerPayload().mapId()>=0) {
                request(screen.observerPayload().mapId());
            } else {
                VIEWS.clear();
                var ordered=heldIds.stream().sorted().toList();
                if(!ordered.isEmpty()) request(ordered.get(Math.floorMod(ticks/10,ordered.size())));
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void request(int mapId) {
        if (mapId < 0 || !ClientPlayNetworking.canSend(RequestNexusMapDetailPayload.TYPE)) return;
        ClientPlayNetworking.send(VIEWS.getOrDefault(mapId,new RequestNexusMapDetailPayload(mapId)));
    }

    public static void accept(NexusMapDetailPayload payload) {
        if (payload == null) return;
        revision++;
        OWNERS.put(payload.mapId(),true);
        while(OWNERS.size()>16) {
            int oldest=OWNERS.keySet().iterator().next();OWNERS.remove(oldest);BY_MAP_ID.remove(oldest);VIEWS.remove(oldest);
        }
        for(int id:payload.ancestorMapIds()) DETAIL_CACHE.put(id,true);
        BY_MAP_ID.put(payload.mapId(), payload.ancestorMapIds());
        var mc=net.minecraft.client.Minecraft.getInstance();
        while(DETAIL_CACHE.size()>64) {
            int oldest=DETAIL_CACHE.keySet().iterator().next();DETAIL_CACHE.remove(oldest);
            var id=new net.minecraft.world.level.saveddata.maps.MapId(oldest);
            if(mc.level!=null) ((dev.totem.nexus.mixin.client.NexusClientLevelMapAccess)mc.level).totem$mapData().remove(id);
            var texture=((dev.totem.nexus.mixin.client.NexusMapTextureCacheAccess)mc.getMapTextureManager()).totem$mapTextures().remove(oldest);
            if(texture instanceof AutoCloseable closeable) try { closeable.close(); } catch(Exception error) { throw new IllegalStateException("Cannot release map detail texture",error); }
        }
        if(mc.level!=null) for(var layer:payload.geometries()) {
            var id=new net.minecraft.world.level.saveddata.maps.MapId(layer.id());
            var previous=mc.level.getMapData(id);
            var dimension=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.Identifier.parse(layer.dimension()));
            if(previous!=null && previous.centerX==layer.centerX() && previous.centerZ==layer.centerZ()
                    && previous.dimension.equals(dimension) && previous.scale==layer.scale() && previous.locked==layer.locked()) continue;
            var next=dev.totem.nexus.mixin.NexusMapItemSavedDataInvoker.totem$createExact(
                    layer.centerX(),layer.centerZ(),(byte)layer.scale(),false,false,layer.locked(),dimension);
            if(previous!=null) { System.arraycopy(previous.colors,0,next.colors,0,16384); }
            mc.level.overrideMapData(id,next);
        }
    }

    public static List<Integer> ancestorMapIds(int mapId) {
        return BY_MAP_ID.getOrDefault(mapId, List.of());
    }

    public static void clear() {
        BY_MAP_ID.clear(); VIEWS.clear(); DETAIL_CACHE.clear(); OWNERS.clear();ticks=0;
    }

    /** Deterministic client GameTest hook; production values still arrive only from the validated server payload. */
    public static void setForVisualTest(int mapId, List<Integer> ancestorMapIds) {
        accept(new NexusMapDetailPayload(mapId, ancestorMapIds));
    }
}
