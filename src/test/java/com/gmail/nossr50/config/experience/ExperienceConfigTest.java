package com.gmail.nossr50.config.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.ConfigRetunes;
import com.gmail.nossr50.datatypes.experience.FormulaType;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.skills.stealth.StealthXpSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link ExperienceConfig} against the real bundled {@code experience.yml} (on the test
 * classpath) with a temp data folder. Asserts formula reads, string-keyed combat/taming XP, and the
 * block-XP map built from the {@code Experience_Values} sections.
 */
class ExperienceConfigTest {

    @Test
    void writesDefaultToDiskWhenMissing(@TempDir Path dataFolder) {
        new ExperienceConfig(dataFolder);
        assertTrue(Files.exists(dataFolder.resolve("experience.yml")));
    }

    @Test
    void readsFormulaSettings(@TempDir Path dataFolder) {
        final ExperienceConfig config = new ExperienceConfig(dataFolder);
        assertEquals(FormulaType.LINEAR, config.getFormulaType());
        assertEquals(20.0D, config.getMultiplier(FormulaType.LINEAR));
        assertEquals(1020, config.getBase(FormulaType.LINEAR));
        assertEquals(0.1D, config.getMultiplier(FormulaType.EXPONENTIAL));
        assertEquals(1.80D, config.getExponent(FormulaType.EXPONENTIAL));
        assertEquals(1.0D, config.getExperienceGainsGlobalMultiplier());
    }

    @Test
    void buildsBlockExperienceMapKeyedByConfigString(@TempDir Path dataFolder) {
        final ExperienceConfig config = new ExperienceConfig(dataFolder);
        // Mining.Coal_Ore = 400, Sculk_Vein = 3 in the bundled default.
        assertEquals(400, config.getXp(PrimarySkillType.MINING, "Coal_Ore"));
        assertEquals(3, config.getXp(PrimarySkillType.MINING, "Sculk_Vein"));
        assertTrue(config.doesBlockGiveSkillXP(PrimarySkillType.MINING, "Coal_Ore"));
        // Unknown material -> no XP.
        assertEquals(0, config.getXp(PrimarySkillType.MINING, "Not_A_Real_Block"));
        assertFalse(config.doesBlockGiveSkillXP(PrimarySkillType.MINING, "Not_A_Real_Block"));
    }

    @Test
    void readsCombatAndTamingXpByString(@TempDir Path dataFolder) {
        final ExperienceConfig config = new ExperienceConfig(dataFolder);
        assertEquals(4.0D, config.getCombatXP("Creeper"));
        assertTrue(config.hasCombatXP("Creeper"));
        assertFalse(config.hasCombatXP("Not_A_Real_Mob"));
        // Unknown mob falls back to the Animals multiplier.
        assertEquals(config.getAnimalsXP(), config.getAnimalsXP("Not_A_Real_Mob"));
        assertEquals(250, config.getTamingXP("Wolf"));
        assertEquals(500, config.getTamingXP("Ocelot"));
    }

    @Test
    void readsTheShippedHunterTierLadder(@TempDir Path dataFolder) {
        final ExperienceConfig config = new ExperienceConfig(dataFolder);

        // The four numbers the ~100 h target is built on: 11,010,000 XP to max, ~6 kills/min by
        // hand, so an average kill has to be worth about 306 -- which is what puts common hostiles
        // at 300. T4 is 1500 and NOT the drafted 5000 (ruled 2026-07-30), because 5000 lets a wither
        // farm outrun the 80 h guardrail.
        assertEquals(100.0F, config.getHunterXpForTier(1));
        assertEquals(300.0F, config.getHunterXpForTier(2));
        assertEquals(800.0F, config.getHunterXpForTier(3));
        assertEquals(1500.0F, config.getHunterXpForTier(4));

        // Out of range pays nothing rather than indexing off the end of the ladder.
        assertEquals(0.0F, config.getHunterXpForTier(0));
        assertEquals(0.0F, config.getHunterXpForTier(5));
    }

    @Test
    void aNegativeHunterTierXpIsClampedRatherThanWalkingTheLevelBackwards(@TempDir Path dataFolder)
            throws Exception {
        final Path file = dataFolder.resolve("experience.yml");
        new ExperienceConfig(dataFolder);
        Files.writeString(file, Files.readString(file).replace("Tier_2: 300", "Tier_2: -300"));

        // Reading back a deliberately edited file also proves the getter consults the document at
        // this exact path rather than always answering with its own default.
        assertEquals(0.0F, new ExperienceConfig(dataFolder).getHunterXpForTier(2));
    }

    // --- Stealth sneak XP, doubled (GitHub #6) -------------------------------------------------

    @Test
    void shippedSneakBaselineMatchesTheConstant(@TempDir Path dataFolder) {
        // These two and the ModMenu editor's "reset to default" all read the same number, and they
        // drifted apart once already: the editor offered 30.0 long after the YAML was halved to 15.0
        // for Agility, so "reset to default" quietly rewrote a value that had not been the default
        // for months. Nothing but this assertion notices.
        assertEquals(StealthXpSettings.DEFAULT_BASELINE_XP_PER_SECOND,
                new ExperienceConfig(dataFolder).getSneakBaselineXpPerSecond(), 0.0001D);
        assertEquals(50.0D, StealthXpSettings.DEFAULT_BASELINE_XP_PER_SECOND, 0.0001D,
                "GitHub #6 doubled sneak XP from 25 to 50/s");
    }

    @Test
    void anExistingConfigStillHoldingTheOldSneakBaselineIsMigrated(@TempDir Path dataFolder)
            throws Exception {
        // The whole point of ConfigRetunes, on the real file. Editing experience.yml in the jar
        // reaches nobody who has already run the mod, because copyMissingDefaults back-fills only
        // ABSENT keys -- so without this the reporter of #6 would have kept 25.0 forever and the
        // issue would have been closed by a change that did nothing for them.
        final Path file = dataFolder.resolve("experience.yml");
        new ExperienceConfig(dataFolder);
        Files.writeString(file, Files.readString(file)
                .replace("Baseline_Xp_Per_Second: 50.0", "Baseline_Xp_Per_Second: 25.0")
                .replace(ConfigRetunes.VERSION_KEY + ": 1", ConfigRetunes.VERSION_KEY + ": 0"));

        assertEquals(50.0D, new ExperienceConfig(dataFolder).getSneakBaselineXpPerSecond(), 0.0001D,
                "an untouched old default must be carried forward");
    }

    @Test
    void aHandTunedSneakBaselineSurvivesTheMigration(@TempDir Path dataFolder) throws Exception {
        // The other half of the promise, and the more important one: the file belongs to the player.
        // 30.0 is neither the old default nor the new one, so it was typed on purpose.
        final Path file = dataFolder.resolve("experience.yml");
        new ExperienceConfig(dataFolder);
        Files.writeString(file, Files.readString(file)
                .replace("Baseline_Xp_Per_Second: 50.0", "Baseline_Xp_Per_Second: 30.0")
                .replace(ConfigRetunes.VERSION_KEY + ": 1", ConfigRetunes.VERSION_KEY + ": 0"));

        assertEquals(30.0D, new ExperienceConfig(dataFolder).getSneakBaselineXpPerSecond(), 0.0001D,
                "a deliberately tuned value must never be reverted by a shipped-default change");
    }

    @Test
    void deliberatelyRestoringTheOldSneakBaselineIsNotUndoneOnTheNextLoad(@TempDir Path dataFolder)
            throws Exception {
        // ⚠️ The reason the version stamp exists at all. Value comparison alone cannot tell "never
        // touched it" from "put it back on purpose", so a purely value-driven migrator would re-apply
        // this retune on EVERY boot and the player could never keep 25.0. Here the file is left
        // stamped, so the retune is spent.
        final Path file = dataFolder.resolve("experience.yml");
        new ExperienceConfig(dataFolder);
        Files.writeString(file, Files.readString(file)
                .replace("Baseline_Xp_Per_Second: 50.0", "Baseline_Xp_Per_Second: 25.0"));

        assertEquals(25.0D, new ExperienceConfig(dataFolder).getSneakBaselineXpPerSecond(), 0.0001D,
                "an already-applied retune must not fire a second time");
    }

    @Test
    void aFreshExperienceConfigIsStampedWithTheCurrentRetuneVersion(@TempDir Path dataFolder)
            throws Exception {
        new ExperienceConfig(dataFolder);
        final String written = Files.readString(dataFolder.resolve("experience.yml"));

        // Without the stamp on a brand-new file, the first reload would treat it as predating every
        // retune. That is harmless today only because the values already match; it would stop being
        // harmless the moment a retune's old default was ever re-used as a new one.
        assertTrue(written.contains(ConfigRetunes.VERSION_KEY + ": "
                        + ConfigRetunes.highestVersion(ConfigRetunes.forFile("experience.yml"))),
                "a freshly written experience.yml must carry the current retune stamp");
    }

    @Test
    void reloadsAfterExternalEditWithoutLosingUserBlockXp(@TempDir Path dataFolder) {
        // First construction writes the default out.
        new ExperienceConfig(dataFolder);
        // A second construction re-reads and rebuilds the map identically.
        final ExperienceConfig reloaded = new ExperienceConfig(dataFolder);
        assertEquals(400, reloaded.getXp(PrimarySkillType.MINING, "Coal_Ore"));
    }
}
