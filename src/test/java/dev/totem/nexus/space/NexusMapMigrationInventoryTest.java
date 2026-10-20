package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NexusMapMigrationInventoryTest {
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000031");
    private static final NexusMapBindingWitness.Expected HASH = new NexusMapBindingWitness.Expected("1".repeat(64));
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static NexusMapBindingSavedData.Entry binding(int id, List<Integer> ancestors) {
        return new NexusMapBindingSavedData.Entry(id, UNIT, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), 0, 0, ancestors, 20);
    }
    private static NexusLegacyTerrainSnapshot detail(Map<Integer, List<Integer>> indexes) {
        return new NexusLegacyTerrainSnapshot(Map.of(), indexes);
    }
    private static NexusMapBindingSavedData.SparseImportIntent intent(NexusMapBindingSavedData.Entry source) {
        return new NexusMapBindingSavedData.SparseImportIntent(UUID.randomUUID(), source, 20, false);
    }
    @Test void completeInventoryRetainsUnpreparedHistoricalEmptyAndOrphanSources() {
        var first = binding(31, List.of(20, 10)); var second = binding(32, List.of(20, 11)); var ticket = intent(first);
        var page = new NexusLegacyTerrainSnapshot.Page(7, 0, 0, 0, true, new byte[16384]);
        var orphan = new NexusLegacyTerrainSnapshot.Page(8, 128, 0, 0, false, new byte[16384]);
        var terrain = new NexusLegacyTerrainSnapshot(Map.of(7, page, 8, orphan), Map.of(31, List.of(7), 99, List.of(7), 100, List.of()));
        var cursor = new NexusMapMigrationInventory(2, List.of(second, first).iterator(),
                id -> id == 31 ? Optional.of(ticket) : Optional.empty(), terrain, HASH, () -> { }, () -> fail("Unexpected cleanup"));
        assertThrows(IllegalStateException.class, cursor::result);
        for (int i = 0; i < 4; i++) assertFalse(cursor.step(1));
        assertTrue(cursor.step(1)); var result = cursor.result();
        assertEquals(List.of(31, 32), new ArrayList<>(result.bindings().keySet()));
        assertEquals(Set.of(10, 11, 20, 31, 32, 99, 100), result.nativeSources());
        assertEquals(Set.of(32), result.unprepared()); assertEquals(ticket, result.bindings().get(31).intent().orElseThrow());
        assertSame(HASH, result.metadata()); assertSame(terrain, result.detail());
        assertEquals(Set.of(7, 8), result.detail().pages().keySet());
        assertEquals(List.of(), result.detail().maps().get(100));
        assertTrue(cursor.step(8)); assertSame(result, cursor.result());
    }
    @Test void finishedSnapshotCannotBeMutatedThroughAnyInventoryCollection() {
        var entry = binding(31, List.of(20));
        var cursor = new NexusMapMigrationInventory(1, List.of(entry).iterator(), id -> Optional.empty(), detail(Map.of()), HASH, () -> { }, () -> { });
        assertTrue(cursor.step(1)); var result = cursor.result();
        assertThrows(UnsupportedOperationException.class, () -> result.bindings().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.nativeSources().add(99));
        assertThrows(UnsupportedOperationException.class, () -> result.unprepared().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.bindings().get(31).binding().detailAncestors().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.detail().maps().clear());
    }
    @Test void eachStepVisitsAtMostItsBudgetAndInvalidBudgetDoesNotAdvance() {
        var entries = new ArrayList<NexusMapBindingSavedData.Entry>(); for (int i = 0; i < 17; i++) entries.add(binding(i, List.of()));
        var visits = new AtomicInteger(); var cursor = new NexusMapMigrationInventory(entries.size(), entries.iterator(),
                id -> { visits.incrementAndGet(); return Optional.empty(); }, detail(Map.of(30, List.of(), 31, List.of())), HASH, () -> { }, () -> fail("Unexpected cleanup"));
        for (int bad : new int[]{-1, 0, 9, Integer.MAX_VALUE}) assertThrows(IllegalArgumentException.class, () -> cursor.step(bad));
        assertEquals(0, visits.get()); assertFalse(cursor.step(8)); assertEquals(8, visits.get());
        assertFalse(cursor.step(8)); assertEquals(16, visits.get());
        assertFalse(cursor.step(1)); assertFalse(cursor.step(1)); assertTrue(cursor.step(1)); assertEquals(17, visits.get());
    }
    @Test void emptyInventoryIsExplicitAndDoesNotCreateTickets() {
        var cursor = new NexusMapMigrationInventory(0, Collections.emptyIterator(), id -> { fail("Empty inventory requested a ticket"); return Optional.empty(); },
                detail(Map.of()), HASH, () -> { }, () -> { });
        assertTrue(cursor.step(1)); assertTrue(cursor.result().bindings().isEmpty()); assertTrue(cursor.result().nativeSources().isEmpty());
    }
    @Test void missingDuplicateAndUnexpectedBindingCountsRefuseAndCleanUp() {
        var entry = binding(31, List.of());
        for (var entries : List.of(List.<NexusMapBindingSavedData.Entry>of(), List.of(entry, entry))) {
            var failed = new AtomicBoolean();
            var cursor = new NexusMapMigrationInventory(1, entries.iterator(), id -> Optional.empty(), detail(Map.of()), HASH, () -> { }, () -> failed.set(true));
            assertThrows(IllegalStateException.class, () -> cursor.step(8)); assertTrue(failed.get());
            assertThrows(IllegalStateException.class, cursor::result); assertThrows(IllegalStateException.class, () -> cursor.step(1));
        }
    }
    @Test void conflictingTicketAndNegativeDetailIdentityRefuse() {
        var entry = binding(31, List.of()); var badIntent = intent(binding(32, List.of())); var failed = new AtomicBoolean();
        var conflict = new NexusMapMigrationInventory(1, List.of(entry).iterator(), id -> Optional.of(badIntent), detail(Map.of()), HASH, () -> { }, () -> failed.set(true));
        assertThrows(IllegalArgumentException.class, () -> conflict.step(8)); assertTrue(failed.get());
        var negative = new NexusMapMigrationInventory(0, Collections.emptyIterator(), id -> Optional.empty(), detail(Map.of(-1, List.of())), HASH, () -> { }, () -> { });
        assertThrows(IllegalStateException.class, () -> negative.step(1));
    }
    @Test void metadataLimitsRefuseBeforeTraversal() {
        Iterator<NexusMapBindingSavedData.Entry> unreadable = new Iterator<>() {
            public boolean hasNext() { fail("Over-limit inventory traversed entries"); return false; }
            public NexusMapBindingSavedData.Entry next() { throw new AssertionError(); }
        };
        assertThrows(IllegalStateException.class, () -> new NexusMapMigrationInventory(NexusMapMigrationInventory.MAX_BINDINGS + 1,
                unreadable, id -> Optional.empty(), detail(Map.of()), HASH, () -> { }, () -> { }));
        var indexes = new HashMap<Integer, List<Integer>>(); for (int i = 0; i <= NexusMapDetailSavedData.MAX_WORLD_PAGES; i++) indexes.put(i, List.of());
        assertThrows(IllegalStateException.class, () -> new NexusMapMigrationInventory(0, unreadable,
                id -> Optional.empty(), detail(indexes), HASH, () -> { }, () -> { }));
    }
    @Test void differentMapsCannotShareOneDurableManifestTicket() {
        var first = binding(31, List.of()); var second = binding(32, List.of()); UUID ticket = UUID.randomUUID();
        var failed = new AtomicBoolean();
        var cursor = new NexusMapMigrationInventory(2, List.of(first, second).iterator(),
                id -> Optional.of(new NexusMapBindingSavedData.SparseImportIntent(ticket, id == 31 ? first : second, 20, false)),
                detail(Map.of()), HASH, () -> { }, () -> failed.set(true));
        assertThrows(IllegalStateException.class, () -> cursor.step(8)); assertTrue(failed.get());
        assertThrows(IllegalStateException.class, cursor::result);
    }
    @Test void nativeUnionLimitRefusesWithoutPublishingPartialSnapshot() {
        var rows = new ArrayList<NexusMapBindingSavedData.Entry>();
        for (int i = 0; i < 3400; i++) {
            var ancestors = new ArrayList<Integer>(); for (int j = 0; j < 20; j++) ancestors.add(i * 20 + j);
            rows.add(binding(100000 + i, ancestors));
        }
        var failed = new AtomicBoolean(); var cursor = new NexusMapMigrationInventory(rows.size(), rows.iterator(), id -> Optional.empty(),
                detail(Map.of()), HASH, () -> { }, () -> failed.set(true));
        assertThrows(IllegalStateException.class, () -> { while (!cursor.step(8)) { } });
        assertTrue(failed.get()); assertThrows(IllegalStateException.class, cursor::result);
    }
    @Test void releasedOrReplacedFreezeCannotContinueOrReturnFinishedResult() {
        var ledger = new NexusMapBindingSavedData(); Object token = ledger.freezeForMigration();
        assertThrows(IllegalStateException.class, () -> ledger.migrationInventory(new Object(), detail(Map.of()), HASH, () -> { }, () -> { }));
        var cursor = ledger.migrationInventory(token, detail(Map.of()), HASH, () -> { }, () -> { }); assertTrue(cursor.step(1));
        var result = cursor.result(); ledger.releaseMigrationFreeze(token); Object newer = ledger.freezeForMigration();
        assertThrows(IllegalStateException.class, cursor::result); assertThrows(IllegalStateException.class, () -> cursor.step(1));
        assertTrue(result.bindings().isEmpty()); ledger.releaseMigrationFreeze(newer);
    }
    @Test void ownerGuardRunsBeforeEveryStepAndResultIncludingAfterCompletion() {
        var valid = new AtomicBoolean(true); var checks = new AtomicInteger();
        var cursor = new NexusMapMigrationInventory(0, Collections.emptyIterator(), id -> Optional.empty(), detail(Map.of()), HASH,
                () -> { checks.incrementAndGet(); if (!valid.get()) throw new IllegalStateException("Owner invalidated"); }, () -> { });
        assertTrue(cursor.step(1)); cursor.result(); assertEquals(2, checks.get()); valid.set(false);
        assertThrows(IllegalStateException.class, cursor::result); assertThrows(IllegalStateException.class, () -> cursor.step(1));
    }
}
