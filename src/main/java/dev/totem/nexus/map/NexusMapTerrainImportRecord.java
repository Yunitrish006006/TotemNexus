package dev.totem.nexus.map;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import static dev.totem.nexus.map.NexusImmutableMapIndex.*;
import static dev.totem.nexus.map.NexusMapRootRegistry.*;
import static dev.totem.nexus.map.NexusSparseMapStore.*;

/**
 * Durable exact import evidence. It describes normalized immutable pages and a completed
 * registry row, but does not import sources, activate a map, or authorize gameplay.
 */
final class NexusMapTerrainImportRecord {
    private static final int MANIFEST_MAGIC = 0x4e58494d, RECEIPT_MAGIC = 0x4e584943, VERSION = 1;
    private static final int MAX_PAGES = 8192, MAX_MANIFEST_BYTES = 1 << 20, MAX_RECEIPT_BYTES = 2048;
    private static final Comparator<Tile> TILE_ORDER = Comparator.comparingInt(Tile::x).thenComparingInt(Tile::z);

    record Manifest(UUID ticket, Identity identity, int scale, boolean locked, String sourceReceiptHash,
                    Map<Tile, String> pages, Root expectedRoot) {
        Manifest {
            Objects.requireNonNull(ticket); Objects.requireNonNull(identity); Objects.requireNonNull(pages); Objects.requireNonNull(expectedRoot);
            requireHash(sourceReceiptHash, "source receipt");
            if (pages.size() > MAX_PAGES)
                throw new IllegalArgumentException("Invalid import manifest bounds");
            new dev.totem.nexus.space.NexusMapExtent(identity.centerX(), identity.centerZ(), scale);
            var canonical = new TreeMap<Tile, String>(TILE_ORDER);
            for (var entry : pages.entrySet()) {
                Tile tile = Objects.requireNonNull(entry.getKey());
                String hash = Objects.requireNonNull(entry.getValue()); requireHash(hash, "page");
                if (canonical.put(tile, hash) != null) throw new IllegalArgumentException("Duplicate import tile");
            }
            pages = Collections.unmodifiableMap(canonical);
            if (!manifestRoot(pages).equals(expectedRoot)) throw new IllegalArgumentException("Import root does not match page manifest");
        }
        String hash() { return hex(digest(body(this))); }
        boolean matches(Map<Tile, Page> actual) {
            if (actual.size() != pages.size()) return false;
            for (var entry : actual.entrySet())
                if (!Objects.equals(pages.get(entry.getKey()), manifestPageHash(entry.getKey(), entry.getValue()))) return false;
            return true;
        }
    }
    record Receipt(Manifest manifest, Entry entry) {
        Receipt {
            Objects.requireNonNull(manifest); Objects.requireNonNull(entry);
            if (!entry.identity().equals(manifest.identity()) || entry.scale() != manifest.scale()
                    || entry.locked() != manifest.locked() || entry.revision() != 0
                    || !entry.root().equals(manifest.expectedRoot()) || !entry.previousRoot().equals(Root.empty())
                    || !entry.operation().equals(manifest.hash()))
                throw new IllegalArgumentException("Registry row does not complete the import manifest");
        }
    }
    private NexusMapTerrainImportRecord() { }

    static Manifest capture(UUID ticket, Identity identity, int scale, boolean locked, String sourceReceiptHash,
                            Map<Tile, Page> pages) {
        Objects.requireNonNull(pages);
        if (pages.size() > MAX_PAGES) throw new IllegalArgumentException("Too many import pages");
        var hashes = new TreeMap<Tile, String>(TILE_ORDER);
        for (var entry : pages.entrySet()) hashes.put(entry.getKey(), manifestPageHash(entry.getKey(), entry.getValue()));
        return new Manifest(ticket, identity, scale, locked, sourceReceiptHash, hashes, manifestRoot(hashes));
    }

    static Path manifestPath(Path directory, UUID ticket) { return directory.resolve("import-" + ticket + ".manifest"); }
    static Path receiptPath(Path directory, UUID ticket) { return directory.resolve("import-" + ticket + ".receipt"); }

    static Optional<Manifest> readManifest(Path directory, UUID ticket, BooleanSupplier alive) throws IOException {
        byte[] bytes = read(manifestPath(directory, ticket), MAX_MANIFEST_BYTES, alive);
        if (bytes == null) return Optional.empty();
        try {
            var in = verified(bytes, MANIFEST_MAGIC);
            UUID stored = new UUID(in.readLong(), in.readLong());
            if (!ticket.equals(stored)) throw new IOException("Import manifest ticket mismatch");
            int mapId = in.readInt(); UUID unit = new UUID(in.readLong(), in.readLong());
            int x = in.readInt(), z = in.readInt(), scale = in.readUnsignedByte(), locked = in.readUnsignedByte();
            if (locked > 1) throw new IOException("Invalid import lock flag");
            int dimensionLength = in.readUnsignedShort();
            if (dimensionLength < 1 || dimensionLength > 256) throw new IOException("Invalid import dimension length");
            byte[] dimension = in.readNBytes(dimensionLength);
            if (dimension.length != dimensionLength) throw new EOFException("Truncated import dimension");
            String source = hash(in); int count = in.readInt();
            if (count < 0 || count > MAX_PAGES) throw new IOException("Import page count exceeds limit");
            var pages = new TreeMap<Tile, String>(TILE_ORDER); Tile previous = null;
            for (int i = 0; i < count; i++) {
                check(alive); Tile tile = new Tile(in.readInt(), in.readInt()); String page = hash(in);
                if (previous != null && TILE_ORDER.compare(previous, tile) >= 0) throw new IOException("Noncanonical import tiles");
                previous = tile; pages.put(tile, page);
            }
            Root root = new Root(hash(in)); eof(in);
            return Optional.of(new Manifest(stored, new Identity(mapId, unit,
                    new String(dimension, StandardCharsets.US_ASCII), x, z), scale, locked == 1, source, pages, root));
        } catch (IllegalArgumentException malformed) { throw new IOException("Invalid import manifest", malformed); }
    }

    static Optional<Receipt> readReceipt(Path directory, Manifest manifest, BooleanSupplier alive) throws IOException {
        if (!readManifest(directory, manifest.ticket(), alive).filter(manifest::equals).isPresent())
            throw new IOException("Import receipt has no exact durable manifest");
        byte[] bytes = read(receiptPath(directory, manifest.ticket()), MAX_RECEIPT_BYTES, alive);
        if (bytes == null) return Optional.empty();
        try {
            var in = verified(bytes, RECEIPT_MAGIC);
            UUID ticket = new UUID(in.readLong(), in.readLong());
            if (!ticket.equals(manifest.ticket()) || !hash(in).equals(manifest.hash()))
                throw new IOException("Import receipt manifest mismatch");
            int length = in.readUnsignedShort();
            if (length < NexusMapRootRegistry.MIN_BYTES || length > NexusMapRootRegistry.MAX_BYTES)
                throw new IOException("Invalid import registry row length");
            byte[] row = in.readNBytes(length); if (row.length != length) throw new EOFException("Truncated import registry row");
            eof(in); Entry entry = NexusMapRootRegistry.decode(row, manifest.identity().mapId());
            if (entry == null) throw new IOException("Invalid import registry row");
            return Optional.of(new Receipt(manifest, entry));
        } catch (IllegalArgumentException malformed) { throw new IOException("Invalid import receipt", malformed); }
    }

    static void publishManifest(Path directory, Manifest manifest, BooleanSupplier alive) throws IOException {
        publish(manifestPath(directory, manifest.ticket()), encodedManifest(manifest), MAX_MANIFEST_BYTES, alive);
    }
    static void publishReceipt(Path directory, Receipt receipt, BooleanSupplier alive) throws IOException {
        if (!readManifest(directory, receipt.manifest().ticket(), alive).filter(receipt.manifest()::equals).isPresent())
            throw new IOException("Completion requires the exact durable import manifest");
        publish(receiptPath(directory, receipt.manifest().ticket()), encodedReceipt(receipt), MAX_RECEIPT_BYTES, alive);
    }

    private static byte[] encodedManifest(Manifest manifest) {
        return checked(MANIFEST_MAGIC, out -> {
            var identity = manifest.identity();
            out.writeLong(manifest.ticket().getMostSignificantBits()); out.writeLong(manifest.ticket().getLeastSignificantBits());
            out.writeInt(identity.mapId()); out.writeLong(identity.unitId().getMostSignificantBits()); out.writeLong(identity.unitId().getLeastSignificantBits());
            out.writeInt(identity.centerX()); out.writeInt(identity.centerZ()); out.writeByte(manifest.scale()); out.writeByte(manifest.locked() ? 1 : 0);
            byte[] dimension = identity.dimension().getBytes(StandardCharsets.US_ASCII); out.writeShort(dimension.length); out.write(dimension);
            bytes(out, manifest.sourceReceiptHash()); out.writeInt(manifest.pages().size());
            for (var entry : manifest.pages().entrySet()) { out.writeInt(entry.getKey().x()); out.writeInt(entry.getKey().z()); bytes(out, entry.getValue()); }
            bytes(out, manifest.expectedRoot().hash());
        });
    }
    private static byte[] encodedReceipt(Receipt receipt) {
        return checked(RECEIPT_MAGIC, out -> {
            out.writeLong(receipt.manifest().ticket().getMostSignificantBits()); out.writeLong(receipt.manifest().ticket().getLeastSignificantBits());
            bytes(out, receipt.manifest().hash()); byte[] row = NexusMapRootRegistry.encode(receipt.entry());
            out.writeShort(row.length); out.write(row);
        });
    }
    private static byte[] body(Manifest manifest) {
        byte[] encoded = encodedManifest(manifest); return Arrays.copyOf(encoded, encoded.length - 32);
    }
    private static byte[] checked(int magic, Writer writer) {
        try {
            var buffer = new ByteArrayOutputStream(); var out = new DataOutputStream(buffer);
            out.writeInt(magic); out.writeInt(VERSION); writer.write(out); out.flush();
            byte[] body = buffer.toByteArray(), result = Arrays.copyOf(body, body.length + 32);
            System.arraycopy(digest(body), 0, result, body.length, 32); return result;
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }
    private static DataInputStream verified(byte[] bytes, int magic) throws IOException {
        if (bytes.length < 40) throw new IOException("Truncated import record");
        int bodyLength = bytes.length - 32; byte[] expected = digest(Arrays.copyOf(bytes, bodyLength));
        if (!MessageDigest.isEqual(expected, Arrays.copyOfRange(bytes, bodyLength, bytes.length))) throw new IOException("Import record checksum mismatch");
        var in = new DataInputStream(new ByteArrayInputStream(bytes, 0, bodyLength));
        if (in.readInt() != magic || in.readInt() != VERSION) throw new IOException("Unsupported import record version");
        return in;
    }
    private static void publish(Path target, byte[] expected, int limit, BooleanSupplier alive) throws IOException {
        check(alive); directory(target.getParent()); if (expected.length > limit) throw new IOException("Import record exceeds limit");
        boolean mayExist = false;
        try {
            byte[] existing = read(target, limit, alive);
            if (existing != null && !Arrays.equals(existing, expected)) throw new IOException("Conflicting immutable import record");
            if (existing == null) {
                try (var file = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    mayExist = true; ByteBuffer data = ByteBuffer.wrap(expected); while (data.hasRemaining()) file.write(data);
                    check(alive); file.force(true);
                }
            } else {
                mayExist = true; try (var file = FileChannel.open(target, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { file.force(true); }
            }
            check(alive); try (var directory = FileChannel.open(target.getParent(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) { directory.force(true); }
            check(alive); if (!Arrays.equals(expected, Objects.requireNonNull(read(target, limit, alive)))) throw new IOException("Import record changed during publication");
        } catch (IOException | RuntimeException failure) {
            if (mayExist) throw new UncertainCommit(failure); throw failure;
        }
    }
    private static byte[] read(Path path, int limit, BooleanSupplier alive) throws IOException {
        check(alive); final BasicFileAttributes attributes;
        try { attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (NoSuchFileException absent) { return null; }
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.size() > limit) throw new IOException("Invalid import record metadata");
        int size = Math.toIntExact(attributes.size()); ByteBuffer data = ByteBuffer.allocate(size);
        try (var file = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (file.size() != size) throw new IOException("Import record changed before reading");
            while (data.hasRemaining()) { check(alive); if (file.read(data) < 0) throw new EOFException("Truncated import record"); }
            check(alive); if (file.size() != size) throw new IOException("Import record changed while reading");
        }
        return data.array();
    }
    private static void directory(Path path) throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) throw new IOException("Invalid import record directory");
    }
    private static void eof(DataInputStream in) throws IOException { if (in.read() != -1) throw new IOException("Trailing import record data"); }
    private static void bytes(DataOutputStream out, String hash) throws IOException { out.write(HexFormat.of().parseHex(hash)); }
    private static String hash(DataInputStream in) throws IOException { byte[] value = in.readNBytes(32); if (value.length != 32) throw new EOFException("Truncated hash"); return hex(value); }
    private static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }
    private static byte[] digest(byte[] bytes) { try { return MessageDigest.getInstance("SHA-256").digest(bytes); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private static void requireHash(String value, String name) { if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid " + name + " hash"); }
    private static void check(BooleanSupplier alive) { if (!alive.getAsBoolean()) throw new CancellationException("Import record cancelled"); }
    @FunctionalInterface private interface Writer { void write(DataOutputStream out) throws IOException; }
    static final class UncertainCommit extends IOException { UncertainCommit(Throwable cause) { super("Import record may be committed; exact retry required", cause); } }
}
