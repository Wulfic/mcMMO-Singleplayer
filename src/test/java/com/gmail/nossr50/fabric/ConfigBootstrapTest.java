package com.gmail.nossr50.fabric;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.McTestRegistries;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies the Phase 8 config bootstrap: loading every ported config from a temp directory wires
 * each into the {@link McMMOMod} service locator, writes the bundled defaults to disk, and unloads
 * cleanly.
 *
 * <p>⚠️⚠️ <b>This class used to say "Runs MC-free" and that comment had expired.</b>
 * {@code ConfigBootstrap.loadAll} constructs {@code RepairConfig}, whose {@code loadKeys} resolves
 * every repairable through {@code Materials.item(...)} — a registry lookup. Without the bootstrap
 * below, {@code Registries.<clinit>} runs un-bootstrapped and throws.
 *
 * <p><b>Why that was worse than one red test.</b> A failed static initialiser is permanent for the
 * life of the JVM: every later class in the same test fork that touches {@code Registries} then dies
 * with {@code NoClassDefFoundError}. This class carried no {@code @BeforeAll}, so whether it poisoned
 * its fork depended entirely on whether some <em>other</em> test class happened to bootstrap first —
 * i.e. on how Gradle distributed classes across the two forks. It passed for a long time on that
 * luck, and adding two unrelated test classes was enough to redistribute the forks and take roughly
 * thirty tests down with it. Bootstrapping here makes the class order-independent.
 */
class ConfigBootstrapTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
    }

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
