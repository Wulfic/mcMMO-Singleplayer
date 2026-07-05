package com.gmail.nossr50.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (client + server) entry point for the mcMMO Fabric mod.
 *
 * <p>This replaces the legacy {@code com.gmail.nossr50.mcMMO} {@code JavaPlugin}
 * lifecycle. Phase 1 wires server lifecycle hooks; later phases register events,
 * commands, and persistence here as they are ported. See CONVERSION_TODO.md.
 */
public class McMMOMod implements ModInitializer {

    public static final String MOD_ID = "mcmmo";
    public static final Logger LOGGER = LoggerFactory.getLogger("mcMMO");

    @Override
    public void onInitialize() {
        LOGGER.info("mcMMO (Fabric) initializing — scaffold online.");
    }
}
