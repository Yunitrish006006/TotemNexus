package dev.totem.nexus.client;

import net.fabricmc.api.ClientModInitializer;

/** Client entrypoint for the Nexus-owned Space Unit and death-node interfaces. */
public final class TotemNexusClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NexusClientBootstrap.registerNetworking();
        NexusDeathNodeAdminClientInitializer.registerReceiver();
    }
}
