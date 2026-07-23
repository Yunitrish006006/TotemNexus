package dev.totem.nexus.space;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in server tick wiring for Nexus-owned pending teleport sessions. */
public final class NexusTeleportLifecycle {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private NexusTeleportLifecycle() { }
    public static void register(NexusTeleportStartAuthority teleports) {
        if (!REGISTERED.compareAndSet(false, true)) return;
        ServerTickEvents.END_SERVER_TICK.register(server ->
                server.getPlayerList().getPlayers().forEach(teleports::tick));
    }
}
