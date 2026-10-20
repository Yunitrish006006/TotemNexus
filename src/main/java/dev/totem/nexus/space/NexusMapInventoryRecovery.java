package dev.totem.nexus.space;

import com.mojang.datafixers.DataFixer;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Worker-only cold verification, not import completion, common T0 or live activation authority. */
final class NexusMapInventoryRecovery {
    static final class Verified {
        private final NexusMapInventoryReceipt.Receipt receipt;
        private Verified(NexusMapInventoryReceipt.Receipt receipt) { this.receipt = receipt; }
        NexusMapInventoryReceipt.Receipt receipt() { return receipt; }
    }
    private NexusMapInventoryRecovery() { }

    /** Caller holds a fresh immutable inventory and excludes external writers for the whole verification. */
    static Verified verify(Path receipts, Path blobs, UUID ticket, NexusMapMigrationInventory.Snapshot inventory,
                           DataFixer nativeFixer, DataFixer detailFixer, HolderLookup.Provider registries,
                           BooleanSupplier alive) throws IOException {
        check(alive);
        var receipt = NexusMapInventoryReceipt.read(receipts, ticket, alive)
                .orElseThrow(() -> new IOException("Required inventory receipt is missing"));
        var manifest = receipt.primary(); var row = inventory.bindings().get(manifest.mapId());
        if (!manifest.ledgerHash().equals(inventory.metadata().hash())
                || !receipt.natives().keySet().equals(inventory.nativeSources())
                || !receipt.unprepared().equals(inventory.unprepared()) || row == null || row.intent().isEmpty()
                || !row.intent().get().ticket().equals(ticket))
            throw new IOException("Recovered receipt does not match held inventory");
        NexusMapNativeBatchStaging.validateInventory(inventory, receipt.natives(), alive);
        NexusMapSourceSnapshot.Base primary = null;
        for (var saved : receipt.natives().values()) {
            check(alive); var blob = saved.blob();
            if (!NexusMapSourceStaging.verify(blobs, blob.kind(), blob.hash(), alive).equals(blob))
                throw new IOException("Recovered native length mismatch");
            var source = new NexusMapDiskSourceVerifier.Source(NexusMapSourceStaging.path(blobs, blob.kind(), blob.hash()),
                    DataFixTypes.SAVED_DATA_MAP_DATA, nativeFixer);
            var observed = NexusMapDiskSourceVerifier.observeNative(source, new MapId(saved.mapId()), registries, alive);
            var base = observed.base();
            if (!blob.hash().equals(observed.fileHash()) || !saved.terrainHash().equals(observed.terrainHash())
                    || base.centerX != saved.centerX() || base.centerZ != saved.centerZ() || base.scale != saved.scale()
                    || base.locked != saved.locked() || !base.dimension.identifier().toString().equals(saved.dimension()))
                throw new IOException("Recovered native semantic mismatch");
            if (saved.mapId() == manifest.mapId()) primary = base;
        }
        var detail = receipt.detail();
        if (!NexusMapSourceStaging.verify(blobs, detail.kind(), detail.hash(), alive).equals(detail))
            throw new IOException("Recovered detail length mismatch");
        var nativeFile = new NexusMapDiskSourceVerifier.Source(NexusMapSourceStaging.path(blobs,
                NexusMapSourceStaging.Kind.NATIVE, manifest.nativeFileHash()), DataFixTypes.SAVED_DATA_MAP_DATA, nativeFixer);
        var detailFile = new NexusMapDiskSourceVerifier.Source(NexusMapSourceStaging.path(blobs, detail.kind(), detail.hash()),
                DataFixTypes.SAVED_DATA_COMMAND_STORAGE, detailFixer);
        final NexusMapSourceSnapshot expected;
        try { expected = new NexusMapSourceSnapshot(row.intent().orElseThrow(), primary, inventory.detail()); }
        catch (RuntimeException malformed) { throw new IOException("Recovered primary conflicts with held intent", malformed); }
        var observation = NexusMapDiskSourceVerifier.verify(nativeFile, detailFile, registries, expected, alive);
        if (!NexusMapSourceManifest.capture(inventory.metadata(), expected.intent(), observation).equals(manifest))
            throw new IOException("Recovered source manifest mismatch");
        check(alive);
        return new Verified(receipt);
    }
    private static void check(BooleanSupplier alive) {
        if (!alive.getAsBoolean()) throw new CancellationException("Inventory recovery stopped");
    }
}
