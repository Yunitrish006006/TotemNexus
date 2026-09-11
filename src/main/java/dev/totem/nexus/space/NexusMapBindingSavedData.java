package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
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
    public static final int DATA_VERSION = 2;
    public static final int MAX_DETAIL_ANCESTORS = MapItemSavedData.MAX_SCALE;

    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("map_id").forGetter(Entry::mapId),
            UUIDUtil.CODEC.fieldOf("space_unit_id").forGetter(Entry::unitId),
            GlobalPos.CODEC.fieldOf("anchor").forGetter(Entry::anchor),
            Codec.INT.fieldOf("center_x").forGetter(Entry::centerX),
            Codec.INT.fieldOf("center_z").forGetter(Entry::centerZ),
            Codec.INT.listOf().optionalFieldOf("detail_ancestors", List.of()).forGetter(Entry::detailAncestors)
    ).apply(instance, Entry::new));

    public static final Codec<NexusMapBindingSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(value -> value.dataVersion),
            ENTRY_CODEC.listOf().optionalFieldOf("maps", List.of()).forGetter(NexusMapBindingSavedData::entries)
    ).apply(instance, NexusMapBindingSavedData::new));

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

    public NexusMapBindingSavedData() { this(DATA_VERSION, List.of()); }

    /** Canonical-first loader with a one-way, non-destructive legacy copy. */
    public static synchronized NexusMapBindingSavedData loadCanonical(SavedDataStorage storage) {
        NexusMapBindingSavedData canonical = storage.get(TYPE);
        if (canonical != null) return canonical;
        NexusMapBindingSavedData legacy = storage.get(LEGACY_COMPATIBILITY_TYPE);
        if (legacy == null) return storage.computeIfAbsent(TYPE);
        NexusMapBindingSavedData migrated = new NexusMapBindingSavedData(legacy.dataVersion, legacy.entries());
        storage.set(TYPE, migrated);
        return migrated;
    }

    private NexusMapBindingSavedData(int dataVersion, List<Entry> entries) {
        this.dataVersion = Math.max(DATA_VERSION, dataVersion);
        for (Entry entry : entries) byMapId.put(entry.mapId(), entry);
    }

    public boolean bind(MapId mapId, UUID unitId, GlobalPos anchor, MapItemSavedData mapData) {
        if (mapId == null || unitId == null || anchor == null || mapData == null) return false;
        Entry next = new Entry(mapId.id(), unitId, anchor, mapData.centerX, mapData.centerZ, List.of());
        Entry existing = byMapId.get(mapId.id());
        if (existing != null) return existing.equals(next);
        byMapId.put(mapId.id(), next);
        setDirty();
        return true;
    }

    public Optional<Entry> get(MapId mapId) {
        return mapId == null ? Optional.empty() : Optional.ofNullable(byMapId.get(mapId.id()));
    }

    public Optional<Entry> resolve(MapId mapId, MapItemSavedData mapData) {
        if (mapData == null) return Optional.empty();
        return get(mapId).filter(entry -> entry.anchor().dimension().equals(mapData.dimension)
                && entry.centerX() == mapData.centerX
                && entry.centerZ() == mapData.centerZ);
    }

    public boolean validates(MapId mapId, UUID claimedUnitId, MapItemSavedData mapData) {
        return claimedUnitId != null
                && resolve(mapId, mapData).filter(entry -> entry.unitId().equals(claimedUnitId)).isPresent();
    }

    /**
     * Carries the server-owned anchor proof to a vanilla SCALE or LOCK result.
     * SCALE appends the source MapId to the bounded detail lineage; LOCK copies
     * the lineage because it does not introduce a finer historical level.
     */
    public boolean derive(MapId sourceMapId, MapItemSavedData sourceData, MapId resultMapId, MapItemSavedData resultData) {
        if (sourceMapId == null || sourceData == null || resultMapId == null || resultData == null
                || get(resultMapId).isPresent()) return false;
        Entry source = resolve(sourceMapId, sourceData).orElse(null);
        if (source == null
                || !source.anchor().dimension().equals(resultData.dimension)
                || source.centerX() != resultData.centerX
                || source.centerZ() != resultData.centerZ) return false;

        List<Integer> detailAncestors;
        if (resultData.scale == sourceData.scale) {
            detailAncestors = source.detailAncestors();
        } else if (resultData.scale == sourceData.scale + 1) {
            if (source.detailAncestors().size() >= MAX_DETAIL_ANCESTORS) return false;
            java.util.ArrayList<Integer> ancestry = new java.util.ArrayList<>(source.detailAncestors());
            ancestry.add(sourceMapId.id());
            detailAncestors = List.copyOf(ancestry);
        } else {
            return false;
        }

        byMapId.put(resultMapId.id(), new Entry(
                resultMapId.id(), source.unitId(), source.anchor(), source.centerX(), source.centerZ(), detailAncestors));
        setDirty();
        return true;
    }

    private List<Entry> entries() { return List.copyOf(byMapId.values()); }

    public record Entry(
            int mapId,
            UUID unitId,
            GlobalPos anchor,
            int centerX,
            int centerZ,
            List<Integer> detailAncestors) {
        public Entry {
            if (mapId < 0 || unitId == null || anchor == null || detailAncestors == null
                    || detailAncestors.size() > MAX_DETAIL_ANCESTORS) {
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

        public Entry(int mapId, UUID unitId, GlobalPos anchor, int centerX, int centerZ) {
            this(mapId, unitId, anchor, centerX, centerZ, List.of());
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
