package dev.totem.nexus;

import dev.totem.nexus.bootstrap.NexusAuthorityBootstrap;
import dev.totem.nexus.space.NexusFriendSavedData;
import dev.totem.nexus.space.NexusTeleportManual;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Nexus module entrypoint. */
public final class TotemNexus implements ModInitializer {
    public static final String MOD_ID = "totem-nexus";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        NexusFriendSavedData.registerLifecycle();
        NexusTeleportManual.register();
        NexusAuthorityBootstrap.register();
        LOGGER.info("TotemNexus authority activated with TotemCore-owned friendship state");
    }
}
