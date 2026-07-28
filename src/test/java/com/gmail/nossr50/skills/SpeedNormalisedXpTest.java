package com.gmail.nossr50.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The shared speed clamp — the single highest-value formula in both Agility and Stealth.
 *
 * <p>These assertions used to live per-medium in {@code MovementXpSettingsTest}. They are restated
 * here against the raw arithmetic because the property being protected is a property of the
 * <em>formula</em>, not of Agility's tuning of it: any future distance skill that calls this class
 * inherits the guarantee, and a regression here breaks every caller at once rather than one.
 *
 * <p>{@link #payoutIsIndependentOfHowFastYouGetThere()} is the structural one. If it goes red, a
 * speed sub-skill can raise its own XP rate again and the feedback loop is back.
 */
class SpeedNormalisedXpTest {

    private static final double EPSILON = 1.0E-9;

    /** A deliberately arbitrary reference speed: nothing here may depend on a shipped default. */
    private static final double REFERENCE = 4.0;

    /** Blocks covered in one tick at exactly the reference speed. */
    private static final double PER_TICK = REFERENCE / SpeedNormalisedXp.TICKS_PER_SECOND;

    @Test
    void travellingAtTheReferenceSpeedCreditsExactlyOneTick() {
        assertEquals(1.0 / SpeedNormalisedXp.TICKS_PER_SECOND,
                SpeedNormalisedXp.creditedSeconds(PER_TICK, REFERENCE), EPSILON);
    }

    @Test
    void travellingSlowerCreditsProRata() {
        assertEquals(1.0 / (SpeedNormalisedXp.TICKS_PER_SECOND * 2),
                SpeedNormalisedXp.creditedSeconds(PER_TICK / 2, REFERENCE), EPSILON);
        assertEquals(1.0 / (SpeedNormalisedXp.TICKS_PER_SECOND * 4),
                SpeedNormalisedXp.creditedSeconds(PER_TICK / 4, REFERENCE), EPSILON);
    }

    @Test
    void travellingFasterThanTheReferenceSpeedCreditsNoMore() {
        final double atReference = SpeedNormalisedXp.creditedSeconds(PER_TICK, REFERENCE);
        // A rocket boost, Dolphin's Grace, an ice boat, a maxed speed sub-skill — anything that
        // multiplies real blocks-per-second.
        assertEquals(atReference, SpeedNormalisedXp.creditedSeconds(PER_TICK * 10, REFERENCE),
                EPSILON);
        assertEquals(atReference, SpeedNormalisedXp.creditedSeconds(PER_TICK * 1000, REFERENCE),
                EPSILON);
    }

    @Test
    void standingStillCreditsNothing() {
        assertEquals(0.0, SpeedNormalisedXp.creditedSeconds(0.0, REFERENCE), EPSILON);
        // A negative distance is nonsense, but it must not pay *negative* XP either — that would
        // silently drain a player's skill instead of merely failing to pay them.
        assertEquals(0.0, SpeedNormalisedXp.creditedSeconds(-5.0, REFERENCE), EPSILON);
    }

    @Test
    void aNonsensicalReferenceSpeedPaysNothingRatherThanDividingByZero() {
        assertEquals(0.0, SpeedNormalisedXp.creditedSeconds(5.0, 0.0), EPSILON);
        assertEquals(0.0, SpeedNormalisedXp.creditedSeconds(5.0, -1.0), EPSILON);
        assertEquals(0.0, SpeedNormalisedXp.xpFor(15.0, 1.0, 5.0, 0.0), EPSILON);
    }

    // --- the anti-feedback-loop property --------------------------------------------------------

    @Test
    void payoutIsIndependentOfHowFastYouGetThere() {
        // The method signature carries no level and no speed-bonus term, which is the whole point:
        // a speed sub-skill can make the player cover more ground per tick, but a tick's *payout* is
        // capped at the reference speed, so it cannot raise its own XP rate. Making that structural
        // rather than incidental is why this test exists.
        final double xp = SpeedNormalisedXp.xpFor(15.0, 1.0, PER_TICK, REFERENCE);
        assertEquals(xp, SpeedNormalisedXp.xpFor(15.0, 1.0, PER_TICK * 10, REFERENCE), EPSILON);
        assertTrue(xp > 0, "a tick at the reference speed must pay something");
    }

    // --- payout rate ----------------------------------------------------------------------------

    @Test
    void aFullSecondOfTravelPaysExactlyTheBaselineTimesTheMultiplier() {
        final double baseline = 15.0;
        final double multiplier = 1.15;
        double earned = 0;
        for (int tick = 0; tick < SpeedNormalisedXp.TICKS_PER_SECOND; tick++) {
            earned += SpeedNormalisedXp.xpFor(baseline, multiplier, PER_TICK, REFERENCE);
        }
        assertEquals(baseline * multiplier, earned, 1.0E-6);
    }

    @Test
    void xpBelowTheReferenceSpeedIsProRata() {
        // Deliberately asserted at HALF the reference speed, and this is not a stylistic choice.
        // `xpFor` takes four bare doubles in a row, so transposing `distance` and `referenceSpeed`
        // compiles cleanly — and at *exactly* the reference speed that transposition is algebraically
        // a no-op (min(r, r/400)/(r/20) == 1/20 == min(r/20, r/20)/r), as is any pure scaling of the
        // distance. A mutation run proved every reference-speed-anchored assertion in this file and
        // in MovementXpSettingsTest passes with the arguments swapped. Only an off-reference distance
        // separates the two, so this test is the one that actually pins the parameter order.
        final double expected = 15.0 * 1.0 * (1.0 / (SpeedNormalisedXp.TICKS_PER_SECOND * 2));
        assertEquals(expected, SpeedNormalisedXp.xpFor(15.0, 1.0, PER_TICK / 2, REFERENCE), EPSILON);
    }

    @Test
    void theReferenceSpeedCancelsOutOfTheRate() {
        // Two skills with different reference speeds but the same baseline pay the same XP per
        // second of travel — the reference speed decides how *fast you must move* to earn the full
        // rate, never how much that rate is. Stealth's slow sneak reference must not make sneaking
        // pay less per second than sprinting does; if it did, the per-skill baselines would have
        // been tuned against a moving target.
        final double slow = SpeedNormalisedXp.xpFor(15.0, 1.0, 1.295 / 20.0, 1.295);
        final double fast = SpeedNormalisedXp.xpFor(15.0, 1.0, 5.61 / 20.0, 5.61);
        assertEquals(slow, fast, EPSILON);
    }
}
