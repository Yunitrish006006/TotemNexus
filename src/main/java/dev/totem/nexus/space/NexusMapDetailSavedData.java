package dev.totem.nexus.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import java.nio.ByteBuffer;
import java.util.*;

/** Map-owned sparse terrain pages. Index and authoritative pixels commit in one SavedData file. */
public final class NexusMapDetailSavedData extends SavedData {
    public static final int MAX_PAGES_PER_MAP = 293; // 289 fine pages plus four historical levels.
    public static final int MAX_WORLD_PAGES = 8192;
    private static final Codec<Page> PAGE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(p -> p.id),
            Codec.INT.fieldOf("x").forGetter(p -> p.x),
            Codec.INT.fieldOf("z").forGetter(p -> p.z),
            Codec.intRange(0, 4).fieldOf("scale").forGetter(p -> p.scale),
            Codec.BOOL.optionalFieldOf("historical",false).forGetter(p -> p.historical),
            Codec.BYTE_BUFFER.fieldOf("colors").forGetter(p -> ByteBuffer.wrap(p.colors))
    ).apply(i, (id,x,z,scale,historical,colors) -> new Page(id,x,z,scale,historical,copy(colors))));
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("map_id").forGetter(Entry::mapId),
            Codec.INT.listOf().fieldOf("pages").forGetter(Entry::pages)
    ).apply(i, Entry::new));
    public static final Codec<NexusMapDetailSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1,1).optionalFieldOf("version",1).forGetter(d -> 1),
            PAGE_CODEC.listOf().fieldOf("pages").forGetter(d -> List.copyOf(d.pages.values())),
            ENTRY_CODEC.listOf().fieldOf("maps").forGetter(d -> d.maps.entrySet().stream()
                    .map(e -> new Entry(e.getKey(), List.copyOf(e.getValue()))).toList())
    ).apply(i, NexusMapDetailSavedData::new));
    public static final SavedDataType<NexusMapDetailSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("totem","nexus_map_detail_v1"),
            NexusMapDetailSavedData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private final Map<Integer, Page> pages = new HashMap<>();
    private final Map<Integer, List<Integer>> maps = new HashMap<>();
    private final Map<Integer, Integer> references = new HashMap<>();
    public NexusMapDetailSavedData() { this(1,List.of(),List.of()); }
    private NexusMapDetailSavedData(int version, List<Page> savedPages, List<Entry> entries) {
        if (savedPages.size() > MAX_WORLD_PAGES) throw new IllegalArgumentException("Too many detail pages");
        savedPages.forEach(p -> pages.put(p.id, p));
        for (Entry e : entries) {
            if (e.mapId < 0 || e.pages.size() > MAX_PAGES_PER_MAP || new HashSet<>(e.pages).size() != e.pages.size())
                throw new IllegalArgumentException("Invalid map detail index");
            maps.put(e.mapId, new ArrayList<>(e.pages));
            e.pages.forEach(id -> references.merge(id,1,Integer::sum));
        }
    }
    public static NexusMapDetailSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }
    /** Missing pages safely fall back to the base map. Never infer other maps from their anchor. */
    public List<Page> pages(int mapId) {
        return maps.getOrDefault(mapId,List.of()).stream().map(pages::get).filter(Objects::nonNull).toList();
    }
    public void initialize(ServerLevel level, MapId mapId, MapItemSavedData base, NexusMapBindingSavedData.Entry binding) {
        if (maps.containsKey(mapId.id())) return;
        maps.put(mapId.id(), new ArrayList<>());
        if (!base.locked && binding != null) {
            var bindings = NexusMapBindingSavedData.loadCanonical(level.getServer().overworld().getDataStorage());
            for (int id : binding.detailAncestors()) {
                MapItemSavedData old = level.getMapData(new MapId(id));
                var proof = bindings.resolve(new MapId(id), old).orElse(null);
                if (old == null || proof == null || !proof.unitId().equals(binding.unitId())
                        || !proof.anchor().equals(binding.anchor()) || old.scale >= base.scale
                        || old.centerX != base.centerX || old.centerZ != base.centerZ) continue;
                add(level, mapId.id(), old.centerX, old.centerZ, old.scale, true, old.colors.clone());
            }
        }
        setDirty();
    }
    public void derive(ServerLevel level, MapId source, MapItemSavedData base,
                       NexusMapBindingSavedData.Entry binding, MapId result) {
        initialize(level, source, base, binding);
        var ids = new ArrayList<>(maps.get(source.id()));
        maps.put(result.id(), ids);
        ids.forEach(id -> references.merge(id,1,Integer::sum));
        // The current source is itself historical detail after SCALE, frozen at this operation.
        if (base.scale < 4) add(level, result.id(), base.centerX, base.centerZ, base.scale, true, base.colors.clone());
        setDirty();
    }
    private Page add(ServerLevel level, int owner, int x, int z, int scale, boolean historical, byte[] colors) {
        var ids = maps.computeIfAbsent(owner, ignored -> new ArrayList<>());
        if (pages.size() >= MAX_WORLD_PAGES || ids.size() >= MAX_PAGES_PER_MAP) return null;
        int id = level.getFreeMapId().id();
        Page page = new Page(id,x,z,scale,historical,colors);
        pages.put(id,page); ids.add(id); references.put(id,1); setDirty();
        return page;
    }
    public boolean record(ServerLevel level, int owner, int worldX, int worldZ, byte color) {
        int x = Math.floorDiv(worldX,128)*128 + 64, z = Math.floorDiv(worldZ,128)*128 + 64;
        Page page = pages(owner).stream().filter(p -> !p.historical && p.scale == 0 && p.x == x && p.z == z).findFirst().orElse(null);
        int index = Math.floorMod(worldX,128) + Math.floorMod(worldZ,128)*128;
        if (page != null && page.colors[index] == color) return false;
        if (page == null) page = add(level,owner,x,z,0,false,new byte[16384]);
        else if (references.getOrDefault(page.id,0) > 1) {
            Page previous = page;
            if (pages.size() >= MAX_WORLD_PAGES) return false;
            int id = level.getFreeMapId().id();
            page = new Page(id,x,z,0,false,previous.colors.clone());
            pages.put(id,page);
            var ids = maps.get(owner); ids.set(ids.indexOf(previous.id),id);
            references.merge(previous.id,-1,Integer::sum); references.put(id,1);
        }
        if (page == null) return false;
        page.colors[index] = color; page.revision++; setDirty(); return true;
    }
    private static byte[] copy(ByteBuffer source) {
        ByteBuffer b = source.duplicate();
        if (b.remaining() != 16384) throw new IllegalArgumentException("Invalid detail pixels");
        byte[] result = new byte[16384]; b.get(result); return result;
    }
    private record Entry(int mapId, List<Integer> pages) { }
    public static final class Page {
        public final int id, x, z, scale;
        public final boolean historical;
        private final byte[] colors;
        private long revision;
        Page(int id,int x,int z,int scale,boolean historical,byte[] colors) {
            if (id < 0 || colors.length != 16384) throw new IllegalArgumentException("Invalid detail page");
            this.id=id;this.x=x;this.z=z;this.scale=scale;this.historical=historical;this.colors=colors;
        }
        public byte color(int x,int z) { return colors[x+z*128]; }
        public long revision() { return revision; }
        public net.minecraft.network.protocol.game.ClientboundMapItemDataPacket packet() {
            return new net.minecraft.network.protocol.game.ClientboundMapItemDataPacket(new MapId(id),(byte)scale,true,
                    List.of(),new MapItemSavedData.MapPatch(0,0,128,128,colors.clone()));
        }
    }
}
