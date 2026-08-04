package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.skills.mining.MiningManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link AdvancedConfig} against the real bundled {@code advanced.yml} on the test
 * classpath, with a temp data folder.
 *
 * <p>{@code McMMOMod.isRetroModeEnabled()} is {@code false} in unit tests (config service un-wired),
 * so the retro-mode-dependent getters resolve to their Standard-scaling branch — which these tests
 * assert.
 */
class AdvancedConfigTest {

    @Test
    void writesDefaultToDiskWhenMissing(@TempDir Path dataFolder) {
        new AdvancedConfig(dataFolder);
        assertTrue(Files.exists(dataFolder.resolve("advanced.yml")));
    }

    @Test
    void readsGeneralAbilitySettings(@TempDir Path dataFolder) {
        final AdvancedConfig config = new AdvancedConfig(dataFolder);
        assertEquals(0, config.getStartingLevel());
        assertEquals(5, config.getEnchantBuff());
    }

    @Test
    void retroModeOffResolvesStandardScaling(@TempDir Path dataFolder) {
        final AdvancedConfig config = new AdvancedConfig(dataFolder);
        // Standard: IncreaseLevel 5 / CapLevel 100 (RetroMode would be 50 / 1000).
        assertEquals(5, config.getAbilityLength());
        assertEquals(100, config.getAbilityLengthCap());
    }

    @Test
    void readsPerSubSkillTuning(@TempDir Path dataFolder) {
        final AdvancedConfig config = new AdvancedConfig(dataFolder);
        // Agility.Dodge: ChanceMax 20.0, MaxBonusLevel.Standard 100, DamageModifier 2.0.
        assertEquals(20.0D, config.getMaximumProbability(SubSkillType.AGILITY_DODGE), 0.0001D);
        assertEquals(100, config.getMaxBonusLevel(SubSkillType.AGILITY_DODGE));
        assertEquals(2.0D, config.getDodgeDamageModifier(), 0.0001D);
    }

    @Test
    void readsNotificationActionBarFlags(@TempDir Path dataFolder) {
        final AdvancedConfig config = new AdvancedConfig(dataFolder);
        // AbilityOff: Enabled true, SendCopyOfMessageToChat false.
        assertTrue(config.doesNotificationUseActionBar(NotificationType.ABILITY_OFF));
        assertFalse(config.doesNotificationSendCopyToChat(NotificationType.ABILITY_OFF));
        // LevelUps: SendCopyOfMessageToChat true.
        assertTrue(config.doesNotificationSendCopyToChat(NotificationType.LEVEL_UP_MESSAGE));
    }

    @Test
    void theHunterRangedMultiplierIsShippedAtOneAndDeclaredInTheFile(@TempDir Path dataFolder)
            throws Exception {
        final AdvancedConfig config = new AdvancedConfig(dataFolder);

        // 1.0 = the ruled behaviour, unchanged. A knob whose default alters the ruling would be a
        // config that lies.
        assertEquals(1.0D, config.getHunterMasteryRangedDamageMultiplier(), 0.0001D);

        // ...and the key is really in the shipped document, not merely in the getter's fallback.
        // Without this, a player would never find the knob and a "turn it down" instruction in the
        // §G notes would refer to a line that does not exist. The written file IS the bundled
        // resource, so asserting on it proves the resource declares the path.
        //
        // A substring search is safe here only because the token is long and unique — contrast the
        // Hunter stage-2 trap where asserting on "kills" passed unconditionally against "skills".
        final String shipped = Files.readString(dataFolder.resolve("advanced.yml"));
        assertTrue(shipped.contains("Ranged_Damage_Multiplier: 1.0"),
                "advanced.yml must ship the Hunter ranged tuning knob");
    }

    @Test
    void aNegativeHunterRangedMultiplierIsClampedRatherThanInverted(@TempDir Path dataFolder)
            throws Exception {
        // Earned mastery must never become a penalty. A hand-edited -1.0 would otherwise make an
        // archer's arrows hit their best-known creature for LESS than an unmastered one, which is a
        // failure no player could ever diagnose.
        final Path file = dataFolder.resolve("advanced.yml");
        new AdvancedConfig(dataFolder);
        Files.writeString(file, Files.readString(file)
                .replace("Ranged_Damage_Multiplier: 1.0", "Ranged_Damage_Multiplier: -1.0"));

        // Reading back a deliberately edited file also proves the getter consults the document at
        // this exact path rather than always answering with its own default.
        assertEquals(0.0D, new AdvancedConfig(dataFolder).getHunterMasteryRangedDamageMultiplier(),
                0.0001D);
    }

    // --- Super Breaker's bonus-drop chance boost (GitHub #5) ------------------------------------

    @Test
    void theShippedSuperBreakerDropChanceMultiplierDoublesTheOdds(@TempDir Path dataFolder)
            throws Exception {
        final AdvancedConfig config = new AdvancedConfig(dataFolder);

        assertEquals(MiningManager.DEFAULT_SUPER_BREAKER_DROP_CHANCE_MULTIPLIER,
                config.getSuperBreakerBonusDropChanceMultiplier(), 0.0001D);

        // The key must be in the shipped document, not only in the getter's fallback: this one is a
        // deliberate divergence from legacy, so an operator who wants the old behaviour back has to
        // be able to find the line and set it to 1.0.
        final String shipped = Files.readString(dataFolder.resolve("advanced.yml"));
        assertTrue(shipped.contains("BonusDropChanceMultiplier: 2.0"),
                "advanced.yml must ship the Super Breaker drop-chance knob");
    }

    @Test
    void aSuperBreakerDropChanceMultiplierBelowOneIsRefused(@TempDir Path dataFolder)
            throws Exception {
        // A super ability that LOWERS your drop rate is undiagnosable from in-game feedback: the
        // player sees fewer ores while their headline ability is running and has no way to attribute
        // it. Refused back to 1.0 (legacy behaviour) with a warning rather than honoured.
        final Path file = dataFolder.resolve("advanced.yml");
        new AdvancedConfig(dataFolder);
        Files.writeString(file, Files.readString(file)
                .replace("BonusDropChanceMultiplier: 2.0", "BonusDropChanceMultiplier: 0.5"));

        assertEquals(MiningManager.MIN_SUPER_BREAKER_DROP_CHANCE_MULTIPLIER,
                new AdvancedConfig(dataFolder).getSuperBreakerBonusDropChanceMultiplier(), 0.0001D);
    }

    @Test
    void aSuperBreakerDropChanceMultiplierOfOneIsHonouredAsLegacyBehaviour(@TempDir Path dataFolder)
            throws Exception {
        // 1.0 is the documented escape hatch, and it sits exactly on the refusal boundary — an
        // off-by-one guard (`<= 1.0`) would log a warning at the one value the docs tell people to
        // use. Reading it back also proves the getter consults this exact path.
        final Path file = dataFolder.resolve("advanced.yml");
        new AdvancedConfig(dataFolder);
        Files.writeString(file, Files.readString(file)
                .replace("BonusDropChanceMultiplier: 2.0", "BonusDropChanceMultiplier: 1.0"));

        assertEquals(1.0D,
                new AdvancedConfig(dataFolder).getSuperBreakerBonusDropChanceMultiplier(), 0.0001D);
    }

    @Test
    void theShippedHunterTierOverridesAreTheTwoAttributeBlindMobsAndNothingElse(
            @TempDir Path dataFolder) {
        final AdvancedConfig config = new AdvancedConfig(dataFolder);

        // Both are creatures whose danger is not expressible as an attribute: a ghast has 10 health
        // and no ATTACK_DAMAGE entry at all, and a wither skeleton's is the inherited default 2.0.
        assertEquals(3, config.getHunterTierOverride("Ghast"));
        assertEquals(3, config.getHunterTierOverride("Wither_Skeleton"));

        // 0 means "derive it" -- the table is an exception list, not a mob table, and the whole
        // point of D-HU5 is that an unlisted creature gets a sane tier rather than a silent zero.
        assertEquals(0, config.getHunterTierOverride("Zombie"));
        assertEquals(0, config.getHunterTierOverride("Some_Modded_Beast"));
    }

    @Test
    void anOutOfRangeHunterTierOverrideIsRefusedRatherThanClamped(@TempDir Path dataFolder)
            throws Exception {
        final Path file = dataFolder.resolve("advanced.yml");
        new AdvancedConfig(dataFolder);
        Files.writeString(file, Files.readString(file).replace("Ghast: 3", "Ghast: 7"));

        // Refused, not clamped to 4. A hand-written 7 means the operator misread the scale, and
        // guessing "they meant boss" for a mob the game can classify itself is the worse answer.
        assertEquals(0, new AdvancedConfig(dataFolder).getHunterTierOverride("Ghast"));
    }

    @Test
    void aHunterTierOverrideForAMobIdContainingADotStillResolves(@TempDir Path dataFolder)
            throws Exception {
        // ⚠️ The stage-2 trap, in a new section. A registry path may legally contain a '.' and this
        // config's addresses are dot-delimited, so reading the table as
        // config.getInt("Skills.Hunter.Tiers.Overrides." + key) would look for this entry inside a
        // phantom "Dread" subsection and find nothing. Vanilla ids have no dots, so every assertion
        // above passes either way -- which is exactly why this test has to exist separately.
        final Path file = dataFolder.resolve("advanced.yml");
        new AdvancedConfig(dataFolder);

        // ⚠️ The indent is READ off the file, never assumed. What lands on disk is not the bundled
        // resource -- ConfigLoader writes the defaults out through YamlConfiguration#save, which
        // re-dumps the parsed tree at SnakeYAML's own two-space indent and drops every comment. A
        // fixture that hard-codes the resource's indentation silently matches nothing, edits
        // nothing, and then fails as if the reader were broken.
        final String shipped = Files.readString(file);
        final Matcher ghast = Pattern.compile("(?m)^(\\s*)Ghast: 3$").matcher(shipped);
        assertTrue(ghast.find(), () -> "expected a Ghast override in the written advanced.yml");
        Files.writeString(file, new StringBuilder(shipped)
                .insert(ghast.end(), "\n" + ghast.group(1) + "Dread.beast: 4").toString());

        assertEquals(4, new AdvancedConfig(dataFolder).getHunterTierOverride("Dread.beast"));
        // The sibling it was inserted next to must still read, or the fixture broke the section
        // rather than extending it.
        assertEquals(3, new AdvancedConfig(dataFolder).getHunterTierOverride("Ghast"));
    }

    @Test
    void trophyHunterShipsAtHalfChanceAndNotTheHundredPercentBlockDropsUse(@TempDir Path dataFolder) {
        final AdvancedConfig config = new AdvancedConfig(dataFolder);

        // ⚠️ 50, not the 100 that Herbalism's and Mining's double drops carry. Those are blocks;
        // this is the mob economy, and at rank 4 it reaches bosses -- 100 would mean two nether
        // stars from every wither. Asserted as a literal so a retune has to come through this test.
        assertEquals(50.0D, config.getMaximumProbability(SubSkillType.HUNTER_TROPHY_HUNTER), 0.0001D);

        // Standard scaling, because McMMOMod.isRetroModeEnabled() is false with the config service
        // un-wired in a unit test (see the class javadoc). RetroMode's 1000 is pinned in the file.
        assertEquals(100, config.getMaxBonusLevel(SubSkillType.HUNTER_TROPHY_HUNTER));
    }

    @Test
    void trophyHuntersScalingCapIsDeclaredRatherThanCollectedFromTheGettersOwnDefault(
            @TempDir Path dataFolder) throws Exception {
        // ⚠️ This is not redundant with the assertion above, and MaxBonusLevel is the reason.
        // getMaxBonusLevel falls back to 100 for Standard and 1000 for RetroMode — exactly the two
        // values shipped — so if the section were deleted tomorrow, every read would keep answering
        // the right number and nothing would notice until an operator went looking for a knob that
        // was not there. Asserted through the parser rather than as a substring of the raw document
        // (the stage-2 "skills" contains "kills" trap).
        new AdvancedConfig(dataFolder);
        final YamlConfiguration written =
                YamlConfiguration.loadConfiguration(dataFolder.resolve("advanced.yml"));
        assertTrue(written.contains("Skills.Hunter.TrophyHunter.ChanceMax"),
                "advanced.yml must ship the Trophy Hunter chance ceiling");
        assertTrue(written.contains("Skills.Hunter.TrophyHunter.MaxBonusLevel.RetroMode"),
                "advanced.yml must ship the Trophy Hunter RetroMode scaling cap");
        assertEquals(1000, written.getInt("Skills.Hunter.TrophyHunter.MaxBonusLevel.RetroMode"));
    }
}
