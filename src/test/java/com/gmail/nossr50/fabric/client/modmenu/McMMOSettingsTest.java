package com.gmail.nossr50.fabric.client.modmenu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.gmail.nossr50.config.YamlConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Guards the curated ModMenu option catalogue against the failure mode that would otherwise be
 * invisible: an option whose dotted key does not actually exist in the shipped config (a typo, or a
 * key renamed upstream). Such an entry would render a widget that silently writes to nothing.
 */
class McMMOSettingsTest {

    private static YamlConfiguration bundled(String resource) throws IOException {
        final InputStream in = McMMOSettingsTest.class.getResourceAsStream("/" + resource);
        assertNotNull(in, "bundled default resource missing from test classpath: " + resource);
        return YamlConfiguration.loadConfiguration(in);
    }

    @Test
    void catalogueIsNonEmptyAndSpansBothFiles() {
        assertFalse(McMMOSettings.all().isEmpty(), "catalogue should not be empty");
        final Set<String> files = new HashSet<>();
        McMMOSettings.all().forEach(s -> files.add(s.file()));
        assertTrue(files.contains(McMMOSettings.CONFIG_YML), "expected some config.yml settings");
        assertTrue(files.contains(McMMOSettings.EXPERIENCE_YML),
                "expected some experience.yml settings");
    }

    @Test
    void everyKeyExistsInBundledDefaultsWithMatchingType() throws IOException {
        final YamlConfiguration config = bundled(McMMOSettings.CONFIG_YML);
        final YamlConfiguration experience = bundled(McMMOSettings.EXPERIENCE_YML);

        for (ConfigSetting setting : McMMOSettings.all()) {
            final YamlConfiguration doc = switch (setting.file()) {
                case McMMOSettings.CONFIG_YML -> config;
                case McMMOSettings.EXPERIENCE_YML -> experience;
                default -> {
                    fail("catalogue references an unknown config file: " + setting.file());
                    yield null;
                }
            };

            final String where = setting.file() + ":" + setting.path();
            assertTrue(doc.contains(setting.path()),
                    "key missing from bundled default (typo or renamed upstream): " + where);

            final Object value = doc.get(setting.path());
            switch (setting.kind()) {
                case BOOLEAN -> assertInstanceOf(Boolean.class, value,
                        where + " is declared BOOLEAN but the default value is not a boolean");
                case INT, DOUBLE -> assertInstanceOf(Number.class, value,
                        where + " is declared numeric but the default value is not a number");
            }
        }
    }

    @Test
    void noDuplicateKeys() {
        final Set<String> seen = new HashSet<>();
        for (ConfigSetting setting : McMMOSettings.all()) {
            final String id = setting.file() + ":" + setting.path();
            assertTrue(seen.add(id), "duplicate catalogue entry for " + id);
        }
    }

    @Test
    void everySettingBelongsToADeclaredCategory() {
        for (ConfigSetting setting : McMMOSettings.all()) {
            assertTrue(McMMOSettings.categories().contains(setting.category()),
                    "setting " + setting.path() + " has orphan category " + setting.category());
            assertTrue(McMMOSettings.byCategory(setting.category()).contains(setting),
                    "byCategory did not return " + setting.path());
        }
    }
}
