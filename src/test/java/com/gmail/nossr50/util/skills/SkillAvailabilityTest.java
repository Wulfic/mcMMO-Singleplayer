package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.util.MaterialMapStore;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The version-support gate behind the owner's ruling that Spears is <em>disabled</em>, not merely
 * inert, on a Minecraft version with no spear items.
 *
 * <p>Minecraft-free on purpose. The disabling half cannot be observed against the live registry on
 * the newest band — it has spears — so the decision is exercised through its injected inputs, where
 * both directions are reachable from any band. {@code SkillAvailabilityRegistryTest} covers the other
 * side: that the real registry is wired to these inputs at all.
 */
class SkillAvailabilityTest {

    /** The seven paths the shipped classification table calls spears. */
    private static final Set<String> SPEAR_PATHS = new MaterialMapStore().getSpears();

    private static final Predicate<String> NOTHING_EXISTS = path -> false;

    @AfterEach
    void clearProbe() {
        // The probed answer is process-wide; leaving one behind would decide it for every test that
        // runs after this class in the same fork.
        SkillAvailability.resetForTesting();
    }

    @Test
    void aVersionWithNoSpearItemsDisablesTheSkill() {
        assertFalse(SkillAvailability.decide(true, SPEAR_PATHS, NOTHING_EXISTS));
    }

    @Test
    void aVersionWithSpearItemsKeepsTheSkill() {
        assertTrue(SkillAvailability.decide(true, SPEAR_PATHS, path -> true));
    }

    /**
     * One spear is enough. The tiers arrived together in vanilla, but a rule that needed all seven
     * would switch the skill off over a single renamed id — a far worse failure than leaving it on.
     */
    @Test
    void aSingleSpearIsEnoughToKeepTheSkill() {
        assertTrue(SkillAvailability.decide(true, SPEAR_PATHS, "netherite_spear"::equals));
    }

    /**
     * ⚠️⚠️ The load-bearing case. An empty registry and a version without spears look identical from
     * here, so an unpopulated registry must never be read as evidence — otherwise the probe disables
     * Spears on <em>every</em> version, including the ones that have them, and says so in the log
     * with total confidence.
     */
    @Test
    void anUnpopulatedRegistryIsNotEvidenceOfAbsence() {
        assertTrue(SkillAvailability.decide(false, SPEAR_PATHS, NOTHING_EXISTS));
    }

    /** Same argument for the other input: with nothing to look for, nothing is proven by not finding it. */
    @Test
    void anEmptySpearListIsNotEvidenceOfAbsence() {
        assertTrue(SkillAvailability.decide(true, Set.of(), NOTHING_EXISTS));
    }

    /**
     * The gate applies to Spears and to nothing else. Without this, a mistake in the skill comparison
     * would disable the entire mod on an older band and every other test here would still pass.
     */
    @Test
    void noOtherSkillIsGatedByVersion() {
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            if (skill == PrimarySkillType.SPEARS) {
                continue;
            }
            assertTrue(SkillAvailability.isSkillSupported(skill), skill + " must not be version-gated");
        }
        assertTrue(SkillAvailability.isSkillSupported(null), "a null skill must not be gated");
    }

    /** Before the probe runs — mod init, and every Minecraft-free test — nothing is switched off. */
    @Test
    void supportIsAssumedUntilProbed() {
        SkillAvailability.resetForTesting();
        assertTrue(SkillAvailability.isSkillSupported(PrimarySkillType.SPEARS));
    }

    /**
     * The gate reaches the funnel every one of the six "disabled" behaviours passes through (XP,
     * procs, super abilities, the XP bar, {@code /mcstats}, plaques). Without this the probe could be
     * perfectly correct and still change nothing at all — which is exactly what the shipped
     * {@code <Skill>.Enabled} key did for years before GitHub #10: a config, a getter and a passing
     * unit test, and no call site.
     *
     * <p>⚠️ Driven through {@code setSupportedForTesting} rather than the live registry <b>because
     * the band this is developed on has spears</b>. Asserting the real answer here would assert
     * {@code true}, which a missing gate satisfies just as well.
     */
    @Test
    void aVersionThatCannotFurnishASkillDisablesItThroughSkillGating() {
        SkillAvailability.setSupportedForTesting(false);
        assertFalse(SkillGating.isSkillEnabled(PrimarySkillType.SPEARS));
        // The reference point, off the same run: the gate is not simply refusing everything.
        assertTrue(SkillGating.isSkillEnabled(PrimarySkillType.MINING));

        SkillAvailability.setSupportedForTesting(true);
        assertTrue(SkillGating.isSkillEnabled(PrimarySkillType.SPEARS));
    }

    /** A disabled parent's sub-skills go with it — that is the path every proc gate reads. */
    @Test
    void anUnsupportedSkillTakesItsSubSkillsWithIt() {
        SkillAvailability.setSupportedForTesting(false);
        assertFalse(SkillGating.isSubSkillEnabled(SubSkillType.SPEARS_MOMENTUM));
        assertTrue(SkillGating.isSubSkillEnabled(SubSkillType.MINING_DOUBLE_DROPS));

        SkillAvailability.setSupportedForTesting(true);
        assertTrue(SkillGating.isSubSkillEnabled(SubSkillType.SPEARS_MOMENTUM));
    }

    /**
     * The probe reads {@link MaterialMapStore#getSpears()}, and {@code isSpear} classifies from the
     * same field — so a spear cannot be classifiable but unsearched, which is how two hard-coded
     * copies of one list start disagreeing.
     */
    @Test
    void everyProbedPathIsAlsoWhatIsSpearAccepts() {
        final MaterialMapStore materials = new MaterialMapStore();
        assertFalse(SPEAR_PATHS.isEmpty(), "an empty list would make the whole probe vacuous");
        for (String path : SPEAR_PATHS) {
            assertTrue(materials.isSpear(path), path + " is probed for but not classified as a spear");
        }
        assertFalse(materials.isSpear("iron_sword"));
    }

    /** The exposed view is read-only: a caller cannot quietly widen what counts as a spear. */
    @Test
    void theProbedListCannotBeMutatedByItsCallers() {
        assertThrows(UnsupportedOperationException.class,
                () -> new MaterialMapStore().getSpears().add("bamboo_spear"));
    }

    /**
     * The seven ids the ruling is written against, spelled out once. If a future Minecraft renames or
     * adds a tier, this is the test that says so out loud instead of the probe quietly looking for
     * the wrong thing.
     */
    @Test
    void theSpearListIsTheSevenVanillaTiers() {
        final Set<String> expected = new LinkedHashSet<>(Set.of("wooden_spear", "stone_spear",
                "copper_spear", "iron_spear", "golden_spear", "diamond_spear", "netherite_spear"));
        assertTrue(expected.equals(SPEAR_PATHS), "expected " + expected + " but was " + SPEAR_PATHS);
    }
}
