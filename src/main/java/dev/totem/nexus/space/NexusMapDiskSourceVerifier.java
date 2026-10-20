package dev.totem.nexus.space;

import com.mojang.datafixers.DataFixer;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestOutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.GZIPInputStream;

/** Read-only, worker-only source observation. No fsync, import receipt or activation authority. */
public final class NexusMapDiskSourceVerifier {
    static final long NATIVE_LIMIT = 4L << 20, DETAIL_LIMIT = 256L << 20;
    public record Source(Path path, DataFixTypes fixType, DataFixer fixer) {
        public Source { Objects.requireNonNull(path); Objects.requireNonNull(fixType); Objects.requireNonNull(fixer); }
    }
    record Observation(UUID ticket, int mapId, String nativeFileHash, String detailFileHash,
                       String nativeTerrainHash, String detailTerrainHash) { }
    /** One cold file, not a ticket, relationship proof, common T0 or activation authority. */
    record NativeObservation(NexusMapSourceSnapshot.Base base, String fileHash, String terrainHash) { }
    private NexusMapDiskSourceVerifier() { }

    static NativeObservation observeNative(Source source, MapId id, HolderLookup.Provider registries,
                                           BooleanSupplier alive) throws IOException {
        return observeNative(source, id, registries, alive, () -> { }, () -> { });
    }
    static NativeObservation observeNative(Source source, MapId id, HolderLookup.Provider registries,
                                           BooleanSupplier alive, AfterDecode beforeRead, AfterDecode afterDecode) throws IOException {
        if (id == null || id.id() < 0) throw new IllegalArgumentException("Invalid inventory native MapId");
        check(alive); beforeRead.run();
        final Parsed parsed;
        final NexusMapSourceSnapshot.Base base;
        try {
            parsed = read(source, NATIVE_LIMIT, 8L << 20, alive);
            base = new NexusMapSourceSnapshot.Base(id, decodeNative(parsed.data(), registries));
            if (!NexusMapExtent.isSupportedCoordinate(base.centerX) || !NexusMapExtent.isSupportedCoordinate(base.centerZ))
                throw new IOException("Unsupported native map center");
        } catch (CancellationException cancelled) { throw cancelled; }
        catch (RuntimeException malformed) { throw new IOException("Invalid inventory native source", malformed); }
        String terrain = baseHash(base);
        afterDecode.run();
        if (!parsed.hash().equals(fileHash(source.path(), NATIVE_LIMIT, alive))) throw new IOException("Native source changed during observation");
        check(alive);
        return new NativeObservation(base, parsed.hash(), terrain);
    }
    private static MapItemSavedData decodeNative(CompoundTag data, HolderLookup.Provider registries) throws IOException {
        byte[] colors = data.getByteArray("colors").orElseThrow(() -> new IOException("Missing native pixels"));
        if (colors.length != 16384) throw new IOException("Incomplete native pixel array");
        if (data.contains("scale") && data.getByte("scale").filter(v -> v >= 0 && v <= 4).isEmpty())
            throw new IOException("Unsupported native map scale");
        return MapItemSavedData.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), data)
                .result().orElseThrow(() -> new IOException("Incomplete native map decode"));
    }

    static Observation verify(Source nativeFile, Source detailFile, HolderLookup.Provider registries,
                              NexusMapSourceSnapshot expected, BooleanSupplier alive) throws IOException {
        return verify(nativeFile, detailFile, registries, expected, alive, () -> { });
    }
    static Observation verify(Source nativeFile, Source detailFile, HolderLookup.Provider registries,
                              NexusMapSourceSnapshot expected, BooleanSupplier alive, AfterDecode afterDecode) throws IOException {
        return verify(nativeFile, detailFile, registries, expected, alive, () -> { }, afterDecode);
    }
    static Observation verify(Source nativeFile, Source detailFile, HolderLookup.Provider registries,
                              NexusMapSourceSnapshot expected, BooleanSupplier alive, AfterDecode beforeRead,
                              AfterDecode afterDecode) throws IOException {
        check(alive);
        String nativeBytes, detailBytes;
        String wantedBase = baseHash(expected.base());
        String wantedDetail = detailHash(expected.detail().pages(), expected.detail().maps(), (out, p) ->
                page(out, p.id, p.x, p.z, p.scale, p.historical, p.colors()), alive);
        String actualBase;
        String actualDetail;
        try {
            beforeRead.run();
            var nativeRead = read(nativeFile, NATIVE_LIMIT, 8L << 20, alive);
            nativeBytes = nativeRead.hash();
            var map = decodeNative(nativeRead.data(), registries);
            if (!expected.intent().matches(expected.intent().source(), map)) throw new IOException("Native source metadata changed");
            actualBase = baseHash(new NexusMapSourceSnapshot.Base(new MapId(expected.base().mapId), map));
            var detailRead = read(detailFile, DETAIL_LIMIT, DETAIL_LIMIT, alive);
            detailBytes = detailRead.hash();
            var detail = NexusMapDetailSavedData.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),
                    detailRead.data())
                    .result().orElseThrow(() -> new IOException("Incomplete detail decode"));
            actualDetail = detail.sourceFingerprint(alive);
        } catch (CancellationException cancelled) { throw cancelled; }
        catch (RuntimeException malformed) { throw new IOException("Invalid terrain source", malformed); }
        if (!wantedBase.equals(actualBase) || !wantedDetail.equals(actualDetail)) throw new IOException("Disk terrain differs from T0 source");
        afterDecode.run();
        if (!nativeBytes.equals(fileHash(nativeFile.path(), NATIVE_LIMIT, alive))
                || !detailBytes.equals(fileHash(detailFile.path(), DETAIL_LIMIT, alive)))
            throw new IOException("Terrain source bytes changed during verification");
        check(alive);
        return new Observation(expected.intent().ticket(), expected.base().mapId, nativeBytes, detailBytes, actualBase, actualDetail);
    }
    private static void regular(Path path, long limit) throws IOException {
        BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!a.isRegularFile() || a.size() > limit) throw new IOException("Invalid or oversized source file");
    }
    private static InputStream input(Path path, long limit, BooleanSupplier alive) throws IOException {
        regular(path, limit);
        return new FilterInputStream(Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long used;
            private void account(int count) throws IOException {
                if (count > 0 && (used += count) > limit) throw new IOException("Source grew past quota");
            }
            @Override public int read() throws IOException { check(alive); int v = in.read(); account(v < 0 ? 0 : 1); return v; }
            @Override public int read(byte[] bytes, int off, int len) throws IOException {
                check(alive); int count = in.read(bytes, off, len); account(count); return count;
            }
        };
    }
    private static String fileHash(Path file, long limit, BooleanSupplier alive) throws IOException {
        var digest = digest(); byte[] buffer = new byte[65536];
        try (var input = input(file, limit, alive)) {
            int count; while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private record Parsed(CompoundTag data, String hash) { }
    private static Parsed read(Source source, long limit, long heap, BooleanSupplier alive) throws IOException {
        var digest = digest();
        try (var raw = new PushbackInputStream(new BufferedInputStream(new DigestInputStream(
                input(source.path(), limit, alive), digest)), 2)) {
            int first = raw.read(), second = raw.read();
            if (second >= 0) raw.unread(second); if (first >= 0) raw.unread(first);
            try (var decoded = new DataInputStream(first == 0x1f && second == 0x8b ? new GZIPInputStream(raw) : raw)) {
                var envelope = NbtIo.read(decoded, NbtAccounter.create(heap));
                if (decoded.read() != -1) throw new IOException("Trailing NBT source data");
                int version = envelope.contains("DataVersion") ? envelope.getInt("DataVersion")
                        .orElseThrow(() -> new IOException("Invalid source data version")) : 1343;
                int current = SharedConstants.getCurrentVersion().dataVersion().version();
                if (version > current) throw new IOException("Future native data version");
                var fixed = source.fixType().update(source.fixer(), envelope, version, current);
                if (!(fixed.get("data") instanceof CompoundTag data)) throw new IOException("Missing terrain payload");
                // Bind the observation to every byte from this exact opened stream,
                // including compressed read-ahead and any raw bytes after its member.
                byte[] drain = new byte[65536];
                while (raw.read(drain) != -1) { check(alive); }
                check(alive); return new Parsed(data, HexFormat.of().formatHex(digest.digest()));
            }
        }
    }
    static String baseHash(NexusMapSourceSnapshot.Base base) throws IOException {
        return hash(out -> {
            out.writeInt(0x4e584231); out.writeInt(base.mapId); out.writeInt(base.centerX); out.writeInt(base.centerZ);
            out.writeInt(base.scale); out.writeUTF(base.dimension.identifier().toString()); out.writeBoolean(base.locked);
            pixels(out, base.colors());
        });
    }
    static <P> String detailHash(Map<Integer, P> pages, Map<Integer, List<Integer>> maps,
                                 PageWriter<P> writer, BooleanSupplier alive) throws IOException {
        if (pages.size() > NexusMapDetailSavedData.MAX_WORLD_PAGES || maps.size() > NexusMapDetailSavedData.MAX_WORLD_PAGES)
            throw new IOException("Source snapshot metadata quota exceeded");
        return hash(out -> {
            out.writeInt(0x4e584431); out.writeInt(pages.size());
            for (var id : new TreeSet<>(pages.keySet())) { check(alive); writer.write(out, pages.get(id)); }
            out.writeInt(maps.size());
            for (var id : new TreeSet<>(maps.keySet())) {
                check(alive); var refs = maps.get(id); out.writeInt(id); out.writeInt(refs.size());
                for (int ref : refs) out.writeInt(ref);
            }
        });
    }
    static void page(DataOutputStream out, int id, int x, int z, int scale, boolean historical, ByteBuffer colors) throws IOException {
        out.writeInt(id); out.writeInt(x); out.writeInt(z); out.writeInt(scale); out.writeBoolean(historical); pixels(out, colors);
    }
    private static void pixels(DataOutputStream out, ByteBuffer source) throws IOException {
        ByteBuffer bytes = source.duplicate(); out.writeInt(bytes.remaining()); byte[] buffer = new byte[4096];
        while (bytes.hasRemaining()) { int count = Math.min(bytes.remaining(), buffer.length); bytes.get(buffer, 0, count); out.write(buffer, 0, count); }
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String hash(Writer writer) throws IOException {
        var digest = digest();
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) { writer.write(out); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void check(BooleanSupplier alive) { if (!alive.getAsBoolean()) throw new CancellationException("Source verification stopped"); }
    @FunctionalInterface interface PageWriter<P> { void write(DataOutputStream out, P page) throws IOException; }
    @FunctionalInterface interface AfterDecode { void run() throws IOException; }
    @FunctionalInterface private interface Writer { void write(DataOutputStream out) throws IOException; }
}
