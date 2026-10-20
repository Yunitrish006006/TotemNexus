package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ReadOnlyBufferException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class NexusLegacyTerrainSnapshotTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void incrementalCapturePreservesSharedHistoricalOrphanAndEmptyData() {
        var store = fixture(10, List.of(index(31, 1, 2), index(32, 1), index(33), index(34, 3)));
        NexusLegacyTerrainSnapshot snapshot;
        try (var capture = store.beginMigrationSnapshot()) {
            assertThrows(IllegalStateException.class, capture::result);
            assertFalse(capture.step(3));
            assertEquals(3, capture.copiedPageCount());
            assertFalse(capture.step(3));
            assertEquals(6, capture.copiedPageCount());
            while (!capture.step(3)) { }
            snapshot = capture.result();
            assertTrue(capture.step(1));
        }
        assertEquals(10, snapshot.pages().size());
        assertEquals(List.of(1, 2), snapshot.maps().get(31));
        assertEquals(List.of(1), snapshot.maps().get(32));
        assertEquals(List.of(), snapshot.maps().get(33));
        assertTrue(snapshot.pages().get(2).historical);
        assertEquals(1, snapshot.pages().get(2).scale);
        assertEquals(-64, snapshot.pages().get(1).x);
        assertEquals(4, Byte.toUnsignedInt(snapshot.pages().get(1).colors().get(0)));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.pages().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.maps().get(31).clear());
        assertThrows(ReadOnlyBufferException.class, () -> snapshot.pages().get(1).colors().put(0, (byte) 99));
        var pixels = snapshot.pages().get(1).colors();
        pixels.position(12);
        assertEquals(0, snapshot.pages().get(1).colors().position());
        assertTrue(store.record(null, 34, -128, 0, (byte) 8));
        assertEquals(4, Byte.toUnsignedInt(snapshot.pages().get(3).colors().get(0)));
    }

    @Test void freezeRejectsEveryAuthoritativeMutationBeforeAllocationAndKeepsReads() {
        var store = fixture(1, List.of(index(31, 1)));
        store.setDirty(false);
        try (var capture = store.beginMigrationSnapshot()) {
            assertThrows(IllegalStateException.class, store::beginMigrationSnapshot);
            assertTrue(store.known(31, -128, 0));
            assertEquals(1, store.pages(31).size());
            assertFalse(store.record(null, 31, -128, 0, (byte) 8));
            assertFalse(store.record(null, 99, 10000, 10000, (byte) 8));
            store.initialize(null, new MapId(99), null, null);
            assertThrows(IllegalStateException.class, () -> store.derive(null, new MapId(31), null, null, new MapId(99)));
            assertFalse(store.isDirty());
            assertTrue(capture.step(8));
            assertFalse(capture.result().maps().containsKey(99));
        }
        assertTrue(store.record(null, 31, -128, 0, (byte) 8));
        assertTrue(store.isDirty());
    }

    @Test void partialCancellationReleasesExactlyItsOwnFreeze() {
        var store = fixture(2, List.of(index(31, 1)));
        var old = store.beginMigrationSnapshot();
        assertFalse(old.step(1));
        old.close();
        assertThrows(IllegalStateException.class, () -> old.step(1));
        assertThrows(IllegalStateException.class, old::result);
        try (var current = store.beginMigrationSnapshot()) {
            old.close();
            assertFalse(store.record(null, 31, -128, 0, (byte) 8));
            while (!current.step(1)) { }
        }
        assertTrue(store.record(null, 31, -128, 0, (byte) 8));
    }

    @Test void wrongThreadCannotCopyOrReleaseAndBadBudgetCannotUnfreeze() {
        var store = fixture(1, List.of(index(31, 1)));
        try (var capture = store.beginMigrationSnapshot()) {
            CompletableFuture.runAsync(() -> {
                assertThrows(IllegalStateException.class, () -> capture.step(1));
                assertThrows(IllegalStateException.class, capture::close);
                assertThrows(IllegalStateException.class, capture::result);
            }).join();
            assertThrows(IllegalArgumentException.class, () -> capture.step(0));
            assertThrows(IllegalArgumentException.class, () -> capture.step(9));
            assertFalse(store.record(null, 31, -128, 0, (byte) 8));
            assertTrue(capture.step(8));
        }
    }

    @Test void missingReferencesRefuseBeforeAnyCaptureCanStart() {
        assertThrows(RuntimeException.class, () -> fixture(1, List.of(index(31, 1, 99))));
    }

    @Test void freezeIsTransientAndDoesNotChangeLegacyEncoding() {
        var store = fixture(1, List.of(index(31, 1)));
        var before = NexusMapDetailSavedData.CODEC.encodeStart(NbtOps.INSTANCE, store).getOrThrow();
        try (var capture = store.beginMigrationSnapshot()) {
            assertEquals(before, NexusMapDetailSavedData.CODEC.encodeStart(NbtOps.INSTANCE, store).getOrThrow());
            var restored = NexusMapDetailSavedData.CODEC.parse(NbtOps.INSTANCE, before).getOrThrow();
            try (var independent = restored.beginMigrationSnapshot()) { assertTrue(independent.step(8)); }
            assertFalse(store.record(null, 31, -128, 0, (byte) 8));
        }
    }

    @Test void oversizedMapIndexAdmissionRefusesWithoutFreezing() {
        var indexes = new java.util.ArrayList<CompoundTag>();
        for (int i = 0; i <= NexusMapDetailSavedData.MAX_WORLD_PAGES; i++) indexes.add(index(i));
        indexes.set(31, index(31, 1));
        var store = fixture(1, indexes);
        assertThrows(IllegalStateException.class, store::beginMigrationSnapshot);
        // Existing fine page can still be changed after refused admission.
        assertTrue(store.record(null, 31, -128, 0, (byte) 8));
        store.initialize(null, new MapId(0), null, null); // Existing entry remains readable.
    }

    private static CompoundTag index(int id, int... pages) {
        var tag = new CompoundTag(); tag.putInt("map_id", id);
        var ids = new ListTag();
        for (int p : pages) ids.add(net.minecraft.nbt.IntTag.valueOf(p));
        tag.put("pages", ids); return tag;
    }
    private static NexusMapDetailSavedData fixture(int pageCount, List<CompoundTag> indexes) {
        var tag = new CompoundTag(); tag.putInt("version", 1);
        var pages = new ListTag();
        for (int i = 1; i <= pageCount; i++) {
            var p = new CompoundTag(); p.putInt("id", i); p.putInt("x", -64); p.putInt("z", 64);
            p.putInt("scale", i == 2 ? 1 : 0); p.putBoolean("historical", i == 2);
            var colors = new byte[16384]; colors[0] = 4; p.putByteArray("colors", colors); pages.add(p);
        }
        tag.put("pages", pages); var maps = new ListTag(); indexes.forEach(maps::add); tag.put("maps", maps);
        return NexusMapDetailSavedData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }
}
