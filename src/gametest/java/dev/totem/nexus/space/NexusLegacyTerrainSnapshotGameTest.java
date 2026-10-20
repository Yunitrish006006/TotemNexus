package dev.totem.nexus.space;

import dev.totem.nexus.mixin.NexusMapIndexAccessor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Real ID allocation and legacy COW/derive behavior around an explicit terrain freeze. */
public final class NexusLegacyTerrainSnapshotGameTest {
    @GameTest(maxTicks = 100)
    public void freezeRetainsSharedPagesAcrossTicksAndResumesCow(GameTestHelper helper) {
        var level = helper.getLevel();
        var store = new NexusMapDetailSavedData();
        var base = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, level.dimension());
        var source = level.getFreeMapId(); var result = level.getFreeMapId();
        store.initialize(level, source, base, null);
        check(store.record(level, source.id(), 0, 0, (byte) 4), "Could not record initial terrain");
        store.derive(level, source, base, null, result);
        var capture = store.beginMigrationSnapshot();
        helper.runBeforeTestEnd(capture::close);
        var counter = level.getServer().getDataStorage().computeIfAbsent(MapIndex.TYPE);
        int before = ((NexusMapIndexAccessor) counter).totem$lastMapId();
        check(!store.record(level, source.id(), 1, 1, (byte) 8), "Frozen record modified shared terrain");
        var unused = new net.minecraft.world.level.saveddata.maps.MapId(Integer.MAX_VALUE - 1);
        store.initialize(level, unused, base, null);
        boolean refused = false;
        try {
            store.derive(level, source, base, null, unused);
        } catch (IllegalStateException expected) {
            refused = true;
            check(store.pages(unused.id()).isEmpty(), "Frozen derive allocated an index");
        }
        check(refused, "Frozen derivation succeeded");
        check(((NexusMapIndexAccessor) counter).totem$lastMapId() == before, "Freeze consumed a native map ID");
        var initial = NexusMapDetailSavedData.CODEC.encodeStart(NbtOps.INSTANCE, store).getOrThrow();
        // One native tick registration; private state prevents repeated completion.
        var finished = new boolean[1];
        helper.onEachTick(() -> {
            if (finished[0]) return;
            check(!store.record(level, source.id(), 0, 0, (byte) 8), "Recording resumed before release");
            if (!capture.step(1)) return;
            var snapshot = capture.result();
            check(snapshot.pages().size() == 2 && snapshot.maps().size() == 2, "Lost historical/shared metadata");
            check(initial.equals(NexusMapDetailSavedData.CODEC.encodeStart(NbtOps.INSTANCE, store).getOrThrow()),
                    "Capture changed legacy saved data");
            int shared = snapshot.maps().get(source.id()).getFirst();
            check(snapshot.maps().get(result.id()).contains(shared), "Shared page identity lost");
            capture.close();
            check(store.record(level, source.id(), 0, 0, (byte) 8), "Recording did not resume");
            check(store.pages(result.id()).stream().filter(p -> !p.historical).findFirst().orElseThrow().color(0, 0) == 4,
                    "Post-release COW changed derived map");
            check(snapshot.pages().get(shared).colors().get(0) == 4, "Post-release write changed snapshot");
            finished[0] = true;
            helper.succeed();
        });
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
