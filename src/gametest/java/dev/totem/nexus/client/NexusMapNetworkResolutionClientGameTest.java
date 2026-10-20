package dev.totem.nexus.client;

import dev.totem.nexus.mixin.NexusMapItemSavedDataInvoker;
import dev.totem.nexus.network.SpaceUnitMapPayload;
import dev.totem.nexus.space.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** No client map injection: exercise owner requests, server LOD builds and vanilla color packets. */
public final class NexusMapNetworkResolutionClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("gamemode creative @a");
            AtomicReference<SpaceUnitMapPayload> payload=new AtomicReference<>();
            world.getServer().runOnServer(server->{
                var player=server.getPlayerList().getPlayers().getFirst();var level=player.level();
                var unit=UUID.randomUUID();var id=level.getFreeMapId();var anchor=new BlockPos(64,64,64);
                level.setBlockAndUpdate(anchor,net.minecraft.world.level.block.Blocks.LODESTONE.defaultBlockState());
                var base=NexusMapItemSavedDataInvoker.totem$createExact(64,64,(byte)4,false,false,true,level.dimension());
                java.util.Arrays.fill(base.colors,MapColor.GRASS.getPackedId(MapColor.Brightness.NORMAL));
                level.setMapData(id,base);
                NexusSpaceUnitSavedData.loadCanonical(server.overworld().getDataStorage()).put(new NexusSpaceUnitRecord(
                        unit,SpaceUnitType.LODESTONE,level.dimension(),anchor,player.getUUID(),"Network resolution",
                        SpaceUnitVisibility.PUBLIC,SpaceUnitStatus.ACTIVE,Set.of(),Set.of(),SpaceStructureSnapshot.EMPTY,1,1));
                NexusMapBindingSavedData.loadCanonical(server.overworld().getDataStorage()).bind(id,unit,GlobalPos.of(level.dimension(),anchor),base);
                var store=NexusMapDetailSavedData.get(level);
                for(int z=0;z<128;z++) for(int x=0;x<128;x++) {
                    int a=x&7,b=z&7;
                    var color=a==0&&b==0?MapColor.WATER:a<2&&b<2?MapColor.SAND:a<4&&b<4?MapColor.STONE:MapColor.WOOD;
                    store.record(level,id.id(),x,z,color.getPackedId(MapColor.Brightness.NORMAL));
                }
                var stack=new ItemStack(Items.FILLED_MAP);stack.set(DataComponents.MAP_ID,id);NexusInterfaceBinding.write(stack,level,anchor,unit);
                player.setItemInHand(InteractionHand.MAIN_HAND,stack);
                payload.set(new SpaceUnitMapPayload(unit,"lodestone","Network resolution",level.dimension().identifier().toString(),
                        64,64,64,TeleportInterfaceType.FILLED_MAP,id.id(),List.of()));
            });
            int id=payload.get().mapId();
            context.waitFor(client->client.level.getMapData(new MapId(id))!=null
                    && NexusMapDetailClientState.recognized(id),200);
            context.setScreen(()->new NexusSpaceUnitMapScreen(payload.get()));
            context.waitForScreen(NexusSpaceUnitMapScreen.class);
            for(int zoom:new int[]{2,4,8,16}) {
                int scale=4-Integer.numberOfTrailingZeros(zoom);
                context.runOnClient(client->{
                    var screen=(NexusSpaceUnitMapScreen)client.gui.screen();var center=screen.mapViewportCenterForVisualTest();
                    screen.mouseScrolled(center[0],center[1],0,1);
                });
                context.waitTicks(80);
                world.getServer().runOnServer(server->{
                    var unit=NexusSpaceUnitSavedData.loadCanonical(server.overworld().getDataStorage()).get(payload.get().sourceUnitId()).orElseThrow();
                    if(unit.status()!=SpaceUnitStatus.ACTIVE) throw new AssertionError("Network fixture anchor lost authorization");
                });
                context.runOnClient(client->{
                    var rendered=((NexusMapDetailVisualTestAccess)client.gui.screen()).totem$detailLayersRenderedForVisualTest();
                    byte expected=(scale==3?MapColor.WOOD:scale==2?MapColor.STONE:scale==1?MapColor.SAND:MapColor.WATER)
                            .getPackedId(MapColor.Brightness.NORMAL);
                    boolean found=false;
                    for(int pageId:NexusMapDetailClientState.ancestorMapIds(id)) {
                        var page=client.level.getMapData(new MapId(pageId));
                        if(page==null || page.scale!=scale || !rendered.contains(pageId)) continue;
                        int px=(64-page.centerX+(64<<scale))>>scale,pz=(64-page.centerZ+(64<<scale))>>scale;
                        if(px>=0&&px<128&&pz>=0&&pz<128&&page.colors[px+pz*128]==expected) found=true;
                    }
                    if(!found) throw new AssertionError("Live resolution missing: zoom="+zoom+" view="+NexusMapDetailClientState.view(id)
                            +" layers="+NexusMapDetailClientState.ancestorMapIds(id)+" serverPaused="+client.getSingleplayerServer().isPaused());
                });
                context.takeScreenshot("nexus-live-network-resolution-"+zoom);
            }
            context.setScreen(()->null);
        }
        extendedViewport(context);
    }
    private static void extendedViewport(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            AtomicReference<SpaceUnitMapPayload> payload=new AtomicReference<>();
            world.getServer().runOnServer(server->{
                var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();
                var anchor=new BlockPos(64,64,64);level.getChunkAt(anchor);level.setBlockAndUpdate(anchor,net.minecraft.world.level.block.Blocks.LODESTONE.defaultBlockState());
                UUID unit=UUID.randomUUID();
                NexusSpaceUnitSavedData.loadCanonical(server.overworld().getDataStorage()).put(new NexusSpaceUnitRecord(unit,SpaceUnitType.LODESTONE,
                        level.dimension(),anchor,player.getUUID(),"Extended viewport",SpaceUnitVisibility.PUBLIC,SpaceUnitStatus.ACTIVE,Set.of(),Set.of(),SpaceStructureSnapshot.EMPTY,1,1));
                var ledger=NexusMapBindingSavedData.loadCanonical(server.overworld().getDataStorage());var store=NexusMapDetailSavedData.get(level);
                var id=level.getFreeMapId();var base=NexusMapItemSavedDataInvoker.totem$createExact(64,64,(byte)4,false,false,false,level.dimension());
                java.util.Arrays.fill(base.colors,MapColor.GRASS.getPackedId(MapColor.Brightness.NORMAL));level.setMapData(id,base);
                if(!ledger.bind(id,unit,GlobalPos.of(level.dimension(),anchor),base)) throw new AssertionError("Extended fixture binding failed");
                for(int scale=5;scale<=7;scale++) {
                    var next=level.getFreeMapId();var copy=NexusMapItemSavedDataInvoker.totem$createExact(64,64,(byte)4,false,false,false,level.dimension());
                    System.arraycopy(base.colors,0,copy.colors,0,16384);var binding=ledger.get(id).orElseThrow();
                    if(!ledger.derive(id,base,next,copy,scale)) throw new AssertionError("Extended fixture derivation failed");
                    store.derive(level,id,base,binding,next);level.setMapData(next,copy);id=next;base=copy;
                }
                for(int z=0;z<128;z++) for(int x=3072;x<3200;x++) store.record(level,id.id(),x,z,MapColor.SAND.getPackedId(MapColor.Brightness.NORMAL));
                var stack=new ItemStack(Items.FILLED_MAP);stack.set(DataComponents.MAP_ID,id);NexusInterfaceBinding.write(stack,level,anchor,unit);
                player.setItemInHand(InteractionHand.MAIN_HAND,stack);player.setNoGravity(true);player.setPos(3136,70,64);
                payload.set(new SpaceUnitMapPayload(unit,"lodestone","Extended viewport",level.dimension().identifier().toString(),64,64,64,TeleportInterfaceType.FILLED_MAP,id.id(),List.of()));
            });
            int id=payload.get().mapId();
            context.waitFor(client->client.level.getMapData(new MapId(id))!=null && NexusMapDetailClientState.effectiveScale(id,4)==7,200);
            context.setScreen(()->new NexusSpaceUnitMapScreen(payload.get()));context.waitForScreen(NexusSpaceUnitMapScreen.class);
            context.takeScreenshot("nexus-extended-viewport-before-drag");
            context.runOnClient(client->{
                var screen=(NexusSpaceUnitMapScreen)client.gui.screen();var center=screen.mapViewportCenterForVisualTest();
                int delta=-192*screen.mapViewportForVisualTest()[4];
                var controls=screen.children().stream().filter(child->child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                        && widget.visible && widget.active && widget.isMouseOver(center[0],center[1])).map(Object::toString).toList();
                boolean clicked=screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(center[0],center[1],new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),false);
                boolean dragged=screen.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(center[0]+delta,center[1],new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)),delta,0);
                screen.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(center[0]+delta,center[1],new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0)));
                if(!clicked || !dragged || screen.observerMapPanX()!=delta) throw new AssertionError("Extended drag gesture failed: clicked="+clicked+" dragged="+dragged
                        +" pan="+screen.observerMapPanX()+" expected="+delta+" materials="+screen.observerShowsMaterials()+" controls="+controls
                        +" root="+client.level.getMapData(new MapId(id)).scale+" extent="+screen.effectiveMapScale());
            });
            context.waitTicks(3);
            context.runOnClient(client->{
                var screen=(NexusSpaceUnitMapScreen)client.gui.screen();
                if(NexusMapDetailClientState.view(id).centerX()<=2000) throw new AssertionError("Extended drag did not move viewport: view="
                        +NexusMapDetailClientState.view(id)+" pan="+java.util.Arrays.toString(screen.mapViewForVisualTest())
                        +" viewport="+java.util.Arrays.toString(screen.mapViewportForVisualTest())+" extent="+screen.effectiveMapScale());
            });
            try { context.waitFor(client->{
                var rendered=((NexusMapDetailVisualTestAccess)client.gui.screen()).totem$detailLayersRenderedForVisualTest();
                return NexusMapDetailClientState.view(id).centerX()>2000 && rendered.stream().anyMatch(pageId->{
                    var page=client.level.getMapData(new MapId(pageId));if(page==null || page.scale!=4 || page.centerX<=2000) return false;
                    int px=(3136-page.centerX+1024)>>4,pz=(64-page.centerZ+1024)>>4;
                    return px>=0 && px<128 && pz>=0 && pz<128 && page.colors[px+pz*128]!=0;
                });
            },300); } catch(AssertionError failure) {
                context.takeScreenshot("nexus-extended-viewport-failure");
                String state=context.computeOnClient(client->"view="+NexusMapDetailClientState.view(id)+" rendered="
                        +((NexusMapDetailVisualTestAccess)client.gui.screen()).totem$detailLayersRenderedForVisualTest()
                        +" layers="+NexusMapDetailClientState.ancestorMapIds(id).stream().map(pageId->{
                            var page=client.level.getMapData(new MapId(pageId));
                            if(page==null) return pageId+":missing";
                            int px=(3136-page.centerX+(64<<page.scale))>>page.scale,pz=(64-page.centerZ+(64<<page.scale))>>page.scale;
                            return pageId+":"+page.centerX+","+page.centerZ+"/"+page.scale+" pixel="
                                    +(px>=0&&px<128&&pz>=0&&pz<128?Byte.toUnsignedInt(page.colors[px+pz*128]):-1);
                        }).toList());
                throw new AssertionError("Extended terrain unavailable: "+state,failure);
            }
            context.takeScreenshot("nexus-extended-viewport-native-scale-four-detail");
            context.runOnClient(client->{var screen=(NexusSpaceUnitMapScreen)client.gui.screen();var center=screen.mapViewportCenterForVisualTest();
                for(int i=0;i<4;i++) screen.mouseScrolled(center[0],center[1],0,1);
            });
            context.waitFor(client->((NexusSpaceUnitMapScreen)client.gui.screen()).observerMapZoom()==16 && NexusMapDetailClientState.view(id).centerX()>2000,100);
            context.waitTicks(30);context.takeScreenshot("nexus-extended-viewport-fine-terrain-owner");
            var provider=new NexusObserverScreenProvider();
            var snapshot=context.computeOnClient(client->provider.capture(client.gui.screen(),1).orElseThrow());
            boolean beyondNative=context.computeOnClient(client->{
                var screen=(NexusSpaceUnitMapScreen)client.gui.screen();var viewport=screen.mapViewportForVisualTest();
                int pan=Integer.parseInt(snapshot.metadata().get("map_pan_x"));
                return dev.totem.nexus.map.MapPointerTransform.clampPan(pan,128*viewport[4]*screen.observerMapZoom(),viewport[2])!=pan;
            });
            if(!"7".equals(snapshot.metadata().get("map_effective_scale")) || !beyondNative
                    || Integer.parseInt(snapshot.metadata().get("player_marker_x"))<=127) throw new AssertionError("Extended semantic state was still clamped to native map: "+snapshot.metadata());
            AtomicReference<net.minecraft.world.level.saveddata.maps.MapItemSavedData> delayedRoot=new AtomicReference<>();
            var handle=context.computeOnClient(client->{
                delayedRoot.set(((dev.totem.nexus.mixin.client.NexusClientLevelMapAccess)client.level).totem$mapData().remove(new MapId(id)));
                var created=provider.create(new dev.totem.core.api.v1.client.observer.ObserverScreenContext(UUID.randomUUID(),"Target",()->{}),snapshot);
                if(((NexusSpaceUnitMapScreen)created.screen()).observerMapPanX()!=Integer.parseInt(snapshot.metadata().get("map_pan_x")))
                    throw new AssertionError("Early Observer snapshot lost pan before root geometry");
                return created;
            });
            context.runOnClient(client->client.setScreenAndShow(handle.screen()));
            context.waitTicks(3);
            context.runOnClient(client->{
                var root=delayedRoot.get();
                NexusMapDetailClientState.accept(new dev.totem.nexus.network.NexusMapDetailPayload(id,NexusMapDetailClientState.ancestorMapIds(id),
                        List.of(new dev.totem.nexus.network.NexusMapDetailPayload.Layer(id,root.centerX,root.centerZ,root.scale,root.dimension.identifier().toString(),root.locked)),7));
                client.level.overrideMapData(new MapId(id),root);
            });
            context.waitFor(client->{var screen=(NexusSpaceUnitMapScreen)client.gui.screen();var probe=(NexusMapDetailVisualTestAccess)screen;
                return screen.effectiveMapScale()==7 && screen.observerMapPanX()==Integer.parseInt(snapshot.metadata().get("map_pan_x"))
                        && probe.totem$observedPlayerMarkerRenderedForVisualTest() && !probe.totem$localPlayerMarkerRenderedForVisualTest();
            },100);
            context.takeScreenshot("nexus-extended-viewport-observer-same-pan-and-marker");
            var metadata=new java.util.LinkedHashMap<>(snapshot.metadata());metadata.put("map_effective_scale","6");
            metadata.keySet().removeIf(k->k.startsWith("player_marker_"));
            var update=new dev.totem.core.api.v1.client.observer.ObserverScreenSnapshot(snapshot.familyId(),snapshot.variant(),snapshot.protocolVersion(),2,
                    snapshot.title(),snapshot.slots(),snapshot.data(),metadata,snapshot.ownerPayload());
            context.runOnClient(client->handle.applySnapshot(update));
            context.waitFor(client->((NexusSpaceUnitMapScreen)client.gui.screen()).effectiveMapScale()==6 && !((NexusMapDetailVisualTestAccess)client.gui.screen()).totem$observedPlayerMarkerRenderedForVisualTest(),100);
            context.runOnClient(client->handle.applySnapshot(snapshot));
            context.runOnClient(client->{if(((NexusSpaceUnitMapScreen)client.gui.screen()).effectiveMapScale()!=6) throw new AssertionError("Stale extent snapshot applied");});
            context.setScreen(()->null);
        }
    }

}
