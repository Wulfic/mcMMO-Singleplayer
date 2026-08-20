package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.util.MaterialMapStore;
import com.gmail.nossr50.util.McTestRegistries;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The other half of {@link SkillAvailabilityTest}: that the decision is wired to the <em>real</em>
 * item registry, and that {@link SkillGating} honours it.
 *
 * <p>Every assertion here is written against what this Minecraft version actually has, never against
 * a version number, so the same source is correct on every band — on one with maces it proves the
 * skill stays on, on one without it proves the skill goes off.
 *
 * <p>⚠️ Driven from {@link SkillAvailability#gatedSkills()}, so a gate added later is probed here
 * without anyone remembering to add it.
 */
class SkillAvailabilityRegistryTest {

    private static final MaterialMapStore MATERIALS = new MaterialMapStore();

    private static final Map<PrimarySkillType, Function<MaterialMapStore, Set<String>>> GATES =
            SkillAvailability.gatedSkills();

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    @AfterEach
    void clearProbe() {
        // Process-wide state: left set, it would decide these skills for every test scheduled into
        // this fork afterwards, and on a band missing either item that would redden them.
        SkillAvailability.resetForTesting();
    }

    /**
     * ⚠️ The converse guard, and it has to come first. Everything below concludes something from
     * which ids resolve; if the bootstrap silently did nothing, none of them resolve and the whole
     * class would agree happily that this version has neither spears nor maces.
     */
    @Test
    void theItemRegistryReallyPopulated() {
        assertTrue(Materials.itemRegistryIsPopulated(),
                "the registry bootstrap did no work, so no absence below would mean anything");
    }

    /** The second converse guard: an empty gate map would make every loop below iterate nothing. */
    @Test
    void thereIsAtLeastOneGateToProbe() {
        assertTrue(GATES.size() >= 2, "expected the Spears and Maces gates, found " + GATES.keySet());
    }

    @Test
    void supportTracksTheItemsThisVersionActuallyHas() {
        assertTrue(Materials.itemRegistryIsPopulated());

        SkillAvailability.probe();

        for (Map.Entry<PrimarySkillType, Function<MaterialMapStore, Set<String>>> gate
                : GATES.entrySet()) {
            final PrimarySkillType skill = gate.getKey();
            final boolean versionHasThem = gate.getValue().apply(MATERIALS).stream()
                    .anyMatch(path -> McTestRegistries.optionalVanillaItem(path).isPresent());
            assertEquals(versionHasThem, SkillAvailability.isSkillSupported(skill),
                    skill + " support must match whether this Minecraft version has its items");
        }
    }

    /** Probing is idempotent — a second world session in the same JVM must not change the answer. */
    @Test
    void probingTwiceGivesTheSameAnswer() {
        SkillAvailability.probe();
        final Map<PrimarySkillType, Boolean> first = snapshot();
        SkillAvailability.probe();
        assertEquals(first, snapshot());
    }

    private static Map<PrimarySkillType, Boolean> snapshot() {
        return GATES.keySet().stream().collect(java.util.stream.Collectors.toMap(
                skill -> skill, SkillAvailability::isSkillSupported));
    }
}
