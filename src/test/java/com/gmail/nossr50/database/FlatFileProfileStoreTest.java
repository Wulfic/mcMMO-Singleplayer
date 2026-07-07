package com.gmail.nossr50.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Round-trips {@link PlayerProfile} data through the {@link FlatFileProfileStore} (MC-free, temp
 * directory). Verifies fresh-profile creation, save→reload fidelity of levels/xp, forward-compat
 * back-fill for skills absent from an old file, and that {@link PlayerProfile#save} is a no-op when
 * no store is bound.
 */
class FlatFileProfileStoreTest {

    private static final int STARTING_LEVEL = 0;

    @AfterEach
    void tearDown() {
        McMMOMod.setProfileStore(null);
    }

    @Test
    void loadsFreshProfileWhenNoFileExists(@TempDir Path dir) {
        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        final UUID uuid = UUID.randomUUID();

        assertFalse(store.hasProfile(uuid));
        final PlayerProfile profile = store.loadProfile(uuid, "Steve", STARTING_LEVEL);

        assertTrue(profile.isLoaded());
        assertEquals("Steve", profile.getPlayerName());
        assertEquals(uuid, profile.getUniqueId());
        assertEquals(STARTING_LEVEL, profile.getSkillLevel(PrimarySkillType.MINING));
    }

    @Test
    void savesAndReloadsLevelsAndXp(@TempDir Path dir) {
        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        McMMOMod.setProfileStore(store);
        final UUID uuid = UUID.randomUUID();

        final PlayerProfile profile = store.loadProfile(uuid, "Alex", STARTING_LEVEL);
        profile.modifySkill(PrimarySkillType.MINING, 7);
        profile.setSkillXpLevel(PrimarySkillType.MINING, 42.5F);
        profile.addLevels(PrimarySkillType.WOODCUTTING, 3);
        profile.save(true);

        assertTrue(store.hasProfile(uuid));
        assertTrue(Files.exists(dir.resolve(uuid + ".yml")));

        final PlayerProfile reloaded = store.loadProfile(uuid, "Alex", STARTING_LEVEL);
        assertEquals(7, reloaded.getSkillLevel(PrimarySkillType.MINING));
        assertEquals(42.5F, reloaded.getSkillXpLevelRaw(PrimarySkillType.MINING));
        assertEquals(3, reloaded.getSkillLevel(PrimarySkillType.WOODCUTTING));
        // Untouched skill retains the starting level.
        assertEquals(STARTING_LEVEL, reloaded.getSkillLevel(PrimarySkillType.ARCHERY));
    }

    @Test
    void backfillsSkillsMissingFromOldFile(@TempDir Path dir) throws Exception {
        final UUID uuid = UUID.randomUUID();
        // Hand-write a minimal "old" file that only knows about MINING.
        Files.writeString(dir.resolve(uuid + ".yml"),
                "uuid: " + uuid + "\nname: Old\nskills:\n  MINING: 9\n");

        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        final PlayerProfile profile = store.loadProfile(uuid, "Old", 2);

        assertEquals(9, profile.getSkillLevel(PrimarySkillType.MINING));
        // A skill absent from the file falls back to the supplied starting level.
        assertEquals(2, profile.getSkillLevel(PrimarySkillType.SWORDS));
    }

    @Test
    void saveIsNoOpWithoutBoundStore(@TempDir Path dir) {
        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        final UUID uuid = UUID.randomUUID();
        final PlayerProfile profile = store.loadProfile(uuid, "NoStore", STARTING_LEVEL);
        profile.modifySkill(PrimarySkillType.MINING, 5);

        // No store bound → save() degrades to a no-op, nothing written.
        profile.save(true);

        assertFalse(store.hasProfile(uuid));
    }
}
