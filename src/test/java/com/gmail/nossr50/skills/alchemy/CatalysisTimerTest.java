package com.gmail.nossr50.skills.alchemy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the MC-free {@link CatalysisTimer}: the whole/fractional split of the brew-speed
 * bonus, per-stand isolation, the once-per-brew speed resolution, the {@code MIN_BREW_TIME} clamp
 * that keeps vanilla in charge of firing the craft, and the total brew duration the whole thing
 * exists to produce.
 *
 * <p>Stand keys are opaque longs here (the production caller packs them with {@code BlockPos#asLong()}),
 * so any two distinct values read as "two different brewing stands".
 */
class CatalysisTimerTest {

    private static final long STAND_A = 1L;
    private static final long STAND_B = 2L;

    /** advanced.yml ships Catalysis MaxSpeed 4.0 — a fully levelled brewer. */
    private static final double MAX_SPEED = 4.0;

    /** Vanilla's brew length, and the value vanilla's tick resets {@code brewTime} to. */
    private static final int VANILLA_BREW_TICKS = 400;

    private CatalysisTimer timer;

    @BeforeEach
    void setUp() {
        timer = new CatalysisTimer();
    }

    private static DoubleSupplier speed(double brewSpeed) {
        return () -> brewSpeed;
    }

    @Test
    void vanillaSpeedCostsNothing() {
        // MinSpeed is 1.0, so an unskilled brewer must come out exactly vanilla: no extra ticks.
        assertEquals(0, timer.extraTicks(STAND_A, speed(CatalysisTimer.VANILLA_BREW_SPEED)));
        assertEquals(0, timer.extraTicks(STAND_A, speed(CatalysisTimer.VANILLA_BREW_SPEED)));
    }

    @Test
    void wholeSpeedBonusYieldsItsWholeTicksEveryTick() {
        // Speed 4.0 = vanilla's one tick plus three of ours, with no fraction to carry.
        for (int tick = 0; tick < 5; tick++) {
            assertEquals(3, timer.extraTicks(STAND_A, speed(MAX_SPEED)));
        }
    }

    @Test
    void fractionalSpeedBonusIsCarriedBetweenTicks() {
        // Speed 1.5 owes half an extra tick each game tick: nothing on the first, a whole one on the
        // second. Rounding it away per-tick instead would silently make 1.5 behave exactly like 1.0.
        assertEquals(0, timer.extraTicks(STAND_A, speed(1.5)));
        assertEquals(1, timer.extraTicks(STAND_A, speed(1.5)));
        assertEquals(0, timer.extraTicks(STAND_A, speed(1.5)));
        assertEquals(1, timer.extraTicks(STAND_A, speed(1.5)));
    }

    @Test
    void standsCarryTheirFractionsIndependently() {
        assertEquals(0, timer.extraTicks(STAND_A, speed(1.5)));
        // Stand B starts from zero rather than inheriting A's half-tick.
        assertEquals(0, timer.extraTicks(STAND_B, speed(1.5)));
        assertEquals(1, timer.extraTicks(STAND_A, speed(1.5)));
        assertEquals(1, timer.extraTicks(STAND_B, speed(1.5)));
        assertEquals(2, timer.trackedStands());
    }

    @Test
    void speedIsResolvedOncePerBrewNotOncePerTick() {
        // The supplier hides an owner lookup plus three config reads, on a path that runs every tick
        // for every loaded brewing stand — and legacy captured the speed once, in its task's
        // constructor, so levelling up mid-brew did not accelerate the brew already running.
        final AtomicInteger resolutions = new AtomicInteger();
        final DoubleSupplier counting = () -> {
            resolutions.incrementAndGet();
            return MAX_SPEED;
        };

        for (int tick = 0; tick < 10; tick++) {
            timer.extraTicks(STAND_A, counting);
        }
        assertEquals(1, resolutions.get());

        // The next brew on that stand re-resolves it, so a level gained since then does apply.
        timer.reset(STAND_A);
        timer.extraTicks(STAND_A, counting);
        assertEquals(2, resolutions.get());
    }

    @Test
    void vanillaSpeedIsAlsoResolvedOnlyOnce() {
        // The no-bonus path must cache too, or the commonest case — a low-level brewer sitting at
        // MinSpeed 1.0 — would pay the lookup on all 400 ticks of every brew.
        final AtomicInteger resolutions = new AtomicInteger();
        final DoubleSupplier counting = () -> {
            resolutions.incrementAndGet();
            return CatalysisTimer.VANILLA_BREW_SPEED;
        };

        for (int tick = 0; tick < 10; tick++) {
            assertEquals(0, timer.extraTicks(STAND_A, counting));
        }
        assertEquals(1, resolutions.get());
    }

    @Test
    void resetDropsTheBrew() {
        assertEquals(0, timer.extraTicks(STAND_A, speed(1.5)));
        timer.reset(STAND_A);
        assertEquals(0, timer.trackedStands());
        // Back to a cold start: the first tick of the next brew owes nothing whole again.
        assertEquals(0, timer.extraTicks(STAND_A, speed(1.5)));
    }

    @Test
    void clearDropsEveryStand() {
        timer.extraTicks(STAND_A, speed(1.5));
        timer.extraTicks(STAND_B, speed(1.5));
        timer.clear();
        assertEquals(0, timer.trackedStands());
    }

    @Test
    void reducedBrewTimeNeverReachesZero() {
        // The clamp is load-bearing: at zero, vanilla's tick reads "no brew in progress" and starts a
        // fresh one — burning another blaze powder and resetting to 400 instead of crafting.
        assertEquals(CatalysisTimer.MIN_BREW_TIME, CatalysisTimer.reducedBrewTime(2, 3));
        assertEquals(CatalysisTimer.MIN_BREW_TIME, CatalysisTimer.reducedBrewTime(1, 3));
        assertEquals(7, CatalysisTimer.reducedBrewTime(10, 3));
    }

    @Test
    void maxSpeedQuartersTheBrewDuration() {
        // The whole point of the sub-skill, driven through the exact loop the tick hook runs: vanilla
        // burns one tick per game tick and Catalysis burns the rest, so a 400-tick brew finishes in
        // 100 game ticks at speed 4.0 (matching the legacy AlchemyBrewTask's 400 / brewSpeed).
        assertEquals(VANILLA_BREW_TICKS / (int) MAX_SPEED, gameTicksToBrew(MAX_SPEED));
    }

    @Test
    void unskilledBrewTakesTheFullVanillaDuration() {
        assertEquals(VANILLA_BREW_TICKS, gameTicksToBrew(CatalysisTimer.VANILLA_BREW_SPEED));
    }

    @Test
    void fractionalSpeedShortensTheBrewProportionally() {
        // 400 / 1.6 = 250. Proves the carried fractions really do add up over a whole brew rather
        // than rounding away tick by tick.
        assertEquals(250, gameTicksToBrew(1.6));
    }

    /** Replays a full brew the way the tick hook drives it, and returns how many game ticks it took. */
    private int gameTicksToBrew(double brewSpeed) {
        int brewTime = VANILLA_BREW_TICKS;
        int gameTicks = 0;

        while (brewTime > 0) {
            brewTime = CatalysisTimer.reducedBrewTime(brewTime,
                    timer.extraTicks(STAND_A, speed(brewSpeed)));
            brewTime--; // vanilla's own decrement, which is what lands the timer on zero
            gameTicks++;
        }

        assertEquals(0, brewTime, "vanilla's decrement must be the one that reaches zero");
        return gameTicks;
    }
}
