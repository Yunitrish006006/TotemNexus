package dev.totem.nexus.network;

import dev.totem.nexus.space.TeleportArrayMaterialCatalog;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.concurrent.atomic.AtomicBoolean;

/** On-demand synchronization for the server-authoritative material reference table. */
public final class NexusMaterialCatalogNetworking {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    private NexusMaterialCatalogNetworking() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        PayloadTypeRegistry.serverboundPlay().register(
                RequestMaterialCatalogPayload.TYPE,
                RequestMaterialCatalogPayload.CODEC
        );
        PayloadTypeRegistry.clientboundPlay().register(
                MaterialCatalogPayload.TYPE,
                MaterialCatalogPayload.CODEC
        );
        ServerPlayNetworking.registerGlobalReceiver(
                RequestMaterialCatalogPayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        ServerPlayNetworking.send(context.player(), TeleportArrayMaterialCatalog.snapshot()))
        );
    }
}
