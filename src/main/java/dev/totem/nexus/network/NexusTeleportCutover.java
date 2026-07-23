package dev.totem.nexus.network;

import dev.totem.nexus.space.NexusTeleportLifecycle;
import dev.totem.nexus.space.NexusTeleportStartAuthority;
import dev.totem.nexus.space.TeleportInterfaceSessionStore;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in StartTeleport receiver cutover; callers provide the map context store they own. */
public final class NexusTeleportCutover {
    private static final AtomicBoolean ACTIVATED = new AtomicBoolean();
    private NexusTeleportCutover() { }
    public static void activate(TeleportInterfaceSessionStore contexts) {
        if (!ACTIVATED.compareAndSet(false, true)) return;
        PayloadTypeRegistry.serverboundPlay().register(StartSpaceUnitTeleportPayload.TYPE, StartSpaceUnitTeleportPayload.CODEC);
        NexusTeleportStartAuthority authority = new NexusTeleportStartAuthority(contexts);
        ServerPlayNetworking.registerGlobalReceiver(StartSpaceUnitTeleportPayload.TYPE,
                (payload, context) -> context.server().execute(() -> authority.start(context.player(), payload.sourceType(), payload.sourceUnitId(), payload.targetUnitId())));
        NexusTeleportLifecycle.register(authority);
    }
}
