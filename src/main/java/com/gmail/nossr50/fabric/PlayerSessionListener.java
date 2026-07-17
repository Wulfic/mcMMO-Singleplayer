package com.gmail.nossr50.fabric;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.database.ProfileStore;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
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
 */
public final class PlayerSessionListener {

    private PlayerSessionListener() {
    }

    /** Register the join/disconnect handlers. Called once at mod load from {@link McMMOMod#onInitialize}. */
    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
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
