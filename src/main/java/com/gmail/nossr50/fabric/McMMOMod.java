package com.gmail.nossr50.fabric;

import com.gmail.nossr50.event.EventBus;
import com.gmail.nossr50.event.SimpleEventBus;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (client + server) entry point for the mcMMO Fabric mod.
 *
 * <p>Replaces the legacy {@code com.gmail.nossr50.mcMMO} {@code JavaPlugin} and doubles
 * as the central service locator that the legacy code reached through the {@code mcMMO.p}
 * singleton. Keeping a single holder here lets the ~279 Bukkit-coupled files port
 * mechanically ({@code mcMMO.getX()} -> {@code McMMOMod.getX()}) instead of being rewired.
 *
 * <p>Lifecycle mapping (see CONVERSION_TODO.md Phase 1):
 * <ul>
 *   <li>{@code JavaPlugin#onEnable}  -> {@link ServerLifecycleEvents#SERVER_STARTING}
 *       ({@link #onServerStarting}) plus one-time {@link #onInitialize} registration.</li>
 *   <li>{@code JavaPlugin#onDisable} -> {@link ServerLifecycleEvents#SERVER_STOPPING}
 *       ({@link #onServerStopping}).</li>
 * </ul>
 *
 * <p>In singleplayer the integrated server starts/stops each time a world is opened/closed,
 * so per-world manager init and data save/teardown belong in the lifecycle handlers, not in
 * {@link #onInitialize} (which fires once at mod load). Manager wiring is filled in as each
 * subsystem is ported in later phases; the call sites are marked with {@code // PORT:}.
 */
public class McMMOMod implements ModInitializer {

    public static final String MOD_ID = "mcmmo";
    public static final Logger LOGGER = LoggerFactory.getLogger("mcMMO");

    private static volatile MinecraftServer server;

    /**
     * mcMMO's internal event bus (Phase 3). Replaces Bukkit's event system for mcMMO's own
     * {@code events/*} events. Created once at mod load and lives for the whole JVM: it holds
     * no per-world state, so subscriptions registered by ported subsystems survive across
     * singleplayer world open/close cycles. Emitted events are fired on the server thread.
     */
    private static final EventBus eventBus = new SimpleEventBus();

    @Override
    public void onInitialize() {
        LOGGER.info("mcMMO (Fabric) initializing.");

        // Register lifecycle hooks once at mod load. The handlers run every time the
        // (integrated) server starts/stops, which in singleplayer is per world session.
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        // PORT Phase 4: register Brigadier commands via CommandRegistrationCallback.

        // PORT Phase 3 (with Phase 10 skills): register the Fabric-native gameplay hooks that
        // drive the legacy listeners, routing each to the ported skill managers. Preferred
        // mapping (Fabric API event where one exists, Mixin otherwise):
        //   BlockListener   -> PlayerBlockBreakEvents.AFTER (+ Mixin for block-place/damage XP)
        //   EntityListener  -> ServerLivingEntityEvents.ALLOW_DAMAGE / AFTER_DEATH,
        //                      AttackEntityCallback; Mixin for projectile-launch bookkeeping
        //   PlayerListener  -> UseItemCallback / UseBlockCallback, ServerPlayConnectionEvents
        //   InventoryListener -> Mixin on the screen-handler slot-click path (no Fabric event)
        //   WorldListener/ChunkListener -> ServerChunkEvents; entity metadata cleanup on unload
        //   SelfListener    -> subscribes to this EventBus (mcMMO's own events), not a MC hook
        // The Mixin classes land under fabric/mixin and get added to mcmmo.mixins.json then.
    }

    /** Equivalent of {@code onEnable}: per-session init when a world's server starts. */
    private void onServerStarting(MinecraftServer startingServer) {
        server = startingServer;
        try {
            LOGGER.info("mcMMO enabling for server session.");
            // PORT Phase 8: load config files.
            // PORT Phase 10: register core skills / interaction maps.
            // PORT Phase 5: initialize per-world persistence + load online player profiles.
            // PORT Phase 11: schedule save/tick tasks via ServerTickEvents.
        } catch (Throwable t) {
            LOGGER.error("Error while enabling mcMMO for the server session", t);
        }
    }

    /** Equivalent of {@code onDisable}: per-session save + teardown when the server stops. */
    private void onServerStopping(MinecraftServer stoppingServer) {
        try {
            LOGGER.info("mcMMO server session stopping, saving and cleaning up data.");
            // PORT Phase 5: save all player profiles, then clear.
            // PORT Phase 10: finish in-progress alchemy brews.
            // PORT Phase 11: cancel scheduled tasks.
        } catch (Exception e) {
            LOGGER.error("Error while disabling mcMMO for the server session", e);
        } finally {
            server = null;
        }
    }

    /**
     * The active {@link MinecraftServer} for this world session, or {@code null} outside one
     * (e.g. at the title screen before a world is opened).
     */
    public static @Nullable MinecraftServer getServer() {
        return server;
    }

    /**
     * mcMMO's internal event bus. Never {@code null} — it exists from mod load onward,
     * independent of whether a world session is active.
     */
    public static @NotNull EventBus getEventBus() {
        return eventBus;
    }
}
