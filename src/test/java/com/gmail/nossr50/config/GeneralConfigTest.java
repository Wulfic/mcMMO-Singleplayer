package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the trimmed {@link GeneralConfig} against the real bundled {@code config.yml} on the
 * test classpath, with a temp data folder. Covers the SP-core getters, the String-keyed
 * material/entity/ability lookups, and the Hardcore setters.
 */
class GeneralConfigTest {

    @Test
    void writesDefaultToDiskWhenMissing(@TempDir Path dataFolder) {
        new GeneralConfig(dataFolder);
        assertTrue(Files.exists(dataFolder.resolve("config.yml")));
    }

    @Test
    void readsGeneralAndLevelCapSettings(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        assertEquals(10, config.getSaveInterval());
        // Power_Level_Cap default 0 -> unlimited.
        assertEquals(Integer.MAX_VALUE, config.getPowerLevelCap());
        // Per-skill level cap default 0 -> unlimited.
        assertEquals(Integer.MAX_VALUE, config.getLevelCap(PrimarySkillType.MINING));
    }

    @Test
    void milestoneAdvancementDefaults(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        // Advancement Plaques support ships on by default with a 100-level round-level bracket.
        assertTrue(config.getMilestoneAdvancementsEnabled());
        assertEquals(100, config.getMilestoneLevelInterval());
    }

    @Test
    void milestoneLevelIntervalIsClampedAboveZero(@TempDir Path dataFolder) {
        final GeneralConfig generalConfig = new GeneralConfig(dataFolder);
        // Even if someone sets a nonsensical 0/negative interval, the getter never returns something
        // that would divide-by-zero in the crossing math. (config is protected in ConfigLoader, which
        // shares this package.)
        generalConfig.config.set("General.Milestone_Advancements.Level_Interval", 0);
        assertTrue(generalConfig.getMilestoneLevelInterval() >= 1);
    }

    @Test
    void readsItemAndAbilitySettings(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        assertEquals("FEATHER", config.getChimaeraItemName());
        assertEquals(1, config.getChimaeraUseCost());
        assertTrue(config.getChimaeraEnabled());
        assertEquals(1000, config.getTreeFellerThreshold());
    }

    @Test
    void hardcoreSettersRoundTrip(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        config.setHardcoreVampirismEnabled(PrimarySkillType.SWORDS, true);
        assertTrue(config.getHardcoreVampirismEnabled(PrimarySkillType.SWORDS));
        config.setHardcoreDeathStatPenaltyPercentage(42.0D);
        assertEquals(42.0D, config.getHardcoreDeathStatPenaltyPercentage());
    }

    @Test
    void lilyPadDoubleDropsAlwaysDisabled(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        // The exploit guard short-circuits regardless of config content.
        assertFalse(config.getDoubleDropsEnabled(PrimarySkillType.HERBALISM, "Lily_Pad"));
    }

    @Test
    void greenThumbReplantDefaultsTrueForUnknownCrop(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        assertTrue(config.isGreenThumbReplantableCrop("Not_A_Real_Crop"));
    }

    @Test
    void superAbilityCooldownTypedOverloadDelegatesToStringKey(@TempDir Path dataFolder) {
        final GeneralConfig config = new GeneralConfig(dataFolder);
        // SuperAbilityType.toString() yields the PascalCase config key (e.g. "Super_Breaker").
        assertEquals(config.getCooldown("Super_Breaker"),
                config.getCooldown(SuperAbilityType.SUPER_BREAKER));
        assertEquals(config.getMaxLength("Super_Breaker"),
                config.getMaxLength(SuperAbilityType.SUPER_BREAKER));
    }
}
