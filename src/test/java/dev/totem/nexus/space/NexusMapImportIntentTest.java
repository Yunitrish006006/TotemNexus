package dev.totem.nexus.space;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NexusMapImportIntentTest {
    private static final MapId ID = new MapId(901);
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final GlobalPos ANCHOR = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static MapItemSavedData map(int scale) {
        try {
            var constructor = MapItemSavedData.class.getDeclaredConstructor(int.class, int.class, byte.class,
                    boolean.class, boolean.class, boolean.class, net.minecraft.resources.ResourceKey.class);
            constructor.setAccessible(true);
            return constructor.newInstance(0, 0, (byte) scale, false, false, false, Level.OVERWORLD);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot create exact-centered map fixture", failure);
        }
    }
    private static NexusMapBindingSavedData bound(MapItemSavedData data) {
        var ledger = new NexusMapBindingSavedData();
        assertTrue(ledger.bind(ID, UNIT, ANCHOR, data));
        return ledger;
    }
    private static JsonObject encode(NexusMapBindingSavedData ledger) {
        return NexusMapBindingSavedData.CODEC.encodeStart(JsonOps.INSTANCE, ledger).getOrThrow().getAsJsonObject();
    }
    private static NexusMapBindingSavedData decode(JsonObject encoded) {
        return NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
    }
    private static JsonObject intent(JsonObject encoded) {
        return encoded.getAsJsonArray("sparse_imports").get(0).getAsJsonObject();
    }

    @Test
    void oldAndNewBindingsRequireImportAndDoNotAcquireAnIntentByReading() {
        var data = map(2);
        var ledger = bound(data);
        assertTrue(ledger.sparseImportIntent(ID).isEmpty());
        for (int version = 1; version <= 3; version++) {
            var old = encode(ledger);
            old.addProperty("data_version", version);
            old.remove("sparse_imports");
            if (version < 3) old.getAsJsonArray("maps").get(0).getAsJsonObject().remove("effective_scale");
            var restored = decode(old);
            assertTrue(restored.validates(ID, UNIT, data));
            assertTrue(restored.sparseImportIntent(ID).isEmpty());
            var prepared = restored.prepareSparseImport(ID, UNIT, data).orElseThrow();
            assertEquals(2, prepared.scale());
            assertEquals(restored.get(ID).orElseThrow(), prepared.source());
        }
    }

    @Test
    void preparationIsIdempotentAndSurvivesSerializationWithoutChangingMapOrBinding() {
        var data = map(1);
        data.colors[17] = 42;
        var ledger = bound(data);
        var binding = ledger.get(ID).orElseThrow();
        var prepared = ledger.prepareSparseImport(ID, UNIT, data).orElseThrow();
        assertEquals(prepared, ledger.prepareSparseImport(ID, UNIT, data).orElseThrow());
        assertTrue(ledger.bind(ID, UNIT, ANCHOR, data));
        assertEquals(prepared, ledger.sparseImportIntent(ID).orElseThrow());
        var restored = decode(encode(ledger));
        assertEquals(prepared, restored.prepareSparseImport(ID, UNIT, data).orElseThrow());
        assertEquals(binding, restored.get(ID).orElseThrow());
        assertEquals(42, data.colors[17]);
        assertFalse(data.locked);
    }

    @Test
    void unboundOrForgedClaimsNeverAllocateAnIntent() {
        var data = map(1);
        var ledger = bound(data);
        assertTrue(ledger.prepareSparseImport(ID, UUID.randomUUID(), data).isEmpty());
        assertTrue(ledger.prepareSparseImport(ID, null, data).isEmpty());
        assertTrue(ledger.prepareSparseImport(new MapId(902), UNIT, data).isEmpty());
        assertTrue(ledger.prepareSparseImport(null, UNIT, data).isEmpty());
        assertTrue(ledger.prepareSparseImport(ID, UNIT, null).isEmpty());
        assertTrue(ledger.prepareSparseImport(ID, UNIT, map(2)).isEmpty());
        var otherCenter = MapItemSavedData.createFresh(4096, 0, (byte) 1, false, false, Level.OVERWORLD);
        var otherDimension = MapItemSavedData.createFresh(0, 0, (byte) 1, false, false, Level.NETHER);
        assertTrue(ledger.prepareSparseImport(ID, UNIT, otherCenter).isEmpty());
        assertTrue(ledger.prepareSparseImport(ID, UNIT, otherDimension).isEmpty());
        assertTrue(ledger.sparseImportIntent(ID).isEmpty());
    }

    @Test
    void lockOrLegacyScaleConflictCannotReplaceAnExistingTicket() {
        var data = map(1);
        var old = encode(bound(data));
        old.getAsJsonArray("maps").get(0).getAsJsonObject().remove("effective_scale");
        var ledger = decode(old);
        var prepared = ledger.prepareSparseImport(ID, UNIT, data).orElseThrow();
        assertTrue(ledger.prepareSparseImport(ID, UNIT, data.locked()).isEmpty());
        // A legacy binding can match another native scale at the same center;
        // its already prepared import must still reject that changed source.
        var changedScale = map(2);
        assertTrue(ledger.resolve(ID, changedScale).isPresent());
        assertTrue(ledger.prepareSparseImport(ID, UNIT, changedScale).isEmpty());
        assertEquals(prepared, ledger.sparseImportIntent(ID).orElseThrow());
        assertEquals(prepared, decode(encode(ledger)).sparseImportIntent(ID).orElseThrow());
    }

    @Test
    void nativeLockAndScaleForksKeepSeparateImportTickets() {
        var data = map(1);
        var ledger = bound(data);
        var source = ledger.prepareSparseImport(ID, UNIT, data).orElseThrow();
        var lockId = new MapId(902);
        var locked = data.locked();
        assertTrue(ledger.derive(ID, data, lockId, locked));
        assertTrue(ledger.sparseImportIntent(lockId).isEmpty());
        var lockIntent = ledger.prepareSparseImport(lockId, UNIT, locked).orElseThrow();
        assertTrue(lockIntent.locked());
        assertNotEquals(source.ticket(), lockIntent.ticket());
        var scaleId = new MapId(903);
        var scaled = map(2);
        assertTrue(ledger.derive(ID, data, scaleId, scaled));
        var scaleIntent = ledger.prepareSparseImport(scaleId, UNIT, scaled).orElseThrow();
        assertEquals(List.of(ID.id()), scaleIntent.source().detailAncestors());
        assertNotEquals(source.ticket(), scaleIntent.ticket());
        assertEquals(source, ledger.sparseImportIntent(ID).orElseThrow());
    }

    @Test
    void duplicateOrOrphanTicketsAndBindingsAreRefused() {
        var data = map(0);
        var ledger = bound(data);
        ledger.prepareSparseImport(ID, UNIT, data).orElseThrow();
        var duplicateTicket = encode(ledger);
        duplicateTicket.getAsJsonArray("sparse_imports").add(intent(duplicateTicket).deepCopy());
        assertTrue(NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, duplicateTicket).isError());
        var orphan = encode(ledger);
        orphan.remove("maps");
        assertTrue(NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, orphan).isError());
        var conflicting = encode(ledger);
        intent(conflicting).getAsJsonObject("source").addProperty("center_x", data.centerX + 1);
        assertTrue(NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, conflicting).isError());
        var duplicateBinding = encode(ledger);
        duplicateBinding.getAsJsonArray("maps").add(duplicateBinding.getAsJsonArray("maps").get(0).deepCopy());
        assertTrue(NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, duplicateBinding).isError());
    }

    @Test
    void malformedOrFutureIntentCannotSilentlyFallBackToLegacy() {
        var data = map(0);
        var ledger = bound(data);
        ledger.prepareSparseImport(ID, UNIT, data).orElseThrow();
        for (String field : List.of("version", "ticket", "source", "scale", "locked")) {
            var missing = encode(ledger);
            intent(missing).remove(field);
            assertTrue(NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, missing).isError(), field);
        }
        var future = encode(ledger);
        intent(future).addProperty("version", 2);
        assertTrue(NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, future).isError());
        var scale = encode(ledger);
        intent(scale).addProperty("scale", 1);
        assertTrue(NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, scale).isError());
        var invalidList = encode(ledger);
        invalidList.addProperty("sparse_imports", "broken");
        assertTrue(NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, invalidList).isError());
    }

    @Test
    void futureLedgerVersionCannotDropUnknownLifecycleState() {
        var data = map(0);
        var ledger = bound(data);
        ledger.prepareSparseImport(ID, UNIT, data).orElseThrow();
        var future = encode(ledger);
        future.addProperty("data_version", NexusMapBindingSavedData.DATA_VERSION + 1);
        assertTrue(NexusMapBindingSavedData.CODEC.parse(JsonOps.INSTANCE, future).isError());
    }

    @Test
    void intentSurvivesActualDiskSaveAndCanonicalLegacyCopy(@TempDir Path directory) {
        var data = map(1);
        var legacy = bound(data);
        var prepared = legacy.prepareSparseImport(ID, UNIT, data).orElseThrow();
        try (var storage = storage(directory)) {
            storage.set(NexusMapBindingSavedData.LEGACY_COMPATIBILITY_TYPE, legacy);
            var canonical = NexusMapBindingSavedData.loadCanonical(storage);
            assertEquals(prepared, canonical.sparseImportIntent(ID).orElseThrow());
            assertNotSame(legacy, canonical);
            storage.saveAndJoin();
        }
        try (var storage = storage(directory)) {
            var canonical = NexusMapBindingSavedData.loadCanonical(storage);
            assertEquals(prepared, canonical.prepareSparseImport(ID, UNIT, data).orElseThrow());
            assertEquals(prepared, storage.get(NexusMapBindingSavedData.LEGACY_COMPATIBILITY_TYPE)
                    .sparseImportIntent(ID).orElseThrow());
        }
    }

    private static SavedDataStorage storage(Path directory) {
        return new SavedDataStorage(directory, net.minecraft.util.datafix.DataFixers.getDataFixer(),
                net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.empty()));
    }
}
