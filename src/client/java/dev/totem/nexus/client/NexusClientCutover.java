package dev.totem.nexus.client;

import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit client-side counterpart to the opt-in Nexus server map cutover. */
public final class NexusClientCutover {
    private static final AtomicBoolean ACTIVATED = new AtomicBoolean();
    private static final NexusClientState STATE = new NexusClientState();
    private NexusClientCutover() { }
    public static NexusClientState activate() {
        if (ACTIVATED.compareAndSet(false, true)) NexusClientPayloadBridge.register(STATE);
        return STATE;
    }
}
