package dev.totem.nexus.client;

import dev.totem.nexus.mixin.NexusMapItemSavedDataInvoker;
import dev.totem.nexus.space.*;
import dev.totem.nexus.network.MapOverviewPayload;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.MapColor;

/** Native held/frame rendering with actual server-authorized palette delivery. */
public final class NexusMapOverviewClientGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("gamemode creative @a");
            AtomicInteger mapId=new AtomicInteger(),frameId=new AtomicInteger();
            world.getServer().runOnServer(server->{
                var player=server.getPlayerList().getPlayers().getFirst();var level=player.level();
                var anchor=new BlockPos(64,90,64);level.getChunkAt(anchor);
                level.setBlockAndUpdate(anchor,net.minecraft.world.level.block.Blocks.LODESTONE.defaultBlockState());
                for(int x=63;x<=65;x++)for(int z=67;z<=69;z++)
                    level.setBlockAndUpdate(new BlockPos(x,89,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                var unit=UUID.randomUUID();
                NexusSpaceUnitSavedData.loadCanonical(server.overworld().getDataStorage()).put(new NexusSpaceUnitRecord(unit,
                        SpaceUnitType.LODESTONE,level.dimension(),anchor,player.getUUID(),"Overview",SpaceUnitVisibility.PRIVATE,
                        SpaceUnitStatus.ACTIVE,Set.of(),Set.of(),SpaceStructureSnapshot.EMPTY,1,1));
                var ledger=NexusMapBindingSavedData.loadCanonical(server.overworld().getDataStorage());
                var original=level.getFreeMapId();var id=level.getFreeMapId();
                var base=NexusMapItemSavedDataInvoker.totem$createExact(64,64,(byte)4,false,false,false,level.dimension());
                var result=NexusMapItemSavedDataInvoker.totem$createExact(64,64,(byte)4,false,false,false,level.dimension());
                Arrays.fill(base.colors,MapColor.GRASS.getPackedId(MapColor.Brightness.NORMAL));
                System.arraycopy(base.colors,0,result.colors,0,16384);level.setMapData(original,base);level.setMapData(id,result);
                if(!ledger.bind(original,unit,GlobalPos.of(level.dimension(),anchor),base)||!ledger.derive(original,base,id,result,5))
                    throw new AssertionError("Overview fixture could not derive extended map");
                var store=NexusMapDetailSavedData.get(level);
                for(int z=0;z<128;z+=32)for(int x=1536;x<1664;x+=32)store.record(level,id.id(),x,z,MapColor.GOLD.getPackedId(MapColor.Brightness.NORMAL));
                var map=new ItemStack(Items.FILLED_MAP);map.set(DataComponents.MAP_ID,id);NexusInterfaceBinding.write(map,level,anchor,unit);
                player.setItemInHand(InteractionHand.MAIN_HAND,map);player.setNoGravity(true);player.teleportTo(level,64.5,90,68.5,Set.of(),180,60,false);
                player.setYRot(180);player.setXRot(60);mapId.set(id.id());
            });
            context.waitFor(client->NexusMapOverviewClient.readyForTest(client.player.getMainHandItem(),-1),400);
            context.runOnClient(client->{
                client.player.setXRot(60);client.player.xRotO=60;
                var stack=client.player.getMainHandItem();var payload=NexusMapOverviewClient.payloadForTest(stack,-1);
                if(payload.scale()!=5||payload.colors()[112+64*128]!=MapColor.GOLD.getPackedId(MapColor.Brightness.NORMAL))
                    throw new AssertionError("Off-base recorded terrain missing from actual overview reply");
                var old=NexusMapItemSavedDataInvoker.totem$createExact(0,0,(byte)4,false,false,false,client.level.dimension());
                var decoration=new net.minecraft.world.level.saveddata.maps.MapDecoration(
                        net.minecraft.world.level.saveddata.maps.MapDecorationTypes.TARGET_X,(byte)10,(byte)-12,(byte)0,Optional.empty());
                old.colors[17]=4;old.addClientSideDecorations(List.of(decoration));
                var corrected=NexusMapOverviewClient.geometry(payload,old);
                if(corrected.centerX!=64||corrected.colors[17]!=4||!corrected.getDecorations().iterator().next().equals(decoration))
                    throw new AssertionError("Canonical geometry replacement lost native decoration or terrain");
                var unrelated=new MapOverviewPayload(UUID.randomUUID(),payload.mapId(),-1,64,64,5,"minecraft:overworld",false,new byte[16384]);
                NexusMapOverviewClient.accept(unrelated);
                if(NexusMapOverviewClient.payloadForTest(stack,-1)!=payload)throw new AssertionError("Uncorrelated response replaced overview");
            });
            context.waitFor(client->((NexusHeldMapVisualTestAccess)client.player.firstPersonHandsAndItems()).totem$lastHeldMap()==mapId.get());
            context.waitTicks(4);context.takeScreenshot("nexus-overview-held-extended");
            world.getServer().runOnServer(server->{
                var player=server.getPlayerList().getPlayers().getFirst();var level=player.level();
                var frame=new ItemFrame(level,new BlockPos(64,90,65),Direction.SOUTH);
                // A new locked MapId has never been held or sent through the detail view.
                var map=player.getMainHandItem().copy();var sourceId=map.get(DataComponents.MAP_ID);
                var source=MapItem.getSavedData(map,level);var newId=level.getFreeMapId();
                var locked=NexusMapItemSavedDataInvoker.totem$createExact(64,64,(byte)4,false,false,true,level.dimension());
                System.arraycopy(source.colors,0,locked.colors,0,16384);
                var ledger=NexusMapBindingSavedData.loadCanonical(server.overworld().getDataStorage());
                var binding=ledger.get(sourceId).orElseThrow();
                if(!ledger.derive(sourceId,source,newId,locked,5))throw new AssertionError("Frame lock derivation failed");
                NexusMapDetailSavedData.get(level).derive(level,sourceId,source,binding,newId);
                level.setMapData(newId,locked);map.set(DataComponents.MAP_ID,newId);
                frame.setItem(map);level.addFreshEntity(frame);frameId.set(frame.getId());
                player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setYRot(180);player.setXRot(18);
            });
            context.runOnClient(client->{client.player.setYRot(180);client.player.yRotO=180;client.player.setXRot(18);client.player.xRotO=18;});
            context.waitFor(client->client.level.getEntity(frameId.get()) instanceof ItemFrame frame
                    && NexusMapOverviewClient.readyForTest(frame.getItem(),frameId.get()),400);
            context.runOnClient(client->{
                var frame=(ItemFrame)client.level.getEntity(frameId.get());
                var payload=NexusMapOverviewClient.payloadForTest(frame.getItem(),frameId.get());
                if(payload.scale()!=5||payload.colors()[112+64*128]!=MapColor.GOLD.getPackedId(MapColor.Brightness.NORMAL))
                    throw new AssertionError("Frame did not receive extended overview");
            });
            context.waitTicks(4);context.takeScreenshot("nexus-overview-frame-extended");
            var texture=new java.util.concurrent.atomic.AtomicReference<net.minecraft.client.renderer.texture.DynamicTexture>();
            context.runOnClient(client->{
                var frame=(ItemFrame)client.level.getEntity(frameId.get());
                texture.set(NexusMapOverviewClient.textureForTest(frame.getItem(),frameId.get()));
                var base=MapItem.getSavedData(frame.getItem(),client.level);
                if(base.centerX!=64||base.centerZ!=64||!base.locked)throw new AssertionError("Frame-only canonical geometry missing");
                NexusMapOverviewClient.clear();
                client.setCameraEntity(frame);
            });
            context.waitTicks(45);
            context.runOnClient(client->{
                if(NexusMapOverviewClient.entryCountForTest()!=0)throw new AssertionError("Remote camera created overview requests");
                client.setCameraEntity(client.player);
            });
            context.waitFor(client->client.level.getEntity(frameId.get()) instanceof ItemFrame frame
                    && NexusMapOverviewClient.readyForTest(frame.getItem(),frameId.get()),400);
            context.runOnClient(client->{
                var frame=(ItemFrame)client.level.getEntity(frameId.get());
                texture.set(NexusMapOverviewClient.textureForTest(frame.getItem(),frameId.get()));
            });
            context.runOnClient(client->{
                // Bound the cache even when more than sixteen visible contexts arrive in one frame.
                var item=((ItemFrame)client.level.getEntity(frameId.get())).getItem().copy();
                var ids=new ArrayList<Integer>();
                try {
                    for(int i=0;i<20;i++) {
                        var extra=new ItemFrame(client.level,new BlockPos(64,90,65),Direction.SOUTH);
                        extra.setId(100000+i);extra.setItem(item.copy());client.level.addEntity(extra);ids.add(extra.getId());
                        NexusMapOverviewClient.apply(extra.getItem(),extra.getId(),new net.minecraft.client.renderer.state.MapRenderState());
                    }
                    if(NexusMapOverviewClient.entryCountForTest()!=16)throw new AssertionError("Overview cache limit changed");
                } finally {
                    for(int id:ids)client.level.removeEntity(id,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
            });
            world.getServer().runOnServer(server->{
                ((ItemFrame)server.overworld().getEntity(frameId.get())).setItem(ItemStack.EMPTY);
            });
            context.waitTicks(5);
            context.runOnClient(client->{
                if(NexusMapOverviewClient.entryCountForTest()!=0 || !texture.get().getPixels().isClosed())
                    throw new AssertionError("Invalid frame retained cached texture");
                NexusMapOverviewClient.clear();
            });
        }
    }
}
