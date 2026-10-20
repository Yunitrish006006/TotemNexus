package dev.totem.nexus.space;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;

/** Server-owned exploration proof. Coarse overview pixels never prove an extended destination. */
final class NexusMapRecordedCoverage {
    private NexusMapRecordedCoverage() { }

    static boolean covers(ServerPlayer player, MapId mapId, ResourceKey<Level> dimension, BlockPos pos) {
        if (player == null || mapId == null || dimension == null || pos == null) return false;
        var data = MapItem.getSavedData(mapId, player.level());
        var storage = player.level().getServer().overworld().getDataStorage();
        var binding = NexusMapBindingSavedData.loadCanonical(storage).resolve(mapId, data).orElse(null);
        if (binding == null || !data.dimension.equals(dimension)
                || !binding.extent(data).samplingBounds().contains(pos.getX(), pos.getZ())) return false;
        // Keep the existing painted vanilla footprint; extended terrain needs the exact fine sample.
        if (FilledMapCoverage.isDrawn(data, dimension, pos)) return true;
        return NexusMapDetailSavedData.available(storage)
                .map(detail -> detail.known(mapId.id(), pos.getX(), pos.getZ())).orElse(false);
    }
}
