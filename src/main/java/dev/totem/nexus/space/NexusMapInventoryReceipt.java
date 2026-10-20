package dev.totem.nexus.space;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Durable saved-source inventory only, not an import receipt, common T0 or activation authority. */
final class NexusMapInventoryReceipt {
    private static final int MAGIC = 0x4e584952, VERSION = 1;
    static final long MAX_BYTES = 24L << 20;
    record Receipt(NexusMapSourceManifest.Manifest primary, NexusMapSourceStaging.Blob detail,
                   Map<Integer, NexusMapNativeBatchStaging.NativeSource> natives, Set<Integer> unprepared) {
        Receipt {
            Objects.requireNonNull(primary); Objects.requireNonNull(detail);
            if (natives.size() < 1 || natives.size() > NexusMapMigrationInventory.MAX_NATIVE_SOURCES
                    || unprepared.size() > NexusMapMigrationInventory.MAX_BINDINGS
                    || !natives.keySet().containsAll(unprepared) || unprepared.contains(primary.mapId())
                    || detail.kind() != NexusMapSourceStaging.Kind.DETAIL || !detail.hash().equals(primary.detailFileHash()))
                throw new IllegalArgumentException("Invalid saved inventory structure");
            var rows = new TreeMap<Integer, NexusMapNativeBatchStaging.NativeSource>();
            for (var entry : natives.entrySet()) {
                var row = Objects.requireNonNull(entry.getValue());
                if (entry.getKey() != row.mapId() || row.mapId() < 0 || row.scale() < 0 || row.scale() > 4
                        || !NexusMapExtent.isSupportedCoordinate(row.centerX()) || !NexusMapExtent.isSupportedCoordinate(row.centerZ())
                        || row.dimension() == null || row.dimension().length() > 256
                        || !row.dimension().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")
                        || row.blob().kind() != NexusMapSourceStaging.Kind.NATIVE || !validHash(row.terrainHash()))
                    throw new IllegalArgumentException("Invalid saved native row");
                rows.put(entry.getKey(), row);
            }
            var selected = rows.get(primary.mapId());
            if (selected == null || !selected.blob().hash().equals(primary.nativeFileHash())
                    || !selected.terrainHash().equals(primary.nativeTerrainHash()))
                throw new IllegalArgumentException("Inventory lost primary source identity");
            natives = Collections.unmodifiableMap(rows); unprepared = Collections.unmodifiableSet(new TreeSet<>(unprepared));
        }
    }
    private NexusMapInventoryReceipt() { }
    /** Worker-only construction; a live owner must supply its checked complete staging result. */
    static Receipt capture(NexusMapMigrationOwner.SourceCapture.StagedInventory proof) {
        var selected = proof.natives().sources().get(proof.primary().manifest().mapId());
        if (!proof.natives().metadata().hash().equals(proof.primary().manifest().ledgerHash())
                || selected == null || !proof.primary().nativeSource().equals(selected.blob()))
            throw new IllegalArgumentException("Inventory metadata/primary mismatch");
        return new Receipt(proof.primary().manifest(), proof.primary().detailSource(), proof.natives().sources(), proof.natives().unprepared());
    }
    static Path path(Path directory, UUID ticket) { return directory.resolve("inventory-" + Objects.requireNonNull(ticket) + ".receipt"); }
    static Optional<Receipt> read(Path directory, UUID ticket, BooleanSupplier alive) throws IOException {
        check(alive); directory(directory); Path target = path(directory, ticket);
        final BasicFileAttributes a;
        try { a = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (NoSuchFileException absent) { return Optional.empty(); }
        if (!a.isRegularFile() || a.size() > MAX_BYTES) throw new IOException("Invalid inventory receipt metadata");
        try (var channel = FileChannel.open(target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            var bounded = new BoundedInput(Channels.newInputStream(channel), alive);
            var digest = new DigestInputStream(new BufferedInputStream(bounded), digest());
            var in = new DataInputStream(digest);
            if (in.readInt() != MAGIC || in.readInt() != VERSION) throw new IOException("Unsupported inventory receipt version");
            UUID stored = new UUID(in.readLong(), in.readLong());
            if (!ticket.equals(stored)) throw new IOException("Inventory receipt ticket mismatch");
            var primary = new NexusMapSourceManifest.Manifest(stored, in.readInt(), hash(in), hash(in), hash(in), hash(in), hash(in), hash(in));
            var detail = new NexusMapSourceStaging.Blob(NexusMapSourceStaging.Kind.DETAIL, primary.detailFileHash(), in.readLong());
            int count = count(in, NexusMapMigrationInventory.MAX_NATIVE_SOURCES);
            if (count == 0) throw new IOException("Empty inventory receipt");
            var rows = new TreeMap<Integer, NexusMapNativeBatchStaging.NativeSource>(); int previous = -1;
            for (int i = 0; i < count; i++) {
                check(alive); int id = in.readInt(), x = in.readInt(), z = in.readInt(), scale = in.readUnsignedByte(), locked = in.readUnsignedByte();
                if (id <= previous || locked > 1) throw new IOException("Noncanonical inventory native rows");
                previous = id; int length = in.readUnsignedShort();
                if (length < 1 || length > 256) throw new IOException("Unbounded inventory dimension");
                byte[] name = new byte[length]; in.readFully(name); String dimension = new String(name, StandardCharsets.US_ASCII);
                long bytes = in.readLong(); String raw = hash(in), terrain = hash(in);
                rows.put(id, new NexusMapNativeBatchStaging.NativeSource(id, x, z, scale, locked != 0, dimension,
                        new NexusMapSourceStaging.Blob(NexusMapSourceStaging.Kind.NATIVE, raw, bytes), terrain));
            }
            int pending = count(in, NexusMapMigrationInventory.MAX_BINDINGS); var unprepared = new TreeSet<Integer>(); previous = -1;
            for (int i = 0; i < pending; i++) {
                check(alive); int id = in.readInt(); if (id <= previous) throw new IOException("Noncanonical unprepared source IDs");
                previous = id; unprepared.add(id);
            }
            byte[] expected = digest.getMessageDigest().digest(); digest.on(false);
            byte[] storedHash = new byte[32]; in.readFully(storedHash);
            if (!MessageDigest.isEqual(expected, storedHash) || in.read() != -1 || bounded.used != a.size())
                throw new IOException("Inventory receipt checksum/length mismatch");
            check(alive); return Optional.of(new Receipt(primary, detail, rows, unprepared));
        } catch (IllegalArgumentException | NullPointerException malformed) { throw new IOException("Invalid inventory receipt payload", malformed); }
    }
    static void publish(Path directory, Receipt expected, BooleanSupplier alive) throws IOException {
        publish(directory, expected, alive, step -> { });
    }
    /** Existing partial/corrupt records remain untouched; this is not a deletion/recreation guard. */
    static synchronized void publish(Path directory, Receipt expected, BooleanSupplier alive, Probe probe) throws IOException {
        check(alive); var existing = read(directory, expected.primary().ticket(), alive);
        if (existing.isPresent() && !existing.get().equals(expected)) throw new IOException("Conflicting immutable inventory receipt");
        Path target = path(directory, expected.primary().ticket()); boolean mayExist = false;
        try {
            check(alive);
            if (existing.isEmpty()) {
                try (var file = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    mayExist = true; probe.at(Step.CREATED); check(alive);
                    write(file, expected, alive); probe.at(Step.WRITTEN); check(alive); file.force(true);
                }
            } else {
                mayExist = true;
                try (var file = FileChannel.open(target, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { file.force(true); }
            }
            probe.at(Step.FILE_FORCED); check(alive);
            try (var dir = FileChannel.open(directory, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) { dir.force(true); }
            probe.at(Step.DIRECTORY_FORCED); check(alive);
            if (!read(directory, expected.primary().ticket(), alive).filter(expected::equals).isPresent())
                throw new IOException("Inventory receipt changed during publication");
        } catch (IOException | RuntimeException failure) {
            if (mayExist) throw new UncertainCommit(failure); throw failure;
        }
    }
    private static void write(FileChannel file, Receipt receipt, BooleanSupplier alive) throws IOException {
        var digest = digest(); var out = new DataOutputStream(new DigestOutputStream(new BufferedOutputStream(Channels.newOutputStream(file)), digest));
        var m = receipt.primary(); out.writeInt(MAGIC); out.writeInt(VERSION); out.writeLong(m.ticket().getMostSignificantBits()); out.writeLong(m.ticket().getLeastSignificantBits());
        out.writeInt(m.mapId());
        for (String hash : List.of(m.ledgerHash(), m.intentHash(), m.nativeFileHash(), m.detailFileHash(), m.nativeTerrainHash(), m.detailTerrainHash())) out.write(HexFormat.of().parseHex(hash));
        out.writeLong(receipt.detail().bytes()); out.writeInt(receipt.natives().size());
        for (var row : receipt.natives().values()) {
            check(alive); out.writeInt(row.mapId()); out.writeInt(row.centerX()); out.writeInt(row.centerZ()); out.writeByte(row.scale()); out.writeByte(row.locked() ? 1 : 0);
            byte[] dimension = row.dimension().getBytes(StandardCharsets.US_ASCII); out.writeShort(dimension.length); out.write(dimension);
            out.writeLong(row.blob().bytes()); out.write(HexFormat.of().parseHex(row.blob().hash())); out.write(HexFormat.of().parseHex(row.terrainHash()));
        }
        out.writeInt(receipt.unprepared().size()); for (int id : receipt.unprepared()) { check(alive); out.writeInt(id); }
        out.flush(); var trailer = ByteBuffer.wrap(digest.digest()); while (trailer.hasRemaining()) file.write(trailer);
    }
    private static int count(DataInputStream in, int limit) throws IOException {
        int count = in.readInt(); if (count < 0 || count > limit) throw new IOException("Inventory receipt count exceeds limit"); return count;
    }
    private static String hash(DataInputStream in) throws IOException { byte[] bytes = new byte[32]; in.readFully(bytes); return HexFormat.of().formatHex(bytes); }
    private static boolean validHash(String value) { return value != null && value.matches("[a-f0-9]{64}"); }
    private static void check(BooleanSupplier alive) { if (!alive.getAsBoolean()) throw new CancellationException("Inventory receipt cancelled"); }
    private static void directory(Path path) throws IOException {
        if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isDirectory()) throw new IOException("Invalid inventory receipt directory");
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static final class BoundedInput extends FilterInputStream {
        final BooleanSupplier alive; long used;
        BoundedInput(InputStream input, BooleanSupplier alive) { super(input); this.alive = alive; }
        private void account(int count) throws IOException { if (count > 0 && (used += count) > MAX_BYTES) throw new IOException("Inventory receipt grew past limit"); }
        @Override public int read() throws IOException { check(alive); int value = in.read(); account(value < 0 ? 0 : 1); return value; }
        @Override public int read(byte[] bytes, int off, int len) throws IOException { check(alive); int count = in.read(bytes, off, len); account(count); return count; }
    }
    enum Step { CREATED, WRITTEN, FILE_FORCED, DIRECTORY_FORCED }
    @FunctionalInterface interface Probe { void at(Step step) throws IOException; }
    static final class UncertainCommit extends IOException {
        UncertainCommit(Throwable cause) { super("Inventory receipt may be committed; exact retry required", cause); }
    }
}
