package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.skills.SkillRenames;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coverage for the {@link ConfigLoader} default-copy + merge flow, using {@code test-config.yml}
 * on the test classpath as the "bundled default" resource and a temp dir as the data folder.
 */
class ConfigLoaderTest {

    /**
     * The path moves the fixture pretends to carry. The real table is scoped per file (see
     * {@link SkillRenames.MovedPath}), so {@code test-config.yml} matches nothing in it — these are
     * injected instead, which is the point: they exercise the <em>mechanism</em> rather than one
     * file's happenstance contents.
     */
    private static final List<SkillRenames.MovedPath> ROLL_MOVED = List.of(
            new SkillRenames.MovedPath("test-config.yml",
                    "Skills.Agility.Roll", "Skills.Parkour.Roll"));

    /**
     * A one-to-many move, as the Agility retirement produced on 2026-08-17: one
     * {@code MaxBonusLevel} under a dissolving skill, three equally correct homes under the three
     * skills that replaced it.
     */
    private static final List<SkillRenames.MovedPath> FAN_OUT = List.of(
            new SkillRenames.MovedPath("test-config.yml",
                    "Skills.Agility.FleetFooted.MaxBonusLevel",
                    List.of("Skills.Parkour.FleetFooted.MaxBonusLevel",
                            "Skills.Swimming.FleetFooted.MaxBonusLevel",
                            "Skills.Flying.FleetFooted.MaxBonusLevel")));

    /** Minimal concrete loader over the test fixture resource. */
    private static final class TestConfig extends ConfigLoader {
        TestConfig(Path dataFolder) {
            super("test-config.yml", dataFolder, List.of(), ROLL_MOVED);
        }

        TestConfig(Path dataFolder, List<ConfigRetunes.Retune> retunes) {
            super("test-config.yml", dataFolder, retunes, ROLL_MOVED);
        }

        /**
         * A loader with an explicit move table. A named factory rather than another two-argument
         * constructor, because {@code List<Retune>} and {@code List<MovedPath>} erase to the same
         * signature.
         */
        static TestConfig withMoves(Path dataFolder, List<SkillRenames.MovedPath> moves) {
            return new TestConfig(dataFolder, List.of(), moves);
        }

        private TestConfig(Path dataFolder, List<ConfigRetunes.Retune> retunes,
                List<SkillRenames.MovedPath> moves) {
            super("test-config.yml", dataFolder, retunes, moves);
        }

        @Override
        protected void loadKeys() {
            // no-op: tests read the protected config directly
        }

        YamlConfiguration config() {
            return config;
        }
    }

    /**
     * The fixture's shipped {@code MaxLevel} moving 100 → 200. Declared here rather than in
     * {@link ConfigRetunes} so these tests exercise the <em>mechanism</em>; the real registry's one
     * live entry is covered end-to-end in {@code ExperienceConfigTest}.
     */
    private static final List<ConfigRetunes.Retune> MAX_LEVEL_DOUBLED = List.of(
            new ConfigRetunes.Retune("test-config.yml", "General.MaxLevel", 100, 200, 1,
                    "a fixture retune"));

    @Test
    void writesDefaultsToDiskWhenUserFileMissing(@TempDir Path dataFolder) {
        final TestConfig loader = new TestConfig(dataFolder);
        assertTrue(Files.exists(dataFolder.resolve("test-config.yml")));
        assertTrue(loader.config().getBoolean("General.Enabled"));
        assertEquals(100, loader.config().getInt("General.MaxLevel"));
        assertEquals(50.0D, loader.config().getDouble("Skills.Mining.DoubleDrops.ChanceMax"));
    }

    @Test
    void backfillsKeysMissingFromUserFile(@TempDir Path dataFolder) throws IOException {
        // A user file that predates a couple of default keys.
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                General:
                  Enabled: false
                """);

        final TestConfig loader = new TestConfig(dataFolder);
        // User's own value is preserved...
        assertFalse(loader.config().getBoolean("General.Enabled"));
        // ...and missing defaults were merged in.
        assertEquals(100, loader.config().getInt("General.MaxLevel"));
        assertEquals("en_US", loader.config().getString("General.Locale"));
        assertTrue(loader.config().getBoolean("Skills.Mining.Enabled"));

        // The merged values were persisted back to disk.
        final YamlConfiguration reloaded =
                YamlConfiguration.loadConfiguration(dataFolder.resolve("test-config.yml"));
        assertEquals(100, reloaded.getInt("General.MaxLevel"));
        assertFalse(reloaded.getBoolean("General.Enabled"));
    }

    @Test
    void preservesExistingUserValuesWithoutRewriteWhenComplete(@TempDir Path dataFolder)
            throws IOException {
        // First construction writes defaults out.
        new TestConfig(dataFolder);
        // Hand-edit a value, then reload: the edit must survive (nothing is missing to trigger a merge).
        final Path file = dataFolder.resolve("test-config.yml");
        final YamlConfiguration edited = YamlConfiguration.loadConfiguration(file);
        edited.set("General.MaxLevel", 999);
        edited.save(file);

        final TestConfig reloaded = new TestConfig(dataFolder);
        assertEquals(999, reloaded.config().getInt("General.MaxLevel"));
    }

    // --- Re-parented sub-skills: the automatic path migration ------------------------------------

    @Test
    void movesTuningFromAPathThatWasReParented(@TempDir Path dataFolder) throws IOException {
        // Roll moved from Skills.Agility.Roll to Skills.Parkour.Roll on 2026-08-03, and seven more
        // sub-skills followed on 2026-08-10. Because copyMissingDefaults back-fills only ABSENT keys,
        // a user who had tuned the old block would end up with BOTH: shipped defaults at the new path
        // (which the code reads) and their own values at the old one, silently ignored.
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                Skills:
                  Agility:
                    Roll:
                      ChanceMax: 42.0
                """);

        final TestConfig loader = new TestConfig(dataFolder);

        assertEquals(42.0, loader.config().getDouble("Skills.Parkour.Roll.ChanceMax"),
                "the player's tuning must arrive at the path the code actually reads");
        assertFalse(loader.config().contains("Skills.Agility.Roll"),
                "the dead path must be removed, or the file keeps lying about what is in effect");

        // ...and it is on DISK, not merely in the loaded object. A migration that is not persisted
        // re-runs every boot and, worse, would be undone the first time anything else saves.
        final YamlConfiguration onDisk =
                YamlConfiguration.loadConfiguration(dataFolder.resolve("test-config.yml"));
        assertEquals(42.0, onDisk.getDouble("Skills.Parkour.Roll.ChanceMax"));
        assertFalse(onDisk.contains("Skills.Agility.Roll"));
    }

    @Test
    void migratesEveryLeafOfAMovedSubTreeNotJustTheFirst(@TempDir Path dataFolder)
            throws IOException {
        // The blocks that moved are multi-leaf and nested (MaxBonusLevel is itself a section), so a
        // migrator that copied only top-level leaves would silently drop the RetroMode ladder --
        // which is the value that matters on this server, and the one nobody would notice missing
        // until a rank gate behaved oddly months later.
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                Skills:
                  Agility:
                    Roll:
                      ChanceMax: 42.0
                      DamageThreshold: 9.0
                      MaxBonusLevel:
                        Standard: 55
                        RetroMode: 555
                """);

        final TestConfig loader = new TestConfig(dataFolder);

        assertEquals(42.0, loader.config().getDouble("Skills.Parkour.Roll.ChanceMax"));
        assertEquals(9.0, loader.config().getDouble("Skills.Parkour.Roll.DamageThreshold"));
        assertEquals(55, loader.config().getInt("Skills.Parkour.Roll.MaxBonusLevel.Standard"));
        assertEquals(555, loader.config().getInt("Skills.Parkour.Roll.MaxBonusLevel.RetroMode"),
                "a nested leaf must move too, not just the shallow ones");
        assertFalse(loader.config().contains("Skills.Agility.Roll"));
    }

    @Test
    void aOneToManyMoveReachesEveryDestination(@TempDir Path dataFolder) throws IOException {
        // Retiring a skill can leave one key with several homes: "the level Fleet Footed stops
        // scaling at" was one number under Agility and is three under Parkour/Swimming/Flying. All
        // three are the player's stated intent, so all three must receive it -- delivering to one and
        // letting the other two fall back to the shipped default half-applies their tuning, which is
        // harder to diagnose than not applying it at all.
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                Skills:
                  Agility:
                    FleetFooted:
                      MaxBonusLevel:
                        Standard: 55
                        RetroMode: 555
                """);

        final TestConfig loader = TestConfig.withMoves(dataFolder, FAN_OUT);

        for (String parent : new String[] {"Parkour", "Swimming", "Flying"}) {
            assertEquals(55, loader.config()
                            .getInt("Skills." + parent + ".FleetFooted.MaxBonusLevel.Standard"),
                    parent + " did not receive the fanned-out tuning");
            assertEquals(555, loader.config()
                            .getInt("Skills." + parent + ".FleetFooted.MaxBonusLevel.RetroMode"),
                    parent + " did not receive the nested RetroMode leaf");
        }
    }

    @Test
    void aOneToManyMoveDeletesTheSourceExactlyOnceAtTheEnd(@TempDir Path dataFolder)
            throws IOException {
        // The trap the list exists to avoid. Declaring the same legacy path as three separate
        // single-target moves looks equivalent and is not: the first entry clears the source, and the
        // other two then find nothing and silently deliver the shipped default. This asserts the
        // source is gone AFTER all three landed -- the ordering, not just the outcome.
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                Skills:
                  Agility:
                    FleetFooted:
                      MaxBonusLevel:
                        Standard: 55
                        RetroMode: 555
                """);

        final TestConfig loader = TestConfig.withMoves(dataFolder, FAN_OUT);

        assertFalse(loader.config().contains("Skills.Agility.FleetFooted.MaxBonusLevel"),
                "the dead path must go, or the file keeps lying about what is in effect");
        assertEquals(3, java.util.stream.Stream.of("Parkour", "Swimming", "Flying")
                        .filter(parent -> loader.config()
                                .contains("Skills." + parent + ".FleetFooted.MaxBonusLevel"))
                        .count(),
                "all three destinations must exist alongside the delete; if only one does, the "
                        + "source was cleared before the other two were written");
    }

    /**
     * <b>The converse of a guard test</b> — ruling A-6, and it is asserted in this direction on
     * purpose.
     *
     * <p>Retiring Agility empties {@code Skills.Agility}, and mcMMO deliberately does <em>not</em>
     * remove what is left: the values were carried to the new paths, so nothing is lost, and deleting
     * a further block from a file the player owns and may have hand-edited buys tidiness at the price
     * of a destructive write. A normal guard test proves a destructive path is blocked; there is no
     * destructive path here to block, so what needs pinning is that nobody adds one later as a
     * "cleanup". Without this test that change passes every other test in the suite.
     */
    @Test
    void whatIsLeftUnderTheRetiredSkillIsKeptRatherThanTidiedAway(@TempDir Path dataFolder)
            throws IOException {
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                Skills:
                  Agility:
                    FleetFooted:
                      MaxBonusLevel:
                        Standard: 55
                        RetroMode: 555
                    SomethingWeNeverMigrated: 7
                """);

        final TestConfig loader = TestConfig.withMoves(dataFolder, FAN_OUT);

        assertTrue(loader.config().contains("Skills.Agility.SomethingWeNeverMigrated"),
                "A-6: a key under the retired skill that no move claims is LEFT ALONE. mcMMO removes "
                        + "only what it has just written somewhere else; anything else is the "
                        + "player's file to keep.");
        assertEquals(7, loader.config().getInt("Skills.Agility.SomethingWeNeverMigrated"),
                "and its value is untouched, not merely its key");
    }

    @Test
    void keepsTheNewPathWhenThePlayerHasTunedBoth(@TempDir Path dataFolder) throws IOException {
        // The case a migrator must not guess at. If both spellings are customised, the value at the
        // NEW path is the one the game has actually been using, so it wins -- silently preferring the
        // old one would change behaviour on upgrade, which is the opposite of a migration's job.
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                Skills:
                  Agility:
                    Roll:
                      ChanceMax: 42.0
                  Parkour:
                    Roll:
                      ChanceMax: 77.0
                """);

        final TestConfig loader = new TestConfig(dataFolder);

        assertEquals(77.0, loader.config().getDouble("Skills.Parkour.Roll.ChanceMax"),
                "the live value must survive a migration of the dead one");
        assertFalse(loader.config().contains("Skills.Agility.Roll"),
                "the dead path still goes, whichever value won");
    }

    @Test
    void leavesAFileWithoutLegacyPathsCompletelyUntouched(@TempDir Path dataFolder)
            throws IOException {
        // The common case, and every freshly generated config: the migrator must not rewrite a file
        // it has nothing to do to. Asserted on the file's modification time rather than its content,
        // because a rewrite that happens to round-trip identically is still a rewrite -- it reorders
        // keys and discards comments on a file the player owns.
        new TestConfig(dataFolder);
        final Path file = dataFolder.resolve("test-config.yml");
        final String before = Files.readString(file);

        final TestConfig loader = new TestConfig(dataFolder);

        assertTrue(loader.strandedLegacyPaths().isEmpty(),
                "nothing is stranded in a config written from current defaults");
        assertEquals(before, Files.readString(file),
                "a config with no legacy paths must not be rewritten at all");
    }

    @Test
    void theShippedConfigsCarryNoPathThisWouldMigrate(@TempDir Path dataFolder) {
        // ⚠️ The converse guard, and the one that actually bites. If a shipped YAML still defined a
        // legacy path, copyMissingDefaults would write it into every user's file and the migrator
        // would then "migrate" a block mcMMO itself had just authored -- every boot, forever.
        //
        // Driven off the real table and the real files, and it must cover EVERY file the table names
        // rather than a hand-picked couple: the first draft of this change registered the advanced.yml
        // paths and forgot skillranks.yml entirely, which a two-file guard would have happily passed.
        final Map<String, YamlConfiguration> shipped = Map.of(
                "advanced.yml", new AdvancedConfig(dataFolder).config,
                "config.yml", new GeneralConfig(dataFolder).config,
                "skillranks.yml", new RankConfig(dataFolder).config);

        for (SkillRenames.MovedPath move : SkillRenames.allMovedConfigPaths()) {
            final YamlConfiguration file = shipped.get(move.fileName());
            assertNotNull(file, "no shipped config loaded for " + move.fileName()
                    + " — this guard must cover every file the move table names");
            assertFalse(file.contains(move.legacyPath()),
                    move.fileName() + " still defines the moved path " + move.legacyPath());
            // Every destination, not just the first: a one-to-many move exists precisely because a
            // dissolving skill leaves one key with several homes, and checking only newPaths.get(0)
            // would pass a move that strands two thirds of the player's tuning.
            for (String target : move.newPaths()) {
                assertTrue(file.contains(target),
                        move.fileName() + " does not define the destination " + target
                                + " — a move whose target does not exist migrates tuning into a void");
            }
        }
    }

    /**
     * Every re-parented sub-skill, and the two files that address it at <em>different depths</em>:
     * advanced.yml nests tuning under a {@code Skills} root, skillranks.yml puts the unlock ladder at
     * the top level. Both must migrate.
     */
    private static Stream<Arguments> reParentedSubSkills() {
        return Stream.of(
                Arguments.of("Dodge", "Parkour"),
                Arguments.of("Athlete", "Parkour"),
                Arguments.of("Smash", "Parkour"),
                Arguments.of("LeadLungs", "Swimming"),
                Arguments.of("LakeRaider", "Swimming"),
                Arguments.of("Glide", "Flying"),
                Arguments.of("SolarWings", "Flying"));
    }

    /**
     * ⚠️ The guard that had to be rewritten because a mutant walked straight through the first one.
     *
     * <p>The original version iterated {@link SkillRenames#allMovedConfigPaths()} and asserted things
     * about each entry — which is vacuous against the failure that actually happened: <b>deleting the
     * skillranks.yml half of every move made it pass</b>, because a table with nothing in it satisfies
     * every statement about its contents. A guard driven by the table cannot detect a missing table
     * entry.
     *
     * <p>So this is driven by the <em>sub-skill list</em> instead and asserts the observable
     * behaviour end-to-end, through the real config classes against a real file on disk: seed the
     * legacy spelling, load, and require the value to have arrived under the new parent with the old
     * key gone. Delete a move and the corresponding case fails.
     */
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("reParentedSubSkills")
    void everyReParentedSubSkillMigratesInBothOfItsFiles(String subSkill, String newParent,
            @TempDir Path dataFolder) throws IOException {
        // advanced.yml: tuning, nested under `Skills`. MaxBonusLevel is a section, so this also
        // covers the nested-leaf path for every sub-skill rather than just for Roll.
        Files.writeString(dataFolder.resolve("advanced.yml"), """
                Skills:
                  Agility:
                    %s:
                      MaxBonusLevel:
                        RetroMode: 321
                """.formatted(subSkill));
        // skillranks.yml: the unlock ladder, at the TOP level -- the depth difference that the
        // file-blind first draft got wrong.
        Files.writeString(dataFolder.resolve("skillranks.yml"), """
                Agility:
                  %s:
                    RetroMode:
                      Rank_1: 321
                """.formatted(subSkill));

        final YamlConfiguration advanced = new AdvancedConfig(dataFolder).config;
        assertEquals(321, advanced.getInt(
                        "Skills." + newParent + "." + subSkill + ".MaxBonusLevel.RetroMode"),
                "advanced.yml did not migrate " + subSkill + " to " + newParent);
        assertFalse(advanced.contains("Skills.Agility." + subSkill),
                "advanced.yml kept the dead Skills.Agility." + subSkill);

        final YamlConfiguration ranks = new RankConfig(dataFolder).config;
        assertEquals(321, ranks.getInt(newParent + "." + subSkill + ".RetroMode.Rank_1"),
                "skillranks.yml did not migrate " + subSkill + " to " + newParent);
        assertFalse(ranks.contains("Agility." + subSkill),
                "skillranks.yml kept the dead Agility." + subSkill);
    }

    @Test
    void everyMovedPathIsScopedToAFileThatActuallyDeclaresIt() {
        // ⚠️ The safety property of MovedPath, asserted directly. A move registered without its file
        // -- or against the wrong one -- silently applies to every config with a matching path, and
        // the concrete hazard is coreskills.yml: its dev copies still carry a root-level
        // `Agility.Roll` block from the pre-#10 per-sub-skill-switch schema. A bare, file-blind
        // `Agility.Roll` entry would "migrate" that dead key into a live-looking Parkour.Roll.
        for (SkillRenames.MovedPath move : SkillRenames.allMovedConfigPaths()) {
            assertFalse(move.fileName().isBlank(), "a move must name its file: " + move);
            assertTrue(SkillRenames.movedConfigPaths(move.fileName()).contains(move),
                    move + " is not returned when filtering by its own file name");
            assertTrue(SkillRenames.movedConfigPaths("coreskills.yml").isEmpty(),
                    "coreskills.yml must never be migrated: it carries a dead Agility.Roll key that "
                            + "a file-blind table would resurrect");
        }
    }

    @Test
    void theShippedAdvancedYmlDoesNotStillDefineTheOldRollPath(@TempDir Path dataFolder) {
        // The trap this closes: if advanced.yml kept its Skills.Agility.Roll block after the move,
        // copyMissingDefaults would write it into every user's file and the warning above would then
        // fire on a config mcMMO itself had just authored.
        final AdvancedConfig advanced = new AdvancedConfig(dataFolder);

        assertTrue(advanced.strandedLegacyPaths().isEmpty(),
                "the shipped advanced.yml must not carry both spellings; stranded="
                        + advanced.strandedLegacyPaths());
    }

    @Test
    void detectsTuningStrandedAtAKeyThatWasRetiredAltogether(@TempDir Path dataFolder)
            throws IOException {
        // GitHub #3, 2026-08-04. Husbandry's anti-exploit gate moved off the BREEDING and onto the XP
        // PAYOUT, which retired Skills.Husbandry.MultiBreed.MaxAdditionalAnimals and moved its
        // replacement into a different FILE. The old key is not renamed so much as deleted, but the
        // failure it leaves behind is identical: copyMissingDefaults never removes anything, so a
        // player who had deliberately tuned it keeps an edited-looking key nothing reads.
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                Skills:
                  Husbandry:
                    MultiBreed:
                      MaxAdditionalAnimals: 12
                """);

        final TestConfig loader = new TestConfig(dataFolder);

        assertEquals("experience.yml → ExploitFix.Husbandry.Breed_Xp_Awards_Per_Window",
                loader.strandedLegacyPaths()
                        .get("Skills.Husbandry.MultiBreed.MaxAdditionalAnimals"),
                "the warning must name the file too, because this move crosses one");
    }

    // --- Retuned shipped defaults (ConfigRetunes) ------------------------------------------------

    @Test
    void carriesAChangedDefaultOntoAnExistingFileThatNeverTouchedIt(@TempDir Path dataFolder)
            throws IOException {
        // The failure this whole mechanism exists for: copyMissingDefaults back-fills only ABSENT
        // keys, so a value edit in the bundled resource reaches nobody who has run the mod once.
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                General:
                  Enabled: true
                  MaxLevel: 100
                """);

        assertEquals(200, new TestConfig(dataFolder, MAX_LEVEL_DOUBLED).config()
                        .getInt("General.MaxLevel"),
                "a value still at the old shipped default is the definition of 'never touched'");
    }

    @Test
    void refusesToOverwriteAValueTheUserChose(@TempDir Path dataFolder) throws IOException {
        // The more important half. 55 is neither default, so it was typed on purpose, and this file
        // belongs to the player -- the same policy as warn-don't-rewrite on renamed sections.
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                General:
                  MaxLevel: 55
                """);

        assertEquals(55, new TestConfig(dataFolder, MAX_LEVEL_DOUBLED).config()
                        .getInt("General.MaxLevel"),
                "a customised value must survive a shipped-default change");
    }

    @Test
    void appliesARetuneOnceEvenIfTheOldValueComesBack(@TempDir Path dataFolder) throws IOException {
        // ⚠️ Why the version stamp exists. Value comparison alone cannot tell "never touched it"
        // from "put it back on purpose": without the stamp this second load would re-migrate, and a
        // player who wanted 100 could never keep it.
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                General:
                  MaxLevel: 100
                """);
        new TestConfig(dataFolder, MAX_LEVEL_DOUBLED);

        final Path file = dataFolder.resolve("test-config.yml");
        final YamlConfiguration restored = YamlConfiguration.loadConfiguration(file);
        restored.set("General.MaxLevel", 100);
        restored.save(file);

        assertEquals(100, new TestConfig(dataFolder, MAX_LEVEL_DOUBLED).config()
                        .getInt("General.MaxLevel"),
                "a spent retune must not fire again");
    }

    @Test
    void matchesTheOldDefaultAcrossYamlNumericTypes(@TempDir Path dataFolder) throws IOException {
        // ⚠️ SnakeYAML reads `50` as an Integer and `50.0` as a Double, so Object#equals would call a
        // hand-typed `50` "customised" and strand exactly the retune that needed to fire. A YAML
        // value the user never edited can still arrive in the other numeric type -- e.g. a config
        // written before a default gained its decimal point.
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                Skills:
                  Mining:
                    DoubleDrops:
                      ChanceMax: 50
                """);

        final List<ConfigRetunes.Retune> retune = List.of(
                new ConfigRetunes.Retune("test-config.yml", "Skills.Mining.DoubleDrops.ChanceMax",
                        50.0D, 75.0D, 1, "a fixture retune across numeric types"));

        assertEquals(75.0D,
                new TestConfig(dataFolder, retune).config()
                        .getDouble("Skills.Mining.DoubleDrops.ChanceMax"),
                0.0001D, "an int 50 on disk is the double 50.0 default, not a customisation");
    }

    @Test
    void leavesAFileWithNoRetunesCompletelyUnstamped(@TempDir Path dataFolder) throws IOException {
        // Every config in the mod is in this state today except experience.yml. Stamping a version
        // onto a file that has never been retuned would add a key nobody can explain, to every file,
        // forever.
        new TestConfig(dataFolder);

        assertFalse(Files.readString(dataFolder.resolve("test-config.yml"))
                        .contains(ConfigRetunes.VERSION_KEY),
                "a never-retuned config must not gain a version stamp");
    }

    @Test
    void stampsAFreshFileSoItsRetunesAreAlreadySpent(@TempDir Path dataFolder) throws IOException {
        // A brand-new file has the NEW defaults, so every retune for it is by definition already
        // applied. Without the writtenFresh signal the next load would read version 0 and reconsider
        // them all -- harmless only while no retune's old default is ever re-used as a new one.
        new TestConfig(dataFolder, MAX_LEVEL_DOUBLED);

        assertEquals(1, YamlConfiguration.loadConfiguration(dataFolder.resolve("test-config.yml"))
                        .getInt(ConfigRetunes.VERSION_KEY),
                "a freshly written file carries the current stamp");
        // ...and the fixture's own 100 is untouched: it is this file's shipped default, and a fresh
        // file is not a stale one.
        assertEquals(100, new TestConfig(dataFolder, MAX_LEVEL_DOUBLED).config()
                        .getInt("General.MaxLevel"));
    }

    @Test
    void neverInventsAKeyThatIsAbsentFromBothTheFileAndTheDefaults(@TempDir Path dataFolder)
            throws IOException {
        // Back-filling absent keys is copyMissingDefaults' job, and it runs immediately after this.
        // A retune must therefore do nothing at all with an absent path -- not write its newDefault
        // (which would resurrect a key deleted from the shipped resource) and not log a spurious
        // "keeping your value" line about a value that does not exist.
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                General:
                  Enabled: false
                """);

        final TestConfig loader = new TestConfig(dataFolder, List.of(
                new ConfigRetunes.Retune("test-config.yml", "General.RemovedSetting", 1, 2, 1,
                        "a retune whose key no longer exists anywhere")));

        assertFalse(loader.config().contains("General.RemovedSetting"),
                "a retune must not create a key that neither the file nor the defaults have");
        // ...and the file was still stamped, so a dead retune does not get reconsidered forever.
        assertEquals(1, YamlConfiguration.loadConfiguration(dataFolder.resolve("test-config.yml"))
                .getInt(ConfigRetunes.VERSION_KEY));
    }
}
