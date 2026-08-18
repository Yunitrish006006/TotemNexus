package dev.totem.nexus;

import dev.totem.nexus.bootstrap.NexusAuthorityBootstrap;
import dev.totem.nexus.space.NexusTeleportManual;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nexus module entrypoint. The 0.1.1 authority owns its complete server-side
 * surface; DeadRecall selects it atomically through its exact bundle pin.
 */
public final class TotemNexus implements ModInitializer {
    public static final String MOD_ID = "totem-nexus";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        NexusTeleportManual.register();
        NexusAuthorityBootstrap.register();
        LOGGER.info("TotemNexus authority and shared manual section activated");
    }
}
