package com.gmail.nossr50.util.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The early-game boost's arithmetic, MC-free.
 *
 * <p>Worth pinning despite being three lines: the cutoff is the whole mechanic, and it is a value
 * that looks like a placeholder ({@code 1}) but is upstream's real one. Somebody will eventually
 * "fix" it to 5 or 50 — these tests make that an explicit decision with a red suite behind it
 * rather than a quiet retune of all 27 skills' first level.
 */
class PlayerLevelUtilsTest {

    @Test
    void onlyLevelZeroQualifies() {
        assertTrue(PlayerLevelUtils.qualifiesForEarlyGameBoost(0), "a brand-new skill is boosted");
        assertFalse(PlayerLevelUtils.qualifiesForEarlyGameBoost(1),
                "the boost stops at the first level — upstream's getEarlyGameCutoff is 1");
        assertFalse(PlayerLevelUtils.qualifiesForEarlyGameBoost(500));
    }

    @Test
    void cutoffIsOneAndIsStatedInTheCode() {
        assertEquals(1, PlayerLevelUtils.EARLY_GAME_CUTOFF,
                "changing this retunes the first level of every skill — see the class javadoc");
    }

    @Test
    void bonusIsFivePercentOfALevelTruncated() {
        assertEquals(51, PlayerLevelUtils.earlyGameBonusXp(1020), "1020 XP to level -> 5% is 51");
        assertEquals(5, PlayerLevelUtils.earlyGameBonusXp(100));
        assertEquals(0, PlayerLevelUtils.earlyGameBonusXp(19), "5% of 19 truncates to 0, not 1");
    }

    /**
     * A non-positive requirement must yield no bonus. Legacy would have produced a negative number
     * here and <em>subtracted</em> XP from the gain; nothing ships a negative curve, but the boost
     * is added to every award in the game and is not the place to find out.
     */
    @Test
    void nonPositiveRequirementYieldsNoBonus() {
        assertEquals(0, PlayerLevelUtils.earlyGameBonusXp(0));
        assertEquals(0, PlayerLevelUtils.earlyGameBonusXp(-1020));
    }
}
