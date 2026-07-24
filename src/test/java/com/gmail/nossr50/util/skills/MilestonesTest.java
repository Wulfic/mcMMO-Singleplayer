package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.util.skills.Milestones.MilestoneAward;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Minecraft-free milestone decision core. No registries, configs, or player are
 * needed — the core is a pure function of the before/after numbers, which is the whole point of the
 * MC-free-core / MC-typed-seam split.
 */
class MilestonesTest {

    private static final int UNLIMITED = Integer.MAX_VALUE;

    private static boolean has(List<MilestoneAward> awards, String path, boolean repeatable) {
        return awards.stream().anyMatch(a -> a.path().equals(path) && a.repeatable() == repeatable);
    }

    // --- Round-level -------------------------------------------------------

    @Test
    void noLevelChangeYieldsNothing() {
        assertTrue(Milestones.skillLevelAwards(PrimarySkillType.MINING, 100, 100, UNLIMITED, 100)
                .isEmpty());
        assertTrue(Milestones.skillLevelAwards(PrimarySkillType.MINING, 120, 100, UNLIMITED, 100)
                .isEmpty(), "a decrease is not a milestone");
    }

    @Test
    void crossingAnIntervalBracketFiresARepeatableLevelAward() {
        final List<MilestoneAward> awards =
                Milestones.skillLevelAwards(PrimarySkillType.MINING, 90, 110, UNLIMITED, 100);
        assertEquals(1, awards.size());
        assertTrue(has(awards, "level/mining", true));
    }

    @Test
    void stayingWithinABracketFiresNothing() {
        assertTrue(Milestones.skillLevelAwards(PrimarySkillType.MINING, 10, 20, UNLIMITED, 100)
                .isEmpty());
        // 100..199 is one bracket; 130 -> 180 does not cross a multiple of 100.
        assertTrue(Milestones.skillLevelAwards(PrimarySkillType.MINING, 130, 180, UNLIMITED, 100)
                .isEmpty());
    }

    @Test
    void aMultiBracketBurstStillFiresASingleLevelAward() {
        final List<MilestoneAward> awards =
                Milestones.skillLevelAwards(PrimarySkillType.MINING, 50, 350, UNLIMITED, 100);
        assertEquals(1, awards.size(), "one plaque for the whole burst, not one per bracket");
        assertTrue(has(awards, "level/mining", true));
    }

    @Test
    void nonPositiveIntervalDisablesRoundLevelAwards() {
        assertTrue(Milestones.skillLevelAwards(PrimarySkillType.MINING, 90, 110, UNLIMITED, 0)
                .isEmpty());
    }

    // --- Maxed -------------------------------------------------------------

    @Test
    void crossingTheCapFiresAOneShotMaxedAward() {
        final List<MilestoneAward> awards =
                Milestones.skillLevelAwards(PrimarySkillType.SWORDS, 95, 100, 100, 100);
        assertEquals(1, awards.size(), "the bracket landing on the cap is owned by the maxed award");
        assertTrue(has(awards, "maxed/swords", false));
    }

    @Test
    void alreadyMaxedFiresNothingFurther() {
        assertTrue(Milestones.skillLevelAwards(PrimarySkillType.SWORDS, 100, 105, 100, 100).isEmpty(),
                "past the cap there is no new milestone");
    }

    @Test
    void aBurstThatCrossesBothAMidBracketAndTheCapFiresBoth() {
        // 850 -> 1000 (cap 1000): the 900 bracket is legitimately crossed below the cap, and the cap
        // itself is reached. Both plaques are earned; only the exact-cap 1000 bracket is suppressed.
        final List<MilestoneAward> awards =
                Milestones.skillLevelAwards(PrimarySkillType.MINING, 850, 1000, 1000, 100);
        assertEquals(2, awards.size());
        assertTrue(has(awards, "level/mining", true));
        assertTrue(has(awards, "maxed/mining", false));
    }

    @Test
    void reachingCapExactlyOnABracketDoesNotDoubleFire() {
        // 950 -> 1000: only the maxed award; the 1000 bracket is clamped away.
        final List<MilestoneAward> awards =
                Milestones.skillLevelAwards(PrimarySkillType.MINING, 950, 1000, 1000, 100);
        assertEquals(1, awards.size());
        assertTrue(has(awards, "maxed/mining", false));
    }

    @Test
    void unlimitedCapNeverFiresMaxed() {
        final List<MilestoneAward> awards =
                Milestones.skillLevelAwards(PrimarySkillType.MINING, 90, 110, UNLIMITED, 100);
        assertFalse(has(awards, "maxed/mining", false));
    }

    // --- Power tiers -------------------------------------------------------

    @Test
    void crossingASinglePowerTier() {
        final List<MilestoneAward> awards = Milestones.powerAwards(400, 600);
        assertEquals(1, awards.size());
        assertTrue(has(awards, "power/500", false));
    }

    @Test
    void tierBoundaryIsInclusive() {
        assertTrue(has(Milestones.powerAwards(499, 500), "power/500", false));
    }

    @Test
    void crossingSeveralPowerTiersInOneBurstFiresEach() {
        final List<MilestoneAward> awards = Milestones.powerAwards(400, 2100);
        assertEquals(3, awards.size());
        assertTrue(has(awards, "power/500", false));
        assertTrue(has(awards, "power/1000", false));
        assertTrue(has(awards, "power/2000", false));
    }

    @Test
    void noPowerTierCrossedFiresNothing() {
        assertTrue(Milestones.powerAwards(600, 700).isEmpty());
        assertTrue(Milestones.powerAwards(600, 600).isEmpty());
    }

    // --- Rank --------------------------------------------------------------

    @Test
    void rankAwardOnlyWhenANewRankUnlocked() {
        assertTrue(Milestones.rankAwards(PrimarySkillType.AXES, false).isEmpty());

        final List<MilestoneAward> awards = Milestones.rankAwards(PrimarySkillType.AXES, true);
        assertEquals(1, awards.size());
        assertTrue(has(awards, "rank/axes", true));
    }

    @Test
    void skillKeyIsLowercase() {
        assertEquals("woodcutting", Milestones.key(PrimarySkillType.WOODCUTTING));
    }
}
