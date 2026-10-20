package dev.totem.nexus.space;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Worker-only native portion of a source inventory. No common T0, durable batch receipt or live authority. */
final class NexusMapNativeBatchStaging {
    record NativeSource(int mapId, int centerX, int centerZ, int scale, boolean locked, String dimension,
                        NexusMapSourceStaging.Blob blob, String terrainHash) { }
    static final class Result {
        private final NexusMapBindingWitness.Expected metadata;
        private final Map<Integer, NativeSource> sources;
        private final Set<Integer> unprepared;
        private Result(NexusMapMigrationInventory.Snapshot inventory, Map<Integer, NativeSource> sources) {
            metadata = inventory.metadata(); this.sources = Collections.unmodifiableMap(sources);
            unprepared = inventory.unprepared();
        }
        NexusMapBindingWitness.Expected metadata() { return metadata; }
        Map<Integer, NativeSource> sources() { return sources; }
        Set<Integer> unprepared() { return unprepared; }
    }
    private NexusMapNativeBatchStaging() { }
    static Result stage(Path directory, NexusMapMigrationInventory.Snapshot inventory,
                        Map<Integer, NexusMapDiskSourceVerifier.Source> descriptors, HolderLookup.Provider registries,
                        NexusMapSourceStaging.Limits limits, BooleanSupplier alive) throws IOException {
        return stage(directory, inventory, descriptors, registries, limits, alive, (id, phase) -> { });
    }
    /** Caller retains metadata ownership, stable descriptors, durable directory and writer exclusion. */
    static Result stage(Path directory, NexusMapMigrationInventory.Snapshot inventory,
                        Map<Integer, NexusMapDiskSourceVerifier.Source> descriptors, HolderLookup.Provider registries,
                        NexusMapSourceStaging.Limits limits, BooleanSupplier alive, Probe probe) throws IOException {
        check(alive);
        if (descriptors.size() > NexusMapMigrationInventory.MAX_NATIVE_SOURCES
                || !descriptors.keySet().equals(inventory.nativeSources()))
            throw new IOException("Native batch descriptors do not cover the exact source inventory");
        var selected = new TreeMap<Integer, NexusMapDiskSourceVerifier.Source>();
        var paths = new HashSet<Path>();
        for (var entry : descriptors.entrySet()) {
            check(alive);
            var source = entry.getValue();
            if (source == null || !paths.add(source.path().toAbsolutePath().normalize()))
                throw new IOException("Missing or duplicate native source descriptor");
            selected.put(entry.getKey(), source);
        }
        if (!selected.keySet().equals(inventory.nativeSources())) throw new IOException("Native batch descriptors changed during selection");
        var completed = new TreeMap<Integer, NativeSource>();
        for (var entry : selected.entrySet()) {
            check(alive); int id = entry.getKey(); var source = entry.getValue();
            var observed = NexusMapDiskSourceVerifier.observeNative(source, new MapId(id), registries, alive);
            var base = observed.base(); String dimension = base.dimension.identifier().toString();
            if (dimension.length() > 256) throw new IOException("Native batch dimension exceeds metadata limit");
            var row = inventory.bindings().get(id);
            if (row != null) validateBinding(row, base);
            probe.at(id, Phase.OBSERVED); check(alive);
            var blob = NexusMapSourceStaging.stage(directory, source.path(), NexusMapSourceStaging.Kind.NATIVE,
                    observed.fileHash(), limits, alive);
            probe.at(id, Phase.STAGED); check(alive);
            var saved = new NexusMapDiskSourceVerifier.Source(NexusMapSourceStaging.path(directory, blob.kind(), blob.hash()),
                    source.fixType(), source.fixer());
            var verified = NexusMapDiskSourceVerifier.observeNative(saved, new MapId(id), registries, alive);
            if (!observed.fileHash().equals(verified.fileHash()) || !observed.terrainHash().equals(verified.terrainHash()))
                throw new IOException("Saved native source differs from observed source");
            completed.put(id, new NativeSource(id, base.centerX, base.centerZ, base.scale, base.locked, dimension, blob, observed.terrainHash()));
            // Only bounded metadata is retained; no pixel array enters the batch result.
        }
        validateInventory(inventory, completed, alive);
        check(alive);
        return new Result(inventory, completed);
    }
    /** Shared relationship checks for staging and read-only recovery. */
    static void validateInventory(NexusMapMigrationInventory.Snapshot inventory, Map<Integer, NativeSource> completed,
                                  BooleanSupplier alive) throws IOException {
        check(alive);
        if (!completed.keySet().equals(inventory.nativeSources())) throw new IOException("Incomplete native inventory");
        for (var row : inventory.bindings().values()) {
            check(alive); var binding = row.binding(); var parent = completed.get(binding.mapId());
            if (parent == null || binding.centerX() != parent.centerX() || binding.centerZ() != parent.centerZ()
                    || !binding.anchor().dimension().identifier().toString().equals(parent.dimension())
                    || (binding.effectiveScale() >= 0 && parent.scale() != Math.min(4, binding.effectiveScale()))
                    || (row.intent().isPresent() && (parent.locked() != row.intent().get().locked()
                    || parent.scale() != Math.min(4, row.intent().get().scale()))))
                throw new IOException("Native metadata conflicts with inventory binding");
            int scale = row.intent().map(NexusMapBindingSavedData.SparseImportIntent::scale)
                    .orElse(binding.effectiveScale() < 0 ? parent.scale() : binding.effectiveScale());
            var ancestors = binding.detailAncestors();
            if (ancestors.size() > scale) throw new IOException("Invalid native source ancestry depth");
            for (int i = 0; i < ancestors.size(); i++) {
                check(alive); int ancestorId = ancestors.get(i); var ancestor = completed.get(ancestorId);
                var ancestorRow = inventory.bindings().get(ancestorId);
                int ancestorScale = scale - ancestors.size() + i;
                if (ancestor == null || ancestor.centerX() != parent.centerX() || ancestor.centerZ() != parent.centerZ()
                        || !ancestor.dimension().equals(parent.dimension()) || ancestor.scale() != Math.min(4, ancestorScale)
                        || (ancestorRow != null && (!ancestorRow.binding().unitId().equals(binding.unitId())
                        || !ancestorRow.binding().anchor().equals(binding.anchor())
                        || ancestorRow.intent().map(NexusMapBindingSavedData.SparseImportIntent::scale)
                        .orElse(ancestorRow.binding().effectiveScale() < 0 ? ancestor.scale() : ancestorRow.binding().effectiveScale()) != ancestorScale)))
                    throw new IOException("Conflicting native ancestor source");
            }
        }
    }
    private static void validateBinding(NexusMapMigrationInventory.Row row, NexusMapSourceSnapshot.Base base) throws IOException {
        var binding = row.binding();
        if (binding.mapId() != base.mapId || binding.centerX() != base.centerX || binding.centerZ() != base.centerZ
                || !binding.anchor().dimension().equals(base.dimension)
                || (binding.effectiveScale() >= 0 && base.scale != Math.min(4, binding.effectiveScale()))
                || (row.intent().isPresent() && (base.locked != row.intent().get().locked()
                || base.scale != Math.min(4, row.intent().get().scale()))))
            throw new IOException("Native source conflicts with inventory binding");
    }
    private static void check(BooleanSupplier alive) { if (!alive.getAsBoolean()) throw new CancellationException("Native batch staging cancelled"); }
    enum Phase { OBSERVED, STAGED }
    @FunctionalInterface interface Probe { void at(int id, Phase phase) throws IOException; }
}
