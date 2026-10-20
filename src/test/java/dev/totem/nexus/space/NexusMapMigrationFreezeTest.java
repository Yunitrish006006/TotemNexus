package dev.totem.nexus.space;

import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NexusMapMigrationFreezeTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @Test void freezeRejectsMutationsAndStalePreparedCommitWithoutBlockingReads() {
        var ledger = new NexusMapBindingSavedData();
        var map = MapItemSavedData.createFresh(0, 0, (byte) 0, false, false, Level.OVERWORLD);
        var id = new MapId(961);
        var unit = UUID.randomUUID();
        var anchor = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);
        assertTrue(ledger.bind(id, unit, anchor, map));
        var resultId = new MapId(962);
        var prepared = ledger.prepareDerivation(id, map, resultId, map.locked(), 0).orElseThrow();
        var token = ledger.freezeForMigration();
        assertTrue(ledger.validates(id, unit, map));
        assertFalse(ledger.bind(new MapId(963), unit, anchor, map));
        assertTrue(ledger.prepareSparseImport(id, unit, map).isEmpty());
        assertFalse(ledger.derive(id, map, resultId, map.locked()));
        assertThrows(IllegalStateException.class, () -> ledger.commitDerivation(prepared));
        assertTrue(ledger.get(resultId).isEmpty());
        assertThrows(IllegalStateException.class, ledger::freezeForMigration);
        assertThrows(IllegalStateException.class, () -> ledger.releaseMigrationFreeze(new Object()));
        assertFalse(ledger.acceptsMutation());
        ledger.releaseMigrationFreeze(token);
        assertTrue(ledger.derive(id, map, resultId, map.locked()));
        assertTrue(ledger.prepareSparseImport(id, unit, map).isPresent());
    }
    @Test void freezeIsTransientAndDoesNotAlterPersistedMetadata() {
        var ledger = new NexusMapBindingSavedData();
        var before = NexusMapBindingSavedData.CODEC.encodeStart(JsonOps.INSTANCE, ledger).getOrThrow();
        var token = ledger.freezeForMigration();
        var during = NexusMapBindingSavedData.CODEC.encodeStart(JsonOps.INSTANCE, ledger).getOrThrow();
        assertEquals(before, during);
        var restored = NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, during).getOrThrow();
        assertTrue(restored.acceptsMutation());
        assertFalse(ledger.acceptsMutation());
        ledger.releaseMigrationFreeze(token);
    }
}
