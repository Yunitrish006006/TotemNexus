package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * MapId-owned Nexus extension metadata. Native map pixels and scale 0-4 remain
 * in {@link MapItemSavedData}; this index never grants Space Unit authority.
 */
public final class NexusMapRecordSavedData extends SavedData {
    public static final int DATA_VERSION = 1;
    public static final int MAX_LINEAGE = NexusMapExtent.MAX_SCALE + 1;
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private record EntryFields(
            int mapId,
            ResourceKey<Level> dimension,
            int centerX,
            int centerZ,
            int nativeScale,
            boolean locked,
            int logicalScale,
            List<Integer> lineage,
            Optional<String> detailRoot,
            Optional<String> coverageRoot,
            long revision) {
        DataResult<Entry> decode() {
            try {
                return DataResult.success(new Entry(mapId, dimension, centerX, centerZ, nativeScale, locked,
                        logicalScale, lineage, detailRoot, coverageRoot, revision));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() -> "Invalid Nexus MapId record");
            }
        }
    }

    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.<EntryFields>create(instance -> instance.group(
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("map_id").forGetter(EntryFields::mapId),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(EntryFields::dimension),
            Codec.intRange(NexusMapExtent.MIN_COORDINATE, NexusMapExtent.MAX_COORDINATE_EXCLUSIVE - 1)
                    .fieldOf("center_x").forGetter(EntryFields::centerX),
            Codec.intRange(NexusMapExtent.MIN_COORDINATE, NexusMapExtent.MAX_COORDINATE_EXCLUSIVE - 1)
                    .fieldOf("center_z").forGetter(EntryFields::centerZ),
            Codec.intRange(0, MapItemSavedData.MAX_SCALE).fieldOf("native_scale").forGetter(EntryFields::nativeScale),
            Codec.BOOL.fieldOf("locked").forGetter(EntryFields::locked),
            Codec.intRange(0, NexusMapExtent.MAX_SCALE).fieldOf("logical_scale").forGetter(EntryFields::logicalScale),
            Codec.INT.listOf().optionalFieldOf("lineage", List.of()).forGetter(EntryFields::lineage),
            Codec.STRING.optionalFieldOf("detail_root").forGetter(EntryFields::detailRoot),
            Codec.STRING.optionalFieldOf("coverage_root").forGetter(EntryFields::coverageRoot),
            Codec.LONG.fieldOf("revision").forGetter(EntryFields::revision)
    ).apply(instance, EntryFields::new)).comapFlatMap(EntryFields::decode,
            entry -> new EntryFields(entry.mapId(), entry.dimension(), entry.centerX(), entry.centerZ(),
                    entry.nativeScale(), entry.locked(), entry.logicalScale(), entry.lineage(), entry.detailRoot(),
                    entry.coverageRoot(), entry.revision()));

    private record Fields(int version, List<Entry> entries) {
        DataResult<NexusMapRecordSavedData> decode() {
            try {
                return DataResult.success(new NexusMapRecordSavedData(version, entries));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() -> "Invalid Nexus MapId record index");
            }
        }
    }

    public static final Codec<NexusMapRecordSavedData> CODEC = RecordCodecBuilder.<Fields>create(instance -> instance.group(
            Codec.intRange(DATA_VERSION, DATA_VERSION).fieldOf("data_version").forGetter(Fields::version),
            ENTRY_CODEC.listOf().optionalFieldOf("maps", List.of()).forGetter(Fields::entries)
    ).apply(instance, Fields::new)).comapFlatMap(Fields::decode,
            value -> new Fields(value.dataVersion, value.entries()));

    public static final SavedDataType<NexusMapRecordSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("totem", "nexus_map_records"),
            NexusMapRecordSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final int dataVersion;
    private final Map<Integer, Entry> byMapId = new HashMap<>();

    public NexusMapRecordSavedData() {
        this(DATA_VERSION, List.of());
    }

    private NexusMapRecordSavedData(int dataVersion, List<Entry> entries) {
        if (dataVersion != DATA_VERSION || entries == null) {
            throw new IllegalArgumentException("Unsupported Nexus map record data");
        }
        this.dataVersion = dataVersion;
        for (Entry entry : entries) {
            if (entry == null || byMapId.putIfAbsent(entry.mapId(), entry) != null) {
                throw new IllegalArgumentException("Duplicate Nexus MapId record");
            }
        }
    }

    /** Server-thread mutation primitive; compatible retries are idempotent. */
    public synchronized RegisterResult register(Entry entry) {
        if (entry == null) return RegisterResult.CONFLICT;
        Entry existing = byMapId.putIfAbsent(entry.mapId(), entry);
        if (existing == null) {
            setDirty();
            return RegisterResult.INSERTED;
        }
        return existing.equals(entry) ? RegisterResult.EXISTING : RegisterResult.CONFLICT;
    }

    /**
     * Atomically establishes the MapId baseline from a copied native snapshot.
     * Existing compatible Nexus extensions are preserved instead of downgraded.
     * Legacy item identity and {@link NexusMapBindingSavedData} are deliberately
     * not inputs: an absent record starts only from current native map data.
     */
    public BootstrapResult bootstrap(MapId mapId, MapItemSavedData nativeData) {
        try {
            return bootstrap(NativeSnapshot.capture(mapId, nativeData));
        } catch (IllegalArgumentException exception) {
            return BootstrapResult.conflict();
        }
    }

    public synchronized BootstrapResult bootstrap(NativeSnapshot snapshot) {
        if (snapshot == null) return BootstrapResult.conflict();
        Entry existing = byMapId.get(snapshot.mapId());
        if (existing != null) {
            return snapshot.matches(existing)
                    ? BootstrapResult.existing(existing)
                    : BootstrapResult.conflict();
        }
        Entry baseline = snapshot.baseline();
        byMapId.put(snapshot.mapId(), baseline);
        setDirty();
        return BootstrapResult.inserted(baseline);
    }

    public synchronized Optional<Entry> get(MapId mapId) {
        return mapId == null ? Optional.empty() : Optional.ofNullable(byMapId.get(mapId.id()));
    }

    public Optional<Entry> resolve(MapId mapId, MapItemSavedData nativeData) {
        return get(mapId).filter(entry -> entry.matchesNative(nativeData));
    }

    /**
     * Pure preflight for a lock or scale derivation. The returned child is tied
     * to the exact source snapshot and can only be committed while that source
     * remains unchanged.
     */
    public synchronized Optional<PreparedDerivation> prepareDerivation(
            MapId sourceMapId,
            MapItemSavedData sourceNative,
            MapId targetMapId,
            MapItemSavedData targetNative,
            int targetLogicalScale) {
        if (sourceMapId == null || targetMapId == null || sourceMapId.equals(targetMapId)
                || sourceNative == null || targetNative == null || byMapId.containsKey(targetMapId.id())) {
            return Optional.empty();
        }
        Entry source = byMapId.get(sourceMapId.id());
        if (source == null || !source.matchesNative(sourceNative) || source.revision() == Long.MAX_VALUE) {
            return Optional.empty();
        }
        try {
            if (!source.dimension().equals(targetNative.dimension)
                    || source.centerX() != targetNative.centerX
                    || source.centerZ() != targetNative.centerZ
                    || targetLogicalScale < source.logicalScale()
                    || targetLogicalScale > source.logicalScale() + 1
                    || targetNative.scale != Math.min(targetLogicalScale, MapItemSavedData.MAX_SCALE)) {
                return Optional.empty();
            }
            boolean lock = targetLogicalScale == source.logicalScale();
            if (lock
                    ? source.locked() || !targetNative.locked
                    : source.locked() || targetNative.locked) {
                return Optional.empty();
            }
            var lineage = new java.util.ArrayList<>(source.lineage());
            lineage.add(source.mapId());
            Entry child = new Entry(targetMapId.id(), source.dimension(), source.centerX(), source.centerZ(),
                    targetNative.scale, targetNative.locked, targetLogicalScale, lineage,
                    source.detailRoot(), source.coverageRoot(), source.revision() + 1);
            if (!child.matchesNative(targetNative)) return Optional.empty();
            return Optional.of(new PreparedDerivation(source, child));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** Publishes one prepared child MapId and its complete lineage atomically. */
    public synchronized boolean commitDerivation(PreparedDerivation prepared) {
        if (prepared == null || byMapId.containsKey(prepared.child().mapId())
                || !prepared.source().equals(byMapId.get(prepared.source().mapId()))) {
            return false;
        }
        byMapId.put(prepared.child().mapId(), prepared.child());
        setDirty();
        return true;
    }

    private synchronized List<Entry> entries() {
        return byMapId.values().stream()
                .sorted(java.util.Comparator.comparingInt(Entry::mapId))
                .toList();
    }

    public enum RegisterResult {
        INSERTED,
        EXISTING,
        CONFLICT
    }

    public record PreparedDerivation(Entry source, Entry child) {
        public PreparedDerivation {
            if (source == null || child == null || source.mapId() == child.mapId()
                    || child.revision() != source.revision() + 1
                    || !child.lineage().equals(append(source.lineage(), source.mapId()))) {
                throw new IllegalArgumentException("Invalid Nexus MapId derivation");
            }
        }

        private static List<Integer> append(List<Integer> lineage, int parent) {
            var result = new java.util.ArrayList<>(lineage);
            result.add(parent);
            return List.copyOf(result);
        }
    }

    public record BootstrapResult(RegisterResult result, Optional<Entry> entry) {
        public BootstrapResult {
            if (result == null || entry == null
                    || (result == RegisterResult.CONFLICT) == entry.isPresent()) {
                throw new IllegalArgumentException("Invalid Nexus map bootstrap result");
            }
        }

        private static BootstrapResult inserted(Entry entry) {
            return new BootstrapResult(RegisterResult.INSERTED, Optional.of(entry));
        }

        private static BootstrapResult existing(Entry entry) {
            return new BootstrapResult(RegisterResult.EXISTING, Optional.of(entry));
        }

        private static BootstrapResult conflict() {
            return new BootstrapResult(RegisterResult.CONFLICT, Optional.empty());
        }
    }

    /** Immutable copy of the native map geometry used during bootstrap. */
    public record NativeSnapshot(
            int mapId,
            ResourceKey<Level> dimension,
            int centerX,
            int centerZ,
            int nativeScale,
            boolean locked) {
        public NativeSnapshot {
            if (mapId < 0 || dimension == null
                    || !NexusMapExtent.isSupportedCoordinate(centerX)
                    || !NexusMapExtent.isSupportedCoordinate(centerZ)
                    || nativeScale < 0 || nativeScale > MapItemSavedData.MAX_SCALE) {
                throw new IllegalArgumentException("Invalid native map snapshot");
            }
        }

        public static NativeSnapshot capture(MapId mapId, MapItemSavedData nativeData) {
            if (mapId == null || nativeData == null) {
                throw new IllegalArgumentException("Missing native map data");
            }
            return new NativeSnapshot(mapId.id(), nativeData.dimension, nativeData.centerX, nativeData.centerZ,
                    nativeData.scale, nativeData.locked);
        }

        public boolean matches(Entry entry) {
            return entry != null
                    && mapId == entry.mapId()
                    && dimension.equals(entry.dimension())
                    && centerX == entry.centerX()
                    && centerZ == entry.centerZ()
                    && nativeScale == entry.nativeScale()
                    && locked == entry.locked();
        }

        public Entry baseline() {
            return new Entry(mapId, dimension, centerX, centerZ, nativeScale, locked, nativeScale,
                    List.of(), Optional.empty(), Optional.empty(), 0L);
        }
    }

    public record Entry(
            int mapId,
            ResourceKey<Level> dimension,
            int centerX,
            int centerZ,
            int nativeScale,
            boolean locked,
            int logicalScale,
            List<Integer> lineage,
            Optional<String> detailRoot,
            Optional<String> coverageRoot,
            long revision) {
        public Entry {
            if (mapId < 0 || dimension == null || lineage == null || detailRoot == null || coverageRoot == null
                    || !NexusMapExtent.isSupportedCoordinate(centerX)
                    || !NexusMapExtent.isSupportedCoordinate(centerZ)
                    || nativeScale < 0 || nativeScale > MapItemSavedData.MAX_SCALE
                    || logicalScale < nativeScale || logicalScale > NexusMapExtent.MAX_SCALE
                    || (logicalScale <= MapItemSavedData.MAX_SCALE && logicalScale != nativeScale)
                    || (logicalScale > MapItemSavedData.MAX_SCALE && nativeScale != MapItemSavedData.MAX_SCALE)
                    || lineage.size() > MAX_LINEAGE || revision < 0
                    || detailRoot.filter(root -> !validRoot(root)).isPresent()
                    || coverageRoot.filter(root -> !validRoot(root)).isPresent()) {
                throw new IllegalArgumentException("Invalid Nexus MapId record");
            }
            LinkedHashSet<Integer> unique = new LinkedHashSet<>();
            for (Integer ancestor : lineage) {
                if (ancestor == null || ancestor < 0 || ancestor == mapId || !unique.add(ancestor)) {
                    throw new IllegalArgumentException("Invalid Nexus MapId lineage");
                }
            }
            lineage = List.copyOf(unique);
        }

        public static Entry baseline(MapId mapId, MapItemSavedData nativeData) {
            return NativeSnapshot.capture(mapId, nativeData).baseline();
        }

        public boolean matchesNative(MapItemSavedData nativeData) {
            return nativeData != null
                    && dimension.equals(nativeData.dimension)
                    && centerX == nativeData.centerX
                    && centerZ == nativeData.centerZ
                    && nativeScale == nativeData.scale
                    && locked == nativeData.locked;
        }

        public NexusMapExtent extent(MapItemSavedData nativeData) {
            if (!matchesNative(nativeData)) throw new IllegalArgumentException("Native map data contradicts Nexus record");
            return new NexusMapExtent(centerX, centerZ, logicalScale);
        }

        private static boolean validRoot(String root) {
            return root != null && SHA_256.matcher(root).matches();
        }
    }
}
