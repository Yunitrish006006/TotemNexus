package dev.totem.nexus.space;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Opt-in lifecycle wiring for the map authority, to be called only at cutover. */
public final class NexusMapAuthorityLifecycle {
    private NexusMapAuthorityLifecycle() { }
    public static void register(NexusMapAuthority authority) {
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) -> authority.disconnect(listener.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> authority.tick(server.overworld().getGameTime()));
    }
}
