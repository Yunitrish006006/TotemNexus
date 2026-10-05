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
    }
}
