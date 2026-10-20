package dev.totem.nexus.space;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Worker-only durable observation record. Not source-byte storage, an import receipt or activation authority. */
final class NexusMapSourceManifest {
    private static final int MAGIC = 0x4e58534d, VERSION = 1;
    static final int BYTES = 252;
    record Manifest(UUID ticket, int mapId, String ledgerHash, String intentHash,
                    String nativeFileHash, String detailFileHash, String nativeTerrainHash, String detailTerrainHash) {
        Manifest {
            Objects.requireNonNull(ticket);
            if (mapId < 0) throw new IllegalArgumentException("Invalid source map identity");
            for (String hash : List.of(ledgerHash, intentHash, nativeFileHash, detailFileHash, nativeTerrainHash, detailTerrainHash))
                if (!hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid manifest fingerprint");
        }
    }
    private NexusMapSourceManifest() { }

    /** Capture under metadata ownership; hashes bind every intent field, including ordered ancestry. */
    static Manifest capture(NexusMapBindingWitness.Expected ledger, NexusMapBindingSavedData.SparseImportIntent intent,
                            NexusMapDiskSourceVerifier.Observation disk) {
        if (!intent.ticket().equals(disk.ticket()) || intent.source().mapId() != disk.mapId())
            throw new IllegalArgumentException("Observation belongs to another import ticket");
        var source = intent.source();
        String dimension = source.anchor().dimension().identifier().toString();
        if (dimension.length() > 256) throw new IllegalArgumentException("Unbounded source dimension");
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(0x4e585349); out.writeInt(1);
                out.writeLong(intent.ticket().getMostSignificantBits()); out.writeLong(intent.ticket().getLeastSignificantBits());
                out.writeInt(source.mapId());
                out.writeLong(source.unitId().getMostSignificantBits()); out.writeLong(source.unitId().getLeastSignificantBits());
                out.writeUTF(dimension);
                var pos = source.anchor().pos(); out.writeInt(pos.getX()); out.writeInt(pos.getY()); out.writeInt(pos.getZ());
                out.writeInt(source.centerX()); out.writeInt(source.centerZ()); out.writeInt(source.effectiveScale());
                out.writeInt(source.detailAncestors().size());
                for (int ancestor : source.detailAncestors()) out.writeInt(ancestor);
                out.writeInt(intent.scale()); out.writeBoolean(intent.locked());
            }
            return new Manifest(intent.ticket(), disk.mapId(), ledger.hash(), hex(hash(bytes.toByteArray())),
                    disk.nativeFileHash(), disk.detailFileHash(), disk.nativeTerrainHash(), disk.detailTerrainHash());
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    static Path path(Path directory, UUID ticket) { return directory.resolve("source-" + ticket + ".manifest"); }

    /** Missing is only discovery information, never permission to reconstruct a previously committed record. */
    static Optional<Manifest> read(Path directory, UUID ticket) throws IOException {
        checkDirectory(directory);
        Path target = path(directory, ticket);
        final BasicFileAttributes attributes;
        try { attributes = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (NoSuchFileException absent) { return Optional.empty(); }
        if (!attributes.isRegularFile() || attributes.size() != BYTES) throw new IOException("Invalid source manifest metadata");
        ByteBuffer bytes = ByteBuffer.allocate(BYTES + 1);
        try (var file = FileChannel.open(target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            while (bytes.hasRemaining() && file.read(bytes) != -1) { }
        }
        if (bytes.position() != BYTES) throw new IOException("Incomplete or oversized source manifest");
        byte[] payload = Arrays.copyOf(bytes.array(), BYTES - 32);
        if (!MessageDigest.isEqual(hash(payload), Arrays.copyOfRange(bytes.array(), BYTES - 32, BYTES)))
            throw new IOException("Source manifest checksum mismatch");
        var in = ByteBuffer.wrap(payload);
        if (in.getInt() != MAGIC || in.getInt() != VERSION) throw new IOException("Unsupported source manifest version");
        UUID storedTicket = new UUID(in.getLong(), in.getLong());
        int mapId = in.getInt();
        if (!ticket.equals(storedTicket) || mapId < 0) throw new IOException("Source manifest identity mismatch");
        return Optional.of(new Manifest(storedTicket, mapId, getHash(in), getHash(in), getHash(in), getHash(in), getHash(in), getHash(in)));
    }

    /**
     * Caller owns an already durably created dedicated directory and serializes external writers.
     * CREATE_NEW deliberately leaves a failed partial record in place: it must never be repaired or overwritten.
     * Cancellation after creation can leave a committed record; only an exact retry may acknowledge it.
     */
    static void publish(Path directory, Manifest expected, BooleanSupplier alive) throws IOException {
        publish(directory, expected, alive, stage -> { });
    }
    static synchronized void publish(Path directory, Manifest expected, BooleanSupplier alive, Probe probe) throws IOException {
        check(alive); checkDirectory(directory);
        var existing = read(directory, expected.ticket());
        if (existing.isPresent() && !existing.get().equals(expected)) throw new IOException("Conflicting immutable source manifest");
        Path target = path(directory, expected.ticket());
        boolean mayExist = false;
        try {
            check(alive);
            if (existing.isEmpty()) {
                // No replacement fallback, including another publisher winning CREATE_NEW.
                try (var file = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    mayExist = true; probe.at(Stage.CREATED); check(alive);
                    var bytes = ByteBuffer.wrap(encode(expected));
                    while (bytes.hasRemaining()) file.write(bytes);
                    probe.at(Stage.WRITTEN); check(alive);
                    file.force(true);
                }
            } else {
                mayExist = true;
                try (var file = FileChannel.open(target, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { file.force(true); }
            }
            probe.at(Stage.FILE_FORCED); check(alive);
            try (var dir = FileChannel.open(directory, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) { dir.force(true); }
            probe.at(Stage.DIRECTORY_FORCED); check(alive);
            if (!read(directory, expected.ticket()).filter(expected::equals).isPresent())
                throw new IOException("Source manifest changed during publication");
        } catch (IOException | RuntimeException failure) {
            if (mayExist) throw new UncertainCommit(failure);
            throw failure;
        }
    }
    private static void checkDirectory(Path directory) throws IOException {
        if (!Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isDirectory())
            throw new IOException("Source manifest directory is not a directory");
    }
    private static void check(BooleanSupplier alive) {
        if (!alive.getAsBoolean()) throw new CancellationException("Source manifest publication cancelled");
    }
    private static byte[] encode(Manifest manifest) {
        var out = ByteBuffer.allocate(BYTES);
        out.putInt(MAGIC).putInt(VERSION).putLong(manifest.ticket().getMostSignificantBits())
                .putLong(manifest.ticket().getLeastSignificantBits()).putInt(manifest.mapId());
        for (String value : List.of(manifest.ledgerHash(), manifest.intentHash(), manifest.nativeFileHash(),
                manifest.detailFileHash(), manifest.nativeTerrainHash(), manifest.detailTerrainHash()))
            out.put(HexFormat.of().parseHex(value));
        out.put(hash(Arrays.copyOf(out.array(), BYTES - 32)));
        return out.array();
    }
    private static String getHash(ByteBuffer in) { byte[] bytes = new byte[32]; in.get(bytes); return hex(bytes); }
    private static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }
    private static byte[] hash(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    enum Stage { CREATED, WRITTEN, FILE_FORCED, DIRECTORY_FORCED }
    @FunctionalInterface interface Probe { void at(Stage stage) throws IOException; }
    static final class UncertainCommit extends IOException {
        UncertainCommit(Throwable cause) { super("Source manifest may be committed; exact retry required", cause); }
    }
}
