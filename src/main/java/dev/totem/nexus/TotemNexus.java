package dev.totem.nexus;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nexus module entrypoint. Gameplay registration remains in the compatibility
 * bundle until its SavedData, payload and client contracts move together.
 */
public final class TotemNexus implements ModInitializer {
    public static final String MOD_ID = "totem-nexus";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("TotemNexus initialized without Remnant dependency");
    }
}
