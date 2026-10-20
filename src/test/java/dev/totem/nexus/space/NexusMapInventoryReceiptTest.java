package dev.totem.nexus.space;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.totem.nexus.space.NexusMapInventoryReceipt.*;
import static org.junit.jupiter.api.Assertions.*;

class NexusMapInventoryReceiptTest {
    @TempDir Path directory;
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-000000000031");
    private static String h(int n) { return String.format("%064x", n); }
    private static NexusMapNativeBatchStaging.NativeSource row(int id, int raw, int semantic) {
        return new NexusMapNativeBatchStaging.NativeSource(id, 0, 0, 2, false, "minecraft:overworld",
                new NexusMapSourceStaging.Blob(NexusMapSourceStaging.Kind.NATIVE, h(raw), 23), h(semantic));
    }
    private static Receipt receipt() {
        var primary = new NexusMapSourceManifest.Manifest(TICKET, 31, h(1), h(2), h(3), h(4), h(5), h(6));
        return new Receipt(primary, new NexusMapSourceStaging.Blob(NexusMapSourceStaging.Kind.DETAIL, h(4), 27),
                Map.of(31, row(31, 3, 5), 32, row(32, 7, 8)), Set.of(32));
    }
    private void publish(Receipt receipt) throws IOException { NexusMapInventoryReceipt.publish(directory, receipt, () -> true); }
    private byte[] bytes() throws IOException { return Files.readAllBytes(path(directory, TICKET)); }
    private void malformed(byte[] data) throws Exception {
        byte[] checksum = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(data, data.length - 32));
        System.arraycopy(checksum, 0, data, data.length - 32, 32); Files.write(path(directory, TICKET), data);
        assertThrows(IOException.class, () -> read(directory, TICKET, () -> true));
        assertThrows(IOException.class, () -> publish(receipt())); assertArrayEquals(data, bytes());
    }
    @Test void durableRoundTripAndExactRetryAreCanonicalAndImmutable() throws Exception {
        assertTrue(read(directory, TICKET, () -> true).isEmpty()); var expected = receipt(); var steps = new ArrayList<Step>();
        NexusMapInventoryReceipt.publish(directory, expected, () -> true, steps::add);
        assertEquals(List.of(Step.CREATED, Step.WRITTEN, Step.FILE_FORCED, Step.DIRECTORY_FORCED), steps);
        byte[] before = bytes(); assertEquals(expected, read(directory, TICKET, () -> true).orElseThrow());
        steps.clear(); NexusMapInventoryReceipt.publish(directory, expected, () -> true, steps::add);
        assertEquals(List.of(Step.FILE_FORCED, Step.DIRECTORY_FORCED), steps); assertArrayEquals(before, bytes());
        var result = read(directory, TICKET, () -> true).orElseThrow();
        assertEquals(List.of(31, 32), new ArrayList<>(result.natives().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> result.natives().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.unprepared().clear());
    }
    @Test void receiptCopiesCallerCollectionsAndRejectsConflictingRetry() throws Exception {
        var original = receipt(); var rows = new HashMap<>(original.natives()); var pending = new HashSet<>(original.unprepared());
        var copy = new Receipt(original.primary(), original.detail(), rows, pending); rows.clear(); pending.clear(); assertEquals(original, copy);
        publish(original); byte[] before = bytes();
        var changed = new Receipt(original.primary(), original.detail(), original.natives(), Set.of());
        assertThrows(IOException.class, () -> publish(changed)); assertArrayEquals(before, bytes());
        var changedRows = new HashMap<>(original.natives()); changedRows.put(32, row(32, 7, 9));
        assertThrows(IOException.class, () -> publish(new Receipt(original.primary(), original.detail(), changedRows, original.unprepared())));
        assertArrayEquals(before, bytes());
    }
    @Test void structuralPrimaryDetailAndUnpreparedConflictsRefuse() {
        var r = receipt();
        assertThrows(IllegalArgumentException.class, () -> new Receipt(r.primary(), r.detail(), Map.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new Receipt(r.primary(), r.detail(), Map.of(32, row(32, 7, 8)), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new Receipt(r.primary(), r.detail(), r.natives(), Set.of(31)));
        assertThrows(IllegalArgumentException.class, () -> new Receipt(r.primary(), r.detail(), r.natives(), Set.of(999)));
        assertThrows(IllegalArgumentException.class, () -> new Receipt(r.primary(), new NexusMapSourceStaging.Blob(NexusMapSourceStaging.Kind.DETAIL, h(99), 1), r.natives(), r.unprepared()));
        assertThrows(IllegalArgumentException.class, () -> new Receipt(r.primary(), r.detail(), Map.of(31, row(31, 99, 5)), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new Receipt(r.primary(), r.detail(), Map.of(31, row(31, 3, 99)), Set.of()));
    }
    @Test void invalidNativeMetadataCannotBeSerialized() {
        var r = receipt(); var good = row(32, 7, 8);
        var invalid = List.of(
                new NexusMapNativeBatchStaging.NativeSource(32, Integer.MAX_VALUE, 0, 2, false, good.dimension(), good.blob(), good.terrainHash()),
                new NexusMapNativeBatchStaging.NativeSource(32, 0, 0, 5, false, good.dimension(), good.blob(), good.terrainHash()),
                new NexusMapNativeBatchStaging.NativeSource(32, 0, 0, 2, false, "bad DIMENSION", good.blob(), good.terrainHash()),
                new NexusMapNativeBatchStaging.NativeSource(32, 0, 0, 2, false, "a:" + "b".repeat(255), good.blob(), good.terrainHash()),
                new NexusMapNativeBatchStaging.NativeSource(32, 0, 0, 2, false, good.dimension(), r.detail(), good.terrainHash()),
                new NexusMapNativeBatchStaging.NativeSource(32, 0, 0, 2, false, good.dimension(), good.blob(), "bad"));
        for (var bad : invalid) assertThrows(IllegalArgumentException.class, () -> new Receipt(r.primary(), r.detail(), Map.of(31, r.natives().get(31), 32, bad), Set.of(32)));
        assertThrows(IllegalArgumentException.class, () -> new Receipt(r.primary(), r.detail(), Map.of(31, r.natives().get(31), 33, good), Set.of()));
    }
    @Test void validChecksumCannotHideUnsupportedVersionCountsOrTicket() throws Exception {
        publish(receipt()); byte[] valid = bytes();
        for (int offset : new int[]{0, 4, 8, 228}) {
            byte[] bad = valid.clone(); ByteBuffer.wrap(bad).putInt(offset, offset == 228 ? 65537 : 99); malformed(bad);
        }
        for (int count : new int[]{0, -1}) { byte[] bad = valid.clone(); ByteBuffer.wrap(bad).putInt(228, count); malformed(bad); }
    }
    @Test void validChecksumCannotHideNoncanonicalOrInvalidRows() throws Exception {
        publish(receipt()); byte[] valid = bytes(); int first = 232; int length = ByteBuffer.wrap(valid).getShort(first + 14) & 65535;
        int second = first + 88 + length;
        for (int kind = 0; kind < 8; kind++) {
            byte[] bad = valid.clone(); var b = ByteBuffer.wrap(bad);
            switch (kind) {
                case 0 -> b.putInt(second, 31);
                case 1 -> bad[first + 12] = 5;
                case 2 -> bad[first + 13] = 2;
                case 3 -> b.putInt(first + 4, Integer.MAX_VALUE);
                case 4 -> b.putShort(first + 14, (short) 257);
                case 5 -> bad[first + 16] = 'A';
                case 6 -> b.putLong(first + 16 + length, NexusMapSourceStaging.Kind.NATIVE.maxBytes + 1);
                case 7 -> bad[92] ^= 1;
            }
            malformed(bad);
        }
        byte[] bad = valid.clone(); ByteBuffer.wrap(bad).putInt(valid.length - 36, 31); malformed(bad);
    }
    @Test void truncationCorruptionOversizeAndTrailingBytesArePreserved() throws Exception {
        publish(receipt()); byte[] valid = bytes();
        for (int size : new int[]{0, 7, 231, valid.length - 1, valid.length + 1}) {
            byte[] bad = Arrays.copyOf(valid, size); Files.write(path(directory, TICKET), bad);
            assertThrows(IOException.class, () -> read(directory, TICKET, () -> true)); assertThrows(IOException.class, () -> publish(receipt())); assertArrayEquals(bad, bytes());
        }
        byte[] bad = valid.clone(); bad[bad.length - 1] ^= 1; Files.write(path(directory, TICKET), bad);
        assertThrows(IOException.class, () -> read(directory, TICKET, () -> true));
        try (var file = new RandomAccessFile(path(directory, TICKET).toFile(), "rw")) { file.setLength(MAX_BYTES + 1); }
        assertThrows(IOException.class, () -> read(directory, TICKET, () -> true)); assertEquals(MAX_BYTES + 1, Files.size(path(directory, TICKET)));
    }
    @Test void failureAtEachForceBoundaryRequiresExactCompleteRetry() throws Exception {
        for (Step failAt : Step.values()) {
            Path run = Files.createDirectory(directory.resolve(failAt.name()));
            assertThrows(UncertainCommit.class, () -> NexusMapInventoryReceipt.publish(run, receipt(), () -> true, step -> { if (step == failAt) throw new IOException("injected boundary failure"); }));
            if (failAt == Step.CREATED) {
                assertEquals(0, Files.size(path(run, TICKET))); assertThrows(IOException.class, () -> NexusMapInventoryReceipt.publish(run, receipt(), () -> true));
            } else {
                byte[] before = Files.readAllBytes(path(run, TICKET)); NexusMapInventoryReceipt.publish(run, receipt(), () -> true);
                assertEquals(receipt(), read(run, TICKET, () -> true).orElseThrow()); assertArrayEquals(before, Files.readAllBytes(path(run, TICKET)));
            }
        }
    }
    @Test void cancellationCannotAcknowledgeBeforeOrAfterPublication() throws Exception {
        assertThrows(CancellationException.class, () -> NexusMapInventoryReceipt.publish(directory, receipt(), () -> false)); assertFalse(Files.exists(path(directory, TICKET)));
        for (Step cancelAt : Step.values()) {
            Path run = Files.createDirectory(directory.resolve(cancelAt.name())); var alive = new AtomicBoolean(true);
            var failure = assertThrows(UncertainCommit.class, () -> NexusMapInventoryReceipt.publish(run, receipt(), alive::get, step -> { if (step == cancelAt) alive.set(false); }));
            assertInstanceOf(CancellationException.class, failure.getCause());
        }
        assertThrows(CancellationException.class, () -> read(directory, TICKET, () -> false));
    }
    @Test void postForceReplacementOrDeletionCannotAcknowledge() throws Exception {
        for (boolean delete : new boolean[]{false, true}) {
            Path run = Files.createDirectory(directory.resolve(Boolean.toString(delete)));
            assertThrows(UncertainCommit.class, () -> NexusMapInventoryReceipt.publish(run, receipt(), () -> true, step -> {
                if (step == Step.DIRECTORY_FORCED) {
                    if (delete) Files.delete(path(run, TICKET)); else Files.writeString(path(run, TICKET), "bad");
                }
            }));
        }
    }
    @Test void filesystemKindsAndMissingDirectoriesNeverBootstrapOrReplace() throws Exception {
        Path target = path(directory, TICKET); Files.createDirectory(target); assertThrows(IOException.class, () -> publish(receipt())); Files.delete(target);
        Path original = directory.resolve("untouched"); Files.writeString(original, "keep"); Files.createSymbolicLink(target, original);
        assertThrows(IOException.class, () -> publish(receipt())); assertEquals("keep", Files.readString(original)); Files.delete(target);
        Files.createSymbolicLink(target, directory.resolve("missing")); assertThrows(IOException.class, () -> read(directory, TICKET, () -> true));
        Path missing = directory.resolve("not-created"); assertThrows(IOException.class, () -> NexusMapInventoryReceipt.publish(missing, receipt(), () -> true)); assertFalse(Files.exists(missing));
        Path linked = directory.resolve("linked"); Files.createSymbolicLink(linked, directory);
        assertThrows(IOException.class, () -> NexusMapInventoryReceipt.publish(linked, receipt(), () -> true));
    }
    @Test void metadataCountBoundsRefuseBeforeTraversingUntrustedCollections() {
        var r = receipt(); Map<Integer, NexusMapNativeBatchStaging.NativeSource> tooMany = new AbstractMap<>() {
            public int size() { return NexusMapMigrationInventory.MAX_NATIVE_SOURCES + 1; }
            public Set<Entry<Integer, NexusMapNativeBatchStaging.NativeSource>> entrySet() { fail("Over-limit receipt traversed rows"); return Set.of(); }
        };
        assertThrows(IllegalArgumentException.class, () -> new Receipt(r.primary(), r.detail(), tooMany, Set.of()));
    }
}
