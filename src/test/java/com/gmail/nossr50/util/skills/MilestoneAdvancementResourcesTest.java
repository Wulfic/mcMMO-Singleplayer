package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Drift + validity guard for the bundled milestone advancement datapack (the optional Advancement
 * Plaques support). Proves that {@link Milestones} and
 * {@code scripts/gen-milestone-advancements.sh} agree in <em>both</em> directions:
 *
 * <ul>
 *   <li>every advancement id the runtime can grant has a matching, well-formed JSON resource, so a
 *       change to the skill/sub-skill roster that isn't re-run through the generator fails the build
 *       instead of silently logging "advancement not loaded" at runtime; and</li>
 *   <li>every shipped file is reachable, so a renamed id leaves no orphan behind — the generator is
 *       a bash script whose per-file loop can (and once did) abort part-way through without failing
 *       the run, and a half-written datapack is otherwise invisible.</li>
 * </ul>
 *
 * <p>JSON is a subset of YAML, so the shipped {@code .json} files are parsed with snakeyaml (already
 * on the classpath) without pulling in a JSON dependency.
 */
class MilestoneAdvancementResourcesTest {

    private static final String RESOURCE_BASE = "/data/mcmmo/advancement/milestone/";
    private static final Path SOURCE_BASE =
            Path.of("src", "main", "resources", "data", "mcmmo", "advancement", "milestone");
    private static final String LOCALE_RESOURCE =
            "/com/gmail/nossr50/locale/locale_en_US.properties";
    private static final Set<String> VALID_FRAMES = Set.of("task", "goal", "challenge");

    /**
     * A sub-skill's parent, derived from its enum name prefix rather than {@code getParentSkill()}.
     * That accessor routes through {@code McMMOMod.getSkillTools()}, which is not wired in a plain
     * unit test — and the prefix <em>is</em> the contract the generator relies on, so pinning it here
     * is the point rather than a workaround.
     */
    private static String parentKeyOf(SubSkillType subSkill) {
        final String name = subSkill.name();
        return name.substring(0, name.indexOf('_')).toLowerCase(Locale.ROOT);
    }

    /** Every id path {@link Milestones} can emit (mirrors what {@code checkXp} hands to the seam). */
    private static List<String> grantablePaths() {
        final List<String> paths = new ArrayList<>();
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            final String key = Milestones.key(skill);
            for (String tier : Milestones.TIER_KEYS) {
                paths.add("level/" + key + "/" + tier);
            }
            paths.add("maxed/" + key);
        }
        for (SubSkillType subSkill : SubSkillType.values()) {
            if (subSkill.getNumRanks() <= 0) {
                continue; // Not ranked; McMMOPlayer never tracks it, so it has no plaque.
            }
            paths.add("rank/" + Milestones.key(subSkill) + "/unlocked");
            if (subSkill.getNumRanks() > 1) {
                paths.add("rank/" + Milestones.key(subSkill) + "/improved");
            }
        }
        for (int tier : Milestones.POWER_TIERS) {
            paths.add("power/" + tier);
        }
        return paths;
    }

    /** The structural (never-granted) nodes: the tab root and its per-skill hubs. */
    private static Set<String> structuralPaths() {
        final Set<String> paths = new LinkedHashSet<>();
        paths.add("root");
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            paths.add("skill/" + Milestones.key(skill));
        }
        return paths;
    }

    private static Map<?, ?> load(String path) {
        final String resource = RESOURCE_BASE + path + ".json";
        try (InputStream in = MilestoneAdvancementResourcesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "bundled milestone advancement missing from classpath: " + resource
                    + " — re-run scripts/gen-milestone-advancements.sh");
            final Object parsed = new Yaml().load(in);
            return assertInstanceOf(Map.class, parsed, resource + " is not a JSON object");
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + resource, e);
        }
    }

    private static Map<?, ?> display(String path, Map<?, ?> advancement) {
        return assertInstanceOf(Map.class, advancement.get("display"), path + " has no display");
    }

    private static String title(String path) {
        final Map<?, ?> display = display(path, load(path));
        final Map<?, ?> title =
                assertInstanceOf(Map.class, display.get("title"), path + " has no title");
        return (String) title.get("text");
    }

    @Test
    void everyGrantablePathHasAValidMilestoneAdvancement() {
        for (String path : grantablePaths()) {
            final Map<?, ?> adv = load(path);
            assertImpossibleCriterion(path, adv);

            final Map<?, ?> display = display(path, adv);
            assertEquals(Boolean.TRUE, display.get("show_toast"),
                    path + " must show a toast (that is what Advancement Plaques re-skins)");
            assertEquals(Boolean.TRUE, display.get("hidden"),
                    path + " must be hidden so unearned milestones stay out of the advancement tab");
            assertTrue(VALID_FRAMES.contains(display.get("frame")),
                    path + " has an invalid frame: " + display.get("frame"));

            final Map<?, ?> icon =
                    assertInstanceOf(Map.class, display.get("icon"), path + " has no icon");
            assertInstanceOf(String.class, icon.get("id"), path + " icon needs an id");
            assertTrue(((String) icon.get("id")).startsWith("minecraft:"),
                    path + " icon id should be a vanilla item: " + icon.get("id"));

            final String title = title(path);
            assertNotNull(title, path + " has no title text");
            assertFalse(title.isBlank(), path + " has a blank title");
        }
    }

    /**
     * The toast renders only the frame line, the title and the icon — {@code AdvancementToast#draw}
     * never reads the description (bytecode-verified against 1.21.11). A title that merely repeats
     * the skill name therefore says nothing the player did not already know, which is exactly the
     * blandness this datapack was rewritten to fix. Pin the specifics that carry the meaning.
     */
    @Test
    void plaqueTitlesNameTheSpecificMilestoneNotJustTheSkill() {
        assertEquals("Master Miner", title("level/mining/master"));
        assertEquals("Apprentice Angler", title("level/fishing/apprentice"));
        assertEquals("Peerless Miner", title("maxed/mining"));
        assertEquals("Mythic (Power 10,000)", title("power/10000"));
    }

    /**
     * Rank plaques are titled with the ability's real name, taken from the locale file by the
     * generator. If a sub-skill is renamed in the locale and the generator is not re-run, the plaque
     * keeps announcing the old name — invisible in game and ungreppable, the same failure shape as
     * the dynamic locale-key families.
     */
    @Test
    void rankPlaqueTitlesMatchTheAbilityNameInTheLocale() {
        final Properties locale = new Properties();
        try (InputStream in =
                MilestoneAdvancementResourcesTest.class.getResourceAsStream(LOCALE_RESOURCE)) {
            assertNotNull(in, "locale file missing from classpath: " + LOCALE_RESOURCE);
            locale.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + LOCALE_RESOURCE, e);
        }

        for (SubSkillType subSkill : SubSkillType.values()) {
            if (subSkill.getNumRanks() <= 0) {
                continue;
            }
            final String localeKey = localeNameKeyOf(subSkill);
            final String ability = locale.getProperty(localeKey);
            assertNotNull(ability, "no locale entry '" + localeKey + "' for " + subSkill.name());

            final String path = "rank/" + Milestones.key(subSkill) + "/unlocked";
            assertEquals(ability + " Unlocked", title(path),
                    path + " title is out of step with " + localeKey
                            + " — re-run scripts/gen-milestone-advancements.sh");
        }
    }

    /** Mirrors {@code SubSkillType#getLocaleKeyRoot}: {@code <Parent>.SubSkill.<CamelCase>.Name}. */
    private static String localeNameKeyOf(SubSkillType subSkill) {
        final String name = subSkill.name();
        final int split = name.indexOf('_');
        final String parent = name.substring(0, split);
        final StringBuilder camel = new StringBuilder();
        for (String part : name.substring(split + 1).split("_")) {
            camel.append(part.charAt(0)).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return parent.charAt(0) + parent.substring(1).toLowerCase(Locale.ROOT)
                + ".SubSkill." + camel + ".Name";
    }

    /**
     * Milestones hang off a per-skill hub so the advancement tab reads as twenty-six branches rather
     * than one enormous row; power tiers are the player's, not a skill's, so they hang off the root.
     */
    @Test
    void everyMilestoneIsParentedIntoTheTabTree() {
        for (String path : grantablePaths()) {
            final String expectedParent = path.startsWith("power/")
                    ? "mcmmo:milestone/root"
                    : "mcmmo:milestone/skill/" + hubKeyOf(path);
            assertEquals(expectedParent, load(path).get("parent"), path + " is parented wrongly");
        }
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            final String path = "skill/" + Milestones.key(skill);
            assertEquals("mcmmo:milestone/root", load(path).get("parent"),
                    path + " must hang off the milestone root");
        }
    }

    /** The skill a milestone id belongs under: the second id segment, or the sub-skill's prefix. */
    private static String hubKeyOf(String path) {
        final String[] segments = path.split("/");
        if (!segments[0].equals("rank")) {
            return segments[1];
        }
        for (SubSkillType subSkill : SubSkillType.values()) {
            if (Milestones.key(subSkill).equals(segments[1])) {
                return parentKeyOf(subSkill);
            }
        }
        throw new AssertionError("no sub-skill matches rank id segment: " + segments[1]);
    }

    /**
     * The root carries a display so the advancement screen actually renders an mcMMO tab. Without
     * one, vanilla creates no tab at all and every description in this datapack — the only place
     * milestone detail can live, since the toast never reads it — would render nowhere.
     */
    @Test
    void rootRendersAnAdvancementTabButNeverToasts() {
        final Map<?, ?> root = load("root");
        assertFalse(root.containsKey("parent"), "the milestone root must have no parent");
        assertImpossibleCriterion("root", root);

        final Map<?, ?> display = display("root", root);
        assertEquals(Boolean.FALSE, display.get("hidden"),
                "a hidden root would not render the mcMMO advancement tab");
        assertEquals(Boolean.FALSE, display.get("show_toast"),
                "the root is structural and is never granted; it must not toast");
    }

    /**
     * No orphans: every shipped file is either grantable or one of the structural tab nodes. This is
     * the direction the old guard missed — it only ever proved that the ids it expected existed, so
     * a renamed id left its predecessor sitting in the jar forever.
     */
    @Test
    void everyShippedFileIsReachable() {
        final Set<String> expected = new TreeSet<>(grantablePaths());
        expected.addAll(structuralPaths());

        final Set<String> shipped = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_BASE)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                final String relative =
                        SOURCE_BASE.relativize(p).toString().replace('\\', '/');
                shipped.add(relative.substring(0, relative.length() - ".json".length()));
            });
        } catch (IOException e) {
            throw new UncheckedIOException("failed to walk " + SOURCE_BASE, e);
        }

        assertFalse(shipped.isEmpty(), "found no milestone advancement files under " + SOURCE_BASE);
        assertEquals(expected, shipped,
                "the bundled datapack is out of step with Milestones — re-run "
                        + "scripts/gen-milestone-advancements.sh");
    }

    private static void assertImpossibleCriterion(String path, Map<?, ?> adv) {
        final Map<?, ?> criteria =
                assertInstanceOf(Map.class, adv.get("criteria"), path + " has no criteria");
        final Map<?, ?> milestone = assertInstanceOf(Map.class, criteria.get("milestone"),
                path + " must define the 'milestone' criterion granted at runtime");
        assertEquals("minecraft:impossible", milestone.get("trigger"),
                path + " milestone criterion must use the impossible trigger (granted in code)");
    }
}
