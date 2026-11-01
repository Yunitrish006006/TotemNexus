package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.UUID;

public final class NexusMapOriginIndependenceGameTest {
    @GameTest
    public void destroyedAndDisabledCreatingLodestoneDoesNotBlockMap(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(anchor, Blocks.LODESTONE.defaultBlockState());
        UUID unitId = UUID.randomUUID();
        var units = NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
        units.put(new NexusSpaceUnitRecord(
                unitId, SpaceUnitType.LODESTONE, level.dimension(), anchor, player.getUUID(),
                "Former Origin", SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE,
                Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, level.getGameTime(), level.getGameTime()));
        ItemStack map = NexusMapLifecycleAuthority.createExactCenteredMap(
                level, anchor, unitId, new ItemStack(Items.MAP)).orElseThrow();
        player.setItemInHand(InteractionHand.MAIN_HAND, map);

        level.setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
        require(helper, units.disableLodestone(unitId, level.getGameTime()),
                "Fixture could not disable the creating unit");
        var context = new NexusTeleportInterfaceAuthority(new TeleportInterfaceSessionStore())
                .establishPlayerAnchor(player, InteractionHand.MAIN_HAND).orElse(null);
        require(helper, context != null && "player".equals(context.sourceType())
                        && context.boundUnitId() == null,
                "Destroyed origin still controlled map opening");
        require(helper, NexusMapLifecycleAuthority.postProcess(map, level, MapPostProcessing.SCALE)
                        == NexusMapLifecycleAuthority.PostProcessResult.PROCESSED,
                "Destroyed origin blocked map scaling");
        require(helper, NexusMapLifecycleAuthority.postProcess(map, level, MapPostProcessing.LOCK)
                        == NexusMapLifecycleAuthority.PostProcessResult.PROCESSED,
                "Destroyed origin blocked map locking");
        player.discard();
        helper.succeed();
    }

    @GameTest
    public void unloadedFormerOriginIsNeverLoadedByOpenScaleOrLock(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        int centerX = 24_000_000;
        int centerZ = -24_000_000;
        int chunkX = centerX >> 4;
        int chunkZ = centerZ >> 4;
        require(helper, level.getChunkSource().getChunkNow(chunkX, chunkZ) == null,
                "Former-origin fixture chunk started loaded");

        var mapId = level.getFreeMapId();
        var nativeData = NexusMapLifecycleAuthority.exactData(
                centerX, centerZ, (byte) 0, false, level.dimension());
        var records = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusMapRecordSavedData.TYPE);
        require(helper, records.bootstrap(mapId, nativeData).result()
                        == NexusMapRecordSavedData.RegisterResult.INSERTED,
                "Could not publish remote MapId fixture");
        level.setMapData(mapId, nativeData);
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, mapId);
        player.setItemInHand(InteractionHand.MAIN_HAND, map);

        require(helper, new NexusTeleportInterfaceAuthority(new TeleportInterfaceSessionStore())
                        .establishPlayerAnchor(player, InteractionHand.MAIN_HAND).isPresent(),
                "Unloaded former origin blocked map opening");
        require(helper, NexusMapLifecycleAuthority.postProcess(map, level, MapPostProcessing.SCALE)
                        == NexusMapLifecycleAuthority.PostProcessResult.PROCESSED,
                "Unloaded former origin blocked map scaling");
        require(helper, NexusMapLifecycleAuthority.postProcess(map, level, MapPostProcessing.LOCK)
                        == NexusMapLifecycleAuthority.PostProcessResult.PROCESSED,
                "Unloaded former origin blocked map locking");
        require(helper, level.getChunkSource().getChunkNow(chunkX, chunkZ) == null,
                "Map lifecycle force-loaded its former-origin chunk");
        player.discard();
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
