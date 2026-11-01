package dev.totem.nexus.space;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.UUID;

/** Real menu paths; every mock player is removed before the fixture completes. */
public final class NexusMapCraftingTransactionGameTest {
    @GameTest(maxTicks = 30)
    public void cartographyActuallyExpandsPastFourAndPreservesExplorationAcrossReload(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            raiseToFour(helper, map); var level = helper.getLevel();
            var sourceId = map.get(DataComponents.MAP_ID); var old = MapItem.getSavedData(map, level); old.setColor(7, 9, (byte) 22);
            var terrain = NexusMapDetailSavedData.get(level); terrain.record(level, sourceId.id(), anchor.getX(), anchor.getZ(), (byte) 30);
            ItemStack current = map;
            for (int scale = 5; scale <= 7; scale++) {
                var menu = cartography(helper, player, anchor);
                menu.container.setItem(0, current.copy()); menu.container.setItem(1, new ItemStack(Items.PAPER));
                require(helper, !menu.getSlot(2).getItem().isEmpty(), "Native cartography preview still capped at four");
                menu.clicked(2, 0, ContainerInput.PICKUP, player); current = menu.getCarried().copy(); menu.setCarried(ItemStack.EMPTY);
                require(helper, !current.isEmpty() && menu.container.getItem(0).isEmpty() && menu.container.getItem(1).isEmpty(), "Expansion did not consume exactly map+paper");
                var data = MapItem.getSavedData(current, level); var records = level.getServer().overworld().getDataStorage()
                        .computeIfAbsent(NexusMapRecordSavedData.TYPE);
                var entry = records.resolve(current.get(DataComponents.MAP_ID), data).orElseThrow();
                require(helper, entry.extent(data).scale() == scale && data.scale == 4 && data.centerX == anchor.getX() && data.centerZ == anchor.getZ(), "Expansion lost fixed center/effective scale");
                require(helper, data.colors[7+9*128] == 22 && terrain.known(current.get(DataComponents.MAP_ID).id(), anchor.getX(), anchor.getZ()), "Expansion lost explored terrain");
                var encoded = NexusMapRecordSavedData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, records).getOrThrow();
                var loaded = NexusMapRecordSavedData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
                require(helper, loaded.resolve(current.get(DataComponents.MAP_ID), data).orElseThrow().extent(data).scale() == scale, "Reload lost expanded extent");
                var detail = NexusMapDetailSavedData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,
                        NexusMapDetailSavedData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, terrain).getOrThrow()).getOrThrow();
                require(helper, detail.known(current.get(DataComponents.MAP_ID).id(), anchor.getX(), anchor.getZ()), "Reload lost explored detail");
            }
            require(helper, old.colors[7+9*128] == 22 && terrain.known(sourceId.id(), anchor.getX(), anchor.getZ()), "Expansion modified source map");
        });
    }
    @GameTest(maxTicks = 30)
    public void workbenchRecipeAndNativeTakeSupportScaleFiveWithoutForgedPreview(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            raiseToFour(helper, map); var menu = workbench(helper, player, anchor); fill(menu, map, 1);
            menu.slotsChanged(menu.getInputGridSlots().getFirst().container);
            require(helper, !menu.getResultSlot().getItem().isEmpty(), "Native workbench preview still capped at four");
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            var result = menu.getCarried(); var data = MapItem.getSavedData(result, helper.getLevel());
            require(helper, data != null && helper.getLevel().getServer().overworld().getDataStorage()
                    .computeIfAbsent(NexusMapRecordSavedData.TYPE)
                    .resolve(result.get(DataComponents.MAP_ID), data).orElseThrow().extent(data).scale() == 5, "Workbench did not expand past four");
            requireInputs(helper, menu, map, 0); menu.setCarried(ItemStack.EMPTY);
        });
    }
    @GameTest(maxTicks = 30)
    public void extendedMapLockKeepsExtentAndCopyOnWriteExploration(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            raiseToFour(helper, map); require(helper, NexusMapLifecycleAuthority.postProcess(map, helper.getLevel(), MapPostProcessing.SCALE)
                    == NexusMapLifecycleAuthority.PostProcessResult.PROCESSED, "Extended fixture failed");
            var source = map.get(DataComponents.MAP_ID); var store = NexusMapDetailSavedData.get(helper.getLevel());
            int x = anchor.getX() + 1300, z = anchor.getZ(); store.record(helper.getLevel(), source.id(), x, z, (byte) 22);
            var menu = cartography(helper, player, anchor); menu.container.setItem(0, map.copy()); menu.container.setItem(1, new ItemStack(Items.GLASS_PANE));
            menu.clicked(2, 0, ContainerInput.PICKUP, player); var locked = menu.getCarried(); var data = MapItem.getSavedData(locked, helper.getLevel());
            require(helper, data != null && data.locked && helper.getLevel().getServer().overworld().getDataStorage()
                    .computeIfAbsent(NexusMapRecordSavedData.TYPE)
                    .resolve(locked.get(DataComponents.MAP_ID), data).orElseThrow().extent(data).scale() == 5, "Lock lost extended scale");
            store.record(helper.getLevel(), source.id(), x, z, (byte) 30);
            var historical = store.pages(locked.get(DataComponents.MAP_ID).id()).stream().filter(p -> !p.historical && p.scale == 0
                    && p.x == Math.floorDiv(x,128)*128+64 && p.z == Math.floorDiv(z,128)*128+64).findFirst().orElseThrow();
            require(helper, historical.color(Math.floorMod(x,128),Math.floorMod(z,128)) == 22, "Locked extended terrain followed mutable source");
            require(helper, NexusMapLifecycleAuthority.expansionAnchorEligibility(locked, helper.getLevel()) == NexusMapLifecycleAuthority.ScaleEligibility.DENIED, "Locked extended map expanded");
            menu.setCarried(ItemStack.EMPTY);
        });
    }
    @GameTest(maxTicks = 30)
    public void repeatedExpansionStopsOnlyAtWorldCoverageAndDestroyedOriginStillExpands(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            raiseToFour(helper, map);
            helper.getLevel().setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
            var menu = cartography(helper, player, anchor); menu.container.setItem(0, map.copy()); menu.container.setItem(1, new ItemStack(Items.PAPER));
            menu.clicked(2, 0, ContainerInput.PICKUP, player);
            var derived = menu.getCarried();
            require(helper, derived.is(Items.FILLED_MAP) && menu.container.getItem(0).isEmpty()
                    && menu.container.getItem(1).isEmpty()
                    && !map.get(DataComponents.MAP_ID).equals(derived.get(DataComponents.MAP_ID)),
                    "Destroyed origin blocked or duplicated extended recipe consumption");
            map = derived;
            int steps=0;
            while (NexusMapLifecycleAuthority.expansionAnchorEligibility(map, helper.getLevel()) == NexusMapLifecycleAuthority.ScaleEligibility.ALLOWED) {
                require(helper, steps++ < NexusMapExtent.MAX_SCALE, "Expansion did not terminate at world bounds");
                require(helper, NexusMapLifecycleAuthority.postProcess(map, helper.getLevel(), MapPostProcessing.SCALE) == NexusMapLifecycleAuthority.PostProcessResult.PROCESSED, "Repeated expansion failed");
            }
            var data = MapItem.getSavedData(map, helper.getLevel()); var extent = helper.getLevel().getServer().overworld().getDataStorage()
                    .computeIfAbsent(NexusMapRecordSavedData.TYPE)
                    .resolve(map.get(DataComponents.MAP_ID), data).orElseThrow().extent(data);
            require(helper, extent.coversSupportedWorld() && data.scale == 4, "Expansion stopped before covering supported world");
        });
    }
    @GameTest(maxTicks = 30)
    public void extendedSamplerRecordsLoadedRouteOutsideVanillaFootprintOnlyInsideLogicalExtent(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            var level = helper.getLevel(); int x = anchor.getX(), z = anchor.getZ();
            level.setBlockAndUpdate(new BlockPos(x, anchor.getY()-1, z), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(x, anchor.getY()-1, z-1), Blocks.STONE.defaultBlockState());
            var base = NexusMapLifecycleAuthority.exactData(x-1400,z,(byte)4,false,level.dimension());
            var store = new NexusMapDetailSavedData(); int owner = map.get(DataComponents.MAP_ID).id();
            var queue = new dev.totem.nexus.map.MapRecordingQueue(); queue.offer(Math.floorDiv(x,8),Math.floorDiv(z,8),true,0);
            NexusMapDetailSampling.advance(level,owner,base,store,queue,0,64,new int[]{16384},Long.MAX_VALUE,new NexusMapExtent(x-1400,z,5));
            require(helper, store.known(owner,x,z), "Extended sampler ignored legitimate loaded route outside vanilla footprint");
            var outside = new NexusMapDetailSavedData(); var second = new dev.totem.nexus.map.MapRecordingQueue(); second.offer(Math.floorDiv(x,8),Math.floorDiv(z,8),true,0);
            NexusMapDetailSampling.advance(level,owner,base,outside,second,0,64,new int[]{16384},Long.MAX_VALUE,new NexusMapExtent(x-3000,z,5));
            require(helper, !outside.known(owner,x,z), "Extended sampler recorded outside its logical extent");
            var old = new NexusMapDetailSavedData(); var third = new dev.totem.nexus.map.MapRecordingQueue(); third.offer(Math.floorDiv(x,8),Math.floorDiv(z,8),true,0);
            NexusMapDetailSampling.advance(level,owner,base,old,third,0,64,new int[]{16384},Long.MAX_VALUE);
            require(helper, !old.known(owner,x,z), "Ordinary sampler invented vanilla exploration");
        });
    }
    @GameTest(maxTicks = 30, environment = "totem-nexus-gametest:map_detail_capacity")
    public void extendedCapacityAllowsLastSnapshotThenRefusesWithoutConsumingInputs(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            raiseToFour(helper, map); var level = helper.getLevel(); var storage = level.getServer().overworld().getDataStorage();
            var original = NexusMapDetailSavedData.get(level); var data = new net.minecraft.nbt.CompoundTag();
            var pages = new net.minecraft.nbt.ListTag(); byte[] pixels = new byte[16384];
            for (int i=0;i<NexusMapDetailSavedData.MAX_WORLD_PAGES-1;i++) {
                var page = new net.minecraft.nbt.CompoundTag(); page.putInt("id", level.getFreeMapId().id());
                page.putInt("x",0); page.putInt("z",0); page.putInt("scale",4); page.putBoolean("historical",true); page.putByteArray("colors",pixels); pages.add(page);
            }
            data.put("pages",pages); var maps = new net.minecraft.nbt.ListTag(); var index = new net.minecraft.nbt.CompoundTag();
            index.putInt("map_id",map.get(DataComponents.MAP_ID).id()); index.put("pages",new net.minecraft.nbt.ListTag()); maps.add(index); data.put("maps",maps);
            var almostFull = NexusMapDetailSavedData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,data).getOrThrow(); storage.set(NexusMapDetailSavedData.TYPE,almostFull);
            try {
                var menu = cartography(helper,player,anchor); menu.container.setItem(0,map.copy()); menu.container.setItem(1,new ItemStack(Items.PAPER));
                require(helper,!menu.getSlot(2).getItem().isEmpty(),"Final available history page was refused");
                menu.clicked(2,0,ContainerInput.PICKUP,player); var expanded = menu.getCarried().copy(); menu.setCarried(ItemStack.EMPTY);
                require(helper,!expanded.isEmpty(),"Final history reservation failed");
                int allocated = lastAllocated(helper); var ledger = encodedLedger(helper);
                var full = cartography(helper,player,anchor); full.container.setItem(0,expanded.copy()); full.container.setItem(1,new ItemStack(Items.PAPER));
                require(helper,full.getSlot(2).getItem().isEmpty(),"Full terrain yielded native preview");
                // Simulate an already visible stale preview; actual result gates must still conserve inputs.
                full.getSlot(2).container.setItem(0,preview(expanded));
                require(helper,!full.getSlot(2).getItem().isEmpty() && full.getSlot(2).getItem().get(DataComponents.MAP_POST_PROCESSING)==MapPostProcessing.SCALE,
                        "Stale-preview fixture was cleared before take");
                full.clicked(2,0,ContainerInput.QUICK_MOVE,player);
                require(helper,ItemStack.matches(expanded,full.container.getItem(0)) && full.container.getItem(1).is(Items.PAPER)
                        && full.getCarried().isEmpty(),"Full terrain consumed map or paper");
                require(helper,allocated==lastAllocated(helper) && ledger.equals(encodedLedger(helper)),"Full terrain allocated map state");
                for(int i=0;i<36;i++) require(helper,player.getInventory().getItem(i).isEmpty(),"Quota refusal leaked quick-move output");
            } finally { storage.set(NexusMapDetailSavedData.TYPE,original); }
        });
    }
    private static void raiseToFour(GameTestHelper helper, ItemStack map) {
        for (int i=0;i<4;i++) {
            var result = NexusMapLifecycleAuthority.postProcess(map,helper.getLevel(),MapPostProcessing.SCALE);
            require(helper, result == NexusMapLifecycleAuthority.PostProcessResult.PROCESSED,
                    "Scale-four fixture failed at step " + i + ": result=" + result
                            + ", lastMapId=" + lastAllocated(helper));
        }
    }
    @GameTest(maxTicks = 30)
    public void unavailableTerrainPreservesRecipeAndAllocatesNoMapId(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            var level = helper.getLevel();
            var state = (NexusMapBindingStorage.TerrainFailureAccess) level.getServer().overworld().getDataStorage();
            var previous = state.totem$terrainFailure();
            var index = level.getServer().getDataStorage().computeIfAbsent(net.minecraft.world.level.saveddata.maps.MapIndex.TYPE);
            int before = ((dev.totem.nexus.mixin.NexusMapIndexAccessor) index).totem$lastMapId();
            CraftingMenu menu = workbench(helper, player, anchor);
            fill(menu, map, 1); menu.getResultSlot().set(preview(map));
            state.totem$terrainFailure(new NexusMapBindingStorage.ReadFailure(NexusMapDetailSavedData.TYPE, null));
            try {
                require(helper, NexusMapLifecycleAuthority.expansionAnchorEligibility(map, level)
                        == NexusMapLifecycleAuthority.ScaleEligibility.DENIED, "Unavailable terrain allowed expansion");
                menu.clicked(0, 0, ContainerInput.PICKUP, player);
                requireInputs(helper, menu, map, 1);
                require(helper, menu.getCarried().isEmpty(), "Unavailable terrain yielded result");
                require(helper, NexusMapLifecycleAuthority.createBoundMap(level, anchor, NexusInterfaceBinding.read(map),
                        new ItemStack(Items.MAP)).isEmpty(), "Unavailable terrain created bound map");
                require(helper, ((dev.totem.nexus.mixin.NexusMapIndexAccessor) index).totem$lastMapId() == before,
                        "Unavailable terrain consumed MapId");
            } finally { state.totem$terrainFailure(previous); }
        });
    }

    @GameTest(maxTicks = 30)
    public void frozenLedgerPreservesRecipeAndDoesNotAllocateMapId(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            var level = helper.getLevel();
            var ledger = NexusMapBindingSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
            CraftingMenu menu = workbench(helper, player, anchor);
            fill(menu, map, 1); menu.getResultSlot().set(preview(map));
            Object token = ledger.freezeForMigration();
            try {
                menu.clicked(0, 0, ContainerInput.PICKUP, player);
                requireCompleted(helper, map, menu.getCarried(), false);
                requireInputs(helper, menu, map, 0);
            } finally { ledger.releaseMigrationFreeze(token); }
        });
    }

    @GameTest(maxTicks = 30)
    public void hotbarSwapPreservesOccupiedSlotAndCraftsIntoAnEmptySlot(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            player.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
            CraftingMenu menu = workbench(helper, player, anchor);
            fill(menu, map, 1); menu.getResultSlot().set(preview(map));
            menu.clicked(0, 0, ContainerInput.SWAP, player);
            // Native result slots reject replacing an occupied hotbar slot.
            require(helper, player.getInventory().getItem(0).is(Items.STONE)
                    && player.getInventory().getItem(0).getCount() == 64, "Occupied hotbar stack changed");
            requireInputs(helper, menu, map, 1);
            player.getInventory().setItem(1, player.getInventory().removeItemNoUpdate(0));
            menu.clicked(0, 0, ContainerInput.SWAP, player);
            requireCompleted(helper, map, player.getInventory().getItem(0), false);
            int stones = 0;
            for (int i = 0; i < 36; i++) if (player.getInventory().getItem(i).is(Items.STONE)) stones += player.getInventory().getItem(i).getCount();
            require(helper, stones == 64, "Hotbar swap lost the displaced stack");
            requireInputs(helper, menu, map, 0);
        });
    }

    @GameTest(maxTicks = 30)
    public void validDropConsumesOneRecipeAndDropsOneBoundMap(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            CraftingMenu menu = workbench(helper, player, anchor);
            fill(menu, map, 1); menu.getResultSlot().set(preview(map));
            menu.clicked(0, 0, ContainerInput.THROW, player);
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(anchor).inflate(8));
            require(helper, drops.size() == 1, "Drop did not produce exactly one entity");
            requireCompleted(helper, map, drops.getFirst().getItem(), false);
            drops.getFirst().discard();
            requireInputs(helper, menu, map, 0);
        });
    }

    @GameTest(maxTicks = 30)
    public void creativeCloneKeepsIngredientsAndProducesOneSharedBoundMapStack(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            player.getAbilities().instabuild = true;
            CraftingMenu menu = workbench(helper, player, anchor);
            fill(menu, map, 1); menu.getResultSlot().set(preview(map));
            menu.clicked(0, 2, ContainerInput.CLONE, player);
            require(helper, menu.getCarried().getCount() == menu.getCarried().getMaxStackSize(), "Creative clone produced the wrong stack size");
            requireCompleted(helper, map, menu.getCarried().copyWithCount(1), false);
            requireInputs(helper, menu, map, 1);
        });
    }

    @GameTest(maxTicks = 30)
    public void validCraftingPickupConsumesOneSetAndKeepsBindingCenter(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            CraftingMenu menu = workbench(helper, player, anchor);
            fill(menu, map, 1);
            menu.getResultSlot().set(preview(map));
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            requireCompleted(helper, map, menu.getCarried(), false);
            requireInputs(helper, menu, map, 0);
        });
    }

    @GameTest(maxTicks = 30)
    public void repeatedQuickMoveConsumesExactlyThreeSets(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            CraftingMenu menu = workbench(helper, player, anchor);
            fill(menu, map, 3);
            menu.getResultSlot().set(preview(map));
            int registrations = encodedLedger(helper).getAsJsonObject().getAsJsonArray("maps").size();
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            require(helper, encodedLedger(helper).getAsJsonObject().getAsJsonArray("maps").size() == registrations + 3,
                    "Quick-move bookkeeping allocated duplicate map registrations");
            int count = 0;
            for (int i = 0; i < 36; i++) {
                ItemStack result = player.getInventory().getItem(i);
                if (result.is(Items.FILLED_MAP)) { requireCompleted(helper, map, result, false); count += result.getCount(); }
            }
            require(helper, count == 3, "Quick move did not produce exactly three expanded maps");
            requireInputs(helper, menu, map, 0);
        });
    }

    @GameTest(maxTicks = 30)
    public void cartographyScaleAndLockConsumeOnePairAndKeepIdentity(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            for (boolean lock : new boolean[]{false, true}) {
                CartographyTableMenu menu = cartography(helper, player, anchor);
                menu.container.setItem(0, map.copy());
                menu.container.setItem(1, new ItemStack(lock ? Items.GLASS_PANE : Items.PAPER));
                ItemStack result = map.copy();
                result.set(DataComponents.MAP_POST_PROCESSING, lock ? MapPostProcessing.LOCK : MapPostProcessing.SCALE);
                menu.getSlot(2).set(result);
                menu.clicked(2, 0, ContainerInput.PICKUP, player);
                requireCompleted(helper, map, menu.getCarried(), lock);
                require(helper, menu.container.getItem(0).isEmpty() && menu.container.getItem(1).isEmpty(),
                        "Cartography did not consume exactly its input pair");
            }
        });
    }

    @GameTest(maxTicks = 30)
    public void staleCartographyIngredientIsNotConsumed(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            CartographyTableMenu menu = cartography(helper, player, anchor);
            menu.container.setItem(0, map.copy());
            menu.container.setItem(1, new ItemStack(Items.GLASS_PANE));
            // Slot.set triggers a native preview refresh; mutate the old preview in place.
            menu.getSlot(2).getItem().set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
            menu.clicked(2, 0, ContainerInput.PICKUP, player);
            require(helper, menu.getCarried().isEmpty()
                    && menu.getSlot(2).getItem().get(DataComponents.MAP_POST_PROCESSING) != MapPostProcessing.SCALE,
                    "Stale SCALE result was taken for glass");
            require(helper, ItemStack.matches(map, menu.container.getItem(0))
                    && menu.container.getItem(1).is(Items.GLASS_PANE), "Rejected cartography changed inputs");
        });
    }

    @GameTest(maxTicks = 30)
    public void forgedSourceBindingCannotUseAnAuthenticPreview(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            CraftingMenu menu = workbench(helper, player, anchor);
            ItemStack forged = map.copy(); NexusInterfaceBinding.writeIdentity(forged, UUID.randomUUID());
            fill(menu, forged, 1);
            menu.getResultSlot().set(preview(map));
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            require(helper, menu.getCarried().isEmpty(), "Forged source acquired an authentic result");
            requireInputs(helper, menu, forged, 1);
        });
    }

    @GameTest(maxTicks = 30)
    public void unregisteredNexusClaimCannotFallBackToVanillaProcessing(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            ItemStack unregistered = map.copy();
            var id = helper.getLevel().getFreeMapId();
            helper.getLevel().setMapData(id, MapItem.getSavedData(map, helper.getLevel()));
            unregistered.set(DataComponents.MAP_ID, id);
            CraftingMenu menu = workbench(helper, player, anchor);
            fill(menu, unregistered, 1); menu.getResultSlot().set(preview(unregistered));
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            require(helper, !menu.getCarried().isEmpty(), "Ordinary unregistered map was not processed as vanilla");
            require(helper, helper.getLevel().getServer().overworld().getDataStorage()
                    .computeIfAbsent(NexusMapRecordSavedData.TYPE)
                    .get(menu.getCarried().get(DataComponents.MAP_ID)).isEmpty(),
                    "Vanilla map processing invented Nexus MapId metadata");
            requireInputs(helper, menu, unregistered, 0);
        });
    }

    @GameTest(maxTicks = 30)
    public void invalidResultsAreRejectedAcrossSwapDropCloneAndQuickMove(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            for (ContainerInput input : new ContainerInput[]{ContainerInput.SWAP, ContainerInput.THROW,
                    ContainerInput.CLONE, ContainerInput.QUICK_MOVE}) {
                CraftingMenu menu = workbench(helper, player, anchor);
                menu.getResultSlot().set(preview(map));
                player.getAbilities().instabuild = input == ContainerInput.CLONE;
                menu.clicked(0, input == ContainerInput.CLONE ? 2 : 0, input, player);
                require(helper, menu.getCarried().isEmpty() && player.getInventory().getItem(0).isEmpty()
                        && menu.getResultSlot().getItem().isEmpty(), "Invalid result escaped through " + input);
            }
            require(helper, helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(anchor).inflate(16)).isEmpty(), "Invalid result was dropped into the world");
        });
    }

    @GameTest(maxTicks = 30)
    public void collectingFromAnotherSlotDoesNotHarvestTheResult(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            CraftingMenu menu = workbench(helper, player, anchor);
            ItemStack pending = preview(map); menu.getResultSlot().set(pending.copy());
            menu.setCarried(pending.copy());
            require(helper, !menu.canTakeItemForPickAll(pending, menu.getResultSlot()), "Native result slot permits collect-all");
            menu.clicked(10, 0, ContainerInput.PICKUP_ALL, player);
            require(helper, ItemStack.matches(pending, menu.getCarried())
                    && ItemStack.matches(pending, menu.getResultSlot().getItem()), "Collect-all harvested a stale result");
        });
    }

    @GameTest(maxTicks = 30)
    public void directQuickMoveRevalidatesTheActualResult(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            CraftingMenu menu = workbench(helper, player, anchor);
            menu.getResultSlot().set(preview(map));
            require(helper, menu.quickMoveStack(player, 0).isEmpty(), "Direct quick-move bypassed result validation");
            require(helper, player.getInventory().getItem(0).isEmpty(), "Direct quick-move added an invalid map");
        });
    }

    @GameTest(maxTicks = 30)
    public void fullInventoryCartographyQuickMoveDoesNotAllocateMaps(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            CartographyTableMenu menu = cartography(helper, player, anchor);
            menu.container.setItem(0, map.copy()); menu.container.setItem(1, new ItemStack(Items.PAPER));
            menu.getSlot(2).set(preview(map));
            int before = helper.getLevel().getFreeMapId().id();
            require(helper, menu.quickMoveStack(player, 2).isEmpty(), "Full cartography quick-move succeeded");
            require(helper, helper.getLevel().getFreeMapId().id() == before + 1, "Full cartography allocated orphan map IDs");
            require(helper, ItemStack.matches(map, menu.container.getItem(0))
                    && menu.container.getItem(1).getCount() == 1, "Full cartography consumed inputs");
        });
    }

    @GameTest(maxTicks = 30)
    public void ordinaryInventoryClickNeverDeletesAPendingMap(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            ItemStack pending = preview(map);
            helper.getLevel().setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
            player.inventoryMenu.getSlot(9).set(pending.copy());
            player.inventoryMenu.clicked(9, 0, ContainerInput.PICKUP, player);
            require(helper, ItemStack.matches(pending, player.inventoryMenu.getCarried()),
                    "Ordinary inventory map was cleared by a recipe-result guard");
        });
    }

    @GameTest(maxTicks = 30)
    public void staleResultWithoutIngredientsCannotProduceAMap(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            CraftingMenu menu = workbench(helper, player, anchor);
            menu.getResultSlot().set(preview(map));
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            require(helper, menu.getCarried().isEmpty() && menu.getResultSlot().getItem().isEmpty(),
                    "Stale result without ingredients produced a map");
        });
    }

    @GameTest(maxTicks = 30)
    public void resultFromAnotherRegisteredMapCannotConsumeCurrentIngredients(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            CraftingMenu menu = workbench(helper, player, anchor);
            fill(menu, map, 1);
            ItemStack other = registeredCopy(helper, map);
            menu.getResultSlot().set(preview(other));
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            require(helper, menu.getCarried().isEmpty() && menu.getResultSlot().getItem().isEmpty(),
                    "Another map's stale result was accepted");
            requireInputs(helper, menu, map, 1);
        });
    }

    @GameTest(maxTicks = 30)
    public void fullInventoryQuickMoveDoesNotAllocateMapsOrConsumeInputs(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            CraftingMenu menu = workbench(helper, player, anchor);
            fill(menu, map, 1);
            menu.getResultSlot().set(preview(map));
            int before = helper.getLevel().getFreeMapId().id();
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            int after = helper.getLevel().getFreeMapId().id();
            require(helper, after == before + 1, "Failed quick-move allocated orphan map records");
            requireInputs(helper, menu, map, 1);
        });
    }

    @GameTest(maxTicks = 30)
    public void lateDenialRestoresWorkbenchAndCartographyForNativeInputModes(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            for (boolean cartography : new boolean[]{false, true}) {
                for (ContainerInput input : new ContainerInput[]{ContainerInput.PICKUP, ContainerInput.QUICK_MOVE,
                        ContainerInput.SWAP, ContainerInput.THROW, ContainerInput.CLONE}) {
                    player.getInventory().clearContent();
                    player.getAbilities().instabuild = input == ContainerInput.CLONE;
                    net.minecraft.world.inventory.AbstractContainerMenu menu;
                    if (cartography) {
                        var table = cartography(helper, player, anchor);
                        table.container.setItem(0, map.copy()); table.container.setItem(1, new ItemStack(Items.PAPER));
                        table.getSlot(2).set(preview(map)); menu=table;
                    } else {
                        var table=workbench(helper, player, anchor); fill(table,map,1);
                        table.getResultSlot().set(preview(map)); menu=table;
                    }
                    int result=cartography?2:0;
                    var before=menu.slots.stream().map(slot->slot.getItem().copy()).toList();
                    var ledger=encodedLedger(helper); int allocated=lastAllocated(helper);
                    int stat=player.getStats().getValue(net.minecraft.stats.Stats.ITEM_CRAFTED.get(Items.FILLED_MAP));
                    NexusMapPostProcessFailureProbe.arm(map,0,NexusMapPostProcessFailureProbe.Failure.DENY);
                    try { menu.clicked(result,input == ContainerInput.CLONE ? 2 : 0,input,player); } finally { NexusMapPostProcessFailureProbe.clear(); }
                    for(int i=0;i<before.size();i++) require(helper,ItemStack.matches(before.get(i),menu.getSlot(i).getItem()),
                            "Late denial changed slot " + i + " via " + input + "; cartography=" + cartography);
                    require(helper,menu.getCarried().isEmpty(),"Late denial changed cursor");
                    require(helper,allocated==lastAllocated(helper) && ledger.equals(encodedLedger(helper)),"Late denial allocated orphan map state");
                    require(helper,stat==player.getStats().getValue(net.minecraft.stats.Stats.ITEM_CRAFTED.get(Items.FILLED_MAP)),"Late denial changed crafted statistic");
                    // A fresh take proves the transaction scope was cleared and inputs remain usable.
                    player.getAbilities().instabuild=false;
                    menu.clicked(result,0,ContainerInput.PICKUP,player);
                    requireCompleted(helper,map,menu.getCarried(),false);
                    menu.setCarried(ItemStack.EMPTY);
                }
            }
        });
    }

    @GameTest(maxTicks = 30)
    public void secondQuickMoveDenialKeepsFirstCommittedCraft(GameTestHelper helper) {
        withFixture(helper,(player,map,anchor)-> {
            var menu=workbench(helper,player,anchor); fill(menu,map,3); menu.getResultSlot().set(preview(map));
            int registrations = encodedLedger(helper).getAsJsonObject().getAsJsonArray("maps").size();
            NexusMapPostProcessFailureProbe.arm(map,1,NexusMapPostProcessFailureProbe.Failure.DENY);
            try { menu.clicked(0,0,ContainerInput.QUICK_MOVE,player); } finally { NexusMapPostProcessFailureProbe.clear(); }
            int count=0;
            for(int i=0;i<36;i++) if(player.getInventory().getItem(i).is(Items.FILLED_MAP)) {
                requireCompleted(helper,map,player.getInventory().getItem(i),false); count+=player.getInventory().getItem(i).getCount();
            }
            require(helper,count==1,"Later failure lost or duplicated earlier successful craft: " + count);
            require(helper, encodedLedger(helper).getAsJsonObject().getAsJsonArray("maps").size() == registrations + 1,
                    "Later failure or bookkeeping allocated an orphan registration");
            requireInputs(helper,menu,map,2);
        });
    }

    @GameTest(maxTicks = 30)
    public void directQuickMoveLateDenialRestoresInputsAndInventory(GameTestHelper helper) {
        withFixture(helper,(player,map,anchor)-> {
            var menu=cartography(helper,player,anchor);
            menu.container.setItem(0,map.copy()); menu.container.setItem(1,new ItemStack(Items.GLASS_PANE));
            int allocated=lastAllocated(helper); var ledger=encodedLedger(helper);
            NexusMapPostProcessFailureProbe.arm(map,0,NexusMapPostProcessFailureProbe.Failure.DENY);
            try { require(helper,menu.quickMoveStack(player,2).isEmpty(),"Failed quick move reported success"); }
            finally { NexusMapPostProcessFailureProbe.clear(); }
            require(helper,ItemStack.matches(map,menu.container.getItem(0)) && menu.container.getItem(1).is(Items.GLASS_PANE),"Direct failed quick move consumed inputs");
            for(int i=0;i<36;i++) require(helper,player.getInventory().getItem(i).isEmpty(),"Direct failure leaked result to inventory");
            require(helper,allocated==lastAllocated(helper) && ledger.equals(encodedLedger(helper)),"Direct failure left map state");
        });
    }

    @GameTest(maxTicks = 30)
    public void unrelatedCallbackFailurePropagatesAndClearsTransactionScope(GameTestHelper helper) {
        withFixture(helper,(player,map,anchor)-> {
            var menu=workbench(helper,player,anchor); fill(menu,map,1); menu.getResultSlot().set(preview(map));
            NexusMapPostProcessFailureProbe.arm(map,0,NexusMapPostProcessFailureProbe.Failure.ERROR);
            boolean propagated=false;
            try { menu.quickMoveStack(player,0); }
            catch(IllegalStateException expected) { propagated="Expected test callback failure".equals(expected.getMessage()); }
            finally { NexusMapPostProcessFailureProbe.clear(); }
            require(helper,propagated,"Unrelated callback failure was swallowed");
            require(helper,!NexusMapCraftingTransaction.abortIfActive(preview(map),helper.getLevel()),"Transaction scope leaked after callback error");
        });
    }

    @GameTest(maxTicks = 30)
    public void originRemovedAfterCartographyConsumptionStillCommitsDerivedMap(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            var menu = cartography(helper, player, anchor);
            menu.container.setItem(0, map.copy());
            menu.container.setItem(1, new ItemStack(Items.PAPER));
            int allocated = lastAllocated(helper);
            helper.getLevel().setBlockAndUpdate(anchor, Blocks.AIR.defaultBlockState());
            menu.clicked(2, 1, ContainerInput.PICKUP, player);
            var derived = menu.getCarried();
            require(helper, menu.container.getItem(0).isEmpty() && menu.container.getItem(1).isEmpty(),
                    "Late origin removal prevented atomic input consumption");
            require(helper, derived.is(Items.FILLED_MAP)
                    && !map.get(DataComponents.MAP_ID).equals(derived.get(DataComponents.MAP_ID)),
                    "Late origin removal did not return a derived MapId");
            require(helper, allocated < lastAllocated(helper),
                    "Late origin removal did not publish derived map state");
        });
    }

    @GameTest(maxTicks = 30)
    public void lateLockDenialRestoresCartographyInputs(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            var menu = cartography(helper, player, anchor);
            menu.container.setItem(0, map.copy());
            menu.container.setItem(1, new ItemStack(Items.GLASS_PANE));
            int allocated = lastAllocated(helper);
            NexusMapPostProcessFailureProbe.arm(map, 0, NexusMapPostProcessFailureProbe.Failure.DENY);
            try { menu.clicked(2, 0, ContainerInput.PICKUP, player); }
            finally { NexusMapPostProcessFailureProbe.clear(); }
            require(helper, ItemStack.matches(map, menu.container.getItem(0))
                    && menu.container.getItem(1).is(Items.GLASS_PANE), "Late lock refusal consumed map/glass");
            require(helper, menu.getCarried().isEmpty() && allocated == lastAllocated(helper), "Late lock refusal leaked result or MapId");
            menu.clicked(2, 0, ContainerInput.PICKUP, player);
            requireCompleted(helper, map, menu.getCarried(), true);
        });
    }

    @GameTest(maxTicks = 30)
    public void occupiedNextMapIdDeniesBeforeAllocatorMutation(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            int allocated = lastAllocated(helper);
            var candidate = new net.minecraft.world.level.saveddata.maps.MapId(allocated + 1);
            var records = helper.getLevel().getServer().overworld().getDataStorage()
                    .computeIfAbsent(NexusMapRecordSavedData.TYPE);
            require(helper, records.bootstrap(candidate, MapItem.getSavedData(map, helper.getLevel())).result()
                    != NexusMapRecordSavedData.RegisterResult.CONFLICT, "Could not reserve collision fixture");
            var menu = workbench(helper, player, anchor);
            fill(menu, map, 1); menu.getResultSlot().set(preview(map));
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            requireCompleted(helper, map, menu.getCarried(), false);
            requireInputs(helper, menu, map, 0);
            require(helper, menu.getCarried().get(DataComponents.MAP_ID).id() > candidate.id(),
                    "Occupied MapId was reused instead of skipped");
        });
    }

    @GameTest(maxTicks = 30)
    public void secondBatchDropDenialKeepsFirstDropAndRemainingInputs(GameTestHelper helper) {
        withFixture(helper, (player, map, anchor) -> {
            var menu = workbench(helper, player, anchor);
            fill(menu, map, 3); menu.getResultSlot().set(preview(map));
            int stat = player.getStats().getValue(net.minecraft.stats.Stats.ITEM_CRAFTED.get(Items.FILLED_MAP));
            NexusMapPostProcessFailureProbe.arm(map, 1, NexusMapPostProcessFailureProbe.Failure.DENY);
            try { menu.clicked(0, 1, ContainerInput.THROW, player); }
            finally { NexusMapPostProcessFailureProbe.clear(); }
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(anchor).inflate(8));
            require(helper, drops.size() == 1 && drops.getFirst().getItem().getCount() == 1,
                    "Later refusal duplicated or removed the committed drop");
            requireCompleted(helper, map, drops.getFirst().getItem(), false);
            requireInputs(helper, menu, map, 2);
            require(helper, player.getStats().getValue(net.minecraft.stats.Stats.ITEM_CRAFTED.get(Items.FILLED_MAP)) == stat + 1,
                    "Failed batch drop changed committed craft count");
            var records = helper.getLevel().getServer().overworld().getDataStorage()
                    .computeIfAbsent(NexusMapRecordSavedData.TYPE);
            require(helper, records.get(drops.getFirst().getItem().get(DataComponents.MAP_ID)).isPresent(),
                    "Committed drop lost its MapId record");
            drops.forEach(net.minecraft.world.entity.Entity::discard);
        });
    }

    @GameTest(maxTicks = 30)
    public void exhaustedAllocatorRefusesBeforeConsumingInputs(GameTestHelper helper) {
        require(helper, NexusMapLifecycleAuthority.mapAllocatorExhausted(Integer.MAX_VALUE),
                "Maximum MapId was not classified as exhausted");
        require(helper, !NexusMapLifecycleAuthority.mapAllocatorExhausted(Integer.MAX_VALUE - 1),
                "Last allocatable MapId was classified as exhausted");
        helper.succeed();
    }

    private static int lastAllocated(GameTestHelper helper) {
        var index=helper.getLevel().getServer().getDataStorage().computeIfAbsent(net.minecraft.world.level.saveddata.maps.MapIndex.TYPE);
        return ((dev.totem.nexus.mixin.NexusMapIndexAccessor)index).totem$lastMapId();
    }
    private static com.google.gson.JsonElement encodedLedger(GameTestHelper helper) {
        return NexusMapRecordSavedData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                helper.getLevel().getServer().overworld().getDataStorage()
                        .computeIfAbsent(NexusMapRecordSavedData.TYPE)).getOrThrow();
    }

    private static ItemStack preview(ItemStack source) {
        ItemStack result = source.copyWithCount(1);
        result.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
        return result;
    }

    private static ItemStack registeredCopy(GameTestHelper helper, ItemStack source) {
        var level = helper.getLevel();
        var sourceData = MapItem.getSavedData(source, level);
        var mapId = level.getFreeMapId();
        var result = source.copyWithCount(1);
        result.set(DataComponents.MAP_ID, mapId);
        NexusInterfaceBinding.clearIdentityMetadata(result);
        level.setMapData(mapId, sourceData);
        var registration = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(NexusMapRecordSavedData.TYPE).bootstrap(mapId, sourceData);
        require(helper, registration.result() != NexusMapRecordSavedData.RegisterResult.CONFLICT,
                "Could not register second MapId fixture");
        return result;
    }

    private static CraftingMenu workbench(GameTestHelper helper, ServerPlayer player, BlockPos anchor) {
        BlockPos table = anchor.east();
        helper.getLevel().setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
        return new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
    }

    private static CartographyTableMenu cartography(GameTestHelper helper, ServerPlayer player, BlockPos anchor) {
        BlockPos table = anchor.south();
        helper.getLevel().setBlockAndUpdate(table, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        return new CartographyTableMenu(2, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
    }

    private static void requireCompleted(GameTestHelper helper, ItemStack source, ItemStack result, boolean lock) {
        require(helper, result.is(Items.FILLED_MAP) && result.getCount() == 1
                && !result.has(DataComponents.MAP_POST_PROCESSING)
                && !source.get(DataComponents.MAP_ID).equals(result.get(DataComponents.MAP_ID))
                && NexusInterfaceBinding.read(source) == null
                && NexusInterfaceBinding.read(result) == null, "Result retained legacy item identity");
        var oldData = MapItem.getSavedData(source, helper.getLevel());
        var newData = MapItem.getSavedData(result, helper.getLevel());
        require(helper, oldData.centerX == newData.centerX && oldData.centerZ == newData.centerZ
                && newData.locked == lock && newData.scale == (lock ? oldData.scale : oldData.scale + 1),
                "Result changed center or has wrong scale/lock state");
    }

    private static void fill(CraftingMenu menu, ItemStack map, int count) {
        for (int i = 0; i < 9; i++) menu.getInputGridSlots().get(i).set(
                i == 4 ? map.copyWithCount(count) : new ItemStack(Items.PAPER, count));
    }

    private static void requireInputs(GameTestHelper helper, CraftingMenu menu, ItemStack map, int count) {
        for (int i = 0; i < 9; i++) {
            ItemStack expected = i == 4 ? map.copyWithCount(count) : new ItemStack(Items.PAPER, count);
            require(helper, ItemStack.matches(expected, menu.getInputGridSlots().get(i).getItem()),
                    "Rejected operation changed input " + i);
        }
    }

    private static void withFixture(GameTestHelper helper, Fixture action) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        BlockPos anchor = helper.absolutePos(new BlockPos(3, 2, 3));
        level.getChunkAt(anchor);
        level.setBlockAndUpdate(anchor, Blocks.LODESTONE.defaultBlockState());
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(anchor.above()));
        UUID unit = UUID.randomUUID();
        NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage()).put(
                new NexusSpaceUnitRecord(unit, SpaceUnitType.LODESTONE, level.dimension(), anchor,
                        player.getUUID(), "Crafting fixture", SpaceUnitVisibility.PRIVATE, SpaceUnitStatus.ACTIVE,
                        Set.of(), Set.of(), SpaceStructureSnapshot.EMPTY, level.getGameTime(), level.getGameTime()));
        try {
            require(helper, level.isLoaded(anchor), "Fixture origin is not loaded");
            var stored = NexusSpaceUnitSavedData.loadCanonical(level.getServer().overworld().getDataStorage()).get(unit).orElseThrow();
            require(helper, stored.status() == SpaceUnitStatus.ACTIVE && stored.pos().equals(anchor)
                    && level.getBlockState(anchor).is(Blocks.LODESTONE), "Fixture origin record/block mismatch");
            ItemStack map = NexusMapLifecycleAuthority.createExactCenteredMap(level, anchor, unit, new ItemStack(Items.MAP))
                    .orElseThrow(() -> {
                        var index = level.getServer().getDataStorage()
                                .computeIfAbsent(net.minecraft.world.level.saveddata.maps.MapIndex.TYPE);
                        var encoded = net.minecraft.world.level.saveddata.maps.MapIndex.CODEC
                                .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, index).getOrThrow().getAsJsonObject();
                        int allocated = encoded.has("map") ? encoded.get("map").getAsInt() : -1;
                        boolean occupied = allocated >= 0 && NexusMapBindingSavedData
                                .loadCanonical(level.getServer().overworld().getDataStorage())
                                .get(new net.minecraft.world.level.saveddata.maps.MapId(allocated)).isPresent();
                        return helper.assertionException("Fixture map creation failed: loaded=" + level.isLoaded(anchor)
                                + ", lodestone=" + level.getBlockState(anchor).is(Blocks.LODESTONE)
                                + ", node=" + stored.status() + ", allocatedId=" + allocated + ", alreadyBound=" + occupied);
                    });
            action.run(player, map, anchor);
            helper.succeed();
        } finally {
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            player.discard();
        }
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) throw helper.assertionException(message);
    }

    @FunctionalInterface
    private interface Fixture {
        void run(ServerPlayer player, ItemStack map, BlockPos anchor);
    }
}
