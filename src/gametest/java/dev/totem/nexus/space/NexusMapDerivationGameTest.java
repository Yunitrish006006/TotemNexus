package dev.totem.nexus.space;

import dev.totem.nexus.mixin.NexusMapIndexAccessor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapIndex;

import java.util.ArrayList;

public final class NexusMapDerivationGameTest {
    @GameTest(maxTicks = 100)
    public void copyScaleFiveAndLockPreserveExactMapIdGeometry(GameTestHelper helper) {
        var level = helper.getLevel();
        var storage = level.getServer().overworld().getDataStorage();
        var records = storage.computeIfAbsent(NexusMapRecordSavedData.TYPE);
        int centerX = 37;
        int centerZ = -53;
        MapId initialId = level.getFreeMapId();
        var initialData = NexusMapLifecycleAuthority.exactData(
                centerX, centerZ, (byte) 0, false, level.dimension());
        level.setMapData(initialId, initialData);
        require(helper, records.bootstrap(initialId, initialData).result()
                == NexusMapRecordSavedData.RegisterResult.INSERTED, "Baseline record was not published");

        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, initialId);
        ItemStack physicalCopy = map.copy();
        require(helper, initialId.equals(physicalCopy.get(DataComponents.MAP_ID))
                        && records.resolve(initialId, level.getMapData(initialId)).isPresent(),
                "A physical map copy created a second identity");

        var expectedLineage = new ArrayList<Integer>();
        for (int logicalScale = 1; logicalScale <= 5; logicalScale++) {
            MapId parentId = map.get(DataComponents.MAP_ID);
            require(helper, NexusMapLifecycleAuthority.postProcess(map, level, MapPostProcessing.SCALE)
                    == NexusMapLifecycleAuthority.PostProcessResult.PROCESSED,
                    "Scale derivation " + logicalScale + " failed");
            MapId childId = map.get(DataComponents.MAP_ID);
            var childNative = level.getMapData(childId);
            var child = records.resolve(childId, childNative).orElse(null);
            expectedLineage.add(parentId.id());
            require(helper, child != null
                            && child.centerX() == centerX && child.centerZ() == centerZ
                            && child.dimension().equals(level.dimension())
                            && child.nativeScale() == Math.min(logicalScale, 4)
                            && child.logicalScale() == logicalScale
                            && child.lineage().equals(expectedLineage),
                    "Scale derivation changed exact geometry or lineage");
        }

        MapId unlockedId = map.get(DataComponents.MAP_ID);
        require(helper, NexusMapLifecycleAuthority.postProcess(map, level, MapPostProcessing.LOCK)
                == NexusMapLifecycleAuthority.PostProcessResult.PROCESSED, "Extended lock derivation failed");
        MapId lockedId = map.get(DataComponents.MAP_ID);
        var lockedNative = level.getMapData(lockedId);
        var locked = records.resolve(lockedId, lockedNative).orElse(null);
        expectedLineage.add(unlockedId.id());
        require(helper, locked != null && locked.locked() && lockedNative.locked
                        && locked.centerX() == centerX && locked.centerZ() == centerZ
                        && locked.dimension().equals(level.dimension())
                        && locked.nativeScale() == 4 && locked.logicalScale() == 5
                        && locked.lineage().equals(expectedLineage),
                "Lock changed exact geometry, extent or lineage");
        helper.succeed();
    }

    @GameTest
    public void contradictoryNativeGeometryAllocatesNoDerivedMapId(GameTestHelper helper) {
        var level = helper.getLevel();
        var storage = level.getServer().overworld().getDataStorage();
        var records = storage.computeIfAbsent(NexusMapRecordSavedData.TYPE);
        MapId mapId = level.getFreeMapId();
        var original = NexusMapLifecycleAuthority.exactData(11, 19, (byte) 0, false, level.dimension());
        level.setMapData(mapId, original);
        records.bootstrap(mapId, original);
        level.setMapData(mapId, NexusMapLifecycleAuthority.exactData(12, 19, (byte) 0, false, level.dimension()));
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, mapId);
        int lastBefore = ((NexusMapIndexAccessor) storage.computeIfAbsent(MapIndex.TYPE)).totem$lastMapId();

        require(helper, NexusMapLifecycleAuthority.postProcess(map, level, MapPostProcessing.SCALE)
                        == NexusMapLifecycleAuthority.PostProcessResult.DENIED,
                "Contradictory native geometry was derived");
        int lastAfter = ((NexusMapIndexAccessor) storage.computeIfAbsent(MapIndex.TYPE)).totem$lastMapId();
        require(helper, lastBefore == lastAfter && mapId.equals(map.get(DataComponents.MAP_ID)),
                "Denied derivation allocated or published a MapId");
        helper.succeed();
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
}
