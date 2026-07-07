package com.gmail.nossr50.util.player;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.LogUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of the online {@link McMMOPlayer} objects, keyed by player {@link UUID}.
 *
 * <p>Phase 10.1 strip: the legacy manager attached each {@link McMMOPlayer} to its Bukkit
 * {@code Player} via {@code FixedMetadataValue} metadata and separately tracked a {@code HashSet}
 * for shutdown saves. Fabric has no Bukkit metadata, so both roles collapse into this single
 * UUID-keyed map. A {@link ConcurrentHashMap} guards against the integrated server's thread split
 * (gameplay runs on the server thread, but the client thread can touch player state during
 * open/close).
 */
public final class UserManager {

    private static final Map<UUID, McMMOPlayer> playerDataMap = new ConcurrentHashMap<>();

    private UserManager() {
    }

    /**
     * Track a new user.
     *
     * @param mmoPlayer the player data to start tracking
     */
    public static void track(@NotNull McMMOPlayer mmoPlayer) {
        playerDataMap.put(mmoPlayer.getPlayer().getUniqueId(), mmoPlayer);
    }

    /**
     * Stop tracking a user (does not run teardown — see {@link #remove(PlatformPlayer)}).
     */
    public static void cleanupPlayer(@NotNull McMMOPlayer mmoPlayer) {
        playerDataMap.remove(mmoPlayer.getPlayer().getUniqueId(), mmoPlayer);
    }

    /**
     * Remove a user from the registry.
     *
     * @param player the player to remove
     */
    public static void remove(@NotNull PlatformPlayer player) {
        remove(player.getUniqueId());
    }

    /**
     * Remove a user from the registry by UUID.
     *
     * @param uuid the player's UUID
     */
    public static void remove(@NotNull UUID uuid) {
        // PORT Phase 10/11: legacy remove() also ran mmoPlayer.cleanup() (super-ability teardown +
        // taming-summon cleanup), both deferred with their subsystems. The player-quit listener
        // that calls this (Phase 3) will drive that teardown once it exists.
        playerDataMap.remove(uuid);
    }

    /**
     * Clear all tracked users.
     */
    public static void clearAll() {
        playerDataMap.clear();
    }

    /**
     * Save all tracked users ON THIS THREAD.
     */
    public static void saveAll() {
        final ImmutableList<McMMOPlayer> tracked = ImmutableList.copyOf(playerDataMap.values());

        McMMOMod.LOGGER.info("Saving mmoPlayers... ({})", tracked.size());

        for (McMMOPlayer playerData : tracked) {
            try {
                LogUtils.debug("Saving data for player: " + playerData.getPlayerName());
                // PORT Phase 5: PlayerProfile.save is a no-op until per-world persistence lands.
                playerData.getProfile().save(true);
            } catch (Exception e) {
                McMMOMod.LOGGER.warn("Could not save mcMMO player data for player: {}",
                        playerData.getPlayerName(), e);
            }
        }

        McMMOMod.LOGGER.info("Finished save operation for {} players!", tracked.size());
    }

    public static @NotNull Collection<McMMOPlayer> getPlayers() {
        return ImmutableList.copyOf(playerDataMap.values());
    }

    /**
     * Get the {@link McMMOPlayer} for a player.
     *
     * @param player the player
     * @return the player's data, or {@code null} if it has not been loaded
     */
    public static @Nullable McMMOPlayer getPlayer(@Nullable PlatformPlayer player) {
        return player == null ? null : playerDataMap.get(player.getUniqueId());
    }

    /**
     * Get the {@link McMMOPlayer} for a UUID.
     *
     * @param uuid the player's UUID
     * @return the player's data, or {@code null} if it has not been loaded
     */
    public static @Nullable McMMOPlayer getPlayer(@Nullable UUID uuid) {
        return uuid == null ? null : playerDataMap.get(uuid);
    }

    /**
     * Get the {@link McMMOPlayer} for a player by (exact) name.
     *
     * @param playerName the player's name
     * @return the player's data, or {@code null} if no online player matches
     */
    public static @Nullable McMMOPlayer getPlayer(@Nullable String playerName) {
        if (playerName == null) {
            return null;
        }

        for (McMMOPlayer mmoPlayer : playerDataMap.values()) {
            if (mmoPlayer.getPlayerName().equals(playerName)) {
                return mmoPlayer;
            }
        }

        McMMOMod.LOGGER.warn("A valid mmoPlayer object could not be found for {}.", playerName);
        return null;
    }

    public static boolean hasPlayerData(@Nullable UUID uuid) {
        return uuid != null && playerDataMap.containsKey(uuid);
    }

    // PORT Phase 5: getOfflinePlayer(...) dropped — it resolved a Bukkit OfflinePlayer from the DB.
    // Offline profile access re-homes onto the per-world save-data store when persistence lands.
}
