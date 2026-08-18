package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The invariant {@link SkillRenames} never had a test for: <b>a rename destination must name
 * something the code still reads.</b>
 *
 * <p>This class exists because that table shipped a destination pointing at a retired skill and
 * nothing noticed. {@code LEGACY_CONFIG_SECTIONS} mapped {@code Acrobatics} to {@code Agility};
 * Agility was itself retired on 2026-08-17, so from that day the warning told a player to move their
 * tuning into a section the very next boot migrates away again. It was never data loss — the
 * {@code MOVED_CONFIG_PATHS} entries key on {@code Skills.Agility.*} and fire on the following load
 * — but it is a pointer aimed at nothing, which is precisely the failure this class was written to
 * prevent for everybody else's tables.
 *
 * <p>It was found by reading the code while writing docs, not by the suite. The point of the guards
 * below is that the next one is found by the suite.
 *
 * <p>⚠️ These are <b>source-text</b> guards over prose destinations, and this repo has been bitten
 * twice by treating that as proof of behaviour. The behavioural half — that the warning actually
 * fires on a real {@code Acrobatics:} section, and quotes a live destination when it does — lives in
 * {@code ConfigLoaderTest#anAcrobaticsSectionIsDetectedAndSentToALiveDestination}. Neither half is
 * sufficient alone.
 */
class SkillRenamesTest {

    /**
     * Every name a primary skill has been spelled with in this port that is <b>no longer a skill</b>
     * — renamed away, or retired outright. None of these may ever appear as a rename destination.
     *
     * <p>⚠️ Hand-kept, which is the transcription shape this repo has shipped wrong three times. It
     * is safe here only because {@link #everyRetiredNameIsGenuinelyAbsentFromTheRoster()} is its
     * converse: resurrect any of these as a real skill and that test reddens, telling you to take it
     * out of this list rather than letting the list quietly lie.
     */
    private static final Set<String> RETIRED_SKILL_NAMES = Set.of(
            // Renamed to Agility on 2026-07-25 when it absorbed the three movement domains.
            "Acrobatics",
            // Retired 2026-08-17. Its last two sub-skills split across Parkour, Swimming and Flying.
            "Agility");

    /**
     * Config roots that are legitimate destinations without being skills.
     *
     * <p>⚠️ {@code Movement} is the one that has to be understood rather than pattern-matched. It is
     * NOT a skill and never was — it is the neutral root that took the settings belonging to all
     * three movement domains when Agility was retired, and {@code GeneralConfig} reads
     * {@code Skills.Movement.Second_Wind_Item} from a literal path. Filing those under any single
     * one of Parkour, Swimming or Flying would read as a lie.
     */
    private static final Set<String> NON_SKILL_CONFIG_ROOTS = Set.of("Movement", "ExploitFix");

    /** Capitalized words, the only tokens that could name a skill section. */
    private static final Pattern CAPITALIZED_WORD = Pattern.compile("\\b[A-Z][A-Za-z_]*\\b");

    /** {@code PrimarySkillType.PARKOUR} spelled the way a YAML section is: {@code Parkour}. */
    private static Set<String> liveSkillNames() {
        return Arrays.stream(PrimarySkillType.values())
                .map(skill -> skill.name().charAt(0)
                        + skill.name().substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Every destination in both warn-only tables, tagged with the table and key it came from. */
    private static List<String[]> allDestinations() {
        final List<String[]> destinations = new ArrayList<>();
        for (Map.Entry<String, String> entry : SkillRenames.legacyConfigSections().entrySet()) {
            destinations.add(
                    new String[] {"legacyConfigSections", entry.getKey(), entry.getValue()});
        }
        for (Map.Entry<String, String> entry : SkillRenames.legacyConfigPaths().entrySet()) {
            destinations.add(new String[] {"legacyConfigPaths", entry.getKey(), entry.getValue()});
        }
        return destinations;
    }

    /**
     * The guard the {@code Acrobatics → Agility} defect needed. Put {@code "Agility"} back as a
     * destination and this goes red.
     */
    @Test
    void noRenameDestinationNamesASkillThatNoLongerExists() {
        for (String[] destination : allDestinations()) {
            final Set<String> words = capitalizedWords(destination[2]);
            for (String retired : RETIRED_SKILL_NAMES) {
                assertFalse(words.contains(retired),
                        destination[0] + " maps '" + destination[1] + "' to a destination naming '"
                                + retired + "', which is not a skill any more. A player who follows "
                                + "that warning moves their tuning somewhere nothing reads. Name the "
                                + "live destination instead.\n  destination: " + destination[2]);
            }
        }
    }

    /**
     * The converse, so {@link #RETIRED_SKILL_NAMES} cannot rot into a lie. If a name on that list
     * ever becomes a real skill again, the list — not the tables — is what is wrong.
     */
    @Test
    void everyRetiredNameIsGenuinelyAbsentFromTheRoster() {
        final Set<String> live = liveSkillNames();
        for (String retired : RETIRED_SKILL_NAMES) {
            assertFalse(live.contains(retired),
                    "'" + retired + "' is listed as retired but is a live PrimarySkillType again. "
                            + "Remove it from RETIRED_SKILL_NAMES, or "
                            + "noRenameDestinationNamesASkillThatNoLongerExists is silently "
                            + "forbidding a valid destination.");
        }
    }

    /**
     * The positive half, without which the guard above passes for free on an empty or vague
     * destination. A rename has to send the player somewhere nameable.
     *
     * <p>Deliberately asserts against {@link #liveSkillNames()} and
     * {@link #NON_SKILL_CONFIG_ROOTS} — both derived from or checked against the real roster — so it
     * cannot be satisfied by prose alone.
     */
    @Test
    void everyDestinationNamesAtLeastOneLiveSkillOrConfigRoot() {
        final Set<String> live = liveSkillNames();
        for (String[] destination : allDestinations()) {
            final boolean namesSomethingReal = capitalizedWords(destination[2]).stream()
                    .anyMatch(word -> live.contains(word) || NON_SKILL_CONFIG_ROOTS.contains(word));
            assertTrue(namesSomethingReal,
                    destination[0] + " maps '" + destination[1] + "' to a destination that names no "
                            + "live skill and no known config root, so the warning tells the player "
                            + "nothing actionable.\n  destination: " + destination[2]);
        }
    }

    /**
     * Every {@link #NON_SKILL_CONFIG_ROOTS} entry must genuinely not be a skill. Without this, a
     * future skill named {@code Movement} would make the test above pass for the wrong reason.
     */
    @Test
    void noNonSkillConfigRootIsSecretlyASkill() {
        final Set<String> live = liveSkillNames();
        for (String root : NON_SKILL_CONFIG_ROOTS) {
            assertFalse(live.contains(root),
                    "'" + root + "' is listed as a non-skill config root but is a live "
                            + "PrimarySkillType. Take it out of NON_SKILL_CONFIG_ROOTS — the roster "
                            + "already accounts for it.");
        }
    }

    /**
     * The other direction: a table entry for a section that is still a live skill would warn every
     * player away from the section the code actually reads.
     */
    @Test
    void noLegacySectionKeyIsStillALiveSkill() {
        final Set<String> live = liveSkillNames();
        for (String legacy : SkillRenames.legacyConfigSections().keySet()) {
            assertFalse(live.contains(legacy),
                    "legacyConfigSections still lists '" + legacy + "' as legacy, but it is a live "
                            + "PrimarySkillType — every player with that section would be told to "
                            + "move out of the section the code actually reads.");
        }
    }

    private static Set<String> capitalizedWords(String text) {
        final Set<String> words = new LinkedHashSet<>();
        final Matcher matcher = CAPITALIZED_WORD.matcher(text);
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }
}
