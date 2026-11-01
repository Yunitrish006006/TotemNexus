package dev.totem.nexus.space;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Optional;
import java.util.function.Function;

/** Resolves only the held MapId and its server-owned vanilla map data. */
public final class NexusHeldMapResolver {
    private NexusHeldMapResolver() { }

    public static Optional<ResolvedMap> resolve(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) return Optional.empty();
        return resolve(player.getItemInHand(hand),
                mapId -> MapItem.getSavedData(mapId, player.level()));
    }

    static Optional<ResolvedMap> resolve(
            ItemStack stack,
            Function<MapId, MapItemSavedData> nativeLookup) {
        if (stack == null || stack.isEmpty() || nativeLookup == null || !stack.is(Items.FILLED_MAP)) {
            return Optional.empty();
        }
        MapId mapId = stack.get(DataComponents.MAP_ID);
        if (mapId == null || mapId.id() < 0) return Optional.empty();
        MapItemSavedData nativeData = nativeLookup.apply(mapId);
        if (nativeData == null) return Optional.empty();
        try {
            return Optional.of(new ResolvedMap(mapId, nativeData));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record ResolvedMap(MapId mapId, MapItemSavedData nativeData) {
        public ResolvedMap {
            NexusMapRecordSavedData.NativeSnapshot.capture(mapId, nativeData);
        }
    }
}
