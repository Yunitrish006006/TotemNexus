package dev.totem.nexus.client;

import net.fabricmc.api.ClientModInitializer;

/** Client entrypoint reserved for the Space Unit map and teleport interfaces. */
public final class TotemNexusClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Client registrations move with their payload contracts during cutover.
    }
}
