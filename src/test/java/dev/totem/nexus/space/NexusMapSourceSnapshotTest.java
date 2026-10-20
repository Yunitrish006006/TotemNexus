package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NexusMapSourceSnapshotTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void nativeCopyRemainsImmutableAfterContinuedRecording() {
        var map = map(0, 0); map.colors[0] = 4;
        var id = new MapId(861); var intent = intent(id, map);
        var base = new NexusMapSourceSnapshot.Base(id, map);
        var snapshot = new NexusMapSourceSnapshot(intent, base, emptyDetail());
        map.colors[0] = 8;
        assertEquals(4, snapshot.base().colors().get(0));
        assertEquals(intent.ticket(), snapshot.intent().ticket());
        assertThrows(ReadOnlyBufferException.class, () -> snapshot.base().colors().put(0, (byte) 12));
        base.colors().position(23);
        assertEquals(0, base.colors().position());
        assertEquals(map.dimension, base.dimension);
    }
    @Test void wrongIdentityCenterScaleAndLockCannotShareAnIntent() {
        var id = new MapId(861); var data = map(0, 0); var intent = intent(id, data);
        assertThrows(IllegalArgumentException.class, () -> new NexusMapSourceSnapshot(intent,
                new NexusMapSourceSnapshot.Base(new MapId(862), data), emptyDetail()));
        assertThrows(IllegalArgumentException.class, () -> new NexusMapSourceSnapshot(intent,
                new NexusMapSourceSnapshot.Base(id, map(1024, 0)), emptyDetail()));
        var scaled = MapItemSavedData.createFresh(0, 0, (byte) 1, false, false, Level.OVERWORLD);
        assertThrows(IllegalArgumentException.class, () -> new NexusMapSourceSnapshot(intent,
                new NexusMapSourceSnapshot.Base(id, scaled), emptyDetail()));
        assertThrows(IllegalArgumentException.class, () -> new NexusMapSourceSnapshot(intent,
                new NexusMapSourceSnapshot.Base(id, data.locked()), emptyDetail()));
        var otherDimension = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.NETHER);
        assertThrows(IllegalArgumentException.class, () -> new NexusMapSourceSnapshot(intent,
                new NexusMapSourceSnapshot.Base(id, otherDimension), emptyDetail()));
    }
    @Test void missingComponentsAndNegativeMapIdsRefuse() {
        var id = new MapId(861); var data = map(0, 0); var intent = intent(id, data);
        var base = new NexusMapSourceSnapshot.Base(id, data);
        assertThrows(NullPointerException.class, () -> new NexusMapSourceSnapshot(null, base, emptyDetail()));
        assertThrows(NullPointerException.class, () -> new NexusMapSourceSnapshot(intent, null, emptyDetail()));
        assertThrows(NullPointerException.class, () -> new NexusMapSourceSnapshot(intent, base, null));
        assertThrows(IllegalArgumentException.class, () -> new NexusMapSourceSnapshot.Base(new MapId(-1), data));
    }
    private static MapItemSavedData map(int x, int z) {
        return MapItemSavedData.createFresh(x, z, (byte) 0, false, false, Level.OVERWORLD);
    }
    private static NexusMapBindingSavedData.SparseImportIntent intent(MapId id, MapItemSavedData map) {
        var ledger = new NexusMapBindingSavedData(); var unit = UUID.randomUUID();
        assertTrue(ledger.bind(id, unit, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), map));
        return ledger.prepareSparseImport(id, unit, map).orElseThrow();
    }
    private static NexusLegacyTerrainSnapshot emptyDetail() { return new NexusLegacyTerrainSnapshot(Map.of(), Map.of()); }
}
