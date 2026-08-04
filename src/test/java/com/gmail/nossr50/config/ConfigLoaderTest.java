package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coverage for the {@link ConfigLoader} default-copy + merge flow, using {@code test-config.yml}
 * on the test classpath as the "bundled default" resource and a temp dir as the data folder.
 */
class ConfigLoaderTest {

    /** Minimal concrete loader over the test fixture resource. */
    private static final class TestConfig extends ConfigLoader {
        TestConfig(Path dataFolder) {
            super("test-config.yml", dataFolder);
        }

        TestConfig(Path dataFolder, List<ConfigRetunes.Retune> retunes) {
            super("test-config.yml", dataFolder, retunes);
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

    // --- The re-parented sub-skill warning (GitHub #4) -------------------------------------------

    @Test
    void detectsTuningStrandedAtAPathThatMoved(@TempDir Path dataFolder) throws IOException {
        // Roll moved from Skills.Agility.Roll to Skills.Parkour.Roll on 2026-08-03. Because
        // copyMissingDefaults back-fills only ABSENT keys, a user who had tuned the old block ends up
        // with BOTH: shipped defaults at the new path (which the code reads) and their own values at
        // the old one, silently ignored. That is the failure this warning exists to name.
        Files.writeString(dataFolder.resolve("test-config.yml"), """
                Skills:
                  Agility:
                    Roll:
                      ChanceMax: 42.0
                """);

        final TestConfig loader = new TestConfig(dataFolder);

        assertEquals("Skills.Parkour.Roll",
                loader.strandedLegacyPaths().get("Skills.Agility.Roll"),
                "the stranded block must be reported, pointing at where it moved to");
    }

    @Test
    void staysSilentForAConfigThatNeverHadTheOldPath(@TempDir Path dataFolder) {
        // The common case, and every freshly generated config. A warning here would cry wolf on every
        // boot for every user, which is how warnings stop being read.
        final TestConfig loader = new TestConfig(dataFolder);

        assertTrue(loader.strandedLegacyPaths().isEmpty(),
                "nothing is stranded in a config written from current defaults");
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
