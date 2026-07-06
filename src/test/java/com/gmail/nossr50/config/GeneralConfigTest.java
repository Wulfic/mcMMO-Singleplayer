package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
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
}
