package com.gmail.nossr50.fabric.client;

import com.gmail.nossr50.fabric.McMMOMod;
import net.fabricmc.api.ClientModInitializer;

/**
 * Client-only entry point for the mcMMO Fabric mod.
 *
 * <p>Reserved for the in-game config {@code Screen}, level-up HUD/action-bar
 * feedback, and other client UI ported in later phases (see CONVERSION_TODO.md
 * Phase 8). Kept as a stub so the {@code client} entrypoint and client mixin
 * environment are wired from the start.
 */
public class McMMOClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        McMMOMod.LOGGER.info("mcMMO (Fabric) client scaffold online.");
    }
}
