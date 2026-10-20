package dev.totem.nexus.space;

import java.util.*;
import java.util.function.IntFunction;

/** Bounded metadata/source requirements, not proof of disk presence, import or activation. */
final class NexusMapMigrationInventory {
    static final int MAX_BINDINGS = 8192, MAX_NATIVE_SOURCES = 65536;
    record Row(NexusMapBindingSavedData.Entry binding, Optional<NexusMapBindingSavedData.SparseImportIntent> intent) {
        Row {
            Objects.requireNonNull(binding); Objects.requireNonNull(intent);
            if (intent.isPresent() && !intent.get().source().equals(binding))
                throw new IllegalArgumentException("Conflicting inventory ticket");
        }
    }
    static final class Snapshot {
        private final NexusMapBindingWitness.Expected metadata;
        private final Map<Integer, Row> bindings;
        private final Set<Integer> nativeSources, unprepared;
        private final NexusLegacyTerrainSnapshot detail;
        // Ownership transfer from a completed cursor; no later cursor mutations are allowed.
        private Snapshot(NexusMapBindingWitness.Expected metadata, Map<Integer, Row> bindings,
                         Set<Integer> nativeSources, Set<Integer> unprepared, NexusLegacyTerrainSnapshot detail) {
            this.metadata = metadata; this.bindings = Collections.unmodifiableMap(bindings);
            this.nativeSources = Collections.unmodifiableSet(nativeSources);
            this.unprepared = Collections.unmodifiableSet(unprepared); this.detail = detail;
        }
        NexusMapBindingWitness.Expected metadata() { return metadata; }
        Map<Integer, Row> bindings() { return bindings; }
        Set<Integer> nativeSources() { return nativeSources; }
        Set<Integer> unprepared() { return unprepared; }
        NexusLegacyTerrainSnapshot detail() { return detail; }
    }
    private final Iterator<NexusMapBindingSavedData.Entry> entries;
    private final IntFunction<Optional<NexusMapBindingSavedData.SparseImportIntent>> intent;
    private final Iterator<Integer> detailMaps;
    private final NexusLegacyTerrainSnapshot detail;
    private final NexusMapBindingWitness.Expected metadata;
    private final Runnable check, failed;
    private final int expectedBindings;
    private final Map<Integer, Row> rows = new TreeMap<>();
    private final Set<Integer> nativeSources = new TreeSet<>(), unprepared = new TreeSet<>();
    private final Set<UUID> tickets = new HashSet<>();
    private Snapshot result;
    private boolean refused;

    NexusMapMigrationInventory(int count, Iterator<NexusMapBindingSavedData.Entry> entries,
                               IntFunction<Optional<NexusMapBindingSavedData.SparseImportIntent>> intent,
                               NexusLegacyTerrainSnapshot detail, NexusMapBindingWitness.Expected metadata,
                               Runnable check, Runnable failed) {
        if (count < 0 || count > MAX_BINDINGS || detail.maps().size() > NexusMapDetailSavedData.MAX_WORLD_PAGES)
            throw new IllegalStateException("Migration inventory exceeds metadata bound");
        expectedBindings = count; this.entries = Objects.requireNonNull(entries); this.intent = Objects.requireNonNull(intent);
        this.detail = Objects.requireNonNull(detail); this.metadata = Objects.requireNonNull(metadata);
        detailMaps = detail.maps().keySet().iterator(); this.check = Objects.requireNonNull(check); this.failed = Objects.requireNonNull(failed);
    }
    private void check() {
        check.run();
        if (refused) throw new IllegalStateException("Migration inventory refused");
    }
    /** One unit visits one binding (at most 20 ancestors) or one detail index. No terrain copies or IO. */
    boolean step(int budget) {
        check();
        if (budget < 1 || budget > 8) throw new IllegalArgumentException("Inventory budget must be 1..8");
        if (result != null) return true;
        try {
            while (budget > 0 && entries.hasNext()) {
                var binding = entries.next();
                if (rows.size() >= expectedBindings || rows.putIfAbsent(binding.mapId(), new Row(binding, intent.apply(binding.mapId()))) != null)
                    throw new IllegalStateException("Incomplete or duplicate inventory bindings");
                var prepared = rows.get(binding.mapId()).intent();
                if (prepared.isEmpty()) unprepared.add(binding.mapId());
                else if (!tickets.add(prepared.get().ticket())) throw new IllegalStateException("Duplicate migration inventory ticket");
                addNative(binding.mapId());
                for (int ancestor : binding.detailAncestors()) addNative(ancestor);
                budget--;
            }
            while (budget > 0 && !entries.hasNext() && detailMaps.hasNext()) { addNative(detailMaps.next()); budget--; }
            if (!entries.hasNext() && !detailMaps.hasNext()) {
                if (rows.size() != expectedBindings) throw new IllegalStateException("Incomplete migration inventory");
                result = new Snapshot(metadata, rows, nativeSources, unprepared, detail);
            }
            return result != null;
        } catch (RuntimeException failure) { refused = true; failed.run(); throw failure; }
    }
    private void addNative(int id) {
        if (id < 0) throw new IllegalStateException("Invalid native source identity");
        if (!nativeSources.contains(id) && nativeSources.size() >= MAX_NATIVE_SOURCES)
            throw new IllegalStateException("Too many native sources in migration inventory");
        nativeSources.add(id);
    }
    Snapshot result() {
        check();
        if (result == null) throw new IllegalStateException("Migration inventory incomplete");
        return result;
    }
}
