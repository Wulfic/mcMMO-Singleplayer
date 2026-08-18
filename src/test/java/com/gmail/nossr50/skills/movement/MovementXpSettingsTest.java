package com.gmail.nossr50.skills.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The speed clamp (D-AG6) — the highest-value tests in the whole skill.
 *
 * <p>This is the one piece of Agility that, if it goes wrong, does not merely mis-tune the skill but
 * breaks it structurally: without the clamp every speed buff becomes an XP multiplier and Fleet
 * Footed levels itself. {@link #sameDistancePaysTheSameAtEveryLevel()} is the direct assertion of
 * that property; if it ever goes red, the feedback loop is back.
 */
class MovementXpSettingsTest {

    private static final double EPSILON = 1.0E-9;

    /**
     * The shipped defaults, restated as literals rather than read from {@link MovementXpSettings}.
     *
     * <p>That duplication is deliberate. If these read the class constants, every derived assertion
     * below would silently follow a retune and prove nothing. Written out, moving a default fails
     * {@link #shippedDefaultsMatchTheConstantsUnderTest()} and forces whoever moved it to come here
     * and re-derive what it now buys — which is the entire job of the budget tests.
     */
    private static MovementXpSettings shipped() {
        final Map<Medium, Double> speeds = new EnumMap<>(Medium.class);
        speeds.put(Medium.LAND, 5.61);
        speeds.put(Medium.WATER, 3.16);
        speeds.put(Medium.AIR, 30.0);
        final Map<Medium, Double> multipliers = new EnumMap<>(Medium.class);
        multipliers.put(Medium.LAND, 1.0);
        multipliers.put(Medium.WATER, 1.15);
        multipliers.put(Medium.AIR, 0.6);
        return MovementXpSettings.of(15.0, speeds, multipliers);
    }

    @Test
    void shippedDefaultsMatchTheConstantsUnderTest() {
        // The tripwire for the duplication above: this is the only test that is *allowed* to pass by
        // being updated in lockstep with a default. The rest must be re-derived by hand.
        final MovementXpSettings settings = shipped();
        assertEquals(MovementXpSettings.DEFAULT_BASELINE_XP_PER_SECOND,
                settings.baselineXpPerSecond(), EPSILON);
        for (Medium medium : Medium.values()) {
            assertEquals(MovementXpSettings.defaultReferenceSpeed(medium),
                    settings.referenceSpeed(medium), EPSILON, "reference speed, " + medium);
            assertEquals(MovementXpSettings.defaultMediumMultiplier(medium),
                    settings.mediumMultiplier(medium), EPSILON, "medium multiplier, " + medium);
        }
    }

    /** Blocks covered in one tick at exactly the medium's reference speed. */
    private static double perTick(Medium medium) {
        return shipped().referenceSpeed(medium) / MovementXpSettings.TICKS_PER_SECOND;
    }

    // --- creditedSeconds: the clamp itself -----------------------------------------------------

    @Test
    void travellingAtTheReferenceSpeedCreditsExactlyOneTick() {
        final MovementXpSettings settings = shipped();
        for (Medium medium : Medium.values()) {
            assertEquals(1.0 / MovementXpSettings.TICKS_PER_SECOND,
                    settings.creditedSeconds(medium, perTick(medium)), EPSILON,
                    "at reference speed, " + medium);
        }
    }

    @Test
    void travellingAtHalfSpeedCreditsHalfATick() {
        final MovementXpSettings settings = shipped();
        for (Medium medium : Medium.values()) {
            assertEquals(1.0 / (MovementXpSettings.TICKS_PER_SECOND * 2),
                    settings.creditedSeconds(medium, perTick(medium) / 2), EPSILON,
                    "at half reference speed, " + medium);
        }
    }

    @Test
    void travellingFasterThanTheReferenceSpeedCreditsNoMore() {
        final MovementXpSettings settings = shipped();
        for (Medium medium : Medium.values()) {
            final double atReference = settings.creditedSeconds(medium, perTick(medium));
            // A rocket boost, Dolphin's Grace, an ice boat — anything that multiplies real speed.
            assertEquals(atReference, settings.creditedSeconds(medium, perTick(medium) * 10),
                    EPSILON, "at 10x reference speed, " + medium);
            assertEquals(atReference, settings.creditedSeconds(medium, perTick(medium) * 1000),
                    EPSILON, "at 1000x reference speed, " + medium);
        }
    }

    @Test
    void standingStillCreditsNothing() {
        final MovementXpSettings settings = shipped();
        for (Medium medium : Medium.values()) {
            assertEquals(0.0, settings.creditedSeconds(medium, 0.0), EPSILON);
            // A negative distance is nonsense but must not pay negative XP either.
            assertEquals(0.0, settings.creditedSeconds(medium, -5.0), EPSILON);
        }
    }

    @Test
    void aZeroReferenceSpeedPaysNothingRatherThanDividingByZero() {
        final Map<Medium, Double> speeds = new EnumMap<>(Medium.class);
        speeds.put(Medium.LAND, 0.0);
        final Map<Medium, Double> multipliers = new EnumMap<>(Medium.class);
        multipliers.put(Medium.LAND, 1.0);
        final MovementXpSettings broken = MovementXpSettings.of(15.0, speeds, multipliers);

        assertEquals(0.0, broken.creditedSeconds(Medium.LAND, 5.0), EPSILON);
        assertEquals(0.0, broken.xpFor(Medium.LAND, 5.0), EPSILON);
    }

    // --- the anti-feedback-loop property --------------------------------------------------------

    @Test
    void sameDistancePaysTheSameAtEveryLevel() {
        // The settings carry no level term at all, which is exactly the point: Fleet Footed can make
        // the player cover more ground per tick, but a tick's *payout* is capped at the reference
        // speed, so it cannot raise its own XP rate. This test exists to make that structural, not
        // incidental — reintroducing a level factor would fail here immediately.
        final MovementXpSettings settings = shipped();
        final double distance = perTick(Medium.LAND);
        final double xp = settings.xpFor(Medium.LAND, distance);

        // Even at ten times the distance a maxed Fleet Footed could produce, the payout is unchanged.
        assertEquals(xp, settings.xpFor(Medium.LAND, distance * 10), EPSILON);
    }

    // --- payout rates ---------------------------------------------------------------------------

    @Test
    void aFullSecondOfTravelPaysTheConfiguredRatePerMedium() {
        final MovementXpSettings settings = shipped();
        for (Medium medium : Medium.values()) {
            double earned = 0;
            for (int tick = 0; tick < MovementXpSettings.TICKS_PER_SECOND; tick++) {
                earned += settings.xpFor(medium, perTick(medium));
            }
            assertEquals(settings.baselineXpPerSecond() * settings.mediumMultiplier(medium), earned,
                    1.0E-6, "one second of travel in " + medium);
        }
    }

    @Test
    void xpBelowTheReferenceSpeedIsProRata() {
        // Pins the arguments this class hands to SpeedNormalisedXp, which every other xpFor
        // assertion in this file is structurally incapable of doing: they all sit at exactly the
        // reference speed (or a pure scaling of it), and there a transposed distance/referenceSpeed
        // pair is algebraically a no-op. Mutation-proven — swapping that pair in
        // MovementXpSettings#xpFor left every other test here green.
        final MovementXpSettings settings = shipped();
        for (Medium medium : Medium.values()) {
            final double expected = settings.baselineXpPerSecond() * settings.mediumMultiplier(medium)
                    / (MovementXpSettings.TICKS_PER_SECOND * 2);
            assertEquals(expected, settings.xpFor(medium, perTick(medium) / 2), EPSILON,
                    "half the reference speed, " + medium);
        }
    }

    @Test
    void derivedXpPerBlockMatchesThePlannedBudget() {
        // Per-block XP is a *derived* quantity — nobody tunes it, it falls out of the reference
        // speed. These are the numbers the budget was signed off against; if a default moves, this
        // test is where the consequence shows up.
        final MovementXpSettings settings = shipped();
        for (Medium medium : Medium.values()) {
            final double xpPerBlock =
                    settings.xpFor(medium, perTick(medium)) / perTick(medium);
            final double expected = switch (medium) {
                case LAND -> 2.67;
                case WATER -> 5.46;
                case AIR -> 0.30;
            };
            assertEquals(expected, xpPerBlock, 0.01, "derived XP per block, " + medium);
        }
    }

    @Test
    void noSingleMediumCanMaxTheSkillInUnderEightyHours() {
        // The actual definition of "not ridiculously fast" (D-AG6's guardrail). Total XP to RetroMode
        // level N on the shipped LINEAR curve (base 1020, multiplier 20) is 10N^2 + 1010N, so the cap
        // at level 1000 is 11,010,000. A cheap arithmetic test that fails loudly if someone "just
        // bumps" the baseline without re-deriving what it buys.
        final long xpToMax = 10L * 1000 * 1000 + 1010L * 1000;
        assertEquals(11_010_000L, xpToMax);

        final MovementXpSettings settings = shipped();
        for (Medium medium : Medium.values()) {
            final double xpPerSecond =
                    settings.baselineXpPerSecond() * settings.mediumMultiplier(medium);
            final double hours = xpToMax / xpPerSecond / 3600.0;
            assertTrue(hours >= 80.0,
                    medium + " maxes in " + Math.round(hours) + "h, under the 80h guardrail");
        }
    }
}
