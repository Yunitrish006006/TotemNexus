package dev.totem.nexus.space;

import dev.totem.nexus.network.RefreshSpaceUnitQuotePayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.concurrent.atomic.AtomicBoolean;

/** Registers the preserved refresh-quote wire contract during the Nexus cutover. */
public final class NexusSpaceUnitRefreshNetworking {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private NexusSpaceUnitRefreshNetworking() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        PayloadTypeRegistry.serverboundPlay().register(
                RefreshSpaceUnitQuotePayload.TYPE,
                RefreshSpaceUnitQuotePayload.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                RefreshSpaceUnitQuotePayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    if (NexusSpaceUnitAuthority.SOURCE_TYPE_LODESTONE.equals(payload.sourceType())) {
                        NexusSpaceUnitStructureRefresh.refresh(context.server(), payload.sourceUnitId());
                    }
                    NexusSpaceUnitStructureRefresh.refresh(context.server(), payload.targetUnitId());
                    NexusSpaceUnitAuthority.sendSpaceUnitMap(
                            context.player(),
                            payload.sourceType(),
                            payload.sourceUnitId()
                    );
                })
        );
    }
}
