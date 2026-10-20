package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.nbt.NbtOps;

public final class NexusMapRecordingGameTest {
    @GameTest public void newRegionWithoutAncestorsSurvivesReloadAndMapHandoff(GameTestHelper helper) {
        var level=helper.getLevel(); var store=new NexusMapDetailSavedData();
        MapId map=level.getFreeMapId(), other=level.getFreeMapId();
        store.record(level,map.id(),-129,257,(byte)22);
        var saved=NexusMapDetailSavedData.CODEC.encodeStart(NbtOps.INSTANCE,store).getOrThrow();
        var loaded=NexusMapDetailSavedData.CODEC.parse(NbtOps.INSTANCE,saved).getOrThrow();
        var page=loaded.pages(map.id()).getFirst();
        if(page.x!=-192 || page.z!=320 || page.color(127,1)!=22 || !loaded.pages(other.id()).isEmpty())
            helper.fail("Map-owned detail, negative coordinates or restart isolation failed");
        helper.succeed();
    }
    @GameTest public void derivedSnapshotDoesNotFollowMutableSource(GameTestHelper helper) {
        var level=helper.getLevel(); var store=new NexusMapDetailSavedData();
        MapId map=level.getFreeMapId(), locked=level.getFreeMapId();
        var base=NexusMapLifecycleAuthority.exactData(0,0,(byte)1,false,level.dimension());
        store.record(level,map.id(),200,0,(byte)22);
        store.derive(level,map,base,null,locked);
        int before=store.pages(locked.id()).getFirst().id;
        store.record(level,map.id(),200,0,(byte)30);
        if(store.pages(locked.id()).getFirst().color(72,0)!=22
                || store.pages(map.id()).getFirst().color(72,0)!=30
                || store.pages(map.id()).getFirst().id==before) helper.fail("SCALE/LOCK snapshot followed mutable source");
        helper.succeed();
    }
    @GameTest public void legacyLockedMapDoesNotImportLiveAncestor(GameTestHelper helper) {
        var level=helper.getLevel(); var store=new NexusMapDetailSavedData();
        var id=level.getFreeMapId();
        store.initialize(level,id,NexusMapLifecycleAuthority.exactData(0,0,(byte)2,true,level.dimension()),null);
        if(!store.pages(id.id()).isEmpty()) helper.fail("Legacy locked map fabricated detail");
        helper.succeed();
    }
    @GameTest public void paymentRollbackRestoresComponentBearingFoodAndCatalysts(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var food = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 7);
        food.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Travel food"));
        player.getInventory().setItem(0, food.copy());
        player.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 9));
        player.getFoodData().setFoodLevel(17); player.getFoodData().setSaturation(3);
        var before = new NexusTeleportPaymentSnapshot(player);
        player.getInventory().getItem(0).shrink(3); player.getInventory().getItem(1).shrink(4);
        player.getFoodData().setFoodLevel(2); player.getFoodData().setSaturation(0);
        before.restore(player);
        if(!net.minecraft.world.item.ItemStack.matches(food, player.getInventory().getItem(0))
                || player.getInventory().getItem(1).getCount()!=9
                || player.getFoodData().getFoodLevel()!=17 || player.getFoodData().getSaturationLevel()!=3)
            helper.fail("Failed payment rollback lost items, components or food data");
        player.discard(); helper.succeed();
    }
    @GameTest(maxTicks = 100)
    public void fullStoragePreservesPixelsDefersSamplesAndLimitsNotices(GameTestHelper helper) {
        var level = helper.getLevel(); var store = new NexusMapDetailSavedData();
        int owner = level.getFreeMapId().id();
        store.record(level,owner,0,0,(byte)4);
        var fork=level.getFreeMapId();
        store.derive(level,new MapId(owner),NexusMapLifecycleAuthority.exactData(0,0,(byte)0,false,level.dimension()),null,fork);
        for (int i=1;i<NexusMapDetailSavedData.MAX_WORLD_PAGES-1;i++) {
            if (!store.record(level,owner,i*128,0,(byte)4)) helper.fail("Could not fill real terrain quota");
        }
        if (store.recordResult(level,owner,0,0,(byte)4) != NexusMapDetailSavedData.RecordResult.UNCHANGED
                || store.recordResult(level,owner,128,0,(byte)8) != NexusMapDetailSavedData.RecordResult.RECORDED
                || store.recordResult(level,owner,0,0,(byte)8) != NexusMapDetailSavedData.RecordResult.CAPACITY
                || store.pages(owner).getFirst().color(0,0) != 4 || store.pages(fork.id()).getFirst().color(0,0) != 4
                || store.recordResult(level,owner,-128,0,(byte)4) != NexusMapDetailSavedData.RecordResult.CAPACITY
                || !store.known(owner,0,0) || store.known(owner,-128,0))
            helper.fail("Capacity refusal changed coverage or prevented existing-page updates");
        var anchor=helper.absolutePos(new net.minecraft.core.BlockPos(1,2,1));
        int x=Math.floorDiv(anchor.getX(),8)*8,z=Math.floorDiv(anchor.getZ(),8)*8;
        level.getChunk(x>>4,z>>4); level.getChunk(x>>4,(z-1)>>4);
        for(int dx=0;dx<8;dx++) for(int dz=-1;dz<8;dz++)
            level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x+dx,anchor.getY(),z+dz),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var base=NexusMapLifecycleAuthority.exactData(x,z,(byte)4,false,level.dimension());
        var queue=new dev.totem.nexus.map.MapRecordingQueue();queue.offer(Math.floorDiv(x,8),Math.floorDiv(z,8),true,0);
        int[] refusals={0};
        NexusMapDetailSampling.advance(level,owner,base,store,queue,0,64,new int[]{16384},Long.MAX_VALUE,
                new NexusMapExtent(x,z,5),()->refusals[0]++);
        if(refusals[0]!=1 || queue.deferredSize()!=1 || store.known(owner,x,z))
            helper.fail("Full storage silently completed a sample instead of retaining a retry");
        var player=helper.makeMockServerPlayerInLevel(); NexusTeleportTestMessages.watch(player.getUUID());
        NexusMapDetailSampling.clear();
        try {
            NexusMapDetailSampling.notifyCapacity(player,0);
            NexusMapDetailSampling.notifyCapacity(player,1);
            NexusMapDetailSampling.notifyCapacity(player,599);
            if(NexusTeleportTestMessages.messages(player.getUUID()).size()!=1)
                helper.fail("Capacity notification spammed within cooldown");
            NexusMapDetailSampling.notifyCapacity(player,600);
            if(NexusTeleportTestMessages.messages(player.getUUID()).size()!=2
                    || !NexusTeleportTestMessages.messages(player.getUUID()).getFirst().equals(NexusMapDetailSampling.CAPACITY_MESSAGE))
                helper.fail("Capacity warning did not resume after cooldown");
        } finally {NexusMapDetailSampling.clear();NexusTeleportTestMessages.clear(player.getUUID());player.discard();}
        helper.succeed();
    }
    @GameTest public void detailSamplingNeverLoadsAnUnknownChunk(GameTestHelper helper) {
        var level=helper.getLevel(); int x=24000000,z=-24000000;
        if(level.getChunkSource().getChunkNow(x>>4,z>>4)!=null) helper.fail("Fixture chunk must be unloaded");
        if(NexusMapDetailSampling.sample(level,x,z)!=null || level.getChunkSource().getChunkNow(x>>4,z>>4)!=null)
            helper.fail("Sampling loaded or invented unknown terrain");
        helper.succeed();
    }
}
