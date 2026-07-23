package dev.totem.nexus.space;

import dev.totem.nexus.network.NexusClientboundPayloadRegistration;
import dev.totem.nexus.network.NexusTeleportCutover;

import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit, opt-in assembly for the Nexus map interaction slice. */
public final class NexusMapCutover {
    private static final AtomicBoolean ACTIVATED = new AtomicBoolean();
    private static final TeleportInterfaceSessionStore CONTEXTS = new TeleportInterfaceSessionStore();
    private NexusMapCutover() { }
    public static TeleportInterfaceSessionStore activate() {
        if (!ACTIVATED.compareAndSet(false, true)) return CONTEXTS;
        NexusClientboundPayloadRegistration.registerMap();
        NexusMapPayloadAuthority payloads = new NexusMapPayloadAuthority(
                (player, target) -> NexusMapQuote.unavailable(TeleportInterfaceType.COMPASS, "pending_context"));
        NexusMapOpenAuthority maps = new NexusMapOpenAuthority(CONTEXTS, payloads, new NexusMapQuoteAuthority());
        NexusMapOpenLifecycle.register(maps);
        NexusPlayerAnchorMapLifecycle.register(maps);
        NexusTeleportCutover.activate(CONTEXTS);
        return CONTEXTS;
    }
}
