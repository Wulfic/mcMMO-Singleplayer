package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Drift + validity guard for the bundled milestone advancement datapack (the optional Advancement
 * Plaques support). Proves that <em>every</em> advancement id {@link Milestones} can grant has a
 * matching, well-formed JSON resource on the classpath — so a change to the skill list or the power
 * tiers that isn't re-run through {@code scripts/gen-milestone-advancements.sh} fails the build
 * instead of silently logging "advancement not loaded" at runtime.
 *
 * <p>JSON is a subset of YAML, so the shipped {@code .json} files are parsed with snakeyaml (already
 * on the classpath) without pulling in a JSON dependency.
 */
class MilestoneAdvancementResourcesTest {

    private static final String BASE = "/data/mcmmo/advancement/milestone/";
    private static final Set<String> VALID_FRAMES = Set.of("task", "goal", "challenge");

    /** Every id path {@link Milestones} can emit (mirrors what {@code checkXp} can hand to the seam). */
    private static List<String> grantablePaths() {
        final List<String> paths = new ArrayList<>();
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            final String key = Milestones.key(skill);
            paths.add("level/" + key);
            paths.add("maxed/" + key);
            paths.add("rank/" + key);
        }
        for (int tier : Milestones.POWER_TIERS) {
            paths.add("power/" + tier);
        }
        return paths;
    }

    private static Map<?, ?> load(String resource) {
        try (InputStream in = MilestoneAdvancementResourcesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "bundled milestone advancement missing from classpath: " + resource
                    + " — re-run scripts/gen-milestone-advancements.sh");
            final Object parsed = new Yaml().load(in);
            return assertInstanceOf(Map.class, parsed, resource + " is not a JSON object");
        } catch (Exception e) {
            throw new AssertionError("failed to read/parse " + resource, e);
        }
    }

    @Test
    void everyGrantablePathHasAValidMilestoneAdvancement() {
        for (String path : grantablePaths()) {
            final String resource = BASE + path + ".json";
            final Map<?, ?> adv = load(resource);

            assertEquals("mcmmo:milestone/root", adv.get("parent"),
                    resource + " must be parented to the hidden milestone root");
            assertImpossibleCriterion(resource, adv);

            final Map<?, ?> display =
                    assertInstanceOf(Map.class, adv.get("display"), resource + " has no display");
            assertEquals(Boolean.TRUE, display.get("show_toast"),
                    resource + " must show a toast (that is what Advancement Plaques re-skins)");
            assertEquals(Boolean.TRUE, display.get("hidden"),
                    resource + " must be hidden so it does not clutter the advancement screen");
            assertTrue(VALID_FRAMES.contains(display.get("frame")),
                    resource + " has an invalid frame: " + display.get("frame"));

            final Map<?, ?> icon =
                    assertInstanceOf(Map.class, display.get("icon"), resource + " has no icon");
            assertInstanceOf(String.class, icon.get("id"), resource + " icon needs an id");
            assertTrue(((String) icon.get("id")).startsWith("minecraft:"),
                    resource + " icon id should be a vanilla item: " + icon.get("id"));
        }
    }

    @Test
    void rootHasNoDisplaySoItCreatesNoAdvancementTab() {
        final Map<?, ?> root = load(BASE + "root.json");
        assertFalse(root.containsKey("display"),
                "the milestone root must have no display, or it would render an advancement-GUI tab");
        assertImpossibleCriterion(BASE + "root.json", root);
    }

    private static void assertImpossibleCriterion(String resource, Map<?, ?> adv) {
        final Map<?, ?> criteria =
                assertInstanceOf(Map.class, adv.get("criteria"), resource + " has no criteria");
        final Map<?, ?> milestone = assertInstanceOf(Map.class, criteria.get("milestone"),
                resource + " must define the 'milestone' criterion granted at runtime");
        assertEquals("minecraft:impossible", milestone.get("trigger"),
                resource + " milestone criterion must use the impossible trigger (granted in code)");
    }
}
