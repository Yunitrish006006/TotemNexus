package dev.totem.nexus.space;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Immutable legacy detail data, including empty indexes and unreferenced pages.
 * Not a disk manifest, native base-map snapshot, import receipt or activation proof. */
final class NexusLegacyTerrainSnapshot {
    private final Map<Integer, Page> pages;
    private final Map<Integer, List<Integer>> maps;

    // Ownership transfer from the completed cursor: it must never mutate these maps again.
    NexusLegacyTerrainSnapshot(Map<Integer, Page> pages, Map<Integer, List<Integer>> maps) {
        this.pages = Collections.unmodifiableMap(pages);
        this.maps = Collections.unmodifiableMap(maps);
    }
    Map<Integer, Page> pages() { return pages; }
    Map<Integer, List<Integer>> maps() { return maps; }

    static final class Page {
        final int id, x, z, scale;
        final boolean historical;
        private final byte[] colors;
        Page(int id, int x, int z, int scale, boolean historical, byte[] colors) {
            if (id < 0 || scale < 0 || scale > 4 || colors.length != 16384)
                throw new IllegalArgumentException("Invalid legacy snapshot page");
            this.id = id; this.x = x; this.z = z; this.scale = scale; this.historical = historical;
            this.colors = colors.clone();
        }
        ByteBuffer colors() { return ByteBuffer.wrap(colors).asReadOnlyBuffer(); }
    }
}
