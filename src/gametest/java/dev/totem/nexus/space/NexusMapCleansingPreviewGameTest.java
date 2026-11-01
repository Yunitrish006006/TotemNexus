package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.UUID;

/** Native cartography preview coverage; result pickup is tested separately. */
public final class NexusMapCleansingPreviewGameTest {
    @GameTest(maxTicks = 20)
    public void exactFilledMapAndWaterBucketOfferCleanEmptyMapWithoutMutation(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos table = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(table, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        try {
            var mapId = level.getFreeMapId();
            var data = MapItemSavedData.createFresh(320, -192, (byte) 2,
                    false, false, level.dimension());
            data.colors[17] = 42;
            level.setMapData(mapId, data);
            ItemStack map = new ItemStack(Items.FILLED_MAP);
            map.set(DataComponents.MAP_ID, mapId);
            ItemStack mapBefore = map.copy();
            ItemStack water = new ItemStack(Items.WATER_BUCKET);

            var menu = new CartographyTableMenu(2, player.getInventory(),
                    ContainerLevelAccess.create(level, table));
            menu.container.setItem(CartographyTableMenu.MAP_SLOT, map);
            menu.container.setItem(CartographyTableMenu.ADDITIONAL_SLOT, water);
            ItemStack preview = menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem();

            if (!ItemStack.isSameItemSameComponents(new ItemStack(Items.MAP), preview)
                    || preview.getCount() != 1 || preview.has(DataComponents.MAP_ID)
                    || !ItemStack.isSameItemSameComponents(mapBefore,
                    menu.container.getItem(CartographyTableMenu.MAP_SLOT))
                    || menu.container.getItem(CartographyTableMenu.MAP_SLOT).getCount() != 1
                    || !menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).is(Items.WATER_BUCKET)
                    || menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).getCount() != 1
                    || level.getMapData(mapId) != data || data.colors[17] != 42) {
                helper.fail("Cleansing preview was not a clean, non-mutating empty map");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void resultPickupRevalidatesAndCommitsBothInputsAtomically(GameTestHelper helper) {
        withMenu(helper, (player, menu, mapId, data) -> {
            menu.clicked(CartographyTableMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
            ItemStack result = menu.getCarried();
            if (!NexusMapRecipeInputs.isCleanEmptyMap(result)
                    || TeleportInterfaceItemResolver.resolveRegistrationInput(result)
                    .filter(input -> input.type()
                            == TeleportInterfaceItemResolver.RegistrationInputType.EMPTY_MAP)
                    .isEmpty()
                    || !menu.container.getItem(CartographyTableMenu.MAP_SLOT).isEmpty()
                    || !menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).is(Items.BUCKET)
                    || menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).getCount() != 1
                    || levelMapChanged(helper, mapId, data)) {
                helper.fail("Cleansing pickup did not atomically produce map plus bucket");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void replacedWaterBucketFailsClosedBeforeConsumption(GameTestHelper helper) {
        withMenu(helper, (player, menu, mapId, data) -> {
            ItemStack mapBefore = menu.container.getItem(CartographyTableMenu.MAP_SLOT).copy();
            menu.container.setItem(CartographyTableMenu.ADDITIONAL_SLOT, new ItemStack(Items.BUCKET));
            menu.getSlot(CartographyTableMenu.RESULT_SLOT).set(new ItemStack(Items.MAP));
            menu.clicked(CartographyTableMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
            if (!menu.getCarried().isEmpty()
                    || !ItemStack.matches(mapBefore,
                    menu.container.getItem(CartographyTableMenu.MAP_SLOT))
                    || !menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).is(Items.BUCKET)
                    || menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).getCount() != 1
                    || levelMapChanged(helper, mapId, data)) {
                helper.fail("Replaced cleansing water bucket did not fail closed");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void cleansingPreservesSavedDataAcrossCodecReload(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos table = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(table, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(table.above()));
        try {
            var mapId = level.getFreeMapId();
            var data = MapItemSavedData.createFresh(320, -192, (byte) 2,
                    false, false, level.dimension());
            data.colors[17] = 42;
            level.setMapData(mapId, data);

            var records = new NexusMapRecordSavedData();
            var recordBefore = new NexusMapRecordSavedData.Entry(
                    mapId.id(), data.dimension, data.centerX, data.centerZ, data.scale, data.locked,
                    data.scale, java.util.List.of(), java.util.Optional.empty(),
                    java.util.Optional.empty(), 0L);
            if (records.register(recordBefore) != NexusMapRecordSavedData.RegisterResult.INSERTED) {
                helper.fail("Could not seed MapId record for cleansing reload test");
                return;
            }
            var details = new NexusMapDetailSavedData();
            if (!details.record(level, mapId.id(), 321, -191, (byte) 28)) {
                helper.fail("Could not seed detail data for cleansing reload test");
                return;
            }
            var bindings = new NexusMapBindingSavedData();
            UUID unitId = UUID.randomUUID();
            GlobalPos anchor = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(2, 2, 2)));
            if (!bindings.bind(mapId, unitId, anchor, data)) {
                helper.fail("Could not seed binding data for cleansing reload test");
                return;
            }
            var bindingBefore = bindings.resolve(mapId, data).orElseThrow();

            ItemStack map = new ItemStack(Items.FILLED_MAP);
            map.set(DataComponents.MAP_ID, mapId);
            var menu = new CartographyTableMenu(2, player.getInventory(),
                    ContainerLevelAccess.create(level, table));
            menu.container.setItem(CartographyTableMenu.MAP_SLOT, map);
            menu.container.setItem(CartographyTableMenu.ADDITIONAL_SLOT,
                    new ItemStack(Items.WATER_BUCKET));
            menu.clicked(CartographyTableMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);

            var ops = net.minecraft.nbt.NbtOps.INSTANCE;
            var reloadedMap = MapItemSavedData.CODEC.parse(ops,
                    MapItemSavedData.CODEC.encodeStart(ops, data).getOrThrow()).getOrThrow();
            var reloadedRecords = NexusMapRecordSavedData.CODEC.parse(ops,
                    NexusMapRecordSavedData.CODEC.encodeStart(ops, records).getOrThrow()).getOrThrow();
            var reloadedDetails = NexusMapDetailSavedData.CODEC.parse(ops,
                    NexusMapDetailSavedData.CODEC.encodeStart(ops, details).getOrThrow()).getOrThrow();
            var reloadedBindings = NexusMapBindingSavedData.CODEC.parse(ops,
                    NexusMapBindingSavedData.CODEC.encodeStart(ops, bindings).getOrThrow()).getOrThrow();

            var reloadedPages = reloadedDetails.pages(mapId.id());
            if (!NexusMapRecipeInputs.isCleanEmptyMap(menu.getCarried())
                    || reloadedMap.colors[17] != 42
                    || !recordBefore.equals(reloadedRecords.resolve(mapId, reloadedMap).orElse(null))
                    || reloadedPages.size() != 1
                    || reloadedPages.getFirst().color(
                    Math.floorMod(321, 128), Math.floorMod(-191, 128)) != (byte) 28
                    || !bindingBefore.equals(
                    reloadedBindings.resolve(mapId, reloadedMap).orElse(null))) {
                helper.fail("Cleansing data did not survive codec save/reload");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void cleansingOneCopyPreservesSharedMapIdAndNexusSavedData(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos table = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(table, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(table.above()));
        try {
            var sourceMapId = level.getFreeMapId();
            var sourceData = MapItemSavedData.createFresh(320, -192, (byte) 1,
                    false, false, level.dimension());
            level.setMapData(sourceMapId, sourceData);
            var mapId = level.getFreeMapId();
            var data = MapItemSavedData.createFresh(320, -192, (byte) 2,
                    false, false, level.dimension());
            data.colors[17] = 42;
            level.setMapData(mapId, data);

            var storage = level.getServer().overworld().getDataStorage();
            var records = storage.computeIfAbsent(NexusMapRecordSavedData.TYPE);
            var seededRecord = new NexusMapRecordSavedData.Entry(
                    mapId.id(), data.dimension, data.centerX, data.centerZ, data.scale, data.locked,
                    2, java.util.List.of(sourceMapId.id()), java.util.Optional.empty(),
                    java.util.Optional.empty(), 1L);
            if (records.register(seededRecord) != NexusMapRecordSavedData.RegisterResult.INSERTED) {
                helper.fail("Could not seed Nexus MapId lineage for cleansing test");
                return;
            }
            var recordBefore = records.resolve(mapId, data).orElseThrow();
            if (!recordBefore.lineage().equals(java.util.List.of(sourceMapId.id()))) {
                helper.fail("Cleansing test did not establish non-empty MapId lineage");
                return;
            }
            var details = NexusMapDetailSavedData.get(level);
            if (!details.record(level, mapId.id(), 321, -191, (byte) 28)) {
                helper.fail("Could not seed Nexus detail data for cleansing test");
                return;
            }
            var detailBefore = details.pages(mapId.id()).getFirst();
            int detailIdBefore = detailBefore.id;
            long detailRevisionBefore = detailBefore.revision();
            byte detailColorBefore = detailBefore.color(Math.floorMod(321, 128), Math.floorMod(-191, 128));

            var bindings = NexusMapBindingSavedData.loadCanonical(storage);
            UUID unitId = UUID.randomUUID();
            GlobalPos anchor = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(2, 2, 2)));
            if (!bindings.bind(mapId, unitId, anchor, data)) {
                helper.fail("Could not seed legacy binding ledger for cleansing test");
                return;
            }
            var bindingBefore = bindings.resolve(mapId, data).orElseThrow();

            ItemStack firstCopy = new ItemStack(Items.FILLED_MAP);
            firstCopy.set(DataComponents.MAP_ID, mapId);
            ItemStack otherCopy = firstCopy.copy();
            var menu = new CartographyTableMenu(2, player.getInventory(),
                    ContainerLevelAccess.create(level, table));
            menu.container.setItem(CartographyTableMenu.MAP_SLOT, firstCopy);
            menu.container.setItem(CartographyTableMenu.ADDITIONAL_SLOT, new ItemStack(Items.WATER_BUCKET));
            menu.clicked(CartographyTableMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);

            var detailAfter = details.pages(mapId.id());
            if (!NexusMapRecipeInputs.isCleanEmptyMap(menu.getCarried())
                    || !otherCopy.is(Items.FILLED_MAP)
                    || !mapId.equals(otherCopy.get(DataComponents.MAP_ID))
                    || level.getMapData(mapId) != data || data.colors[17] != 42
                    || !recordBefore.equals(records.resolve(mapId, data).orElse(null))
                    || detailAfter.size() != 1 || detailAfter.getFirst().id != detailIdBefore
                    || detailAfter.getFirst().revision() != detailRevisionBefore
                    || detailAfter.getFirst().color(
                    Math.floorMod(321, 128), Math.floorMod(-191, 128)) != detailColorBefore
                    || !bindingBefore.equals(bindings.resolve(mapId, data).orElse(null))) {
                helper.fail("Cleansing one copy changed shared MapId or Nexus SavedData");
                return;
            }
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(maxTicks = 20)
    public void stalePreviewRefusesBeforeConsumingReplacementInputs(GameTestHelper helper) {
        withMenu(helper, (player, menu, mapId, data) -> {
            ItemStack replacement = new ItemStack(Items.FILLED_MAP, 2);
            replacement.set(DataComponents.MAP_ID, mapId);
            menu.container.setItem(CartographyTableMenu.MAP_SLOT, replacement);
            menu.getSlot(CartographyTableMenu.RESULT_SLOT).set(new ItemStack(Items.MAP));
            menu.clicked(CartographyTableMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
            if (!menu.getCarried().isEmpty()
                    || menu.container.getItem(CartographyTableMenu.MAP_SLOT).getCount() != 2
                    || !menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).is(Items.WATER_BUCKET)
                    || menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).getCount() != 1
                    || levelMapChanged(helper, mapId, data)) {
                helper.fail("Stale cleansing preview consumed or changed live inputs");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void quickMoveProducesExactlyOneMapAndOneBucket(GameTestHelper helper) {
        withMenu(helper, (player, menu, mapId, data) -> {
            ItemStack moved = menu.quickMoveStack(player, CartographyTableMenu.RESULT_SLOT);
            if (!NexusMapRecipeInputs.isCleanEmptyMap(moved)
                    || inventoryCount(player, Items.MAP) != 1
                    || !menu.container.getItem(CartographyTableMenu.MAP_SLOT).isEmpty()
                    || !isSingleBucket(menu) || levelMapChanged(helper, mapId, data)) {
                helper.fail("Quick-move did not conserve the cleansing outputs");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void hotbarSwapProducesExactlyOneMapAndOneBucket(GameTestHelper helper) {
        withMenu(helper, (player, menu, mapId, data) -> {
            menu.clicked(CartographyTableMenu.RESULT_SLOT, 0, ContainerInput.SWAP, player);
            if (!NexusMapRecipeInputs.isCleanEmptyMap(player.getInventory().getItem(0))
                    || inventoryCount(player, Items.MAP) != 1
                    || !menu.container.getItem(CartographyTableMenu.MAP_SLOT).isEmpty()
                    || !isSingleBucket(menu) || levelMapChanged(helper, mapId, data)) {
                helper.fail("Hotbar swap did not conserve the cleansing outputs");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void resultDropProducesExactlyOneMapAndOneBucket(GameTestHelper helper) {
        withMenu(helper, (player, menu, mapId, data) -> {
            menu.clicked(CartographyTableMenu.RESULT_SLOT, 0, ContainerInput.THROW, player);
            int dropped = helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(8)).stream()
                    .filter(entity -> entity.getItem().is(Items.MAP))
                    .mapToInt(entity -> entity.getItem().getCount()).sum();
            if (dropped != 1 || inventoryCount(player, Items.MAP) != 0
                    || !menu.container.getItem(CartographyTableMenu.MAP_SLOT).isEmpty()
                    || !isSingleBucket(menu) || levelMapChanged(helper, mapId, data)) {
                helper.fail("Drop did not conserve the cleansing outputs");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void fullInventoryQuickMoveRefusesWithoutConsumption(GameTestHelper helper) {
        withMenu(helper, (player, menu, mapId, data) -> {
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            ItemStack mapBefore = menu.container.getItem(CartographyTableMenu.MAP_SLOT).copy();
            if (!menu.quickMoveStack(player, CartographyTableMenu.RESULT_SLOT).isEmpty()
                    || !ItemStack.matches(mapBefore, menu.container.getItem(CartographyTableMenu.MAP_SLOT))
                    || !menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).is(Items.WATER_BUCKET)
                    || menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).getCount() != 1
                    || !NexusMapRecipeInputs.isCleanEmptyMap(
                    menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem())
                    || levelMapChanged(helper, mapId, data)) {
                helper.fail("Full-inventory quick-move consumed or changed cleansing state");
                return;
            }
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void creativeCloneCannotDuplicateCleansingResult(GameTestHelper helper) {
        withMenu(helper, (player, menu, mapId, data) -> {
            player.getAbilities().instabuild = true;
            ItemStack mapBefore = menu.container.getItem(CartographyTableMenu.MAP_SLOT).copy();
            menu.clicked(CartographyTableMenu.RESULT_SLOT, 2, ContainerInput.CLONE, player);
            if (!menu.getCarried().isEmpty() || inventoryCount(player, Items.MAP) != 0
                    || !ItemStack.matches(mapBefore, menu.container.getItem(CartographyTableMenu.MAP_SLOT))
                    || !menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).is(Items.WATER_BUCKET)
                    || menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT).getCount() != 1
                    || !NexusMapRecipeInputs.isCleanEmptyMap(
                    menu.getSlot(CartographyTableMenu.RESULT_SLOT).getItem())
                    || levelMapChanged(helper, mapId, data)) {
                helper.fail("Creative clone duplicated or consumed cleansing state");
                return;
            }
            helper.succeed();
        });
    }

    private static void withMenu(GameTestHelper helper, MenuCase test) {
        var level = helper.getLevel();
        BlockPos table = helper.absolutePos(new BlockPos(4, 2, 4));
        level.setBlockAndUpdate(table, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        var player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(table.above()));
        var mapId = level.getFreeMapId();
        var data = MapItemSavedData.createFresh(320, -192, (byte) 2,
                false, false, level.dimension());
        data.colors[17] = 42;
        level.setMapData(mapId, data);
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, mapId);
        var menu = new CartographyTableMenu(2, player.getInventory(),
                ContainerLevelAccess.create(level, table));
        menu.container.setItem(CartographyTableMenu.MAP_SLOT, map);
        menu.container.setItem(CartographyTableMenu.ADDITIONAL_SLOT, new ItemStack(Items.WATER_BUCKET));
        try {
            test.run(player, menu, mapId, data);
        } finally {
            player.discard();
        }
    }

    private static boolean levelMapChanged(
            GameTestHelper helper,
            net.minecraft.world.level.saveddata.maps.MapId mapId,
            MapItemSavedData data) {
        return helper.getLevel().getMapData(mapId) != data || data.colors[17] != 42;
    }

    private static boolean isSingleBucket(CartographyTableMenu menu) {
        ItemStack bucket = menu.container.getItem(CartographyTableMenu.ADDITIONAL_SLOT);
        return bucket.is(Items.BUCKET) && bucket.getCount() == 1;
    }

    private static int inventoryCount(net.minecraft.server.level.ServerPlayer player,
                                      net.minecraft.world.item.Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    @FunctionalInterface
    private interface MenuCase {
        void run(net.minecraft.server.level.ServerPlayer player, CartographyTableMenu menu,
                 net.minecraft.world.level.saveddata.maps.MapId mapId, MapItemSavedData data);
    }
}
