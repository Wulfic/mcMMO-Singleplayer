package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.util.MaterialMapStore;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The version-support gate behind the owner's ruling that a skill this Minecraft version cannot
 * furnish is <em>disabled</em>, not merely inert.
 *
 * <p>Minecraft-free on purpose. The disabling half cannot be observed against the live registry on
 * the newest band — it has both spears and maces — so the decision is exercised through its injected
 * inputs, where both directions are reachable from any band. {@code SkillAvailabilityRegistryTest}
 * covers the other side: that the real registry is wired to these inputs at all.
 *
 * <p>⚠️ Everything here is driven from {@link SkillAvailability#gatedSkills()} rather than from a
 * hand-written list of skills. A new gate added with no matching assertion is the failure a
 * hand-listed test cannot see, and this class exists partly because {@code MACES} was the second
 * gate and the first one's tests would not have noticed it.
 */
class SkillAvailabilityTest {

    private static final MaterialMapStore MATERIALS = new MaterialMapStore();

    /** Every gated skill and the id paths it needs, read from the production map. */
    private static final Map<PrimarySkillType, Function<MaterialMapStore, Set<String>>> GATES =
            SkillAvailability.gatedSkills();

    private static final Predicate<String> NOTHING_EXISTS = path -> false;

    private static Set<String> pathsFor(PrimarySkillType skill) {
        return GATES.get(skill).apply(MATERIALS);
    }

    @AfterEach
    void clearProbe() {
        // The probed answers are process-wide; leaving one behind would decide it for every test that
        // runs after this class in the same fork.
        SkillAvailability.resetForTesting();
    }

    /**
     * ⚠️ The converse guard, and it comes first. Every assertion below concludes something from the
     * contents of {@link SkillAvailability#gatedSkills()}; if that map were empty they would all pass
     * by iterating nothing.
     */
    @Test
    void theGatedSkillMapIsTheTwoSkillsTheRulingsCover() {
        assertEquals(Set.of(PrimarySkillType.SPEARS, PrimarySkillType.MACES), GATES.keySet(),
                "a gate was added or removed without updating the tests that drive it");
    }

    /** No gate may look for an empty set of ids — that decides "supported" unconditionally. */
    @Test
    void everyGateHasSomethingToLookFor() {
        for (PrimarySkillType skill : GATES.keySet()) {
            assertFalse(pathsFor(skill).isEmpty(),
                    skill + " probes for no id paths at all, which makes its gate vacuous");
        }
    }

    @Test
    void aVersionWithNoneOfTheItemsDisablesTheSkill() {
        for (PrimarySkillType skill : GATES.keySet()) {
            assertFalse(SkillAvailability.decide(true, pathsFor(skill), NOTHING_EXISTS),
                    skill + " must be disabled on a version with none of its items");
        }
    }

    @Test
    void aVersionWithTheItemsKeepsTheSkill() {
        for (PrimarySkillType skill : GATES.keySet()) {
            assertTrue(SkillAvailability.decide(true, pathsFor(skill), path -> true),
                    skill + " must stay enabled on a version that has its items");
        }
    }

    /**
     * One matching id is enough. The spear tiers arrived together in vanilla, but a rule that needed
     * all seven would switch the skill off over a single renamed id — a far worse failure than
     * leaving it on.
     */
    @Test
    void aSingleMatchingItemIsEnoughToKeepTheSkill() {
        for (PrimarySkillType skill : GATES.keySet()) {
            final String one = pathsFor(skill).iterator().next();
            assertTrue(SkillAvailability.decide(true, pathsFor(skill), one::equals),
                    skill + " must stay enabled when only " + one + " exists");
        }
    }

    /**
     * ⚠️⚠️ The load-bearing case. An empty registry and a version without the items look identical
     * from here, so an unpopulated registry must never be read as evidence — otherwise the probe
     * disables the skill on <em>every</em> version, including the ones that have the items, and says
     * so in the log with total confidence.
     */
    @Test
    void anUnpopulatedRegistryIsNotEvidenceOfAbsence() {
        for (PrimarySkillType skill : GATES.keySet()) {
            assertTrue(SkillAvailability.decide(false, pathsFor(skill), NOTHING_EXISTS),
                    skill + " must not be disabled on the strength of an unpopulated registry");
        }
    }

    /** Same argument for the other input: with nothing to look for, nothing is proven by not finding it. */
    @Test
    void anEmptyIdPathListIsNotEvidenceOfAbsence() {
        assertTrue(SkillAvailability.decide(true, Set.of(), NOTHING_EXISTS));
    }

    /**
     * The gate applies to the gated skills and to nothing else. Without this, a mistake in the map
     * lookup would disable the entire mod on an older band and every other test here would still
     * pass.
     */
    @Test
    void noUngatedSkillIsGatedByVersion() {
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            if (GATES.containsKey(skill)) {
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
        for (PrimarySkillType skill : GATES.keySet()) {
            assertTrue(SkillAvailability.isSkillSupported(skill),
                    skill + " must read as supported before the probe has run");
        }
    }

    /**
     * Holding one gate at "unsupported" must not move the other. With a single shared field this was
     * true for free; with a map it is a real invariant, and getting it wrong would disable both
     * skills on any band missing either one.
     */
    @Test
    void gatesAreIndependentOfEachOther() {
        SkillAvailability.setSupportedForTesting(PrimarySkillType.MACES, false);
        assertFalse(SkillAvailability.isSkillSupported(PrimarySkillType.MACES));
        assertTrue(SkillAvailability.isSkillSupported(PrimarySkillType.SPEARS),
                "disabling Maces must not take Spears with it");

        SkillAvailability.setSupportedForTesting(PrimarySkillType.SPEARS, false);
        assertFalse(SkillAvailability.isSkillSupported(PrimarySkillType.SPEARS));
        assertFalse(SkillAvailability.isSkillSupported(PrimarySkillType.MACES),
                "the first answer must survive the second being set");
    }

    /** Clearing one gate's answer returns it to undecided without touching the other. */
    @Test
    void aGateCanBeReturnedToUndecided() {
        SkillAvailability.setSupportedForTesting(PrimarySkillType.MACES, false);
        SkillAvailability.setSupportedForTesting(PrimarySkillType.SPEARS, false);

        SkillAvailability.setSupportedForTesting(PrimarySkillType.MACES, null);
        assertTrue(SkillAvailability.isSkillSupported(PrimarySkillType.MACES));
        assertFalse(SkillAvailability.isSkillSupported(PrimarySkillType.SPEARS));
    }

    /**
     * The gate reaches the funnel every one of the six "disabled" behaviours passes through (XP,
     * procs, super abilities, the XP bar, {@code /mcstats}, plaques). Without this the probe could be
     * perfectly correct and still change nothing at all — which is exactly what the shipped
     * {@code <Skill>.Enabled} key did for years before GitHub #10: a config, a getter and a passing
     * unit test, and no call site.
     *
     * <p>⚠️ Driven through {@code setSupportedForTesting} rather than the live registry <b>because
     * the band this is developed on has both spears and maces</b>. Asserting the real answer here
     * would assert {@code true}, which a missing gate satisfies just as well.
     */
    @Test
    void aVersionThatCannotFurnishASkillDisablesItThroughSkillGating() {
        for (PrimarySkillType skill : GATES.keySet()) {
            SkillAvailability.setSupportedForTesting(skill, false);
            assertFalse(SkillGating.isSkillEnabled(skill), skill + " must be gated off");
            // The reference point, off the same run: the gate is not simply refusing everything.
            assertTrue(SkillGating.isSkillEnabled(PrimarySkillType.MINING));

            SkillAvailability.setSupportedForTesting(skill, true);
            assertTrue(SkillGating.isSkillEnabled(skill), skill + " must come back on");
            SkillAvailability.setSupportedForTesting(skill, null);
        }
    }

    /** A disabled parent's sub-skills go with it — that is the path every proc gate reads. */
    @Test
    void anUnsupportedSkillTakesItsSubSkillsWithIt() {
        SkillAvailability.setSupportedForTesting(PrimarySkillType.SPEARS, false);
        assertFalse(SkillGating.isSubSkillEnabled(SubSkillType.SPEARS_MOMENTUM));
        assertTrue(SkillGating.isSubSkillEnabled(SubSkillType.MINING_DOUBLE_DROPS));
        SkillAvailability.setSupportedForTesting(PrimarySkillType.SPEARS, true);
        assertTrue(SkillGating.isSubSkillEnabled(SubSkillType.SPEARS_MOMENTUM));

        SkillAvailability.setSupportedForTesting(PrimarySkillType.MACES, false);
        assertFalse(SkillGating.isSubSkillEnabled(SubSkillType.MACES_CRUSH));
        assertFalse(SkillGating.isSubSkillEnabled(SubSkillType.MACES_CRIPPLE));
        assertTrue(SkillGating.isSubSkillEnabled(SubSkillType.MINING_DOUBLE_DROPS));
        SkillAvailability.setSupportedForTesting(PrimarySkillType.MACES, true);
        assertTrue(SkillGating.isSubSkillEnabled(SubSkillType.MACES_CRUSH));
    }

    /**
     * Each gate probes for exactly what its classifier accepts — so an item cannot be classifiable
     * but unsearched, which is how two hard-coded copies of one list start disagreeing.
     */
    @Test
    void everyProbedPathIsAlsoWhatTheClassifierAccepts() {
        for (String path : pathsFor(PrimarySkillType.SPEARS)) {
            assertTrue(MATERIALS.isSpear(path), path + " is probed for but not classified as a spear");
        }
        assertFalse(MATERIALS.isSpear("iron_sword"));

        for (String path : pathsFor(PrimarySkillType.MACES)) {
            assertTrue(MATERIALS.isMace(path), path + " is probed for but not classified as a mace");
        }
        assertFalse(MATERIALS.isMace("iron_sword"));
    }

    /** The exposed views are read-only: a caller cannot quietly widen what counts. */
    @Test
    void theProbedListsCannotBeMutatedByTheirCallers() {
        assertThrows(UnsupportedOperationException.class,
                () -> new MaterialMapStore().getSpears().add("bamboo_spear"));
        assertThrows(UnsupportedOperationException.class,
                () -> new MaterialMapStore().getMaces().add("bamboo_mace"));
    }

    /**
     * The ids each ruling is written against, spelled out once. If a future Minecraft renames or adds
     * a tier, this is the test that says so out loud instead of the probe quietly looking for the
     * wrong thing.
     */
    @Test
    void theSpearListIsTheSevenVanillaTiers() {
        final Set<String> expected = new LinkedHashSet<>(Set.of("wooden_spear", "stone_spear",
                "copper_spear", "iron_spear", "golden_spear", "diamond_spear", "netherite_spear"));
        assertEquals(expected, pathsFor(PrimarySkillType.SPEARS));
    }

    /**
     * The mace has exactly one id and no tiers — which is why its gate is the sharpest of the two: a
     * single rename takes the whole skill out, with no sibling id to keep it alive.
     */
    @Test
    void theMaceListIsTheSingleVanillaMace() {
        assertEquals(Set.of("mace"), pathsFor(PrimarySkillType.MACES));
    }
}
