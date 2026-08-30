package dev.totem.nexus.client;

import dev.totem.nexus.client.manual.NexusManualPageOverlay;
import dev.totem.nexus.client.manual.NexusSpecialistMaterialPageOverlay;
import net.fabricmc.api.ClientModInitializer;

/** Client entrypoint for the Nexus-owned Space Unit and death-node interfaces. */
public final class TotemNexusClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NexusManualPageOverlay.register();
        NexusSpecialistMaterialPageOverlay.register();
        NexusClientBootstrap.registerNetworking();
        NexusDeathNodeAdminClientInitializer.registerReceiver();
        NexusMaterialCatalogClientState.register();
        NexusArrayVisualizationClient.register();
    }
}
