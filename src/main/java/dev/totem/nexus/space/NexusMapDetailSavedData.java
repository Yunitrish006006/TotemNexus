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
    private final Map<Integer, Map<Long, Page>> fineIndex = new HashMap<>();
    // Rebuildable LOD: deliberately absent from the SavedData codec. Old saves keep their format.
    private final LinkedHashMap<DerivedKey, Page> derived = new LinkedHashMap<>(32, .75f, true);
    private final LinkedHashMap<BuildKey, Build> builds = new LinkedHashMap<>();
    private static final int MAX_DERIVED = 256, MAX_BUILDS = 1024;
    private final int[] reductionCounts = new int[256];
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
        fineIndex.remove(result.id());
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
        var indexByTile = fineIndex(owner);
        Page page = indexByTile.get(tileKey(x,z));
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
        indexByTile.put(tileKey(x,z),page);
        page.colors[index] = color; page.revision++; setDirty(); return true;
    }

    public boolean known(int owner, int worldX, int worldZ) {
        Page p = fineIndex(owner).get(tileKey(Math.floorDiv(worldX,128)*128+64,Math.floorDiv(worldZ,128)*128+64));
        return p != null && (Byte.toUnsignedInt(p.colors[Math.floorMod(worldX,128)+Math.floorMod(worldZ,128)*128]) >>> 2) != 0;
    }

    private Map<Long, Page> fineIndex(int owner) {
        return fineIndex.computeIfAbsent(owner, id -> {
            Map<Long,Page> result = new HashMap<>();
            for (Page p : pages(id)) if (!p.historical && p.scale == 0) result.put(tileKey(p.x,p.z),p);
            return result;
        });
    }
    private static long tileKey(int x,int z) { return ((long)x << 32) | (z & 0xffffffffL); }

    /** Only recorded source pages intersecting the authorized viewport may create LOD work. */
    public List<Page> resolutionPages(ServerLevel level, int owner, int scale, int x, int z, int radius) {
        List<Page> result = new ArrayList<>();
        for (Page p : pages(owner)) {
            if (Math.abs((long)p.x-x)>=radius+(64L<<p.scale) || Math.abs((long)p.z-z)>=radius+(64L<<p.scale)) continue;
            if (p.historical) { if (p.scale>=scale) result.add(p); continue; }
            if (scale==0) { result.add(p); continue; }
            if (scale>=4) continue;
            int width=128<<scale;
            var key=new DerivedKey(owner,scale,Math.floorDiv(p.x-64,width)*width+width/2,
                    Math.floorDiv(p.z-64,width)*width+width/2);
            Page dest=derived.get(key);
            if (dest==null) {
                dest=new Page(level.getFreeMapId().id(),key.x,key.z,scale,false,new byte[16384]);
                derived.put(key,dest);
                while(derived.size()>MAX_DERIVED) {
                    var oldest=derived.entrySet().iterator(); var evicted=oldest.next(); oldest.remove();
                    Page removed=evicted.getValue(); builds.values().removeIf(b->b.dest==removed);
                }
            }
            if (!result.contains(dest)) result.add(dest);
            var buildKey=new BuildKey(owner,p.id,scale);
            if(dest.sources.getOrDefault(p.id,-1L)!=p.revision && !builds.containsKey(buildKey) && builds.size()<MAX_BUILDS)
                builds.put(buildKey,new Build(key,p,dest,p.revision));
        }
        return result;
    }

    /** At most 32768 source reads per tick, plus a 2 ms scheduling deadline. */
    public void buildResolutions() {
        int reads=32768; long deadline=System.nanoTime()+2_000_000L;
        while(!builds.isEmpty() && reads>0 && System.nanoTime()<deadline) {
            var it=builds.entrySet().iterator(); var entry=it.next(); Build b=entry.getValue(); it.remove();
            if(derived.get(b.key)!=b.dest) continue;
            // COW may have replaced the source while this job was waiting.
            if(fineIndex(b.key.owner).get(tileKey(b.source.x,b.source.z))!=b.source) continue;
            int size=1<<b.key.scale, side=128/size, steps=Math.min(64,Math.min(side*side-b.cursor,reads/(size*size)));
            for(int i=0;i<steps;i++,b.cursor++) {
                int sx=b.cursor%side*size, sz=b.cursor/side*size;
                byte color=dev.totem.nexus.map.MapResolution.reduce(b.source.colors,sx,sz,b.key.scale,reductionCounts);
                int dx=(b.source.x-64+sx-(b.dest.x-(64<<b.key.scale)))/size;
                int dz=(b.source.z-64+sz-(b.dest.z-(64<<b.key.scale)))/size;
                int pixel=dx+dz*128;
                if(b.dest.colors[pixel]!=color) { b.dest.colors[pixel]=color;b.dest.revision++; }
            }
            reads-=steps*size*size;
            if(b.cursor==side*side) b.dest.sources.put(b.source.id,b.revision);
            else builds.put(entry.getKey(),b); // Round-robin, never reset progress on repeated requests.
        }
    }

    private record DerivedKey(int owner,int scale,int x,int z) { }
    int pendingResolutionBuilds() { return builds.size(); }
    int derivedCacheSize() { return derived.size(); }
    private record BuildKey(int owner,int source,int scale) { }
    private static final class Build {
        final DerivedKey key; final Page source,dest; final long revision; int cursor;
        Build(DerivedKey key,Page source,Page dest,long revision) { this.key=key;this.source=source;this.dest=dest;this.revision=revision; }
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
        private final Map<Integer,Long> sources = new HashMap<>();
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
