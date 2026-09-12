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
    @GameTest public void detailSamplingNeverLoadsAnUnknownChunk(GameTestHelper helper) {
        var level=helper.getLevel(); int x=24000000,z=-24000000;
        if(level.getChunkSource().getChunkNow(x>>4,z>>4)!=null) helper.fail("Fixture chunk must be unloaded");
        if(NexusMapDetailSampling.sample(level,x,z)!=null || level.getChunkSource().getChunkNow(x>>4,z>>4)!=null)
            helper.fail("Sampling loaded or invented unknown terrain");
        helper.succeed();
    }
}
