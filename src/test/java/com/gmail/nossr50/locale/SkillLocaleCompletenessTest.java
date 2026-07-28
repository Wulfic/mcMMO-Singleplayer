package com.gmail.nossr50.locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.util.skills.SkillTools;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Pins that every skill and sub-skill has the locale strings the runtime builds <em>dynamically</em>
 * from an enum name, so adding a {@link PrimarySkillType} or {@link SubSkillType} without its locale
 * entries fails the build instead of shipping.
 *
 * <h2>Why this test exists</h2>
 * A literal key like {@code "JSON.Rank"} is greppable, so a missing one is easy to spot. These are
 * not: three call sites concatenate a capitalised enum name onto a prefix, and
 * {@link LocaleLoader#getRawString} answers a miss with {@code !Key!} rather than throwing. The
 * result renders on-screen as literal {@code !XPBar.Stealth!} and nothing else notices — which is
 * exactly how Stealth and Unarmored shipped missing six keys between them (2026-07-28). The dynamic
 * key families are:
 * <ul>
 *   <li>{@code XPBar.<Skill>} — {@code ExperienceBarWrapper#renderTitle}, the XP boss bar title</li>
 *   <li>{@code Overhaul.Name.<Skill>} — {@code NotificationManager#sendPlayerLevelUpNotification}</li>
 *   <li>{@code Commands.XPGain.<Skill>} — {@code SkillStatsRenderer#sendHeader}, non-child only</li>
 *   <li>{@code <Skill>.SkillName} — {@code SkillTools#getLocalizedSkillName}</li>
 *   <li>{@code <Skill>.SubSkill.<Name>.{Name,Description}} — {@code SubSkillType#getLocaleKeyRoot}</li>
 * </ul>
 *
 * <h2>Why the key derivation is duplicated here</h2>
 * The sub-skill root is rebuilt from the enum name rather than read off
 * {@link SubSkillType#getLocaleKeyRoot()} on purpose. That method routes through
 * {@code getParentSkill()} → {@code McMMOMod.getSkillTools()}, a static that is unwired in a plain
 * unit test; deriving it independently keeps this MC-free and makes it a genuine cross-check rather
 * than a tautology. {@code SkillToolsTest#everySubSkillResolvesToItsNamePrefixParent} pins the other
 * half — that the production parent lookup agrees with the name prefix used here.
 *
 * <p>{@code .Stat} is deliberately <b>not</b> required: 17 legacy sub-skills (all of Taming, several
 * Axes, …) render their stat lines from bespoke keys in their dedicated renderer instead.
 */
class SkillLocaleCompletenessTest {

    /** A locale miss is {@code !key!} — see {@link LocaleLoader#getRawString}. */
    private static boolean isMissing(String key) {
        return LocaleLoader.getString(key).equals('!' + key + '!');
    }

    private static String capitalized(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(Locale.ENGLISH);
    }

    /** Mirrors {@code SubSkillType#getConfigName}: drop the parent prefix, CamelCase the rest. */
    private static String subSkillConfigName(SubSkillType subSkill) {
        final String afterPrefix = subSkill.name().substring(subSkill.name().indexOf('_') + 1);
        final StringBuilder out = new StringBuilder();
        for (String part : afterPrefix.split("_")) {
            out.append(capitalized(part));
        }
        return out.toString();
    }

    private static void assertAllPresent(List<String> missing) {
        assertTrue(missing.isEmpty(),
                () -> "Missing " + missing.size() + " locale key(s):\n  " + String.join("\n  ", missing));
    }

    @Test
    void everySkillHasAnXpBarTitle() {
        final List<String> missing = new ArrayList<>();
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            final String key = "XPBar." + capitalized(skill.name());
            if (isMissing(key)) {
                missing.add(key);
            }
        }
        assertAllPresent(missing);
    }

    @Test
    void everyXpBarTitleCarriesTheLevelPlaceholder() {
        // The bar title is the only place the player sees their level, so a title without {0}
        // resolves but renders uselessly ("Stealth Lv."). getString with no arguments leaves an
        // unfilled placeholder verbatim, which is what makes this assertable.
        final List<String> broken = new ArrayList<>();
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            final String key = "XPBar." + capitalized(skill.name());
            if (!isMissing(key) && !LocaleLoader.getString(key).contains("{0}")) {
                broken.add(key);
            }
        }
        assertTrue(broken.isEmpty(), () -> "XP bar titles with no {0} level placeholder: " + broken);
    }

    @Test
    void everySkillHasAnOverhaulDisplayName() {
        final List<String> missing = new ArrayList<>();
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            final String key = "Overhaul.Name." + capitalized(skill.name());
            if (isMissing(key)) {
                missing.add(key);
            }
        }
        assertAllPresent(missing);
    }

    @Test
    void everySkillHasASkillName() {
        final List<String> missing = new ArrayList<>();
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            final String key = capitalized(skill.name()) + ".SkillName";
            if (isMissing(key)) {
                missing.add(key);
            }
        }
        assertAllPresent(missing);
    }

    @Test
    void everyNonChildSkillHasAnXpGainDescription() {
        // Child skills earn no XP directly, so SkillStatsRenderer#sendHeader renders the shared
        // "Commands.XPGain.Child" line for them and never builds a per-skill key.
        final List<String> missing = new ArrayList<>();
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            if (SkillTools.isChildSkill(skill)) {
                continue;
            }
            final String key = "Commands.XPGain." + capitalized(skill.name());
            if (isMissing(key)) {
                missing.add(key);
            }
        }
        assertAllPresent(missing);
        assertFalse(isMissing("Commands.XPGain.Child"), "the shared child-skill line must exist");
    }

    @Test
    void everySubSkillHasANameAndDescription() {
        final List<String> missing = new ArrayList<>();
        for (SubSkillType subSkill : SubSkillType.values()) {
            final String parent = capitalized(subSkill.name().substring(0, subSkill.name().indexOf('_')));
            final String root = parent + ".SubSkill." + subSkillConfigName(subSkill);
            for (String suffix : new String[] {".Name", ".Description"}) {
                if (isMissing(root + suffix)) {
                    missing.add(root + suffix);
                }
            }
        }
        assertAllPresent(missing);
    }
}
