package dev.totem.nexus.space;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.nio.ByteBuffer;
import java.util.Objects;

/** One ticket's point-in-time terrain source. Not disk provenance, an import receipt or live authority. */
record NexusMapSourceSnapshot(NexusMapBindingSavedData.SparseImportIntent intent,
                              Base base, NexusLegacyTerrainSnapshot detail) {
    NexusMapSourceSnapshot {
        Objects.requireNonNull(intent); Objects.requireNonNull(base); Objects.requireNonNull(detail);
        var binding = intent.source();
        if (base.mapId != binding.mapId() || base.centerX != binding.centerX() || base.centerZ != binding.centerZ()
                || !base.dimension.equals(binding.anchor().dimension()) || base.locked != intent.locked()
                || base.scale != Math.min(intent.scale(), MapItemSavedData.MAX_SCALE))
            throw new IllegalArgumentException("Inconsistent map source snapshot");
    }
    static final class Base {
        final int mapId, centerX, centerZ, scale;
        final ResourceKey<Level> dimension;
        final boolean locked;
        private final byte[] colors;
        Base(MapId id, MapItemSavedData data) {
            if (id == null || id.id() < 0 || data == null || data.colors.length != 16384
                    || data.scale < 0 || data.scale > MapItemSavedData.MAX_SCALE)
                throw new IllegalArgumentException("Invalid native source map");
            mapId = id.id(); centerX = data.centerX; centerZ = data.centerZ; scale = data.scale;
            dimension = data.dimension; locked = data.locked; colors = data.colors.clone();
        }
        ByteBuffer colors() { return ByteBuffer.wrap(colors).asReadOnlyBuffer(); }
    }
}
