package com.gmail.nossr50.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * MC-free coverage of the snakeyaml-backed {@link YamlConfiguration} accessor surface that
 * mcMMO's config classes depend on.
 */
class YamlConfigurationTest {

    private static final String SAMPLE = """
            General:
              Enabled: true
              MaxLevel: 100
              XpMultiplier: 1.5
              Locale: en_US
            Skills:
              Mining:
                DoubleDrops:
                  ChanceMax: 50.0
                Enabled: true
            Blacklist:
              - STONE
              - DIRT
            """;

    private static YamlConfiguration sample() throws IOException {
        return YamlConfiguration.loadConfiguration(
                new ByteArrayInputStream(SAMPLE.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsTypedScalarsByDottedPath() throws IOException {
        final YamlConfiguration config = sample();
        assertTrue(config.getBoolean("General.Enabled"));
        assertEquals(100, config.getInt("General.MaxLevel"));
        assertEquals(1.5D, config.getDouble("General.XpMultiplier"));
        assertEquals("en_US", config.getString("General.Locale"));
        assertEquals(50.0D, config.getDouble("Skills.Mining.DoubleDrops.ChanceMax"));
    }

    @Test
    void returnsDefaultsForMissingKeys() throws IOException {
        final YamlConfiguration config = sample();
        assertEquals(7, config.getInt("General.DoesNotExist", 7));
        assertTrue(config.getBoolean("Nope.Missing", true));
        assertEquals("fallback", config.getString("Nope.Missing", "fallback"));
        assertNull(config.getString("Nope.Missing"));
    }

    @Test
    void containsDistinguishesPresentFromAbsent() throws IOException {
        final YamlConfiguration config = sample();
        assertTrue(config.contains("General.Enabled"));
        assertTrue(config.contains("Skills.Mining.DoubleDrops.ChanceMax"));
        assertFalse(config.contains("Skills.Mining.Nonexistent"));
    }

    @Test
    void sectionViewIsRelativeAndReflectsRoot() throws IOException {
        final YamlConfiguration config = sample();
        assertTrue(config.isConfigurationSection("Skills.Mining"));
        final YamlConfiguration mining = config.getConfigurationSection("Skills.Mining");
        assertTrue(mining.getBoolean("Enabled"));
        assertEquals(50.0D, mining.getDouble("DoubleDrops.ChanceMax"));
        assertNull(config.getConfigurationSection("Skills.Mining.Enabled")); // leaf, not a section
    }

    @Test
    void getKeysShallowAndDeep() throws IOException {
        final YamlConfiguration config = sample();
        assertIterableEquals(List.of("General", "Skills", "Blacklist"),
                List.copyOf(config.getKeys(false)));
        assertTrue(config.getKeys(true).contains("Skills.Mining.DoubleDrops.ChanceMax"));
        assertFalse(config.getKeys(false).contains("Skills.Mining"));
    }

    @Test
    void readsStringLists() throws IOException {
        assertIterableEquals(List.of("STONE", "DIRT"), sample().getStringList("Blacklist"));
        assertTrue(sample().getStringList("General.Locale").isEmpty()); // not a list
    }

    @Test
    void setCreatesIntermediateSectionsAndRemovesOnNull() throws IOException {
        final YamlConfiguration config = sample();
        config.set("New.Nested.Value", 42);
        assertEquals(42, config.getInt("New.Nested.Value"));
        assertTrue(config.isConfigurationSection("New.Nested"));

        config.set("General.Enabled", null);
        assertFalse(config.contains("General.Enabled"));
    }

    @Test
    void saveThenReloadRoundTrips(@TempDir Path tmp) throws IOException {
        final YamlConfiguration config = sample();
        config.set("General.MaxLevel", 250);
        final Path file = tmp.resolve("out.yml");
        config.save(file);

        final YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(file);
        assertEquals(250, reloaded.getInt("General.MaxLevel"));
        assertEquals("en_US", reloaded.getString("General.Locale"));
        assertIterableEquals(List.of("STONE", "DIRT"), reloaded.getStringList("Blacklist"));
    }

    @Test
    void emptyConfigIsUsable() {
        final YamlConfiguration config = YamlConfiguration.empty();
        assertFalse(config.contains("anything"));
        assertTrue(config.getKeys(false).isEmpty());
        assertEquals(3, config.getInt("missing", 3));
    }
}
