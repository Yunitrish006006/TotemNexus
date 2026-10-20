package dev.totem.nexus.space;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** Monotonic deletion guard, not a backup or a terrain-import completion receipt. */
public final class NexusMapBindingWitness {
    private static final byte[] CONTENT = ByteBuffer.allocate(8).putInt(0x4e584257).putInt(1).array();
    private NexusMapBindingWitness() {}

    static Path path(Path canonical) {
        return canonical.resolveSibling(canonical.getFileName() + ".witness");
    }

    /** Bounded cold-read probe; malformed/partial markers are never absence. */
    static boolean present(Path canonical) throws IOException {
        Path marker = path(canonical);
        final BasicFileAttributes attributes;
        try { attributes = Files.readAttributes(marker, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (NoSuchFileException absent) { return false; }
        if (!attributes.isRegularFile() || attributes.size() != CONTENT.length)
            throw new IOException("Invalid Nexus binding witness metadata");
        try (var channel = FileChannel.open(marker, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            var bytes = ByteBuffer.allocate(CONTENT.length + 1);
            while (bytes.hasRemaining() && channel.read(bytes) != -1) { }
            if (!Arrays.equals(CONTENT, Arrays.copyOf(bytes.array(), bytes.position())))
                throw new IOException("Invalid Nexus binding witness version/content");
        }
        return true;
    }

    record Expected(String hash) {
        Expected {
            if (hash == null || !hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid ledger fingerprint");
        }
    }

    /** Capture on the owner thread while ledger mutations are frozen; no filesystem IO. */
    static Expected capture(NexusMapBindingSavedData ledger) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var payload = (CompoundTag) NexusMapBindingSavedData.CODEC.encodeStart(NbtOps.INSTANCE, ledger).getOrThrow();
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                NbtIo.write(payload, output);
            }
            return new Expected(HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Cannot fingerprint Nexus binding metadata", impossible);
        }
    }

    /**
     * Worker-only migration barrier. The single world owner must freeze binding
     * mutations, join prior native saves and retain that freeze until this returns.
     * The existing data directory must already belong to a durably created world.
     * This serializes explicit witness writers, not native async saves. There is
     * no production establishment caller or activation in this foundation step.
     */
    static synchronized void establish(SavedDataStorage reader, Path canonical, HolderLookup.Provider registries,
                                       Expected expected) throws IOException {
        establish(reader, canonical, registries, expected, () -> {});
    }

    static synchronized void establish(SavedDataStorage reader, Path canonical, HolderLookup.Provider registries,
                                       Expected expected, DurabilityObserver observer) throws IOException {
        verify(reader, canonical, registries, expected);
        forceFile(canonical);
        forceDirectories(canonical);
        boolean markerMayExist = false;
        try {
            // Never truncate an existing witness, including a partial failed creation.
            markerMayExist = true;
            try (var channel = FileChannel.open(path(canonical), StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                var bytes = ByteBuffer.wrap(CONTENT);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            } catch (FileAlreadyExistsException existing) {
                if (!present(canonical)) throw new IOException("Nexus binding witness disappeared");
                forceFile(path(canonical));
            }
            observer.afterMarkerForce();
            forceDirectories(canonical);
            // No ack if the snapshot changed during the barrier despite the owner contract.
            verify(reader, canonical, registries, expected);
        } catch (IOException | RuntimeException failure) {
            if (markerMayExist) throw new UncertainCommit(failure);
            throw failure;
        }
    }

    private static void verify(SavedDataStorage reader, Path canonical, HolderLookup.Provider registries,
                               Expected expected) throws IOException {
        var disk = NexusMapBindingStorage.readStrict(reader, NexusMapBindingSavedData.TYPE, canonical, registries);
        if (disk == null || !capture(disk).equals(expected)) throw new IOException("Nexus binding snapshot changed or missing");
    }
    private static void forceFile(Path file) throws IOException {
        try (var channel = FileChannel.open(file, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }
    private static void forceDirectories(Path canonical) throws IOException {
        // Native canonical identifier is data/totem/nexus_map_bindings.dat.
        Path namespace = canonical.toAbsolutePath().getParent();
        for (Path directory : new Path[] {namespace, namespace.getParent()}) {
            try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
        }
    }
    @FunctionalInterface interface DurabilityObserver { void afterMarkerForce() throws IOException; }
    static final class UncertainCommit extends IOException {
        UncertainCommit(Throwable cause) { super("Nexus binding witness may be committed; exact retry required", cause); }
    }
}
