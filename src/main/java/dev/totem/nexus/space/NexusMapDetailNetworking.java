package dev.totem.nexus.space;

import dev.totem.nexus.network.NexusMapDetailPayload;
import dev.totem.nexus.network.RequestNexusMapDetailPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server-authoritative validation and vanilla-packet synchronization for historical map detail. */
public final class NexusMapDetailNetworking {
    private static final AtomicBoolean RECEIVER_REGISTERED = new AtomicBoolean();

    private NexusMapDetailNetworking() {
    }

    public static void registerReceiver() {
        if (!RECEIVER_REGISTERED.compareAndSet(false, true)) return;
        ServerPlayNetworking.registerGlobalReceiver(RequestNexusMapDetailPayload.TYPE,
                (payload, context) -> context.server().execute(() -> send(context.player(), payload.mapId())));
    }

    static void send(ServerPlayer player, int requestedMapId) {
        if (player == null || requestedMapId < 0) return;
        MapId mapId = new MapId(requestedMapId);
        TeleportInterfaceItemResolver.ResolvedInterface held = heldNexusMap(player, mapId);
        if (held == null) return;

        MapItemSavedData currentData = MapItem.getSavedData(mapId, player.level());
        if (currentData == null) return;
        NexusMapBindingSavedData bindings = NexusMapBindingSavedData.loadCanonical(
                player.level().getServer().overworld().getDataStorage());
        NexusMapBindingSavedData.Entry currentBinding = bindings.resolve(mapId, currentData)
                .filter(entry -> entry.unitId().equals(held.boundUnitId()))
                .orElse(null);
        if (currentBinding == null) return;

        sendVanillaUpdate(player, mapId, currentData);
        List<Integer> accepted = new ArrayList<>(currentBinding.detailAncestors().size());
        int previousScale = -1;
        for (int ancestorValue : currentBinding.detailAncestors()) {
            MapId ancestorId = new MapId(ancestorValue);
            MapItemSavedData ancestorData = MapItem.getSavedData(ancestorId, player.level());
            if (ancestorData == null || ancestorData.scale < 0 || ancestorData.scale >= currentData.scale
                    || ancestorData.scale <= previousScale) continue;
            NexusMapBindingSavedData.Entry ancestorBinding = bindings.resolve(ancestorId, ancestorData).orElse(null);
            if (ancestorBinding == null
                    || !ancestorBinding.unitId().equals(currentBinding.unitId())
                    || !ancestorBinding.anchor().equals(currentBinding.anchor())
                    || ancestorBinding.centerX() != currentBinding.centerX()
                    || ancestorBinding.centerZ() != currentBinding.centerZ()) continue;
            accepted.add(ancestorValue);
            previousScale = ancestorData.scale;
            sendVanillaUpdate(player, ancestorId, ancestorData);
        }

        ServerPlayNetworking.send(player, new NexusMapDetailPayload(mapId.id(), List.copyOf(accepted)));
    }

    private static TeleportInterfaceItemResolver.ResolvedInterface heldNexusMap(ServerPlayer player, MapId mapId) {
        for (InteractionHand hand : InteractionHand.values()) {
            TeleportInterfaceItemResolver.ResolvedInterface resolved =
                    TeleportInterfaceItemResolver.resolve(player, hand).orElse(null);
            if (resolved != null
                    && resolved.type() == TeleportInterfaceType.FILLED_MAP
                    && mapId.equals(resolved.mapId())) return resolved;
        }
        return null;
    }

    private static void sendVanillaUpdate(ServerPlayer player, MapId mapId, MapItemSavedData mapData) {
        var packet = mapData.getUpdatePacket(mapId, player);
        if (packet != null) player.connection.send(packet);
    }
}
