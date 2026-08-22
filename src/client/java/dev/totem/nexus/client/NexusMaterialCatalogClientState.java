package dev.totem.nexus.client;

import dev.totem.nexus.network.MaterialCatalogPayload;
import dev.totem.nexus.network.RequestMaterialCatalogPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Client cache for the server-authoritative Nexus material reference table. */
public final class NexusMaterialCatalogClientState {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static volatile MaterialCatalogPayload current = new MaterialCatalogPayload(0L, List.of());

    private NexusMaterialCatalogClientState() {
    }

    public static void register() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ClientPlayNetworking.registerGlobalReceiver(
                MaterialCatalogPayload.TYPE,
                (payload, context) -> context.client().execute(() -> current = payload)
        );
    }

    public static void requestRefresh() {
        if (ClientPlayNetworking.canSend(RequestMaterialCatalogPayload.TYPE)) {
            ClientPlayNetworking.send(new RequestMaterialCatalogPayload());
        }
    }

    public static MaterialCatalogPayload snapshot() {
        return current;
    }
}
