package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-owned proof that a vanilla MapId was created for one Nexus anchor. */
public final class NexusMapBindingSavedData extends SavedData {
    public static final int DATA_VERSION = 4;
    public static final int MAX_DETAIL_ANCESTORS = NexusMapExtent.MAX_SCALE;

    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("map_id").forGetter(Entry::mapId),
            UUIDUtil.CODEC.fieldOf("space_unit_id").forGetter(Entry::unitId),
            GlobalPos.CODEC.fieldOf("anchor").forGetter(Entry::anchor),
            Codec.intRange(NexusMapExtent.MIN_COORDINATE, NexusMapExtent.MAX_COORDINATE_EXCLUSIVE - 1)
                    .fieldOf("center_x").forGetter(Entry::centerX),
            Codec.intRange(NexusMapExtent.MIN_COORDINATE, NexusMapExtent.MAX_COORDINATE_EXCLUSIVE - 1)
                    .fieldOf("center_z").forGetter(Entry::centerZ),
            Codec.INT.listOf().optionalFieldOf("detail_ancestors", List.of()).forGetter(Entry::detailAncestors),
            Codec.intRange(0, NexusMapExtent.MAX_SCALE).optionalFieldOf("effective_scale")
                    .forGetter((Entry entry) -> entry.effectiveScale() < 0 ? Optional.empty() : Optional.of(entry.effectiveScale()))
    ).apply(instance, (mapId, unitId, anchor, centerX, centerZ, ancestors, scale) ->
            new Entry(mapId, unitId, anchor, centerX, centerZ, ancestors, scale.orElse(-1))));

    private static final Codec<SparseImportIntent> IMPORT_CODEC = RecordCodecBuilder.<ImportFields>create(instance -> instance.group(
            Codec.intRange(1, 1).fieldOf("version").forGetter(ImportFields::version),
            UUIDUtil.CODEC.fieldOf("ticket").forGetter(ImportFields::ticket),
            ENTRY_CODEC.fieldOf("source").forGetter(ImportFields::source),
            Codec.intRange(0, NexusMapExtent.MAX_SCALE).fieldOf("scale").forGetter(ImportFields::scale),
            Codec.BOOL.fieldOf("locked").forGetter(ImportFields::locked)
    ).apply(instance, ImportFields::new)).comapFlatMap(ImportFields::decode,
            intent -> new ImportFields(1, intent.ticket(), intent.source(), intent.scale(), intent.locked()));

    private record ImportFields(int version, UUID ticket, Entry source, int scale, boolean locked) {
        DataResult<SparseImportIntent> decode() {
            try { return DataResult.success(new SparseImportIntent(ticket, source, scale, locked)); }
            catch (IllegalArgumentException exception) { return DataResult.error(() -> "Invalid sparse import intent"); }
        }
    }

    private record Fields(int version, List<Entry> entries, List<SparseImportIntent> imports) {
        DataResult<NexusMapBindingSavedData> decode() {
            try { return DataResult.success(new NexusMapBindingSavedData(version, entries, imports)); }
            catch (IllegalArgumentException exception) { return DataResult.error(() -> "Invalid map binding/import index"); }
        }
    }
    public static final Codec<NexusMapBindingSavedData> CODEC = RecordCodecBuilder.<Fields>create(instance -> instance.group(
            Codec.intRange(1, DATA_VERSION).optionalFieldOf("data_version", DATA_VERSION).forGetter(Fields::version),
            ENTRY_CODEC.listOf().optionalFieldOf("maps", List.of()).forGetter(Fields::entries),
            IMPORT_CODEC.listOf().optionalFieldOf("sparse_imports", List.of()).forGetter(Fields::imports)
    ).apply(instance, Fields::new)).comapFlatMap(Fields::decode,
            value -> new Fields(value.dataVersion, value.entries(), value.importIntents()));

    public static final SavedDataType<NexusMapBindingSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("totem", "nexus_map_bindings"),
            NexusMapBindingSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );
    static final SavedDataType<NexusMapBindingSavedData> LEGACY_COMPATIBILITY_TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("deadrecall", "nexus_map_bindings"),
            NexusMapBindingSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final int dataVersion;
    private final Map<Integer, Entry> byMapId = new HashMap<>();
    private final Map<Integer, SparseImportIntent> sparseImports = new HashMap<>();
    private volatile Object migrationFreeze;

    public boolean acceptsMutation() { return migrationFreeze == null; }
    synchronized Object freezeForMigration() {
        if (migrationFreeze != null) throw new IllegalStateException("Map bindings already frozen");
        return migrationFreeze = new Object();
    }
    synchronized void releaseMigrationFreeze(Object token) {
        if (migrationFreeze != token) throw new IllegalStateException("Wrong map migration freeze");
        migrationFreeze = null;
    }
    NexusMapMigrationInventory migrationInventory(Object token, NexusLegacyTerrainSnapshot detail,
                                                  NexusMapBindingWitness.Expected metadata, Runnable check, Runnable failed) {
        Runnable held = () -> {
            check.run();
            if (token == null || migrationFreeze != token) throw new IllegalStateException("Inventory metadata freeze released");
        };
        held.run();
        return new NexusMapMigrationInventory(byMapId.size(), byMapId.values().iterator(),
                id -> Optional.ofNullable(sparseImports.get(id)), detail, metadata, held, failed);
    }

    public NexusMapBindingSavedData() { this(DATA_VERSION, List.of(), List.of()); }

    /** Canonical-first loader with a one-way, non-destructive legacy copy. */
    public static synchronized NexusMapBindingSavedData loadCanonical(SavedDataStorage storage) {
        NexusMapBindingSavedData canonical = storage.get(TYPE);
        if (canonical != null) return canonical;
        NexusMapBindingSavedData legacy = storage.get(LEGACY_COMPATIBILITY_TYPE);
        if (legacy == null) return storage.computeIfAbsent(TYPE);
        NexusMapBindingSavedData migrated = new NexusMapBindingSavedData(legacy.dataVersion, legacy.entries(), legacy.importIntents());
        storage.set(TYPE, migrated);
        return migrated;
    }

    private NexusMapBindingSavedData(int dataVersion, List<Entry> entries, List<SparseImportIntent> imports) {
        this.dataVersion = Math.max(DATA_VERSION, dataVersion);
        for (Entry entry : entries) {
            if (byMapId.putIfAbsent(entry.mapId(), entry) != null)
                throw new IllegalArgumentException("Duplicate map binding");
        }
        for (SparseImportIntent intent : imports) {
            if (!intent.source().equals(byMapId.get(intent.source().mapId()))
                    || sparseImports.putIfAbsent(intent.source().mapId(), intent) != null)
                throw new IllegalArgumentException("Orphan, conflicting or duplicate sparse import intent");
        }
    }

    public boolean bind(MapId mapId, UUID unitId, GlobalPos anchor, MapItemSavedData mapData) {
        if (!acceptsMutation()) return false;
        if (mapId == null || unitId == null || anchor == null || mapData == null
                || mapData.scale < 0 || mapData.scale > MapItemSavedData.MAX_SCALE
                || !anchor.dimension().equals(mapData.dimension)
                || !NexusMapExtent.isSupportedCoordinate(mapData.centerX)
                || !NexusMapExtent.isSupportedCoordinate(mapData.centerZ)) return false;
        Entry next = new Entry(mapId.id(), unitId, anchor, mapData.centerX, mapData.centerZ, List.of(), mapData.scale);
        Entry existing = byMapId.get(mapId.id());
        if (existing != null) return existing.equals(next) || existing.equals(new Entry(
                mapId.id(), unitId, anchor, mapData.centerX, mapData.centerZ, List.of()));
        byMapId.put(mapId.id(), next);
        setDirty();
        return true;
    }

    public Optional<Entry> get(MapId mapId) {
        return mapId == null ? Optional.empty() : Optional.ofNullable(byMapId.get(mapId.id()));
    }

    public Optional<Entry> resolve(MapId mapId, MapItemSavedData mapData) {
        if (mapData == null) return Optional.empty();
        return get(mapId).filter(entry -> entry.matchesMapData(mapData));
    }

    public boolean validates(MapId mapId, UUID claimedUnitId, MapItemSavedData mapData) {
        return claimedUnitId != null
                && resolve(mapId, mapData).filter(entry -> entry.unitId().equals(claimedUnitId)).isPresent();
    }

    /**
     * Server-thread-only import handoff, not permission to create an empty sparse row.
     * All currently bound maps still use legacy terrain, including newly bound maps.
     * The future importer must durably save this intent before sparse publication,
     * freeze/snapshot the legacy terrain, and retain that terrain throughout recovery.
     * No completion/activation or legacy-retirement transition is exposed here.
     */
    Optional<SparseImportIntent> prepareSparseImport(MapId mapId, UUID claimedUnitId, MapItemSavedData mapData) {
        if (!acceptsMutation()) return Optional.empty();
        Entry source = resolve(mapId, mapData).filter(entry -> entry.unitId().equals(claimedUnitId)).orElse(null);
        if (source == null) return Optional.empty();
        SparseImportIntent existing = sparseImports.get(mapId.id());
        if (existing != null) return existing.matches(source, mapData) ? Optional.of(existing) : Optional.empty();
        SparseImportIntent intent = new SparseImportIntent(UUID.randomUUID(), source, source.extent(mapData).scale(), mapData.locked);
        sparseImports.put(mapId.id(), intent);
        setDirty();
        return Optional.of(intent);
    }

    /** Absence means legacy import is required; it never proves a map is empty/new. */
    Optional<SparseImportIntent> sparseImportIntent(MapId mapId) {
        return mapId == null ? Optional.empty() : Optional.ofNullable(sparseImports.get(mapId.id()));
    }

    /** Persisted identity/extent handoff only; this is not a frozen terrain snapshot. */
    record SparseImportIntent(UUID ticket, Entry source, int scale, boolean locked) {
        SparseImportIntent {
            if (ticket == null || source == null || scale < 0 || scale > NexusMapExtent.MAX_SCALE
                    || (source.effectiveScale() >= 0 && source.effectiveScale() != scale)
                    || (source.effectiveScale() < 0 && scale > MapItemSavedData.MAX_SCALE)
                    || source.detailAncestors().size() > scale)
                throw new IllegalArgumentException("Invalid sparse import intent");
        }
        boolean matches(Entry binding, MapItemSavedData data) {
            return source.equals(binding) && source.matchesMapData(data)
                    && source.extent(data).scale() == scale && data.locked == locked;
        }
    }

    /**
     * Carries the server-owned anchor proof to a vanilla SCALE or LOCK result.
     * SCALE appends the source MapId to the bounded detail lineage; LOCK copies
     * the lineage because it does not introduce a finer historical level.
     */
    public boolean derive(MapId sourceMapId, MapItemSavedData sourceData, MapId resultMapId, MapItemSavedData resultData) {
        if (sourceData == null || resultData == null) return false;
        Entry source = resolve(sourceMapId, sourceData).orElse(null);
        if (source == null) return false;
        int delta = resultData.scale - sourceData.scale;
        if (delta < 0 || delta > 1) return false;
        return derive(sourceMapId, sourceData, resultMapId, resultData, source.extent(sourceData).scale() + delta);
    }

    /** Server-only extent transition; callers must also validate the physical recipe and anchor. */
    public boolean derive(MapId sourceMapId, MapItemSavedData sourceData, MapId resultMapId,
                          MapItemSavedData resultData, int resultScale) {
        Optional<Entry> prepared = prepareDerivation(sourceMapId, sourceData, resultMapId, resultData, resultScale);
        if (prepared.isEmpty()) return false;
        commitDerivation(prepared.get());
        return true;
    }

    /** Pure validation/construction; no MapId allocation or ledger writes. */
    Optional<Entry> prepareDerivation(MapId sourceMapId, MapItemSavedData sourceData, MapId resultMapId,
                                      MapItemSavedData resultData, int resultScale) {
        if (!acceptsMutation()) return Optional.empty();
        if (sourceMapId == null || sourceData == null || resultMapId == null || resultData == null
                || get(resultMapId).isPresent() || resultScale < 0 || resultScale > NexusMapExtent.MAX_SCALE
                || resultData.scale != Math.min(resultScale, MapItemSavedData.MAX_SCALE)) return Optional.empty();
        Entry source = resolve(sourceMapId, sourceData).orElse(null);
        if (source == null
                || !source.anchor().dimension().equals(resultData.dimension)
                || source.centerX() != resultData.centerX
                || source.centerZ() != resultData.centerZ) return Optional.empty();

        NexusMapExtent sourceExtent = source.extent(sourceData);
        List<Integer> detailAncestors;
        if (resultScale == sourceExtent.scale()) {
            if (sourceData.locked || !resultData.locked) return Optional.empty();
            detailAncestors = source.detailAncestors();
        } else if (resultScale == sourceExtent.scale() + 1) {
            if (sourceData.locked || resultData.locked || sourceExtent.expanded().isEmpty()
                    || source.detailAncestors().size() >= MAX_DETAIL_ANCESTORS) return Optional.empty();
            java.util.ArrayList<Integer> ancestry = new java.util.ArrayList<>(source.detailAncestors());
            ancestry.add(sourceMapId.id());
            detailAncestors = List.copyOf(ancestry);
        } else {
            return Optional.empty();
        }

        return Optional.of(new Entry(
                resultMapId.id(), source.unitId(), source.anchor(), source.centerX(), source.centerZ(), detailAncestors, resultScale));
    }

    /** Only a server-thread prepared, fresh transition may be committed. */
    void commitDerivation(Entry entry) {
        if (!acceptsMutation()) throw new IllegalStateException("Map bindings frozen for migration");
        if (byMapId.putIfAbsent(entry.mapId(), entry) != null)
            throw new IllegalStateException("Prepared map ID became occupied");
        setDirty();
    }

    private List<SparseImportIntent> importIntents() {
        return sparseImports.values().stream().sorted(java.util.Comparator.comparingInt(value -> value.source().mapId())).toList();
    }

    private List<Entry> entries() {
        return byMapId.values().stream().sorted(java.util.Comparator.comparingInt(Entry::mapId)).toList();
    }

    public record Entry(
            int mapId,
            UUID unitId,
            GlobalPos anchor,
            int centerX,
            int centerZ,
            List<Integer> detailAncestors,
            int effectiveScale) {
        public Entry {
            if (mapId < 0 || unitId == null || anchor == null || detailAncestors == null
                    || detailAncestors.size() > MAX_DETAIL_ANCESTORS
                    || effectiveScale < -1 || effectiveScale > NexusMapExtent.MAX_SCALE
                    || !NexusMapExtent.isSupportedCoordinate(centerX)
                    || !NexusMapExtent.isSupportedCoordinate(centerZ)
                    || (effectiveScale >= 0 && detailAncestors.size() > effectiveScale)) {
                throw new IllegalArgumentException("Invalid Nexus map binding");
            }
            LinkedHashSet<Integer> unique = new LinkedHashSet<>();
            for (Integer ancestor : detailAncestors) {
                if (ancestor == null || ancestor < 0 || ancestor == mapId || !unique.add(ancestor)) {
                    throw new IllegalArgumentException("Invalid Nexus map detail ancestry");
                }
            }
            detailAncestors = List.copyOf(unique);
        }

        /** Legacy entries have no authoritative scale until their actual map data is resolved. */
        public Entry(int mapId, UUID unitId, GlobalPos anchor, int centerX, int centerZ,
                     List<Integer> detailAncestors) {
            this(mapId, unitId, anchor, centerX, centerZ, detailAncestors, -1);
        }

        public Entry(int mapId, UUID unitId, GlobalPos anchor, int centerX, int centerZ) {
            this(mapId, unitId, anchor, centerX, centerZ, List.of());
        }

        public boolean matchesMapData(MapItemSavedData data) {
            return data != null && anchor.dimension().equals(data.dimension)
                    && centerX == data.centerX && centerZ == data.centerZ
                    && data.scale >= 0 && data.scale <= MapItemSavedData.MAX_SCALE
                    && detailAncestors.size() <= (effectiveScale < 0 ? data.scale : effectiveScale)
                    && (effectiveScale < 0 || data.scale == Math.min(effectiveScale, MapItemSavedData.MAX_SCALE));
        }

        public NexusMapExtent extent(MapItemSavedData data) {
            if (!matchesMapData(data)) throw new IllegalArgumentException("Map data does not match its binding");
            return new NexusMapExtent(centerX, centerZ, effectiveScale < 0 ? data.scale : effectiveScale);
        }

        public boolean matchesUnit(NexusSpaceUnitRecord unit) {
            return unit != null
                    && unit.id().equals(unitId)
                    && unit.isLodestoneAnchor()
                    && unit.status() == SpaceUnitStatus.ACTIVE
                    && unit.dimension().equals(anchor.dimension())
                    && unit.pos().equals(anchor.pos());
        }
    }
}
