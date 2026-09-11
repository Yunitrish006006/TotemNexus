package dev.totem.nexus.space;

import dev.totem.nexus.network.RequestAccessPlayersPayload;
import dev.totem.nexus.network.AccessPlayersPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Coalesces requests without stranding the latest client request; at most four queries/player/second. */
public final class NexusAccessQueries {
    private static final Map<UUID, RequestAccessPlayersPayload> pending = new HashMap<>();
    public static void enqueue(ServerPlayer player, RequestAccessPlayersPayload request) {
        pending.put(player.getUUID(), request);
    }
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 5 != 0 || pending.isEmpty()) return;
            var batch = Map.copyOf(pending); pending.clear();
            batch.forEach((id, request) -> {
                var player = server.getPlayerList().getPlayer(id);
                if (player != null && ServerPlayNetworking.canSend(player, AccessPlayersPayload.TYPE))
                    NexusSpaceUnitAuthority.accessPlayers(player, request).ifPresent(page -> ServerPlayNetworking.send(player,page));
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> pending.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> pending.clear());
    }
    private NexusAccessQueries() { }
}
