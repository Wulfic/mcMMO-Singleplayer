package com.gmail.nossr50.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.YamlConfiguration;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.skills.SkillTools;
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

        // Asserted over the whole enum rather than one hand-picked skill, because this is the
        // regression every NEW skill needs and naming them one at a time means the test only ever
        // covers the skills someone remembered to add. Every save file on disk predates Husbandry,
        // Stealth and Unarmored; a skill the loader failed to pre-populate would come back absent
        // and blow up on first read rather than defaulting quietly.
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            if (skill == PrimarySkillType.MINING) {
                continue; // The one skill the hand-written file actually knows about.
            }
            if (SkillTools.isChildSkill(skill)) {
                // A child skill has no save key at all -- its level is the mean of its parents, so
                // it reads back as whatever they average to (Smelting is Mining+Repair, and this
                // file's Mining 9 drags it to 5). Nothing was defaulted, so there is nothing here
                // for this test to assert; SkillToolsTest#childSkillParents owns that contract.
                continue;
            }
            assertEquals(2, profile.getSkillLevel(skill),
                    () -> skill + " must default to the starting level, not go missing");
        }
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
        // The 2026-07-27 ruling, pinned so it cannot be undone by accident, and re-checked when
        // AGILITY was retired outright on 2026-08-17. A stored AGILITY key is an ORPHAN, not a
        // rename: a child skill never had a level of its own to migrate, so the key is ignored on
        // read and never written back.
        //
        // ⚠️ Both halves matter and only one is obvious. Load ignores it because the loader
        // iterates SkillTools.NON_CHILD_SKILLS; SAVE drops it because saveProfile builds a FRESH
        // YamlConfiguration rather than merging into what was read. The save direction is where
        // the Taming UniqueDataType defect lived.
        final UUID uuid = UUID.randomUUID();
        Files.writeString(dir.resolve(uuid + ".yml"),
                "uuid: " + uuid + "\nname: Veteran\n"
                        + "skills:\n  ACROBATICS: 47\n  AGILITY: 63\n  PARKOUR: 30\n"
                        + "experience:\n  ACROBATICS: 812.5\n  AGILITY: 100.0\n");

        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        McMMOMod.setProfileStore(store);
        final PlayerProfile profile = store.loadProfile(uuid, "Veteran", STARTING_LEVEL);

        // The derived-Agility assertion that stood here was deleted 2026-08-17 with the constant
        // itself; there is no skill left to derive. The two orphan-key assertions below are the
        // ones this test exists for and they are deliberately untouched.

        // Dirty the profile so save() actually rewrites — nothing marks it dirty on load any more,
        // which is itself the point: there is no migration left to perform.
        profile.addLevels(PrimarySkillType.MINING, 1);
        profile.save(true);

        final String written = Files.readString(dir.resolve(uuid + ".yml"));
        assertFalse(written.contains("AGILITY"), written);
        assertFalse(written.contains("ACROBATICS"), written);
        assertTrue(written.contains("PARKOUR: 30"), written);
    }

    // --- Hunter's kill counters (D-HU2) -------------------------------------------------------
    //
    // The one open-ended, string-keyed section in the profile. Everything above is a fixed key set
    // derived from an enum's .values(), so a bad entry costs one skill its default; here both the keys
    // AND the entry count come off disk, which makes this the only place a save file can drive an
    // allocation. Each test below pins one of the four guards in FlatFileProfileStore#readMobKills.

    @Test
    void savesAndReloadsMobKillCounters(@TempDir Path dir) {
        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        McMMOMod.setProfileStore(store);
        final UUID uuid = UUID.randomUUID();

        final PlayerProfile profile = store.loadProfile(uuid, "Hunter", STARTING_LEVEL);
        for (int i = 0; i < 3; i++) {
            profile.incrementMobKills("minecraft:zombie");
        }
        profile.incrementMobKills("minecraft:creeper");
        profile.save(true);

        final PlayerProfile reloaded = store.loadProfile(uuid, "Hunter", STARTING_LEVEL);
        assertEquals(3, reloaded.getMobKills("minecraft:zombie"));
        assertEquals(1, reloaded.getMobKills("minecraft:creeper"));
        assertEquals(0, reloaded.getMobKills("minecraft:skeleton"));
        assertEquals(2, reloaded.getAllMobKills().size());
    }

    @Test
    void aMobIdContainingADotSurvivesTheRoundTrip(@TempDir Path dir) {
        // The trap this exists for: a registry namespace legally contains '.' ([a-z0-9_.-]), and
        // YamlConfiguration's addresses are dot-delimited. Writing this section key-by-key as
        // "kills.<id>" would silently bury a modded id in a phantom subsection and read back 0.
        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        McMMOMod.setProfileStore(store);
        final UUID uuid = UUID.randomUUID();

        final PlayerProfile profile = store.loadProfile(uuid, "Modded", STARTING_LEVEL);
        profile.incrementMobKills("some.pack:dread.beast");
        profile.incrementMobKills("some.pack:dread.beast");
        profile.save(true);

        final PlayerProfile reloaded = store.loadProfile(uuid, "Modded", STARTING_LEVEL);
        assertEquals(2, reloaded.getMobKills("some.pack:dread.beast"));
        assertEquals(1, reloaded.getAllMobKills().size());
    }

    @Test
    void aCountedKillAloneIsEnoughToMakeTheProfileSave(@TempDir Path dir) {
        // save() is a no-op on a clean profile, so if incrementMobKills failed to dirty it a whole
        // session of mastery progress would vanish on quit -- and nothing would log it.
        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        McMMOMod.setProfileStore(store);
        final UUID uuid = UUID.randomUUID();

        final PlayerProfile profile = store.loadProfile(uuid, "Dirty", STARTING_LEVEL);
        profile.incrementMobKills("minecraft:zombie");
        profile.save(true);

        assertTrue(store.hasProfile(uuid), "the kill alone must have dirtied the profile");
        assertEquals(1, store.loadProfile(uuid, "Dirty", STARTING_LEVEL)
                .getMobKills("minecraft:zombie"));
    }

    @Test
    void aProfileWithNoKillsWritesNoKillsSectionAtAll(@TempDir Path dir) throws Exception {
        // Keeps every pre-Hunter save byte-identical to what it was, and keeps the section
        // proportional to what the player actually did rather than to the mob roster.
        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        McMMOMod.setProfileStore(store);
        final UUID uuid = UUID.randomUUID();

        final PlayerProfile profile = store.loadProfile(uuid, "Pacifist", STARTING_LEVEL);
        profile.addLevels(PrimarySkillType.MINING, 1);
        profile.save(true);

        // Asserted through the parser, NOT with a substring search on the raw text: "kills" is a
        // substring of "skills", which every profile writes, so the naive string check passes
        // unconditionally and proves nothing.
        final YamlConfiguration written =
                YamlConfiguration.loadConfiguration(dir.resolve(uuid + ".yml"));
        assertFalse(written.contains("kills"), Files.readString(dir.resolve(uuid + ".yml")));
        assertTrue(written.contains("skills"), "the rest of the profile is still written");
    }

    @Test
    void aProfileWrittenBeforeHunterExistedLoadsWithNoKillCounters(@TempDir Path dir)
            throws Exception {
        // The old-profile regression the plan calls for: every save file on disk predates Hunter, so
        // an absent section must read as "killed nothing", never as a failure.
        final UUID uuid = UUID.randomUUID();
        Files.writeString(dir.resolve(uuid + ".yml"),
                "uuid: " + uuid + "\nname: Veteran\nskills:\n  MINING: 9\n");

        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        final PlayerProfile profile = store.loadProfile(uuid, "Veteran", 2);

        assertTrue(profile.getAllMobKills().isEmpty());
        assertEquals(0, profile.getMobKills("minecraft:zombie"));
        // And the rest of the profile is untouched by the new section's absence.
        assertEquals(9, profile.getSkillLevel(PrimarySkillType.MINING));
        assertEquals(2, profile.getSkillLevel(PrimarySkillType.HUNTER));
    }

    @Test
    void unusableKillEntriesAreDroppedRatherThanTrusted(@TempDir Path dir) throws Exception {
        // A count is a threshold comparison's input, so a negative or non-numeric one is worse than a
        // missing row: it would reach the mastery resolver as a number no threshold expects.
        final UUID uuid = UUID.randomUUID();
        Files.writeString(dir.resolve(uuid + ".yml"),
                "uuid: " + uuid + "\nname: Corrupt\n"
                        + "kills:\n"
                        + "  minecraft:zombie: 1204\n"
                        + "  minecraft:creeper: -50\n"
                        + "  minecraft:skeleton: 0\n"
                        + "  minecraft:spider: not_a_number\n"
                        + "  minecraft:blaze:\n"
                        + "  '': 77\n");

        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        final PlayerProfile profile = store.loadProfile(uuid, "Corrupt", STARTING_LEVEL);

        assertEquals(1204, profile.getMobKills("minecraft:zombie"));
        assertEquals(0, profile.getMobKills("minecraft:creeper"), "a negative count is dropped");
        assertEquals(0, profile.getMobKills("minecraft:skeleton"), "a zero is not worth carrying");
        assertEquals(0, profile.getMobKills("minecraft:spider"), "a non-numeric count is dropped");
        assertEquals(0, profile.getMobKills("minecraft:blaze"), "a null count is dropped");
        assertEquals(0, profile.getMobKills(""), "a blank mob id is dropped");
        assertEquals(1, profile.getAllMobKills().size(), profile.getAllMobKills().toString());
    }

    @Test
    void aKillsValueThatIsNotASectionIsIgnored(@TempDir Path dir) throws Exception {
        final UUID uuid = UUID.randomUUID();
        Files.writeString(dir.resolve(uuid + ".yml"),
                "uuid: " + uuid + "\nname: Wrong\nskills:\n  MINING: 4\nkills: nonsense\n");

        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        final PlayerProfile profile = store.loadProfile(uuid, "Wrong", STARTING_LEVEL);

        // Degrades to "no kills" and leaves the rest of the profile intact, rather than throwing on a
        // cast and costing the player every skill they had.
        assertTrue(profile.getAllMobKills().isEmpty());
        assertEquals(4, profile.getSkillLevel(PrimarySkillType.MINING));
    }

    @Test
    void anOversizedKillSectionIsTruncatedOnReadRatherThanLoadedWhole(@TempDir Path dir)
            throws Exception {
        // [[placed-block-persistence]] defect #16 generalised from one number to a whole collection:
        // never let a size read off disk drive an allocation. 4,096 is ~40x the vanilla mob roster, so
        // reaching it means a modded world or a corrupt file -- both cases where "load it all" is a bug.
        final int oversized = PlayerProfile.MAX_TRACKED_MOB_TYPES + 500;
        final StringBuilder yaml = new StringBuilder(64 * oversized);
        final UUID uuid = UUID.randomUUID();
        yaml.append("uuid: ").append(uuid).append("\nname: Modpack\nkills:\n");
        for (int i = 0; i < oversized; i++) {
            yaml.append("  test:mob_").append(i).append(": 1\n");
        }
        Files.writeString(dir.resolve(uuid + ".yml"), yaml);

        final FlatFileProfileStore store = new FlatFileProfileStore(dir);
        final PlayerProfile profile = store.loadProfile(uuid, "Modpack", STARTING_LEVEL);

        assertEquals(PlayerProfile.MAX_TRACKED_MOB_TYPES, profile.getAllMobKills().size());
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
