package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link CoreSkillsConfig} against the real bundled {@code coreskills.yml} on the test
 * classpath, with a temp data folder.
 */
class CoreSkillsConfigTest {

    @Test
    void writesDefaultToDiskWhenMissing(@TempDir Path dataFolder) {
        new CoreSkillsConfig(dataFolder);
        assertTrue(Files.exists(dataFolder.resolve("coreskills.yml")));
    }

    @Test
    void primarySkillEnabledReadsExplicitTrue(@TempDir Path dataFolder) {
        final CoreSkillsConfig config = new CoreSkillsConfig(dataFolder);
        // coreskills.yml sets Acrobatics.Enabled: true explicitly.
        assertTrue(config.isPrimarySkillEnabled(PrimarySkillType.ACROBATICS));
    }

    @Test
    void primarySkillEnabledDefaultsTrueForUnlistedSkill(@TempDir Path dataFolder) {
        final CoreSkillsConfig config = new CoreSkillsConfig(dataFolder);
        // Mining has no entry in the bundled default -> defaults true.
        assertTrue(config.isPrimarySkillEnabled(PrimarySkillType.MINING));
    }
}
