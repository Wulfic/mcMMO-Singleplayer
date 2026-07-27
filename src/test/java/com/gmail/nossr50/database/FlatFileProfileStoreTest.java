package com.gmail.nossr50.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.YamlConfiguration;
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
 * back-fill for skills absent from an old file, the legacy-save-key migration for a skill that has
 * been renamed ({@link com.gmail.nossr50.util.skills.SkillRenames}), and that
 * {@link PlayerProfile#save} is a no-op when no store is bound.
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

    // --- Renamed-skill migration --------------------------------------------------------------
    //
    // A skill's name() IS its save key, so a rename silently resets every profile written before it:
    // the new key is absent, the default wins, and nothing is logged. SkillRenames + savedKeyFor are
    // the fix, and these pin the whole contract — legacy key honoured, current key preferred when a
    // mixed-version file carries both, never-renamed skills unaffected.
    //
    // They drive savedKeyFor with an EXPLICIT legacy name rather than a real PrimarySkillType. The
    // only rename the mod has ever had (ACROBATICS -> AGILITY) was retired on 2026-07-27 when Agility
    // became a child skill, since a child has no save key to migrate to — so SkillRenames is now
    // empty, and a test routed through a live skill would pass by doing nothing and keep passing if
    // savedKeyFor were deleted outright.

    private static YamlConfiguration profileDoc(String body) throws Exception {
        final Path file = Files.createTempFile("profile", ".yml");
        Files.writeString(file, body);
        return YamlConfiguration.loadConfiguration(file);
    }

    @Test
    void readsRenamedSkillFromItsLegacySaveKey() throws Exception {
        final YamlConfiguration yc = profileDoc("skills:\n  OLDNAME: 47\n");

        assertEquals("OLDNAME",
                FlatFileProfileStore.savedKeyFor(yc, "NEWNAME", "OLDNAME", "Veteran"));
    }

    @Test
    void prefersCurrentSaveKeyOverLegacyWhenBothPresent() throws Exception {
        // A file touched by both a pre- and post-rename build. The current key is authoritative.
        final YamlConfiguration yc = profileDoc("skills:\n  OLDNAME: 47\n  NEWNAME: 63\n");

        assertEquals("NEWNAME",
                FlatFileProfileStore.savedKeyFor(yc, "NEWNAME", "OLDNAME", "Mixed"));
    }

    @Test
    void defaultsToTheCurrentKeyWhenNeitherIsPresent() throws Exception {
        final YamlConfiguration yc = profileDoc("skills:\n  MINING: 9\n");

        assertEquals("NEWNAME",
                FlatFileProfileStore.savedKeyFor(yc, "NEWNAME", "OLDNAME", "Fresh"));
    }

    @Test
    void aSkillThatWasNeverRenamedAlwaysUsesItsCurrentKey() throws Exception {
        final YamlConfiguration yc = profileDoc("skills:\n  MINING: 9\n");

        assertEquals("MINING", FlatFileProfileStore.savedKeyFor(yc, "MINING", null, "Fresh"));
        assertEquals("SWORDS", FlatFileProfileStore.savedKeyFor(yc, "SWORDS", null, "Fresh"));
    }

    @Test
    void agilityProgressIsNotMigratedBecauseAChildSkillHasNoSaveKey(@TempDir Path dir)
            throws Exception {
        // The 2026-07-27 ruling, pinned so it cannot be undone by accident. Agility's level is
        // recomputed from Parkour/Swimming/Flying on every load, so a stored AGILITY key is ignored
        // on read and never written back.
        final UUID uuid = UUID.randomUUID();
        Files.writeString(dir.resolve(uuid + ".yml"),
                "uuid: " + uuid + "\nname: Veteran\n"
                        + "skills:\n  ACROBATICS: 47\n  AGILITY: 63\n  PARKOUR: 30\n"
                        + "experience:\n  ACROBATICS: 812.5\n  AGILITY: 100.0\n");

        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        McMMOMod.setProfileStore(store);
        final PlayerProfile profile = store.loadProfile(uuid, "Veteran", STARTING_LEVEL);

        // (30 + starting + starting) / 3 — derived from the parents, not read from the file.
        assertEquals((30 + STARTING_LEVEL + STARTING_LEVEL) / 3,
                profile.getSkillLevel(PrimarySkillType.AGILITY));

        // Dirty the profile so save() actually rewrites — nothing marks it dirty on load any more,
        // which is itself the point: there is no migration left to perform.
        profile.addLevels(PrimarySkillType.MINING, 1);
        profile.save(true);

        final String written = Files.readString(dir.resolve(uuid + ".yml"));
        assertFalse(written.contains("AGILITY"), written);
        assertFalse(written.contains("ACROBATICS"), written);
        assertTrue(written.contains("PARKOUR: 30"), written);
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
