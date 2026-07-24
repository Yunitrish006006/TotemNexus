package dev.totem.nexus.client;

import dev.totem.nexus.network.SpaceUnitMapPayload;
import net.minecraft.client.Minecraft;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Explicit client-side half of the map cutover. The module entrypoint does
 * not call this while DeadRecall still owns the live payload receiver.
 */
public final class NexusMapClientCutover {
    private static final AtomicBoolean ACTIVATED = new AtomicBoolean();

    private NexusMapClientCutover() { }

    public static void activate() {
        if (!ACTIVATED.compareAndSet(false, true)) return;
        NexusClientPayloadRegistration.registerSpaceUnitMap(NexusMapClientCutover::apply);
    }

    private static void apply(SpaceUnitMapPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        NexusMapScreen screen = NexusMapScreen.CURRENT;
        if (screen != null && screen.isFor(payload.sourceType(), payload.sourceUnitId())) {
            screen.apply(payload);
        } else {
            minecraft.setScreenAndShow(new NexusMapScreen(payload));
        }
    }
}
