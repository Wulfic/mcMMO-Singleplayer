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
        // Acrobatics.Dodge: ChanceMax 20.0, MaxBonusLevel.Standard 100, DamageModifier 2.0.
        assertEquals(20.0D, config.getMaximumProbability(SubSkillType.ACROBATICS_DODGE), 0.0001D);
        assertEquals(100, config.getMaxBonusLevel(SubSkillType.ACROBATICS_DODGE));
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
}
