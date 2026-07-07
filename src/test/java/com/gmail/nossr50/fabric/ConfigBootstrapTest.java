package com.gmail.nossr50.fabric;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the Phase 8 config bootstrap: loading every ported config from a temp directory wires
 * each into the {@link McMMOMod} service locator, writes the bundled defaults to disk, and unloads
 * cleanly. Runs MC-free — the bundled default {@code .yml}s live on the test classpath.
 */
class ConfigBootstrapTest {

    @AfterEach
    void tearDown() {
        // Reset the static locator so tests don't leak config state into one another.
        ConfigBootstrap.unload();
    }

    @Test
    void loadAllWiresEveryConfigAndWritesDefaults(@TempDir Path dataFolder) throws Exception {
        ConfigBootstrap.loadAll(dataFolder);

        assertNotNull(McMMOMod.getGeneralConfig(), "GeneralConfig should be wired");
        assertNotNull(McMMOMod.getExperienceConfig(), "ExperienceConfig should be wired");
        assertNotNull(McMMOMod.getCoreSkillsConfig(), "CoreSkillsConfig should be wired");
        assertNotNull(McMMOMod.getRankConfig(), "RankConfig should be wired");
        assertNotNull(McMMOMod.getSoundConfig(), "SoundConfig should be wired");
        assertNotNull(McMMOMod.getAdvancedConfig(), "AdvancedConfig should be wired");
        assertNotNull(McMMOMod.getTreasureConfig(), "TreasureConfig should be wired");

        // First run must materialise the default files on disk.
        assertTrue(Files.exists(dataFolder.resolve("config.yml")), "config.yml written");
        assertTrue(Files.exists(dataFolder.resolve("experience.yml")), "experience.yml written");
        assertTrue(Files.exists(dataFolder.resolve("skillranks.yml")), "skillranks.yml written");
    }

    @Test
    void loadAllCreatesMissingDataFolder(@TempDir Path parent) throws Exception {
        final Path nested = parent.resolve("does-not-exist-yet");
        assertFalse(Files.exists(nested));

        ConfigBootstrap.loadAll(nested);

        assertTrue(Files.isDirectory(nested), "bootstrap should create the config directory");
        assertNotNull(McMMOMod.getGeneralConfig());
    }

    @Test
    void unloadClearsWiredConfigs(@TempDir Path dataFolder) throws Exception {
        ConfigBootstrap.loadAll(dataFolder);
        assertNotNull(McMMOMod.getGeneralConfig());

        ConfigBootstrap.unload();

        assertNull(McMMOMod.getGeneralConfig());
        assertNull(McMMOMod.getExperienceConfig());
        assertNull(McMMOMod.getTreasureConfig());
    }
}
