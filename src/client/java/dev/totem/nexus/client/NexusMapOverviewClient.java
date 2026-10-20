package dev.totem.nexus.client;

import dev.totem.nexus.network.MapOverviewPayload;
import dev.totem.nexus.network.RequestMapOverviewPayload;
import dev.totem.nexus.space.NexusInterfaceBinding;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Session-local palette textures used only by the native held/frame renderers. */
public final class NexusMapOverviewClient {
    private static final LinkedHashMap<Key,Entry> ENTRIES = new LinkedHashMap<>(16,.75f,true);
    private static ClientLevel level;
    private static long ticks;
    private static boolean registered;
    private record Key(int map, int frame, UUID binding) {}
    private static final class Entry {
        final Key key;
        final Identifier textureId = Identifier.fromNamespaceAndPath("totem", "map_overview/"+UUID.randomUUID());
        DynamicTexture texture;
        MapOverviewPayload payload;
        RequestMapOverviewPayload pending;
        long seen, sent=-100, received=-1000;
        Entry(Key key) { this.key=key; }
    }
    private NexusMapOverviewClient() {}
    public static void register() {
        if(registered)return; registered=true;
        ClientPlayNetworking.registerGlobalReceiver(MapOverviewPayload.TYPE,
                (payload,context)->context.client().execute(()->accept(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(client->tick());
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->clear());
    }
    private static boolean readOnly() {
        var mc=Minecraft.getInstance();
        return (mc.player!=null&&mc.getCameraEntity()!=mc.player) || mc.gui.screen() instanceof dev.totem.core.api.v1.client.observer.ObserverReadOnlyScreen ro
                && ro.totem$isObserverReadOnly();
    }
    private static void session() {
        var current=Minecraft.getInstance().level;
        if(level!=current){clear();level=current;}
    }
    public static void clear() {
        var manager=Minecraft.getInstance().getTextureManager();
        for(var entry:ENTRIES.values())if(entry.texture!=null)manager.release(entry.textureId);
        ENTRIES.clear();level=null;ticks=0;
    }
    private static Key key(ItemStack stack,int frame) {
        var id=stack.get(DataComponents.MAP_ID);var binding=NexusInterfaceBinding.read(stack);
        return id==null||binding==null?null:new Key(id.id(),frame,binding);
    }
    private static boolean valid(Key key) {
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null)return false;
        if(key.frame<0) {
            for(var hand:InteractionHand.values())if(key.equals(key(mc.player.getItemInHand(hand),-1)))return true;
            return false;
        }
        return mc.level.getEntity(key.frame) instanceof ItemFrame frame && !frame.isRemoved()
                && mc.player.distanceToSqr(frame)<=64 && mc.player.hasLineOfSight(frame)
                && key.equals(key(frame.getItem(),key.frame));
    }
    private static void tick() {
        session(); ticks++;
        if(readOnly())return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        // Only held items and recently rendered frames are candidates; never scan the world.
        for(var hand:InteractionHand.values())touch(mc.player.getItemInHand(hand),-1);
        var iterator=ENTRIES.values().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();
            if(!valid(entry.key)||ticks-entry.seen>100) {
                if(entry.texture!=null)mc.getTextureManager().release(entry.textureId);
                iterator.remove();
            }
        }
        if(ticks%20!=0||!ClientPlayNetworking.canSend(RequestMapOverviewPayload.TYPE))return;
        Entry next=null;
        for(var entry:ENTRIES.values()) {
            if(ticks-entry.received<200||ticks-entry.sent<40)continue;
            if(next==null||entry.sent<next.sent)next=entry;
        }
        if(next!=null) {
            // Retry the same correlation ID: replacing it would discard long-running jobs.
            if(next.pending==null)next.pending=new RequestMapOverviewPayload(UUID.randomUUID(),next.key.map,next.key.frame);
            next.sent=ticks;ClientPlayNetworking.send(next.pending);
        }
    }
    private static Entry touch(ItemStack stack,int frame) {
        if(readOnly())return null;
        var key=key(stack,frame);if(key==null||!valid(key))return null;
        var entry=ENTRIES.get(key);
        if(entry==null) {
            if(ENTRIES.size()>=16)return null;
            entry=new Entry(key);ENTRIES.put(key,entry);
        }
        entry.seen=ticks;return entry;
    }
    public static void accept(MapOverviewPayload payload) {
        session();if(readOnly())return;
        for(var entry:ENTRIES.values()) {
            if(!payload.matches(entry.pending)||!valid(entry.key))continue;
            var base=level.getMapData(new net.minecraft.world.level.saveddata.maps.MapId(entry.key.map));
            var corrected=geometry(payload,base);
            if(corrected!=base)level.overrideMapData(new net.minecraft.world.level.saveddata.maps.MapId(entry.key.map),corrected);
            if(entry.texture==null) {
                entry.texture=new DynamicTexture("Nexus map overview",128,128,true);
                Minecraft.getInstance().getTextureManager().register(entry.textureId,entry.texture);
            }
            byte[] colors=payload.colors();var pixels=entry.texture.getPixels();
            for(int i=0;i<colors.length;i++)pixels.setPixel(i&127,i>>>7,MapColor.getColorFromPackedId(Byte.toUnsignedInt(colors[i])));
            entry.texture.upload();entry.payload=payload;entry.pending=null;entry.received=ticks;return;
        }
    }
    static MapItemSavedData geometry(MapOverviewPayload payload,MapItemSavedData base) {
            // Vanilla map packets omit center/dimension. A frame need never have been held.
            var dimension=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    Identifier.parse(payload.dimension()));
            int nativeScale=Math.min(4,payload.scale());
            if(base==null||base.centerX!=payload.centerX()||base.centerZ!=payload.centerZ()
                    ||base.scale!=nativeScale||!base.dimension.equals(dimension)||base.locked!=payload.locked()) {
                var corrected=dev.totem.nexus.mixin.NexusMapItemSavedDataInvoker.totem$createExact(
                        payload.centerX(),payload.centerZ(),(byte)nativeScale,false,false,payload.locked(),dimension);
                if(base!=null) {
                    System.arraycopy(base.colors,0,corrected.colors,0,16384);
                    var decorations=new ArrayList<net.minecraft.world.level.saveddata.maps.MapDecoration>();
                    base.getDecorations().forEach(decorations::add);
                    corrected.addClientSideDecorations(decorations);
                }
                return corrected;
            }
        return base;
    }
    /** Returns true only when this native render state actually uses an authorized overview. */
    public static boolean apply(ItemStack stack,int frame,MapRenderState state) {
        session();var entry=touch(stack,frame);
        if(entry==null||entry.payload==null)return false;
        var base=MapItem.getSavedData(stack,level);if(base==null)return false;
        var payload=entry.payload;state.texture=entry.textureId;
        // Vanilla byte decorations refer to native pixels; project them into the full extent.
        double ratio=Math.scalb(1.0,base.scale-payload.scale());
        // Edge-clamped vanilla coordinates cannot be reconstructed as world positions.
        // Keep them at the overview edge instead of moving them to a false interior point.
        for(var decoration:state.decorations) {
            if(Math.abs((int)decoration.x)>=126||Math.abs((int)decoration.y)>=126)continue;
            decoration.x=(byte)Math.clamp(Math.round(decoration.x*ratio),-128,127);
            decoration.y=(byte)Math.clamp(Math.round(decoration.y*ratio),-128,127);
        }
        if(frame<0 && level.dimension().equals(base.dimension)) {
            var player=Minecraft.getInstance().player;
            var marker=NexusMapPlayerMarker.extract(NexusMapPlayerMarker.marker(player.getX(),player.getZ(),player.getYRot(),
                    payload.centerX(),payload.centerZ(),payload.scale()));
            if(marker!=null)state.decorations.add(marker);
        }
        return true;
    }
    public static int entryCountForTest() { return ENTRIES.size(); }
    public static DynamicTexture textureForTest(ItemStack stack,int frame) {
        var entry=ENTRIES.get(key(stack,frame));return entry==null?null:entry.texture;
    }
    public static boolean readyForTest(ItemStack stack,int frame) {
        var entry=ENTRIES.get(key(stack,frame));return entry!=null&&entry.payload!=null;
    }
    public static MapOverviewPayload payloadForTest(ItemStack stack,int frame) {
        var entry=ENTRIES.get(key(stack,frame));return entry==null?null:entry.payload;
    }
}
