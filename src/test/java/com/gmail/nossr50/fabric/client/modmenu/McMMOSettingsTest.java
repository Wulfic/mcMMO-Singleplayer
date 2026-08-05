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

    /**
     * The converse of {@link #everyKeyExistsInBundledDefaultsWithMatchingType()}, and the gap that
     * test could never close: it proves every <em>declared</em> key exists in the yml, so a shipped
     * cooldown key that never reaches the catalogue is invisible to it. That is exactly how
     * {@code Herdsmans_Call} shipped with a working ability, a working config key and no slider —
     * found by an audit rather than by the suite.
     *
     * <p>Deliberately driven off the shipped {@code config.yml} rather than {@code SuperAbilityType},
     * because the catalogue's contract is with the file: an ability with no cooldown key needs no
     * widget, and a key with no ability is a different defect that
     * {@code everyKeyExistsInBundledDefaults...} already covers from the other side.
     */
    @Test
    void everyCooldownKeyInConfigIsOfferedInTheCatalogue() throws IOException {
        final YamlConfiguration config = bundled(McMMOSettings.CONFIG_YML);
        final YamlConfiguration cooldowns = config.getConfigurationSection("Abilities.Cooldowns");
        assertNotNull(cooldowns, "config.yml has no Abilities.Cooldowns section at all");
        final Set<String> shipped = cooldowns.getKeys(false);
        assertFalse(shipped.isEmpty(),
                "no Abilities.Cooldowns keys found in config.yml — the test is asserting nothing");

        final Set<String> offered = new HashSet<>();
        McMMOSettings.all().stream()
                .map(ConfigSetting::path)
                .filter(path -> path.startsWith("Abilities.Cooldowns."))
                .forEach(path -> offered.add(path.substring("Abilities.Cooldowns.".length())));

        for (String ability : shipped) {
            assertTrue(offered.contains(ability),
                    "config.yml ships Abilities.Cooldowns." + ability + " but the ModMenu catalogue "
                            + "does not offer it — that super ability's cooldown has no slider");
        }
    }

    /**
     * Every {@code ExploitFix} key the shipped {@code experience.yml} carries must appear in the
     * catalogue — the same converse guard as
     * {@link #everyCooldownKeyInConfigIsOfferedInTheCatalogue()}, aimed at the section that needs it
     * most.
     *
     * <p><b>Why this section specifically.</b> The GitHub #9 audit found twelve anti-farm gates whose
     * config keys shipped, and read, and did nothing — no caller anywhere in the port. A player
     * reading {@code SnowGolemExcavation: true} believed they were protected and were not. Those are
     * wired now, and this test is what stops the gap re-opening from the other direction: a gate
     * added to the yml but never surfaced is invisible, exactly as {@code Herdsmans_Call}'s cooldown
     * was.
     *
     * <p><b>The rule it enforces:</b> a <em>getter</em> may be legacy-faithful and dead — the port
     * keeps {@code isPistonExploitPrevented}, which upstream also never calls — but a <em>shipped
     * key</em> may not, because only the key makes a promise to the player. That is why
     * {@code PreventPluginNPCInteraction} was deleted from the yml in the same pass rather than
     * given a switch: NPC plugins cannot exist in singleplayer, so no toggle could ever be honest.
     */
    @Test
    void everyExploitFixKeyIsOfferedInTheCatalogue() throws IOException {
        final YamlConfiguration experience = bundled(McMMOSettings.EXPERIENCE_YML);
        final Set<String> shipped = new HashSet<>();
        collectScalarPaths(experience, "ExploitFix", shipped);
        assertFalse(shipped.isEmpty(),
                "no ExploitFix keys found in experience.yml — the test is asserting nothing");

        final Set<String> offered = new HashSet<>();
        McMMOSettings.all().forEach(setting -> offered.add(setting.path()));

        for (String path : shipped) {
            assertTrue(offered.contains(path),
                    "experience.yml ships " + path + " but the Anti-Cheat tab does not offer it — "
                            + "an anti-farm gate a player cannot see or change");
        }
    }

    /** Collects the dotted paths of every scalar (non-section) leaf beneath {@code root}. */
    private static void collectScalarPaths(YamlConfiguration doc, String root, Set<String> into) {
        final YamlConfiguration section = doc.getConfigurationSection(root);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            final String path = root + "." + key;
            if (section.isConfigurationSection(key)) {
                collectScalarPaths(doc, path, into);
            } else {
                into.add(path);
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
