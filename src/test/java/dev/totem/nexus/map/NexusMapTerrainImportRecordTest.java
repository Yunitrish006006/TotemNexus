package dev.totem.nexus.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.totem.nexus.map.NexusImmutableMapIndex.*;
import static dev.totem.nexus.map.NexusMapRootRegistry.*;
import static dev.totem.nexus.map.NexusMapTerrainImportRecord.*;
import static dev.totem.nexus.map.NexusSparseMapStore.*;
import static org.junit.jupiter.api.Assertions.*;

class NexusMapTerrainImportRecordTest {
    @TempDir Path root;
    private static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-000000000071");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000072");
    private static final String SOURCE = "7".repeat(64);
    private static final Identity IDENTITY = new Identity(71, UNIT, "minecraft:overworld", 64, -64);
    private static final Tile LEFT = Tile.at(-129, 255), RIGHT = Tile.at(2048, -4097);

    private static Page page(long revision, int color, int... pixels) {
        byte[] colors = new byte[PIXELS], coverage = new byte[COVERAGE_BYTES];
        for (int pixel : pixels) { colors[pixel] = (byte) color; coverage[pixel / 8] |= (byte) (1 << (pixel % 8)); }
        return new Page(revision, colors, coverage);
    }
    private static Map<Tile, Page> pages() {
        var pages = new LinkedHashMap<Tile, Page>();
        pages.put(RIGHT, page(3, 20, 0, 19)); pages.put(LEFT, page(2, 16, 7)); return pages;
    }
    private static Manifest manifest() { return capture(TICKET, IDENTITY, 5, true, SOURCE, pages()); }
    private static Entry completed(Manifest manifest) {
        return new Entry(manifest.identity(), manifest.scale(), manifest.locked(), 0,
                manifest.expectedRoot(), Root.empty(), manifest.hash());
    }
    private Path records() throws Exception { Path path = root.resolve("records"); Files.createDirectory(path); return path; }

    @Test void canonicalManifestAndCompletionReceiptRoundTripAndRetryExactly() throws Exception {
        Path records = records(); Manifest manifest = manifest();
        Receipt pendingReceipt = new Receipt(manifest, completed(manifest));
        assertThrows(java.io.IOException.class, () -> publishReceipt(records, pendingReceipt, () -> true));
        assertFalse(Files.exists(receiptPath(records, TICKET)));
        assertEquals(List.of(LEFT, RIGHT), new ArrayList<>(manifest.pages().keySet()));
        assertTrue(manifest.matches(pages())); assertFalse(manifest.matches(Map.of(LEFT, page(2, 16, 7))));
        publishManifest(records, manifest, () -> true); byte[] first = Files.readAllBytes(manifestPath(records, TICKET));
        publishManifest(records, manifest, () -> true); assertArrayEquals(first, Files.readAllBytes(manifestPath(records, TICKET)));
        Manifest loaded = readManifest(records, TICKET, () -> true).orElseThrow(); assertEquals(manifest, loaded);
        Receipt receipt = new Receipt(loaded, completed(loaded)); publishReceipt(records, receipt, () -> true);
        byte[] completion = Files.readAllBytes(receiptPath(records, TICKET)); publishReceipt(records, receipt, () -> true);
        assertArrayEquals(completion, Files.readAllBytes(receiptPath(records, TICKET)));
        assertEquals(receipt, readReceipt(records, loaded, () -> true).orElseThrow());
    }

    @Test void manifestBindsSourceGeometryPixelsCoverageAndExpectedRoot() {
        Manifest original = manifest();
        Manifest empty = capture(TICKET, IDENTITY, 5, true, SOURCE, Map.of());
        assertEquals(Root.empty(), empty.expectedRoot()); assertTrue(empty.matches(Map.of()));
        assertDoesNotThrow(() -> new Receipt(empty, completed(empty)));
        assertNotEquals(original.hash(), capture(TICKET, IDENTITY, 5, true, "8".repeat(64), pages()).hash());
        assertNotEquals(original.hash(), capture(TICKET, IDENTITY, 5, true, SOURCE,
                Map.of(LEFT, page(2, 17, 7), RIGHT, pages().get(RIGHT))).hash());
        assertNotEquals(original.hash(), capture(TICKET, IDENTITY, 5, true, SOURCE,
                Map.of(LEFT, page(2, 16, 8), RIGHT, pages().get(RIGHT))).hash());
        assertThrows(IllegalArgumentException.class, () -> new Manifest(TICKET, IDENTITY, 5, true, SOURCE,
                original.pages(), Root.empty()));
    }

    @Test void pureManifestRootMatchesTheProductionImmutableIndex() throws Exception {
        Manifest manifest = manifest();
        var store = new NexusSparseMapStore(root.resolve("store"), new Limits(200, 4, 16));
        var index = new NexusImmutableMapIndex(store, 4); Root current = Root.empty();
        try {
            var ordered = new TreeMap<Tile, Page>(Comparator.comparingInt(Tile::x).thenComparingInt(Tile::z));
            ordered.putAll(pages());
            for (var entry : ordered.entrySet()) {
                var result = index.put(current, entry.getKey(), entry.getValue()).get(30, TimeUnit.SECONDS);
                assertEquals(Status.OK, result.status()); current = result.root().orElseThrow();
            }
            assertEquals(manifest.expectedRoot(), current);
        } finally { index.closeAsync().get(30, TimeUnit.SECONDS); }
    }

    @Test void conflictingImmutableRecordNeverOverwritesExistingBytes() throws Exception {
        Path records = records(); Manifest original = manifest(); publishManifest(records, original, () -> true);
        byte[] before = Files.readAllBytes(manifestPath(records, TICKET));
        Manifest conflict = capture(TICKET, IDENTITY, 5, true, "8".repeat(64), pages());
        assertThrows(java.io.IOException.class, () -> publishManifest(records, conflict, () -> true));
        assertArrayEquals(before, Files.readAllBytes(manifestPath(records, TICKET)));
        Receipt receipt = new Receipt(original, completed(original)); publishReceipt(records, receipt, () -> true);
        byte[] receiptBefore = Files.readAllBytes(receiptPath(records, TICKET));
        Manifest other = capture(TICKET, new Identity(72, UNIT, "minecraft:overworld", 64, -64), 5, true, SOURCE, pages());
        assertThrows(java.io.IOException.class, () -> publishReceipt(records, new Receipt(other, completed(other)), () -> true));
        assertArrayEquals(receiptBefore, Files.readAllBytes(receiptPath(records, TICKET)));
    }

    @Test void corruptTruncatedFutureAndTrailingRecordsFailClosedWithoutRepair() throws Exception {
        Path records = records(); Manifest manifest = manifest(); publishManifest(records, manifest, () -> true);
        Path path = manifestPath(records, TICKET); byte[] valid = Files.readAllBytes(path);
        for (byte[] malformed : List.of(Arrays.copyOf(valid, valid.length - 1), resigned(changed(valid, 7, (byte) 2)),
                changed(valid, valid.length - 1, (byte) (valid[valid.length - 1] ^ 1)), appended(valid))) {
            Files.write(path, malformed, StandardOpenOption.TRUNCATE_EXISTING);
            assertThrows(java.io.IOException.class, () -> readManifest(records, TICKET, () -> true));
            assertArrayEquals(malformed, Files.readAllBytes(path));
        }
    }

    @Test void completionReceiptRequiresTheExactFinalRegistryRow() {
        Manifest manifest = manifest(); Entry valid = completed(manifest); assertDoesNotThrow(() -> new Receipt(manifest, valid));
        assertThrows(IllegalArgumentException.class, () -> new Receipt(manifest, new Entry(IDENTITY, 5, true, 1,
                manifest.expectedRoot(), Root.empty(), manifest.hash())));
        assertThrows(IllegalArgumentException.class, () -> new Receipt(manifest, new Entry(IDENTITY, 5, true, 0,
                Root.empty(), Root.empty(), manifest.hash())));
        assertThrows(IllegalArgumentException.class, () -> new Receipt(manifest, new Entry(IDENTITY, 5, true, 0,
                manifest.expectedRoot(), Root.empty(), "9".repeat(64))));
    }

    @Test void cancelledPublicationIsEitherAbsentOrAnUncertainExactRetry() throws Exception {
        Path records = records(); Manifest manifest = manifest();
        assertThrows(java.util.concurrent.CancellationException.class, () -> publishManifest(records, manifest, () -> false));
        assertFalse(Files.exists(manifestPath(records, TICKET)));
        AtomicInteger checks = new AtomicInteger();
        assertThrows(UncertainCommit.class, () -> publishManifest(records, manifest, () -> checks.incrementAndGet() < 3));
        assertTrue(Files.exists(manifestPath(records, TICKET)));
        publishManifest(records, manifest, () -> true);
        assertEquals(manifest, readManifest(records, TICKET, () -> true).orElseThrow());
    }

    private static byte[] changed(byte[] source, int index, byte value) { byte[] copy = source.clone(); copy[index] = value; return copy; }
    private static byte[] resigned(byte[] source) throws Exception {
        byte[] copy = source.clone(), checksum = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(copy, copy.length - 32));
        System.arraycopy(checksum, 0, copy, copy.length - 32, 32); return copy;
    }
    private static byte[] appended(byte[] source) { byte[] copy = Arrays.copyOf(source, source.length + 1); copy[copy.length - 1] = 1; return copy; }
}
