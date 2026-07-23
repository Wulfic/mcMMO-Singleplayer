package com.gmail.nossr50.fabric;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.database.ProfileStore;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Drives the per-player session lifecycle (Phase 5 persistence + Phase 3 join/quit hooks): loads a
 * player's {@link PlayerProfile} from the bound {@link ProfileStore} when they join, tracks the
 * resulting {@link McMMOPlayer} in {@link UserManager}, and saves + untracks it on disconnect.
 *
 * <p>Replaces the legacy {@code PlayerListener} join/quit handling (which attached the data to the
 * Bukkit player via metadata and scheduled an async DB load). In the integrated singleplayer server
 * the local player joins immediately after the server starts — after {@link McMMOMod#onServerStarting}
 * has bound the store — so the store is always present by the time {@link #onJoin} fires.
 *
 * <p>Also owns the respawn half of the lifecycle ({@link #onRespawn}), which legacy split across
 * {@code PlayerListener#onPlayerRespawn} and a Bukkit {@code Player} object that stayed valid for the
 * whole session. Vanilla gives no such guarantee, so the respawn hook is load-bearing here in a way
 * it never was upstream.
 */
public final class PlayerSessionListener {

    private PlayerSessionListener() {
    }

    /**
     * Register the join/respawn/disconnect handlers. Called once at mod load from
     * {@link McMMOMod#onInitialize}.
     */
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> onRespawn(newPlayer));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onQuit(handler.player));
    }

    private static void onJoin(ServerPlayerEntity vanilla) {
        final PlatformPlayer player = new PlatformPlayer(vanilla);
        try {
            final ProfileStore store = McMMOMod.getProfileStore();
            if (store == null) {
                McMMOMod.LOGGER.warn(
                        "No mcMMO profile store bound on join for {}; skill data will not load.",
                        player.getName());
                return;
            }

            final boolean isNew = !store.hasProfile(player.getUniqueId());
            final PlayerProfile profile = store.loadProfile(player.getUniqueId(), player.getName(),
                    startingLevel());
            profile.updateLastLogin();
            if (isNew) {
                // Force the fresh profile to be written on the first save so the file exists even
                // if the player never gains XP this session.
                profile.markProfileDirty();
            }

            final McMMOPlayer mmoPlayer = new McMMOPlayer(player, profile);
            UserManager.track(mmoPlayer);
            McMMOMod.getTransientEntityTracker().initPlayer(player.getUniqueId());
            McMMOMod.LOGGER.info("Loaded mcMMO data for {} ({} profile).",
                    player.getName(), isNew ? "new" : "existing");
        } catch (Exception e) {
            McMMOMod.LOGGER.error("Failed to load mcMMO data for {} on join.", player.getName(), e);
        }
    }

    /**
     * Re-point the player's mcMMO state at the entity vanilla just built for them, and stamp the
     * respawn timestamp the exploit guards read.
     *
     * <p>Legacy only did the second half ({@code PlayerListener#onPlayerRespawn} →
     * {@code actualizeRespawnATS}) because a Bukkit {@code Player} was a stable session-long handle.
     * Vanilla's {@code PlayerManager#respawnPlayer} removes the old {@link ServerPlayerEntity} and
     * constructs a replacement, so without {@link PlatformPlayer#rebind} every MC-typed call for the
     * rest of the session would target a removed entity. Note this fires for the End-exit path too
     * ({@code alive == true}), not just death — hence rebinding unconditionally rather than on death.
     *
     * <p>{@code respawnPlayer} is the <i>only</i> path that recreates the entity: ordinary dimension
     * travel goes through {@code ServerPlayerEntity#teleportTo(TeleportTarget)}, which contains no
     * {@code new ServerPlayerEntity} at all (bytecode-verified) and moves the existing one. So this
     * one hook is sufficient — don't go looking for a nether-portal equivalent.
     *
     * @param vanilla the replacement entity for the respawned player
     */
    private static void onRespawn(ServerPlayerEntity vanilla) {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(vanilla.getUuid());
        if (mmoPlayer == null) {
            // Only reachable if the join load failed or was skipped; the player simply has no mcMMO
            // state to re-point, but log it because a silent miss here degrades the whole session.
            McMMOMod.LOGGER.warn(
                    "No mcMMO data tracked for {} on respawn; skill data will not follow them.",
                    vanilla.getName().getString());
            return;
        }
        mmoPlayer.getPlayer().rebind(vanilla);
        mmoPlayer.actualizeRespawnATS();
    }

    private static void onQuit(ServerPlayerEntity vanilla) {
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(vanilla.getUuid());
        if (mmoPlayer == null) {
            return;
        }
        try {
            mmoPlayer.getProfile().save(true);
        } catch (Exception e) {
            McMMOMod.LOGGER.error("Failed to save mcMMO data for {} on quit.",
                    mmoPlayer.getPlayerName(), e);
        } finally {
            UserManager.remove(vanilla.getUuid());
            // Despawn the player's Call-of-the-Wild summons so persistent pets aren't orphaned in the
            // saved world. Ordered after UserManager.remove: the summon's despawn resolves its owner
            // through UserManager to notify them, which is correctly skipped for a leaving player.
            McMMOMod.getTransientEntityTracker().cleanupPlayer(vanilla.getUuid());
        }
    }

    /** The starting level for a brand-new profile, from {@link AdvancedConfig} (defaulting to 0). */
    private static int startingLevel() {
        final AdvancedConfig advanced = McMMOMod.getAdvancedConfig();
        return advanced == null ? 0 : advanced.getStartingLevel();
    }
}
