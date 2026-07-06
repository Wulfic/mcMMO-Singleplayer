package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        @Override
        protected void loadKeys() {
            // no-op: tests read the protected config directly
        }

        YamlConfiguration config() {
            return config;
        }
    }

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
}
