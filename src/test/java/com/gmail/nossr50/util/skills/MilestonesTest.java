package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
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
        assertTrue(has(awards, "level/mining/apprentice", true));
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
        assertTrue(has(awards, "level/mining/adept", true),
                "the tier is the standing the player ends on (350), not the one they left");
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
        assertTrue(has(awards, "level/mining/grandmaster", true));
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

    // --- Tiers -------------------------------------------------------------

    @Test
    void eachTierThresholdSelectsItsOwnTier() {
        assertEquals("apprentice", Milestones.tierKey(100));
        assertEquals("adept", Milestones.tierKey(250));
        assertEquals("expert", Milestones.tierKey(500));
        assertEquals("master", Milestones.tierKey(750));
        assertEquals("grandmaster", Milestones.tierKey(1000));
    }

    @Test
    void aLevelInsideATierKeepsThatTier() {
        assertEquals("apprentice", Milestones.tierKey(249));
        assertEquals("adept", Milestones.tierKey(499));
        assertEquals("grandmaster", Milestones.tierKey(9999), "the top tier has no ceiling");
    }

    @Test
    void theFirstTierIsAFloorNotAGate() {
        // A configured Level_Interval below 100 can fire a plaque before the apprentice threshold.
        // Such a level must still have a name, or the runtime would grant an id with no resource.
        assertEquals("apprentice", Milestones.tierKey(25));
        assertEquals("apprentice", Milestones.tierKey(1));
        assertEquals("apprentice", Milestones.tierKey(0));
    }

    @Test
    void tierLaddersStayInLockStep() {
        assertEquals(Milestones.TIER_KEYS.length, Milestones.TIER_THRESHOLDS.length,
                "TIER_KEYS and TIER_THRESHOLDS are parallel arrays");
        for (int i = 1; i < Milestones.TIER_THRESHOLDS.length; i++) {
            assertTrue(Milestones.TIER_THRESHOLDS[i] > Milestones.TIER_THRESHOLDS[i - 1],
                    "thresholds must ascend, or tierKey would never reach tier " + i);
        }
    }

    // --- Rank --------------------------------------------------------------

    private static Milestones.RankChange change(SubSkillType subSkill, int oldRank, int newRank) {
        return new Milestones.RankChange(subSkill, oldRank, newRank);
    }

    @Test
    void aFirstRankIsAnUnlockAndALaterRankIsAnImprovement() {
        assertTrue(has(Milestones.rankAwards(
                        List.of(change(SubSkillType.MINING_SUPER_BREAKER, 0, 1))),
                "rank/mining_super_breaker/unlocked", true));

        assertTrue(has(Milestones.rankAwards(
                        List.of(change(SubSkillType.ARCHERY_SKILL_SHOT, 6, 7))),
                "rank/archery_skill_shot/improved", true));
    }

    @Test
    void aSubSkillThatDidNotClimbFiresNothing() {
        assertTrue(Milestones.rankAwards(
                List.of(change(SubSkillType.MINING_SUPER_BREAKER, 1, 1))).isEmpty());
        assertTrue(Milestones.rankAwards(
                        List.of(change(SubSkillType.MINING_SUPER_BREAKER, 2, 1))).isEmpty(),
                "a rank loss is not a milestone");
        assertTrue(Milestones.rankAwards(List.of()).isEmpty());
    }

    @Test
    void everySubSkillThatClimbedGetsItsOwnPlaque() {
        // One level-up can rank several abilities at once; each is named, because a single
        // "something in Mining ranked up" plaque is exactly the vague text this ladder replaced.
        final List<MilestoneAward> awards = Milestones.rankAwards(List.of(
                change(SubSkillType.MINING_SUPER_BREAKER, 0, 1),
                change(SubSkillType.MINING_DOUBLE_DROPS, 0, 1),
                change(SubSkillType.MINING_BLAST_MINING, 3, 4)));

        assertEquals(3, awards.size());
        assertTrue(has(awards, "rank/mining_super_breaker/unlocked", true));
        assertTrue(has(awards, "rank/mining_double_drops/unlocked", true));
        assertTrue(has(awards, "rank/mining_blast_mining/improved", true));
    }

    @Test
    void skillKeyIsLowercase() {
        assertEquals("woodcutting", Milestones.key(PrimarySkillType.WOODCUTTING));
    }

    @Test
    void subSkillKeyKeepsItsParentPrefixSoSharedNamesStayDistinct() {
        // "Double Drops" exists in both skills; without the prefix they would collide on one id and
        // a Herbalism unlock would pop the Mining plaque.
        assertEquals("mining_double_drops", Milestones.key(SubSkillType.MINING_DOUBLE_DROPS));
        assertEquals("herbalism_double_drops", Milestones.key(SubSkillType.HERBALISM_DOUBLE_DROPS));
    }
}
