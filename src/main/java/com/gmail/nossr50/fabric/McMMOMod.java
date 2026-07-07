package com.gmail.nossr50.fabric;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.treasure.TreasureConfig;
import com.gmail.nossr50.config.CoreSkillsConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.SoundConfig;
import com.gmail.nossr50.commands.McMMOCommands;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.database.FlatFileProfileStore;
import com.gmail.nossr50.database.ProfileStore;
import com.gmail.nossr50.event.EventBus;
import com.gmail.nossr50.event.SimpleEventBus;
import com.gmail.nossr50.fabric.listeners.BlockBreakListener;
import com.gmail.nossr50.fabric.listeners.CombatListener;
import com.gmail.nossr50.util.experience.FormulaManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.skills.SkillTools;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
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

    /**
     * Skill metadata/relationship registry (subskill↔parent, super-ability↔skill, tool maps,
     * localized name lists). Legacy code reached it via {@code mcMMO.p.getSkillTools()}. It holds
     * no per-world state and only reads the (English) locale bundle, so it is built lazily on
     * first access and lives for the whole JVM.
     */
    private static volatile SkillTools skillTools;

    /**
     * XP-curve engine (level ↔ experience conversions). Legacy code reached it via
     * {@code mcMMO.getFormulaManager()}. Like {@link #skillTools} it holds no per-world state and
     * only reads {@link ExperienceConfig}/{@link GeneralConfig} on demand, so it is built lazily on
     * first access and lives for the whole JVM.
     */
    private static volatile FormulaManager formulaManager;

    /**
     * The loaded config instances. Wired in when the concrete configs are loaded at server start
     * (PORT Phase 8 — {@code onServerStarting}); {@code null} before then. The ported enums/
     * {@link SkillTools} only touch these on in-game code paths, so lazy wiring is safe.
     */
    /**
     * The per-world player-data store (Phase 5). Bound at server start once the world save path is
     * known and cleared at server stop; {@code null} outside a world session (and in unit tests
     * that don't exercise persistence, where {@link com.gmail.nossr50.datatypes.player.PlayerProfile#save}
     * degrades to a no-op). Replaces the legacy {@code DatabaseManager} singleton.
     */
    private static volatile ProfileStore profileStore;

    private static volatile GeneralConfig generalConfig;
    private static volatile ExperienceConfig experienceConfig;
    private static volatile CoreSkillsConfig coreSkillsConfig;
    private static volatile RankConfig rankConfig;
    private static volatile SoundConfig soundConfig;
    private static volatile AdvancedConfig advancedConfig;
    private static volatile TreasureConfig treasureConfig;

    @Override
    public void onInitialize() {
        LOGGER.info("mcMMO (Fabric) initializing.");

        // Register lifecycle hooks once at mod load. The handlers run every time the
        // (integrated) server starts/stops, which in singleplayer is per world session.
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        // Phase 5 / Phase 3: per-player session lifecycle (load-on-join, save-on-quit). Registered
        // once here; the handlers fire per player and read the store bound at server start.
        PlayerSessionListener.register();

        // Phase 4: register the Brigadier command tree (/mcmmo, /mcstats, /addlevels, /addxp).
        McMMOCommands.register();

        // Phase 3: gameplay XP hooks — block-break gathering XP and mob-kill combat XP.
        BlockBreakListener.register();
        CombatListener.register();

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
            // Phase 8: load config files from <configDir>/mcmmo. Resolved via FabricLoader so the
            // configs live alongside every other mod's config, not inside the world save.
            ConfigBootstrap.loadAll(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID));
            // Phase 5: bind the per-world profile store under <worldRoot>/mcmmo/players/. Player
            // profiles load lazily on join (PlayerSessionListener), not eagerly here.
            final Path playersDir = startingServer.getSavePath(WorldSavePath.ROOT)
                    .resolve(MOD_ID).resolve("players");
            McMMOMod.setProfileStore(new FlatFileProfileStore(playersDir));
            // PORT Phase 10: register core skills / interaction maps.
            // PORT Phase 11: schedule save/tick tasks via ServerTickEvents.
        } catch (Throwable t) {
            LOGGER.error("Error while enabling mcMMO for the server session", t);
        }
    }

    /** Equivalent of {@code onDisable}: per-session save + teardown when the server stops. */
    private void onServerStopping(MinecraftServer stoppingServer) {
        try {
            LOGGER.info("mcMMO server session stopping, saving and cleaning up data.");
            // Phase 5: flush every online player's profile to disk, then drop the registry so the
            // next world session starts clean.
            UserManager.saveAll();
            UserManager.clearAll();
            McMMOMod.setProfileStore(null);
            // PORT Phase 10: finish in-progress alchemy brews.
            // PORT Phase 11: cancel scheduled tasks.
            ConfigBootstrap.unload();
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

    /**
     * The skill metadata registry. Never {@code null} — built lazily on first access (it needs
     * no world session, only the locale bundle). Replaces legacy {@code mcMMO.p.getSkillTools()}.
     */
    public static @NotNull SkillTools getSkillTools() {
        SkillTools local = skillTools;
        if (local == null) {
            synchronized (McMMOMod.class) {
                local = skillTools;
                if (local == null) {
                    local = new SkillTools();
                    skillTools = local;
                }
            }
        }
        return local;
    }

    /**
     * The XP-curve engine. Never {@code null} — built lazily on first access (it needs no world
     * session; it reads the configs on demand). Replaces legacy {@code mcMMO.getFormulaManager()}.
     */
    public static @NotNull FormulaManager getFormulaManager() {
        FormulaManager local = formulaManager;
        if (local == null) {
            synchronized (McMMOMod.class) {
                local = formulaManager;
                if (local == null) {
                    local = new FormulaManager();
                    formulaManager = local;
                }
            }
        }
        return local;
    }

    /**
     * The active per-world {@link ProfileStore}, or {@code null} outside a world session. Replaces
     * the legacy {@code mcMMO.getDatabaseManager()}.
     */
    public static @Nullable ProfileStore getProfileStore() {
        return profileStore;
    }

    /** Binds the per-world {@link ProfileStore} at server start (Phase 5). */
    public static void setProfileStore(@Nullable ProfileStore store) {
        profileStore = store;
    }

    /**
     * The loaded {@link GeneralConfig}, or {@code null} outside a world session (before the
     * configs are wired in at server start — PORT Phase 8). Replaces {@code mcMMO.p.getGeneralConfig()}.
     */
    public static @Nullable GeneralConfig getGeneralConfig() {
        return generalConfig;
    }

    /** Wires the loaded {@link GeneralConfig} at server start (PORT Phase 8). */
    public static void setGeneralConfig(@Nullable GeneralConfig config) {
        generalConfig = config;
    }

    /**
     * Whether RetroMode (1–1000) scaling is enabled, or {@code false} when the config is not yet
     * loaded (outside a world session / in unit tests → Standard scaling). Replaces the legacy
     * {@code mcMMO.isRetroModeEnabled()} static, which cached {@code generalConfig.getIsRetroMode()}
     * at enable time; a live null-safe read avoids a stale snapshot.
     */
    public static boolean isRetroModeEnabled() {
        final GeneralConfig config = generalConfig;
        return config != null && config.getIsRetroMode();
    }

    /**
     * The loaded {@link ExperienceConfig}, or {@code null} outside a world session (before the
     * configs are wired in at server start — PORT Phase 8). Replaces {@code ExperienceConfig.getInstance()}.
     */
    public static @Nullable ExperienceConfig getExperienceConfig() {
        return experienceConfig;
    }

    /** Wires the loaded {@link ExperienceConfig} at server start (PORT Phase 8). */
    public static void setExperienceConfig(@Nullable ExperienceConfig config) {
        experienceConfig = config;
    }

    /**
     * The loaded {@link CoreSkillsConfig}, or {@code null} outside a world session (before the
     * configs are wired in at server start — PORT Phase 8). Replaces {@code CoreSkillsConfig.getInstance()}.
     */
    public static @Nullable CoreSkillsConfig getCoreSkillsConfig() {
        return coreSkillsConfig;
    }

    /** Wires the loaded {@link CoreSkillsConfig} at server start (PORT Phase 8). */
    public static void setCoreSkillsConfig(@Nullable CoreSkillsConfig config) {
        coreSkillsConfig = config;
    }

    /**
     * The loaded {@link RankConfig}, or {@code null} outside a world session (before the configs
     * are wired in at server start — PORT Phase 8). Replaces {@code RankConfig.getInstance()}.
     */
    public static @Nullable RankConfig getRankConfig() {
        return rankConfig;
    }

    /** Wires the loaded {@link RankConfig} at server start (PORT Phase 8). */
    public static void setRankConfig(@Nullable RankConfig config) {
        rankConfig = config;
    }

    /**
     * The loaded {@link SoundConfig}, or {@code null} outside a world session (before the configs
     * are wired in at server start — PORT Phase 8). Replaces {@code SoundConfig.getInstance()}.
     */
    public static @Nullable SoundConfig getSoundConfig() {
        return soundConfig;
    }

    /** Wires the loaded {@link SoundConfig} at server start (PORT Phase 8). */
    public static void setSoundConfig(@Nullable SoundConfig config) {
        soundConfig = config;
    }

    /**
     * The loaded {@link AdvancedConfig}, or {@code null} outside a world session (before the configs
     * are wired in at server start — PORT Phase 8). Replaces {@code AdvancedConfig.getInstance()}.
     */
    public static @Nullable AdvancedConfig getAdvancedConfig() {
        return advancedConfig;
    }

    /** Wires the loaded {@link AdvancedConfig} at server start (PORT Phase 8). */
    public static void setAdvancedConfig(@Nullable AdvancedConfig config) {
        advancedConfig = config;
    }

    /**
     * The loaded {@link TreasureConfig}, or {@code null} outside a world session (before the configs
     * are wired in at server start — PORT Phase 8). Replaces {@code TreasureConfig.getInstance()}.
     */
    public static @Nullable TreasureConfig getTreasureConfig() {
        return treasureConfig;
    }

    /** Wires the loaded {@link TreasureConfig} at server start (PORT Phase 8). */
    public static void setTreasureConfig(@Nullable TreasureConfig config) {
        treasureConfig = config;
    }
}
