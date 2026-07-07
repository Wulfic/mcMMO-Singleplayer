package com.gmail.nossr50.util.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.database.FlatFileProfileStore;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the Phase 10.1 stripped {@link UserManager} — the UUID-keyed online-player registry that
 * replaced the legacy Bukkit-metadata attachment. {@link McMMOPlayer} is mocked (the registry only
 * reads its player handle, name, and profile), so no config wiring is needed. The static registry is
 * cleared around every test to isolate them.
 */
class UserManagerTest {

    private static final UUID UID_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID UID_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @BeforeEach
    @AfterEach
    void clearRegistry() {
        UserManager.clearAll();
    }

    private static McMMOPlayer mockUser(UUID uuid, String name) {
        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId()).thenReturn(uuid);

        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        when(mmoPlayer.getPlayerName()).thenReturn(name);
        return mmoPlayer;
    }

    @Test
    void trackThenLookupByUuidNameAndPlayer() {
        final McMMOPlayer user = mockUser(UID_A, "Alice");
        UserManager.track(user);

        assertSame(user, UserManager.getPlayer(UID_A));
        assertSame(user, UserManager.getPlayer("Alice"));
        assertSame(user, UserManager.getPlayer(user.getPlayer()));
        assertTrue(UserManager.hasPlayerData(UID_A));
    }

    @Test
    void unknownLookupsReturnNull() {
        assertNull(UserManager.getPlayer(UID_A));
        assertNull(UserManager.getPlayer("Nobody"));
        assertNull(UserManager.getPlayer((UUID) null));
        assertNull(UserManager.getPlayer((PlatformPlayer) null));
        assertNull(UserManager.getPlayer((String) null));
        assertFalse(UserManager.hasPlayerData(UID_A));
        assertFalse(UserManager.hasPlayerData(null));
    }

    @Test
    void removeByPlayerEvictsFromRegistry() {
        final McMMOPlayer user = mockUser(UID_A, "Alice");
        UserManager.track(user);

        UserManager.remove(user.getPlayer());

        assertNull(UserManager.getPlayer(UID_A));
        assertFalse(UserManager.hasPlayerData(UID_A));
    }

    @Test
    void cleanupPlayerOnlyRemovesTheMatchingEntry() {
        final McMMOPlayer stale = mockUser(UID_A, "Alice");
        final McMMOPlayer current = mockUser(UID_A, "Alice");

        UserManager.track(current);
        // Trying to clean up a stale object for the same UUID must not evict the current one.
        UserManager.cleanupPlayer(stale);
        assertSame(current, UserManager.getPlayer(UID_A));

        UserManager.cleanupPlayer(current);
        assertNull(UserManager.getPlayer(UID_A));
    }

    @Test
    void getPlayersReflectsAllTrackedUsers() {
        final McMMOPlayer a = mockUser(UID_A, "Alice");
        final McMMOPlayer b = mockUser(UID_B, "Bob");
        UserManager.track(a);
        UserManager.track(b);

        assertTrue(UserManager.getPlayers().containsAll(java.util.List.of(a, b)));
        assertEquals(2, UserManager.getPlayers().size());
    }

    @Test
    void clearAllEmptiesTheRegistry() {
        UserManager.track(mockUser(UID_A, "Alice"));
        UserManager.track(mockUser(UID_B, "Bob"));

        UserManager.clearAll();

        assertTrue(UserManager.getPlayers().isEmpty());
    }

    @Test
    void saveAllPersistsChangedProfilesToBoundStore(@TempDir Path dir) {
        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        McMMOMod.setProfileStore(store);
        try {
            final PlayerProfile profile = store.loadProfile(UID_A, "Alice", 0);
            profile.modifySkill(PrimarySkillType.MINING, 4); // marks the profile dirty
            final McMMOPlayer user = mockUser(UID_A, "Alice");
            when(user.getProfile()).thenReturn(profile);
            UserManager.track(user);

            UserManager.saveAll();

            // Phase 5: saveAll flushes changed profiles through the bound store.
            assertTrue(store.hasProfile(UID_A));
            assertEquals(4, store.loadProfile(UID_A, "Alice", 0)
                    .getSkillLevel(PrimarySkillType.MINING));
            // Registry is untouched by a save.
            assertSame(user, UserManager.getPlayer(UID_A));
        } finally {
            McMMOMod.setProfileStore(null);
        }
    }
}
