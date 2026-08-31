package dev.totem.nexus.space;

import java.util.concurrent.atomic.AtomicBoolean;

/** Retired compatibility hook: interfaces must now open their bound Space Unit. */
public final class NexusPlayerAnchorMapLifecycle {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private NexusPlayerAnchorMapLifecycle() { }

    public static void register(NexusMapOpenAuthority maps) {
        REGISTERED.compareAndSet(false, true);
    }
}
