package com.gmail.nossr50.database;

import com.gmail.nossr50.datatypes.player.PlayerProfile;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * Persistence backend for {@link PlayerProfile} data, replacing the legacy
 * {@code DatabaseManager} hierarchy (flatfile + SQL). The SQL backend was cut for the
 * singleplayer port (CONVERSION_TODO Phase 5); the sole implementation is
 * {@link FlatFileProfileStore}, a per-world flatfile store.
 *
 * <p>MC-free by design: implementations take a filesystem {@code Path}, so the whole
 * load/save round-trip is unit-testable without a running server (matching the
 * {@link com.gmail.nossr50.config.ConfigLoader} pattern).
 */
public interface ProfileStore {

    /**
     * Load a player's profile, or build a fresh one at {@code startingLevel} if none is stored.
     * The returned profile is always {@link PlayerProfile#isLoaded() loaded}.
     *
     * @param uuid the player UUID (the storage key)
     * @param playerName the player's current name (recorded for readability / lookup)
     * @param startingLevel the level a brand-new profile's skills start at
     * @return the loaded (existing or freshly created) profile
     */
    @NotNull PlayerProfile loadProfile(@NotNull UUID uuid, @NotNull String playerName,
            int startingLevel);

    /**
     * Persist a profile to disk. Callers should only invoke this for a
     * {@link PlayerProfile#isLoaded() loaded} profile that has unsaved changes.
     *
     * @param profile the profile to write
     */
    void saveProfile(@NotNull PlayerProfile profile);

    /**
     * @param uuid the player UUID
     * @return whether a stored profile exists for this UUID
     */
    boolean hasProfile(@NotNull UUID uuid);
}
