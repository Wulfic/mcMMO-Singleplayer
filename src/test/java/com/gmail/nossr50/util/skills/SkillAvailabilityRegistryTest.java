package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.util.MaterialMapStore;
import com.gmail.nossr50.util.McTestRegistries;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The other half of {@link SkillAvailabilityTest}: that the decision is wired to the <em>real</em>
 * item registry, and that {@link SkillGating} honours it.
 *
 * <p>Every assertion here is written against what this Minecraft version actually has, never against
 * a version number, so the same source is correct on every band — on one with spears it proves the
 * skill stays on, on one without it proves the skill goes off.
 */
class SkillAvailabilityRegistryTest {

    private static final Set<String> SPEAR_PATHS = new MaterialMapStore().getSpears();

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    @AfterEach
    void clearProbe() {
        // Process-wide state: left set, it would decide Spears for every test scheduled into this
        // fork afterwards, and on a band without spears that would redden them.
        SkillAvailability.resetForTesting();
    }

    /**
     * ⚠️ The converse guard, and it has to come first. Everything below concludes something from
     * which spear ids resolve; if the bootstrap silently did nothing, none of them resolve and the
     * whole class would agree happily that this version has no spears.
     */
    @Test
    void theItemRegistryReallyPopulated() {
        assertTrue(Materials.itemRegistryIsPopulated(),
                "the registry bootstrap did no work, so no absence below would mean anything");
    }

    @Test
    void spearSupportTracksTheItemsThisVersionHas() {
        assertTrue(Materials.itemRegistryIsPopulated());
        final boolean versionHasSpears = SPEAR_PATHS.stream()
                .anyMatch(path -> McTestRegistries.optionalVanillaItem(path).isPresent());

        SkillAvailability.probe();

        assertEquals(versionHasSpears, SkillAvailability.isSkillSupported(PrimarySkillType.SPEARS),
                "Spears support must match whether this Minecraft version has spear items");
    }

    /** Probing is idempotent — a second world session in the same JVM must not change the answer. */
    @Test
    void probingTwiceGivesTheSameAnswer() {
        SkillAvailability.probe();
        final boolean first = SkillAvailability.isSkillSupported(PrimarySkillType.SPEARS);
        SkillAvailability.probe();
        assertEquals(first, SkillAvailability.isSkillSupported(PrimarySkillType.SPEARS));
    }
}
