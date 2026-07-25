package dev.totem.nexus;

import dev.totem.nexus.bootstrap.NexusAuthorityBootstrap;
import net.fabricmc.api.ModInitializer;

/**
 * Nexus module entrypoint. The 0.1.1 authority owns its complete server-side
 * surface; DeadRecall selects it atomically through its exact bundle pin.
 */
public final class TotemNexus implements ModInitializer {
    public static final String MOD_ID = "totem-nexus";

    @Override
    public void onInitialize() {
        NexusAuthorityBootstrap.register();
    }
}
