package com.gmail.nossr50.fabric.client.modmenu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.CoreSkillsConfig;
import com.gmail.nossr50.config.YamlConfiguration;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards the Skills tab — the per-skill master switches in {@code coreskills.yml}, reachable from the
 * ModMenu config screen since 2026-08-18.
 *
 * <h2>Why this is a separate class from {@link McMMOSettingsTest}</h2>
 *
 * That class asks whether a catalogue key <em>exists in a yml file</em>, and
 * {@link CatalogueKeysReachCodeTest} asks whether some config getter <em>reads the literal</em>.
 * Neither can speak about this family:
 *
 * <ul>
 *   <li>The rows are <b>generated</b> from {@code PrimarySkillType.values()}, so the interesting
 *       question is not "does this key exist" but "is every skill covered, and only real skills".</li>
 *   <li>{@code CoreSkillsConfig} reads the key as {@code config.getBoolean(enabledPath(skill), true)}
 *       — <b>there is no string literal anywhere</b> for a source scan to find. The reachability
 *       guard exempts this file for that reason, and this class is the exemption's price: what it
 *       cannot prove by reading source, this proves by actually doing it.</li>
 * </ul>
 *
 * <p>⚠️ The end-to-end test below is the one that matters. Everything else here compares a list to an
 * enum; only {@link #togglingARowOffActuallyDisablesTheSkill} proves the widget → yml → config chain
 * carries a value from one end to the other.
 */
class CoreSkillsCatalogueTest {

    private static final String SKILLS_TAB = "Skills";

    private static List<ConfigSetting> masterSwitchRows() {
        final List<ConfigSetting> rows = new ArrayList<>();
        for (ConfigSetting setting : McMMOSettings.all()) {
            if (McMMOSettings.CORESKILLS_YML.equals(setting.file())) {
                rows.add(setting);
            }
        }
        return rows;
    }

    /**
     * Every skill has exactly one switch, at the path {@code CoreSkillsConfig} itself computes.
     *
     * <p>Driven off {@code values()} rather than a list written here — a test that transcribes the
     * roster fails in the same direction as the code it is guarding, which is how Cooking reached
     * neither hand-kept array in this file and how {@code Herdsmans_Call} shipped with no slider.
     */
    @Test
    void everySkillHasExactlyOneMasterSwitchRow() {
        final Map<String, ConfigSetting> byPath = new HashMap<>();
        for (ConfigSetting row : masterSwitchRows()) {
            assertNull(byPath.put(row.path(), row), "two Skills rows share the path " + row.path());
        }

        for (PrimarySkillType skill : PrimarySkillType.values()) {
            final String path = CoreSkillsConfig.enabledPath(skill);
            final ConfigSetting row = byPath.remove(path);
            assertNotNull(row, skill + " has no master switch on the Skills tab — a player cannot "
                    + "turn it off without hand-editing coreskills.yml, which is the gap the tab "
                    + "exists to close");
            assertEquals(SKILLS_TAB, row.category(), path + " is not on the Skills tab");
            assertEquals(ConfigSetting.Kind.BOOLEAN, row.kind(), path + " must be a toggle");
            assertTrue(row.defBoolean(), path + " must default to ON — a skill switched off by "
                    + "default would silently disable itself for a player who never opened the "
                    + "screen");
            assertNotNull(row.tooltip(), path + " has no tooltip. Every row must say that the level "
                    + "is kept and that the change applies on the next world load (ruling S-1); "
                    + "without it, a player toggling from the pause menu reads the delay as a bug");
            assertTrue(row.tooltip().contains("next world load"),
                    path + "'s tooltip does not mention that it applies on the next world load");
        }

        assertTrue(byPath.isEmpty(), "the Skills tab offers switches for things that are not skills: "
                + byPath.keySet() + " — each writes a key nothing will ever read");
    }

    /**
     * The converse, read off the shipped file: every {@code <Something>.Enabled} block in
     * {@code coreskills.yml} must belong to a live skill.
     *
     * <p>This is the direction that catches a <b>retired</b> skill, and it is the direction the three
     * hand-kept rosters in {@code McMMOSettings} have never had. {@code Agility} was retired on
     * 2026-08-17 and its row deleted from the file; a resurrected one would be a config key nothing
     * reads, offered as a control that does nothing — the exact shape of the defect that took
     * {@code Skills.Agility.Level_Cap} out of the catalogue.
     */
    @Test
    void everyEnabledKeyInTheShippedFileBelongsToALiveSkill() throws IOException {
        final InputStream in =
                CoreSkillsCatalogueTest.class.getResourceAsStream("/" + McMMOSettings.CORESKILLS_YML);
        assertNotNull(in, "coreskills.yml is missing from the test classpath");
        final YamlConfiguration shipped = YamlConfiguration.loadConfiguration(in);

        final Set<String> live = EnumSet.allOf(PrimarySkillType.class).stream()
                .map(CoreSkillsConfig::enabledPath)
                .collect(Collectors.toSet());

        final Set<String> sections = shipped.getKeys(false);
        assertFalse(sections.isEmpty(),
                "coreskills.yml parsed to nothing — this test is asserting about an empty file");

        for (String section : sections) {
            final String path = section + ".Enabled";
            if (!shipped.contains(path)) {
                continue; // not a master-switch block at all
            }
            assertTrue(live.contains(path), "coreskills.yml ships " + path + " but there is no such "
                    + "skill — a dead key, and the Skills tab would either offer a switch for it or "
                    + "silently ignore it. Delete the block.");
        }
    }

    /**
     * <b>The end-to-end guard, and the only proof the tab does anything at all.</b>
     *
     * <p>Writes {@code false} through a real {@link ConfigSession} exactly as the screen's save
     * consumer does, flushes it, then constructs a {@link CoreSkillsConfig} over the same directory
     * and asks the question gameplay asks. Every link in the chain is the production one — the
     * catalogue row, the YAML write, the config load, the getter {@code SkillGating} calls.
     *
     * <p>⚠️ The second assertion is not decoration. Asserting only that Mining came back disabled
     * passes just as happily if the load path returns {@code false} for everything, or if the file
     * failed to parse — both of which would disable the entire mod. A positive co-assertion on an
     * untouched skill is what separates "the toggle worked" from "nothing is enabled any more".
     */
    @Test
    void togglingARowOffActuallyDisablesTheSkill(@TempDir Path dataFolder) throws IOException {
        final ConfigSetting mining = rowFor(PrimarySkillType.MINING);

        final ConfigSession session = new ConfigSession(dataFolder);
        assertTrue(session.readBoolean(mining), "Mining should start enabled, from the bundled file");

        session.booleanSaveConsumer(mining, false).accept(false);
        assertEquals(1, session.saveAll(), "the edit should have written exactly coreskills.yml");

        final CoreSkillsConfig config = new CoreSkillsConfig(dataFolder);
        assertFalse(config.isPrimarySkillEnabled(PrimarySkillType.MINING),
                "the Skills tab wrote Mining.Enabled: false and CoreSkillsConfig still reads it as "
                        + "enabled — the tab is a control over nothing");
        assertTrue(config.isPrimarySkillEnabled(PrimarySkillType.WOODCUTTING),
                "Woodcutting was never touched but came back disabled — the write did not land on "
                        + "one key, it broke the whole file");
    }

    /**
     * A locked row writes nothing (ruling S-3).
     *
     * <p>Cloth calls every entry's save consumer on save, editable or not, so "the widget is greyed
     * out" is not by itself a guarantee that the file is left alone. The player's on-disk value for a
     * skill this version cannot furnish must survive the screen being opened and saved untouched.
     */
    @Test
    void aLockedRowDoesNotWriteAnything(@TempDir Path dataFolder) throws IOException {
        final ConfigSetting spears = rowFor(PrimarySkillType.SPEARS);

        final ConfigSession session = new ConfigSession(dataFolder);
        final Consumer<Boolean> locked = session.booleanSaveConsumer(spears, true);
        locked.accept(false);

        assertFalse(session.hasPendingChanges(),
                "a locked row staged an edit — the screen would rewrite a value the player never "
                        + "touched, in a file they may have hand-edited");
        assertEquals(0, session.saveAll(), "a locked row caused a file to be written");
    }

    /**
     * The lock applies to exactly the rows whose skill this Minecraft version cannot furnish, and to
     * nothing else on the screen.
     *
     * <p>Fed a stub predicate rather than {@code SkillAvailability}: the real one answers from a
     * registry probe that needs a running server, and the decision under test is which rows the
     * answer applies to. ⚠️ The single line in {@code ClothConfigScreenBuilder} that hands the real
     * predicate to this method is not covered by any test — stated rather than hidden.
     */
    @Test
    void onlyAnUnfurnishableSkillRowIsLocked() {
        final Predicate<PrimarySkillType> allButSpears = skill -> skill != PrimarySkillType.SPEARS;

        assertTrue(McMMOSettings.isLockedByVersion(rowFor(PrimarySkillType.SPEARS), allButSpears),
                "a skill this version cannot furnish must be rendered read-only — its toggle cannot "
                        + "do anything, and an editable one tells the player otherwise");
        assertFalse(McMMOSettings.isLockedByVersion(rowFor(PrimarySkillType.MINING), allButSpears),
                "a supported skill's switch was locked — the player cannot turn Mining off");

        for (ConfigSetting setting : McMMOSettings.all()) {
            if (!McMMOSettings.CORESKILLS_YML.equals(setting.file())) {
                assertFalse(McMMOSettings.isLockedByVersion(setting, skill -> false),
                        setting.path() + " is not a skill master switch but the version lock claimed "
                                + "it — every other tab would go read-only on a band missing one "
                                + "skill");
                assertNull(McMMOSettings.masterSwitchSkill(setting),
                        setting.path() + " resolved to a skill master switch and is not one");
            }
        }
    }

    /** A child skill's row says so, and names the parents its level actually comes from. */
    @Test
    void childSkillRowsExplainWhereTheirLevelComesFrom() {
        final String salvage = rowFor(PrimarySkillType.SALVAGE).tooltip();
        assertTrue(salvage.contains("child skill"), "Salvage's row does not say it is a child skill");
        assertTrue(salvage.contains("Repair") && salvage.contains("Fishing"),
                "Salvage's row does not name Repair and Fishing, the skills its level is derived "
                        + "from — a player watching a 'disabled' skill keep levelling needs that: "
                        + salvage);

        final String smelting = rowFor(PrimarySkillType.SMELTING).tooltip();
        assertTrue(smelting.contains("Mining") && smelting.contains("Repair"),
                "Smelting's row does not name Mining and Repair: " + smelting);

        assertFalse(rowFor(PrimarySkillType.MINING).tooltip().contains("child skill"),
                "Mining is not a child skill but its row says so");
    }

    private static ConfigSetting rowFor(PrimarySkillType skill) {
        final String path = CoreSkillsConfig.enabledPath(skill);
        return masterSwitchRows().stream()
                .filter(row -> row.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no Skills-tab row for " + skill));
    }
}
