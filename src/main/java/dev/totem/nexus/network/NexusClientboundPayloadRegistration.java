package dev.totem.nexus.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.concurrent.atomic.AtomicBoolean;

/** Server-side clientbound codec boundary for the opt-in Nexus map cutover. */
public final class NexusClientboundPayloadRegistration {
    private static final AtomicBoolean MAP_REGISTERED = new AtomicBoolean();
    private NexusClientboundPayloadRegistration() { }
    public static void registerMap() {
        if (MAP_REGISTERED.compareAndSet(false, true)) {
            PayloadTypeRegistry.clientboundPlay().register(SpaceUnitMapPayload.TYPE, SpaceUnitMapPayload.CODEC);
        }
    }
}
