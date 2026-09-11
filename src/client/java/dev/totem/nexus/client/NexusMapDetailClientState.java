package dev.totem.nexus.client;

import dev.totem.nexus.network.NexusMapDetailPayload;
import dev.totem.nexus.network.RequestNexusMapDetailPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Session-only identities of historical vanilla map layers approved by the server. */
public final class NexusMapDetailClientState {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Map<Integer, List<Integer>> BY_MAP_ID = new HashMap<>();

    private NexusMapDetailClientState() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void request(int mapId) {
        if (mapId < 0 || !ClientPlayNetworking.canSend(RequestNexusMapDetailPayload.TYPE)) return;
        ClientPlayNetworking.send(new RequestNexusMapDetailPayload(mapId));
    }

    public static void accept(NexusMapDetailPayload payload) {
        if (payload == null) return;
        BY_MAP_ID.put(payload.mapId(), payload.ancestorMapIds());
    }

    public static List<Integer> ancestorMapIds(int mapId) {
        return BY_MAP_ID.getOrDefault(mapId, List.of());
    }

    public static void clear() {
        BY_MAP_ID.clear();
    }

    /** Deterministic client GameTest hook; production values still arrive only from the validated server payload. */
    public static void setForVisualTest(int mapId, List<Integer> ancestorMapIds) {
        accept(new NexusMapDetailPayload(mapId, ancestorMapIds));
    }
}
