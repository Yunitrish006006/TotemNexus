package dev.totem.nexus.space;

import java.util.concurrent.atomic.AtomicBoolean;

/** Retired compatibility hook; the unified Space Unit authority owns interface gestures. */
public final class NexusMapOpenLifecycle {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private NexusMapOpenLifecycle() { }

    public static void register(NexusMapOpenAuthority maps) {
        REGISTERED.compareAndSet(false, true);
    }
}
