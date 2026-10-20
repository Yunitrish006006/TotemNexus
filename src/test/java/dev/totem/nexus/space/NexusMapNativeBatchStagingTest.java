package dev.totem.nexus.space;

import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class NexusMapNativeBatchStagingTest {
    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(Stream.empty());
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000031");
    private static final NexusMapBindingWitness.Expected HASH = new NexusMapBindingWitness.Expected("1".repeat(64));
    private static final NexusMapSourceStaging.Limits LIMITS = new NexusMapSourceStaging.Limits(32, 2L << 20);
    @TempDir Path root;
    Path saved;
    Map<Integer, NexusMapDiskSourceVerifier.Source> descriptors;
    NexusMapMigrationInventory.Snapshot inventory;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void fixture() throws Exception {
        saved = Files.createDirectory(root.resolve("saved")); descriptors = new HashMap<>();
        write(20, 1, 0, false, false); write(31, 2, 0, false, false); write(32, 0, 0, false, false); write(99, 0, 0, false, true);
        inventory = inventory(List.of(binding(31, 2, List.of(20)), binding(32, 0, List.of())), Set.of(31), Map.of(99, List.of()));
    }
    private NexusMapBindingSavedData.Entry binding(int id, int scale, List<Integer> ancestors) {
        return new NexusMapBindingSavedData.Entry(id, UNIT, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), 0, 0, ancestors, scale);
    }
    private NexusMapMigrationInventory.Snapshot inventory(List<NexusMapBindingSavedData.Entry> rows, Set<Integer> prepared,
                                                         Map<Integer, List<Integer>> detail) {
        var cursor = new NexusMapMigrationInventory(rows.size(), rows.iterator(), id -> rows.stream().filter(r -> r.mapId() == id && prepared.contains(id))
                .findFirst().map(r -> new NexusMapBindingSavedData.SparseImportIntent(UUID.randomUUID(), r, r.effectiveScale(), false)),
                new NexusLegacyTerrainSnapshot(Map.of(), detail), HASH, () -> { }, () -> { });
        while (!cursor.step(8)) { } return cursor.result();
    }
    private void write(int id, int scale, int x, boolean locked, boolean nether) throws Exception {
        var map = MapItemSavedData.createFresh(0, 0, (byte) scale, false, false, nether ? Level.NETHER : Level.OVERWORLD);
        var data = (CompoundTag) MapItemSavedData.CODEC.encodeStart(NbtOps.INSTANCE, map).getOrThrow();
        data.putInt("xCenter", x); data.putInt("zCenter", 0); data.putBoolean("locked", locked);
        var envelope = new CompoundTag(); envelope.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version()); envelope.put("data", data);
        Path path = root.resolve("map-" + id + ".dat"); NbtIo.writeCompressed(envelope, path);
        descriptors.put(id, new NexusMapDiskSourceVerifier.Source(path, DataFixTypes.SAVED_DATA_MAP_DATA, DataFixers.getDataFixer()));
    }
    private NexusMapNativeBatchStaging.Result stage() throws IOException {
        return NexusMapNativeBatchStaging.stage(saved, inventory, descriptors, REGISTRIES, LIMITS, () -> true);
    }
    private long count() throws IOException { try (var paths = Files.list(saved)) { return paths.count(); } }
    @Test void completeNativeInventoryRetainsUnpreparedAndOrphanSourcesWithImmutableMetadata() throws Exception {
        var result = stage(); assertSame(HASH, result.metadata());
        assertEquals(List.of(20, 31, 32, 99), new ArrayList<>(result.sources().keySet())); assertEquals(Set.of(32), result.unprepared());
        assertEquals(1, result.sources().get(20).scale()); assertEquals("minecraft:the_nether", result.sources().get(99).dimension());
        assertThrows(UnsupportedOperationException.class, () -> result.sources().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.unprepared().clear());
        assertEquals(result.sources(), stage().sources());
        for (var descriptor : descriptors.values()) Files.delete(descriptor.path());
        for (var source : result.sources().values())
            assertEquals(source.blob(), NexusMapSourceStaging.verify(saved, source.blob().kind(), source.blob().hash(), () -> true));
    }
    @Test void incompleteExtraNullAndAliasedDescriptorsRefuseBeforeSaving() throws Exception {
        var original = new HashMap<>(descriptors);
        descriptors.remove(20); assertThrows(IOException.class, this::stage); assertEquals(0, count());
        descriptors = new HashMap<>(original); descriptors.put(123, original.get(20)); assertThrows(IOException.class, this::stage); assertEquals(0, count());
        descriptors = new HashMap<>(original); descriptors.put(20, null); assertThrows(IOException.class, this::stage); assertEquals(0, count());
        descriptors = new HashMap<>(original); descriptors.put(20, original.get(31)); assertThrows(IOException.class, this::stage); assertEquals(0, count());
    }
    @Test void missingOrCorruptSourceNeverReturnsPartialInventoryOrRepairsInput() throws Exception {
        Files.delete(descriptors.get(31).path()); assertThrows(IOException.class, this::stage);
        assertFalse(Files.exists(descriptors.get(31).path())); assertTrue(count() > 0);
        Files.write(descriptors.get(31).path(), new byte[]{1, 2, 3}); assertThrows(IOException.class, this::stage);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(descriptors.get(31).path()));
    }
    @Test void boundCenterDimensionScaleAndPreparedLockMustMatch() throws Exception {
        for (int kind = 0; kind < 4; kind++) {
            write(31, kind == 2 ? 1 : 2, kind == 0 ? 128 : 0, kind == 3, kind == 1);
            assertThrows(IOException.class, this::stage);
        }
    }
    @Test void ancestorCenterDimensionAndNativeScaleMustMatchLineage() throws Exception {
        for (int kind = 0; kind < 3; kind++) {
            write(20, kind == 2 ? 0 : 1, kind == 0 ? 128 : 0, false, kind == 1);
            assertThrows(IOException.class, this::stage);
        }
    }
    @Test void clampedNativeScaleCannotHideConflictingLogicalAncestorOrUnit() throws Exception {
        write(20, 4, 0, false, false); write(31, 4, 0, false, false); descriptors.remove(32); descriptors.remove(99);
        inventory = inventory(List.of(binding(31, 6, List.of(20)), binding(20, 6, List.of())), Set.of(31, 20), Map.of());
        assertThrows(IOException.class, this::stage);
        write(20, 1, 0, false, false); write(31, 2, 0, false, false);
        var other = new NexusMapBindingSavedData.Entry(20, UUID.randomUUID(), GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), 0, 0, List.of(), 1);
        inventory = inventory(List.of(binding(31, 2, List.of(20)), other), Set.of(31, 20), Map.of()); assertThrows(IOException.class, this::stage);
    }
    @Test void sourceChangedAfterObservationCannotPublishAsOldBytes() throws Exception {
        assertThrows(IOException.class, () -> NexusMapNativeBatchStaging.stage(saved, inventory, descriptors, REGISTRIES, LIMITS, () -> true, (id, phase) -> {
            if (id == 20 && phase == NexusMapNativeBatchStaging.Phase.OBSERVED) Files.write(descriptors.get(id).path(), new byte[]{8, 9});
        }));
        assertEquals(0, count()); assertArrayEquals(new byte[]{8, 9}, Files.readAllBytes(descriptors.get(20).path()));
    }
    @Test void savedCorruptionCannotProduceBatchResultOrTriggerOriginalFallback() throws Exception {
        assertThrows(IOException.class, () -> NexusMapNativeBatchStaging.stage(saved, inventory, descriptors, REGISTRIES, LIMITS, () -> true, (id, phase) -> {
            if (phase == NexusMapNativeBatchStaging.Phase.STAGED) {
                String hash;
                try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(descriptors.get(id).path()))); }
                catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
                Files.writeString(NexusMapSourceStaging.path(saved, NexusMapSourceStaging.Kind.NATIVE, hash), "corrupt");
            }
        }));
        assertTrue(count() > 0); assertThrows(IOException.class, this::stage);
    }
    @Test void cancellationBeforeAndAfterSavingNeverReturnsPartialBatch() throws Exception {
        assertThrows(CancellationException.class, () -> NexusMapNativeBatchStaging.stage(saved, inventory, descriptors, REGISTRIES, LIMITS, () -> false));
        for (var cancelAt : NexusMapNativeBatchStaging.Phase.values()) {
            Path run = Files.createDirectory(root.resolve(cancelAt.name())); var alive = new AtomicBoolean(true);
            assertThrows(CancellationException.class, () -> NexusMapNativeBatchStaging.stage(run, inventory, descriptors, REGISTRIES, LIMITS, alive::get,
                    (id, phase) -> { if (phase == cancelAt) alive.set(false); }));
            try (var paths = Files.list(run)) { assertEquals(cancelAt == NexusMapNativeBatchStaging.Phase.STAGED ? 1 : 0, paths.count()); }
        }
    }
    @Test void emptyNativeInventoryIsExplicitAndQuotaRefusesWithoutCompleting() throws Exception {
        var empty = inventory(List.of(), Set.of(), Map.of());
        assertTrue(NexusMapNativeBatchStaging.stage(saved, empty, Map.of(), REGISTRIES, LIMITS, () -> true).sources().isEmpty());
        assertThrows(IOException.class, () -> NexusMapNativeBatchStaging.stage(saved, inventory, descriptors, REGISTRIES,
                new NexusMapSourceStaging.Limits(1, 1), () -> true)); assertEquals(0, count());
    }
    private Path receipts;
    private NexusMapInventoryReceipt.Receipt receipt;
    private void savedReceipt() throws Exception {
        var batch = stage(); receipts = Files.createDirectory(root.resolve("receipts"));
        var data = new CompoundTag(); data.putInt("version", 1); data.put("pages", new ListTag());
        var maps = new ListTag(); var index = new CompoundTag(); index.putInt("map_id", 99); index.put("pages", new ListTag()); maps.add(index); data.put("maps", maps);
        var envelope = new CompoundTag(); envelope.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version()); envelope.put("data", data);
        Path detail = root.resolve("detail.dat"); NbtIo.writeCompressed(envelope, detail);
        var intent = inventory.bindings().get(31).intent().orElseThrow();
        var primary = NexusMapDiskSourceVerifier.observeNative(descriptors.get(31), new net.minecraft.world.level.saveddata.maps.MapId(31), REGISTRIES, () -> true);
        var expected = new NexusMapSourceSnapshot(intent, primary.base(), inventory.detail());
        var observed = NexusMapDiskSourceVerifier.verify(descriptors.get(31), new NexusMapDiskSourceVerifier.Source(detail,
                DataFixTypes.SAVED_DATA_COMMAND_STORAGE, DataFixers.getDataFixer()), REGISTRIES, expected, () -> true);
        var blob = NexusMapSourceStaging.stage(saved, detail, NexusMapSourceStaging.Kind.DETAIL, observed.detailFileHash(), LIMITS, () -> true);
        receipt = new NexusMapInventoryReceipt.Receipt(NexusMapSourceManifest.capture(HASH, intent, observed), blob, batch.sources(), batch.unprepared());
        NexusMapInventoryReceipt.publish(receipts, receipt, () -> true);
    }
    private NexusMapInventoryRecovery.Verified recover() throws IOException {
        return NexusMapInventoryRecovery.verify(receipts, saved, receipt.primary().ticket(), inventory,
                DataFixers.getDataFixer(), DataFixers.getDataFixer(), REGISTRIES, () -> true);
    }
    private void replaceReceipt(NexusMapInventoryReceipt.Receipt changed) throws IOException {
        Files.delete(NexusMapInventoryReceipt.path(receipts, receipt.primary().ticket())); receipt = changed;
        NexusMapInventoryReceipt.publish(receipts, changed, () -> true);
    }
    private NexusMapMigrationInventory.Snapshot changedInventory(NexusMapBindingWitness.Expected metadata,
            Map<Integer, NexusMapMigrationInventory.Row> rows, NexusLegacyTerrainSnapshot detail) {
        var cursor = new NexusMapMigrationInventory(rows.size(), rows.values().stream().map(NexusMapMigrationInventory.Row::binding).iterator(),
                id -> rows.get(id).intent(), detail, metadata, () -> { }, () -> { });
        while (!cursor.step(8)) { } return cursor.result();
    }
    @Test void coldRecoveryVerifiesCompleteSavedInventoryWithoutOriginalFilesOrWrites() throws Exception {
        savedReceipt(); for (var descriptor : descriptors.values()) Files.delete(descriptor.path()); Files.delete(root.resolve("detail.dat"));
        var before = new TreeMap<String, byte[]>();
        try (var files = Files.list(saved)) { for (var path : files.toList()) before.put(path.getFileName().toString(), Files.readAllBytes(path)); }
        assertEquals(receipt, recover().receipt()); assertEquals(receipt, recover().receipt());
        assertEquals(before.size(), count()); for (var entry : before.entrySet()) assertArrayEquals(entry.getValue(), Files.readAllBytes(saved.resolve(entry.getKey())));
    }
    @Test void coldRecoveryMissingOrCorruptReceiptNeverReconstructsIt() throws Exception {
        savedReceipt(); Path path = NexusMapInventoryReceipt.path(receipts, receipt.primary().ticket());
        Files.delete(path); assertThrows(IOException.class, this::recover); assertFalse(Files.exists(path));
        Files.writeString(path, "partial"); assertThrows(IOException.class, this::recover); assertEquals("partial", Files.readString(path));
    }
    @Test void coldRecoveryMissingOrCorruptNativeNeverFallsBackToOriginals() throws Exception {
        savedReceipt(); var blob = receipt.natives().get(99).blob(); var file = NexusMapSourceStaging.path(saved, blob.kind(), blob.hash());
        Files.delete(file); assertThrows(IOException.class, this::recover); assertFalse(Files.exists(file));
        Files.writeString(file, "corrupt"); assertThrows(IOException.class, this::recover); assertEquals("corrupt", Files.readString(file));
    }
    @Test void coldRecoveryCorruptDetailNeverRepairsIt() throws Exception {
        savedReceipt(); var file = NexusMapSourceStaging.path(saved, receipt.detail().kind(), receipt.detail().hash());
        Files.writeString(file, "broken"); assertThrows(IOException.class, this::recover); assertEquals("broken", Files.readString(file));
    }
    @Test void coldRecoveryExactRequiredSetAndUnpreparedStateMustMatch() throws Exception {
        savedReceipt(); var original = receipt; var missing = new TreeMap<>(receipt.natives()); missing.remove(99);
        replaceReceipt(new NexusMapInventoryReceipt.Receipt(original.primary(), original.detail(), missing, original.unprepared()));
        assertThrows(IOException.class, this::recover);
        var extra = new TreeMap<>(original.natives()); var row = extra.get(99);
        extra.put(100, new NexusMapNativeBatchStaging.NativeSource(100, row.centerX(), row.centerZ(), row.scale(), row.locked(), row.dimension(), row.blob(), row.terrainHash()));
        replaceReceipt(new NexusMapInventoryReceipt.Receipt(original.primary(), original.detail(), extra, original.unprepared()));
        assertThrows(IOException.class, this::recover);
        replaceReceipt(new NexusMapInventoryReceipt.Receipt(original.primary(), original.detail(), original.natives(), Set.of()));
        assertThrows(IOException.class, this::recover);
    }
    @Test void coldRecoveryRequiresHeldLedgerAndExactPrimaryIntent() throws Exception {
        savedReceipt(); var original = inventory;
        inventory = changedInventory(new NexusMapBindingWitness.Expected("2".repeat(64)), original.bindings(), original.detail());
        assertThrows(IOException.class, this::recover);
        var rows = new TreeMap<>(original.bindings()); var row = rows.get(31); var intent = row.intent().orElseThrow();
        rows.put(31, new NexusMapMigrationInventory.Row(row.binding(), Optional.of(new NexusMapBindingSavedData.SparseImportIntent(UUID.randomUUID(), row.binding(), intent.scale(), intent.locked()))));
        inventory = changedInventory(HASH, rows, original.detail()); assertThrows(IOException.class, this::recover);
        // A different unit preserves map geometry but must still invalidate the manifest's intent fingerprint.
        var source = row.binding(); var changed = new NexusMapBindingSavedData.Entry(source.mapId(), UUID.randomUUID(), source.anchor(), source.centerX(), source.centerZ(), source.detailAncestors(), source.effectiveScale());
        rows.put(31, new NexusMapMigrationInventory.Row(changed, Optional.of(new NexusMapBindingSavedData.SparseImportIntent(intent.ticket(), changed, intent.scale(), intent.locked()))));
        inventory = changedInventory(HASH, rows, original.detail()); assertThrows(IOException.class, this::recover);
    }
    @Test void coldRecoveryRejectsForgedGeometryTerrainAndLengthMetadata() throws Exception {
        savedReceipt(); var original = receipt; var row = receipt.natives().get(99);
        for (int kind = 0; kind < 3; kind++) {
            var rows = new TreeMap<>(original.natives());
            rows.put(99, new NexusMapNativeBatchStaging.NativeSource(99, kind == 0 ? 128 : row.centerX(), row.centerZ(), row.scale(), row.locked(), row.dimension(),
                    kind == 2 ? new NexusMapSourceStaging.Blob(row.blob().kind(), row.blob().hash(), row.blob().bytes() + 1) : row.blob(), kind == 1 ? "0".repeat(64) : row.terrainHash()));
            replaceReceipt(new NexusMapInventoryReceipt.Receipt(original.primary(), original.detail(), rows, original.unprepared()));
            assertThrows(IOException.class, this::recover);
        }
    }
    @Test void coldRecoveryRechecksBindingAndAncestorRelationships() throws Exception {
        savedReceipt(); var original = receipt; var ancestor = receipt.natives().get(20);
        var rows = new TreeMap<>(receipt.natives());
        rows.put(20, new NexusMapNativeBatchStaging.NativeSource(20, 128, ancestor.centerZ(), ancestor.scale(), ancestor.locked(), ancestor.dimension(), ancestor.blob(), ancestor.terrainHash()));
        replaceReceipt(new NexusMapInventoryReceipt.Receipt(original.primary(), original.detail(), rows, original.unprepared()));
        assertThrows(IOException.class, this::recover);
        replaceReceipt(original); var bindings = new TreeMap<>(inventory.bindings()); var row = bindings.get(32); var b = row.binding();
        bindings.put(32, new NexusMapMigrationInventory.Row(new NexusMapBindingSavedData.Entry(32, b.unitId(), b.anchor(), 128, 0, List.of(), 0), Optional.empty()));
        inventory = changedInventory(HASH, bindings, inventory.detail()); assertThrows(IOException.class, this::recover);
    }
    @Test void coldRecoveryMustVerifyFullHeldDetailIncludingUnreferencedPages() throws Exception {
        savedReceipt(); var detail = new NexusLegacyTerrainSnapshot(Map.of(7, new NexusLegacyTerrainSnapshot.Page(7, 0, 0, 0, false, new byte[16384])), inventory.detail().maps());
        inventory = changedInventory(HASH, inventory.bindings(), detail); assertThrows(IOException.class, this::recover);
    }
    @Test void coldRecoveryHashConsistentMalformedNativeStillRequiresStrictDecode() throws Exception {
        savedReceipt(); var rows = new TreeMap<>(receipt.natives()); var row = rows.get(99); byte[] bad = {1, 2, 3};
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bad));
        var blob = new NexusMapSourceStaging.Blob(NexusMapSourceStaging.Kind.NATIVE, hash, bad.length);
        Files.write(NexusMapSourceStaging.path(saved, blob.kind(), blob.hash()), bad);
        rows.put(99, new NexusMapNativeBatchStaging.NativeSource(99, row.centerX(), row.centerZ(), row.scale(), row.locked(), row.dimension(), blob, row.terrainHash()));
        replaceReceipt(new NexusMapInventoryReceipt.Receipt(receipt.primary(), receipt.detail(), rows, receipt.unprepared()));
        assertThrows(IOException.class, this::recover);
    }
    @Test void coldRecoveryHashConsistentMalformedDetailStillRequiresStrictDecode() throws Exception {
        savedReceipt(); byte[] bad = {1, 2, 3};
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bad));
        var detail = new NexusMapSourceStaging.Blob(NexusMapSourceStaging.Kind.DETAIL, hash, bad.length);
        Files.write(NexusMapSourceStaging.path(saved, detail.kind(), detail.hash()), bad);
        var p = receipt.primary(); var changed = new NexusMapSourceManifest.Manifest(p.ticket(), p.mapId(), p.ledgerHash(), p.intentHash(),
                p.nativeFileHash(), hash, p.nativeTerrainHash(), p.detailTerrainHash());
        replaceReceipt(new NexusMapInventoryReceipt.Receipt(changed, detail, receipt.natives(), receipt.unprepared()));
        assertThrows(IOException.class, this::recover);
    }
    @Test void coldRecoveryCancellationNeverReturnsPartialVerifiedResult() throws Exception {
        savedReceipt(); var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(CancellationException.class, () -> NexusMapInventoryRecovery.verify(receipts, saved, receipt.primary().ticket(), inventory,
                DataFixers.getDataFixer(), DataFixers.getDataFixer(), REGISTRIES, () -> false));
        assertThrows(CancellationException.class, () -> NexusMapInventoryRecovery.verify(receipts, saved, receipt.primary().ticket(), inventory,
                DataFixers.getDataFixer(), DataFixers.getDataFixer(), REGISTRIES, () -> calls.incrementAndGet() < 40));
        assertTrue(calls.get() >= 40); assertEquals(receipt, recover().receipt());
    }

}
