package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server-owned proof that a vanilla MapId was created for one Nexus anchor. */
public final class NexusMapBindingSavedData extends SavedData {
    public static final int DATA_VERSION = 1;

    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("map_id").forGetter(Entry::mapId),
            UUIDUtil.CODEC.fieldOf("space_unit_id").forGetter(Entry::unitId),
            GlobalPos.CODEC.fieldOf("anchor").forGetter(Entry::anchor),
            Codec.INT.fieldOf("center_x").forGetter(Entry::centerX),
            Codec.INT.fieldOf("center_z").forGetter(Entry::centerZ)
    ).apply(instance, Entry::new));

    public static final Codec<NexusMapBindingSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(value -> value.dataVersion),
            ENTRY_CODEC.listOf().optionalFieldOf("maps", List.of()).forGetter(NexusMapBindingSavedData::entries)
    ).apply(instance, NexusMapBindingSavedData::new));

    public static final SavedDataType<NexusMapBindingSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("deadrecall", "nexus_map_bindings"),
            NexusMapBindingSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final int dataVersion;
    private final Map<Integer, Entry> byMapId = new HashMap<>();

    public NexusMapBindingSavedData() { this(DATA_VERSION, List.of()); }

    private NexusMapBindingSavedData(int dataVersion, List<Entry> entries) {
        this.dataVersion = Math.max(DATA_VERSION, dataVersion);
        for (Entry entry : entries) byMapId.put(entry.mapId(), entry);
    }

    public boolean bind(MapId mapId, UUID unitId, GlobalPos anchor, MapItemSavedData mapData) {
        if (mapId == null || unitId == null || anchor == null || mapData == null) return false;
        Entry next = new Entry(mapId.id(), unitId, anchor, mapData.centerX, mapData.centerZ);
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
     * The new data must retain the exact persisted center and dimension.
     */
    public boolean derive(MapId sourceMapId, MapItemSavedData sourceData, MapId resultMapId, MapItemSavedData resultData) {
        if (resultMapId == null || resultData == null || get(resultMapId).isPresent()) return false;
        Entry source = resolve(sourceMapId, sourceData).orElse(null);
        if (source == null
                || !source.anchor().dimension().equals(resultData.dimension)
                || source.centerX() != resultData.centerX
                || source.centerZ() != resultData.centerZ) return false;
        byMapId.put(resultMapId.id(), new Entry(
                resultMapId.id(), source.unitId(), source.anchor(), source.centerX(), source.centerZ()));
        setDirty();
        return true;
    }

    private List<Entry> entries() { return List.copyOf(byMapId.values()); }

    public record Entry(int mapId, UUID unitId, GlobalPos anchor, int centerX, int centerZ) {
        public Entry {
            if (mapId < 0 || unitId == null || anchor == null) throw new IllegalArgumentException("Invalid Nexus map binding");
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
