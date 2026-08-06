package com.gmail.nossr50.config.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.YamlConfiguration;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.skills.SkillTools;
import com.gmail.nossr50.util.text.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Both directions on the hand-kept {@code Diminished_Returns.Threshold} table in the bundled
 * {@code experience.yml}.
 *
 * <p><b>Why this exists.</b> The table is 25 lines a human types, keyed by skill name, and it is read
 * by {@link ExperienceConfig#getDiminishedReturnsThreshold} through string concatenation — so a
 * missing or misspelled row is invisible to grep <em>and</em> to the reader, which silently answers
 * with its hard-coded {@code 20000} fallback. When diminished returns was wired on 2026-08-06 the
 * table was wrong in <b>both</b> directions at once:
 * <ul>
 *   <li>{@code Cooking} — the 27th skill, added across the five Cooking stages — had <b>no row</b>.
 *       Nobody back-filled the table. The consequence was invisible because the fallback and every
 *       other row are the same number, the {@code Damage_Limit} shape for the third time.</li>
 *   <li>{@code Agility} <b>had</b> a row, and it is a child skill. A child's gain is split to its
 *       parents before it ever reaches the throttle, so that row was a knob wired to nothing —
 *       precisely the defect class the 2026-08-06 audit exists to close.</li>
 * </ul>
 *
 * <p>The converse direction is the one the {@code Herdsmans_Call} and Husbandry audits both learned
 * the hard way: a one-directional completeness test is half a test. Adding a 28th skill must redden
 * this file, and so must adding a row for a skill that cannot use one.
 *
 * @see com.gmail.nossr50.datatypes.player.McMMOPlayer#applyDiminishedReturns
 */
class DiminishedReturnsThresholdCatalogueTest {

    private static final String SECTION = "Diminished_Returns.Threshold";

    private static YamlConfiguration bundled() throws IOException {
        try (InputStream in = DiminishedReturnsThresholdCatalogueTest.class
                .getResourceAsStream("/experience.yml")) {
            assertNotNull(in, "bundled experience.yml missing from the test classpath");
            return YamlConfiguration.loadConfiguration(in);
        }
    }

    private static Set<String> shippedRows() throws IOException {
        final YamlConfiguration section = bundled().getConfigurationSection(SECTION);
        assertNotNull(section, "the bundled experience.yml no longer ships " + SECTION);
        final Set<String> keys = section.getKeys(false);
        assertFalse(keys.isEmpty(), "no rows under " + SECTION + " — the test is asserting nothing");
        return keys;
    }

    /**
     * Forward: every skill the throttle can actually be asked about must have a row. A skill with no
     * row is not "unthrottled" — it silently inherits the getter's fallback, so the file understates
     * what the mod does.
     */
    @Test
    void everyNonChildSkillHasAThresholdRow() throws IOException {
        final Set<String> shipped = shippedRows();

        for (PrimarySkillType skill : PrimarySkillType.values()) {
            if (SkillTools.isChildSkill(skill)) {
                continue;
            }
            final String row = StringUtils.getCapitalized(skill.toString());
            assertTrue(shipped.contains(row),
                    "experience.yml ships no " + SECTION + "." + row + " — that skill's throttle "
                            + "silently falls back to the getter's hard-coded default, so editing "
                            + "the file cannot change it. Cooking shipped this way.");
        }
    }

    /**
     * Converse: every row must name a real skill that can reach the throttle. A row for a child
     * skill, or for a skill that no longer exists, is a knob wired to nothing.
     */
    @Test
    void everyThresholdRowNamesAThrottleableSkill() throws IOException {
        for (String row : shippedRows()) {
            final PrimarySkillType skill;
            try {
                skill = PrimarySkillType.valueOf(row.toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException e) {
                throw new AssertionError(SECTION + "." + row + " names no PrimarySkillType — "
                        + "a row nothing will ever read", e);
            }
            assertFalse(SkillTools.isChildSkill(skill),
                    SECTION + "." + row + " is a CHILD skill. Its XP is split to its parents before "
                            + "McMMOPlayer#applyDiminishedReturns is reached, so this row can never "
                            + "be consulted. Agility shipped one until 2026-08-06.");
        }
    }

    /**
     * The row name must be the exact string the getter concatenates. {@code getCapitalized} is what
     * builds the path at runtime, so a row that differs from it in case is read by nobody — and
     * because the reader falls back rather than failing, nothing else in the suite would notice.
     */
    @Test
    void theRowNamesAreExactlyWhatTheGetterConcatenates() throws IOException {
        final Set<String> shipped = shippedRows();

        for (String row : shipped) {
            assertEquals(StringUtils.getCapitalized(row), row,
                    SECTION + "." + row + " is not spelled the way the getter builds the path "
                            + "(first letter upper, rest lower), so the getter cannot find it");
        }
    }
}
