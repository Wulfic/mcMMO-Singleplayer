package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
