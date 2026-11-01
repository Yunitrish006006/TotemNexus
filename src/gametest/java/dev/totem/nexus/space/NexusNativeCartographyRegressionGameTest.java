package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Guards the three native cartography recipes alongside the water-bucket branch. */
public final class NexusNativeCartographyRegressionGameTest {
    @GameTest(maxTicks = 30)
    public void paperStillScalesFilledMaps(GameTestHelper helper) {
        withMap(helper, (player, menu, sourceId, sourceData) -> {
            menu.container.setItem(CartographyTableMenu.ADDITIONAL_SLOT, new ItemStack(Items.PAPER));
            ItemStack preview = menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem();
            if (!preview.is(Items.FILLED_MAP) || preview.getCount() != 1
                    || preview.get(DataComponents.MAP_POST_PROCESSING) != MapPostProcessing.SCALE) {
                helper.fail("Paper no longer offers the native scale result");
                return;
            }

            menu.clicked(CartographyTableMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
            ItemStack result = menu.getCarried();
            MapId resultId = result.get(DataComponents.MAP_ID);
            MapItemSavedData resultData = MapItem.getSavedData(result, helper.getLevel());
            if (!result.is(Items.FILLED_MAP) || result.getCount() != 1 || resultId == null
                    || resultId.equals(sourceId) || resultData == null || resultData.scale != 2
                    || !menu.container.getItem(CartographyTableMenu.MAP_SLOT).isEmpty()
                    || !menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).isEmpty()
                    || helper.getLevel().getMapData(sourceId) != sourceData
                    || sourceData.scale != 1 || sourceData.colors[17] != 42) {
                helper.fail("Paper scaling was intercepted or changed its source map");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 30)
    public void glassPaneStillLocksFilledMaps(GameTestHelper helper) {
        withMap(helper, (player, menu, sourceId, sourceData) -> {
            menu.container.setItem(CartographyTableMenu.ADDITIONAL_SLOT, new ItemStack(Items.GLASS_PANE));
            ItemStack preview = menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem();
            if (!preview.is(Items.FILLED_MAP) || preview.getCount() != 1
                    || preview.get(DataComponents.MAP_POST_PROCESSING) != MapPostProcessing.LOCK) {
                helper.fail("Glass pane no longer offers the native lock result");
                return;
            }

            menu.clicked(CartographyTableMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
            ItemStack result = menu.getCarried();
            MapId resultId = result.get(DataComponents.MAP_ID);
            MapItemSavedData resultData = MapItem.getSavedData(result, helper.getLevel());
            if (!result.is(Items.FILLED_MAP) || result.getCount() != 1 || resultId == null
                    || resultId.equals(sourceId) || resultData == null || !resultData.locked
                    || resultData.scale != sourceData.scale || resultData.colors[17] != 42
                    || !menu.container.getItem(CartographyTableMenu.MAP_SLOT).isEmpty()
                    || !menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).isEmpty()
                    || helper.getLevel().getMapData(sourceId) != sourceData
                    || sourceData.locked || sourceData.colors[17] != 42) {
                helper.fail("Glass-pane locking was intercepted or changed its source map");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 30)
    public void emptyMapStillCopiesFilledMaps(GameTestHelper helper) {
        withMap(helper, (player, menu, sourceId, sourceData) -> {
            menu.container.setItem(CartographyTableMenu.ADDITIONAL_SLOT, new ItemStack(Items.MAP));
            ItemStack preview = menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem();
            if (!preview.is(Items.FILLED_MAP) || preview.getCount() != 2
                    || !sourceId.equals(preview.get(DataComponents.MAP_ID))
                    || preview.has(DataComponents.MAP_POST_PROCESSING)) {
                helper.fail("Empty map no longer offers two native map copies");
                return;
            }

            menu.clicked(CartographyTableMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
            ItemStack result = menu.getCarried();
            if (!result.is(Items.FILLED_MAP) || result.getCount() != 2
                    || !sourceId.equals(result.get(DataComponents.MAP_ID))
                    || result.has(DataComponents.MAP_POST_PROCESSING)
                    || !menu.container.getItem(CartographyTableMenu.MAP_SLOT).isEmpty()
                    || !menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).isEmpty()
                    || helper.getLevel().getMapData(sourceId) != sourceData
                    || sourceData.colors[17] != 42) {
                helper.fail("Native map copying was intercepted or mutated SavedData");
                return;
            }
            helper.succeed();
        });
    }

    private static void withMap(GameTestHelper helper, NativeCase test) {
        var level = helper.getLevel();
        BlockPos table = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(table, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(table.above()));
        var records = level.getServer().overworld().getDataStorage().get(NexusMapRecordSavedData.TYPE);
        MapId mapId;
        do {
            mapId = level.getFreeMapId();
        } while (level.getMapData(mapId) != null
                || (records != null && records.get(mapId).isPresent()));
        var data = MapItemSavedData.createFresh(320, -192, (byte) 1,
                false, false, level.dimension());
        data.colors[17] = 42;
        level.setMapData(mapId, data);
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, mapId);
        var menu = new CartographyTableMenu(3, player.getInventory(),
                ContainerLevelAccess.create(level, table));
        menu.container.setItem(CartographyTableMenu.MAP_SLOT, map);
        try {
            test.run(player, menu, mapId, data);
        } finally {
            player.discard();
        }
    }

    @FunctionalInterface
    private interface NativeCase {
        void run(net.minecraft.server.level.ServerPlayer player, CartographyTableMenu menu,
                 MapId sourceId, MapItemSavedData sourceData);
    }
}
