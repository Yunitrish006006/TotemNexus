package dev.totem.nexus.space;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Worker-only immutable raw source storage. Caller owns the durable directory and excludes external writers. */
final class NexusMapSourceStaging {
    enum Kind {
        NATIVE("native", 4L << 20), DETAIL("detail", 256L << 20);
        final String name; final long maxBytes;
        Kind(String name, long maxBytes) { this.name = name; this.maxBytes = maxBytes; }
    }
    record Limits(int maxFiles, long maxBytes) {
        Limits {
            if (maxFiles < 1 || maxFiles > 65536 || maxBytes < 1 || maxBytes > (1L << 40))
                throw new IllegalArgumentException("Invalid source staging quota");
        }
    }
    record Blob(Kind kind, String hash, long bytes) {
        Blob {
            Objects.requireNonNull(kind); validHash(hash);
            if (bytes < 0 || bytes > kind.maxBytes) throw new IllegalArgumentException("Invalid staged source size");
        }
    }
    private record Usage(int files, long bytes) { }
    private NexusMapSourceStaging() { }
    static Path path(Path directory, Kind kind, String hash) {
        Objects.requireNonNull(kind); validHash(hash);
        return directory.resolve(kind.name + "-" + hash + ".source");
    }
    private static void validHash(String hash) {
        if (hash == null || !hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid source fingerprint");
    }
    static Blob stage(Path directory, Path source, Kind kind, String hash, Limits limits, BooleanSupplier alive) throws IOException {
        return stage(directory, source, kind, hash, limits, alive, step -> { });
    }
    /**
     * An exact existing blob is reverified/forced without opening the original source.
     * New files publish by CREATE_NEW hard link, never by a rename that can replace another blob.
     * Unsupported hard links fail closed. Abandoned pending files consume persistent quota.
     */
    static synchronized Blob stage(Path directory, Path source, Kind kind, String hash, Limits limits,
                                    BooleanSupplier alive, Probe probe) throws IOException {
        Path target = path(directory, kind, hash); Objects.requireNonNull(limits);
        check(alive); directory(directory);
        Path pending = null; boolean pendingOwned = false, mayBePublished = false;
        try {
            if (exists(target)) {
                Blob existing = verify(directory, kind, hash, alive);
                mayBePublished = true;
                try (var file = FileChannel.open(target, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { file.force(true); }
                probe.at(Step.FILE_FORCED); check(alive); forceDirectory(directory);
                probe.at(Step.DIRECTORY_FORCED); check(alive);
                if (!verify(directory, kind, hash, alive).equals(existing)) throw new IOException("Staged source changed during retry");
                return existing;
            }
            var usage = usage(directory, limits, alive);
            long sourceBytes = regular(source, kind.maxBytes).size();
            // A crash can retain both pending and final names. Scans count both
            // even when they share an inode, so reserve the full two-name peak.
            if (usage.files > limits.maxFiles - 2 || sourceBytes > (limits.maxBytes - usage.bytes) / 2)
                throw new IOException("Source staging quota exceeded");
            long allowed = Math.min(kind.maxBytes, (limits.maxBytes - usage.bytes) / 2);
            check(alive); pending = directory.resolve(".pending-" + UUID.randomUUID());
            long copied = 0; var digest = digest();
            try (var file = FileChannel.open(pending, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                pendingOwned = true;
                probe.at(Step.CREATED); check(alive);
                try (var input = Files.newInputStream(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                    byte[] buffer = new byte[65536]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        check(alive); copied += count;
                        if (copied > allowed) throw new IOException("Source grew past staging quota");
                        digest.update(buffer, 0, count);
                        var bytes = ByteBuffer.wrap(buffer, 0, count); while (bytes.hasRemaining()) file.write(bytes);
                    }
                }
                if (copied != sourceBytes || !HexFormat.of().formatHex(digest.digest()).equals(hash))
                    throw new IOException("Source bytes differ from verified observation");
                probe.at(Step.COPIED); check(alive); file.force(true);
            }
            probe.at(Step.FILE_FORCED); check(alive);
            mayBePublished = true;
            Files.createLink(target, pending);
            probe.at(Step.PUBLISHED); check(alive);
            Files.delete(pending); pending = null;
            forceDirectory(directory); probe.at(Step.DIRECTORY_FORCED); check(alive);
            Blob result = verify(directory, kind, hash, alive);
            if (result.bytes != copied) throw new IOException("Published source size changed");
            return result;
        } catch (IOException | RuntimeException failure) {
            if (pending != null && pendingOwned) {
                try { Files.deleteIfExists(pending); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            if (mayBePublished) throw new UncertainCommit(failure);
            throw failure;
        }
    }
    /** Bounded cold verification of the exact bytes named by the expected observation hash. No source fallback. */
    static Blob verify(Path directory, Kind kind, String hash, BooleanSupplier alive) throws IOException {
        Path target = path(directory, kind, hash); check(alive); directory(directory);
        long size = regular(target, kind.maxBytes).size(), read = 0; var digest = digest();
        try (var input = Files.newInputStream(target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) {
                check(alive); read += count;
                if (read > kind.maxBytes) throw new IOException("Staged source grew past quota");
                digest.update(buffer, 0, count);
            }
        }
        check(alive);
        if (read != size || !HexFormat.of().formatHex(digest.digest()).equals(hash)) throw new IOException("Corrupt staged source");
        return new Blob(kind, hash, read);
    }
    private static Usage usage(Path directory, Limits limits, BooleanSupplier alive) throws IOException {
        int files = 0; long bytes = 0;
        try (var entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                check(alive); String name = entry.getFileName().toString();
                boolean nativeFile = name.matches("native-[a-f0-9]{64}\\.source");
                boolean detailFile = name.matches("detail-[a-f0-9]{64}\\.source");
                boolean pending = name.matches("\\.pending-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
                if (!nativeFile && !detailFile && !pending) throw new IOException("Unexpected source staging entry");
                bytes = Math.addExact(bytes, regular(entry, nativeFile ? Kind.NATIVE.maxBytes : Kind.DETAIL.maxBytes).size());
                if (++files > limits.maxFiles || bytes > limits.maxBytes) throw new IOException("Source staging quota exceeded");
            }
        }
        return new Usage(files, bytes);
    }
    private static boolean exists(Path target) throws IOException {
        try { Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); return true; }
        catch (NoSuchFileException absent) { return false; }
    }
    private static BasicFileAttributes regular(Path file, long limit) throws IOException {
        var attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() > limit) throw new IOException("Invalid source file metadata");
        return attributes;
    }
    private static void directory(Path path) throws IOException {
        if (!Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isDirectory())
            throw new IOException("Invalid source staging directory");
    }
    private static void forceDirectory(Path directory) throws IOException {
        try (var file = FileChannel.open(directory, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) { file.force(true); }
    }
    private static void check(BooleanSupplier alive) { if (!alive.getAsBoolean()) throw new CancellationException("Source staging cancelled"); }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    enum Step { CREATED, COPIED, FILE_FORCED, PUBLISHED, DIRECTORY_FORCED }
    @FunctionalInterface interface Probe { void at(Step step) throws IOException; }
    static final class UncertainCommit extends IOException {
        UncertainCommit(Throwable cause) { super("Staged source may be published; exact retry required", cause); }
    }
}
