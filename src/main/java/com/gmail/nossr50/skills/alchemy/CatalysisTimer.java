package com.gmail.nossr50.skills.alchemy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

/**
 * The MC-free half of the Catalysis brew-speed sub-skill: converts a brew-speed multiplier into the
 * number of <i>extra</i> brew-timer ticks to burn off this game tick, carrying the fractional
 * remainder between ticks.
 *
 * <p><b>Why this shape.</b> Legacy ran its own brew loop: {@code AlchemyBrewTask} held a
 * {@code double brewTimer} starting at 400 and subtracted {@link AlchemyManager#calculateBrewSpeed}
 * from it every tick, writing {@code (int) brewTimer} back to the stand. This port has no brew loop
 * to own — vanilla's {@code BrewingStandBlockEntity#tick} already decrements its {@code brewTime} by
 * exactly one per tick — so Catalysis instead subtracts the <i>difference</i> ahead of vanilla's own
 * decrement. At speed 4.0 that is three extra ticks per tick, which with vanilla's one makes four:
 * the same 100-tick brew legacy's timer produced, and, because the countdown still starts at 400, the
 * same "bar starts full and drains fast" animation legacy showed.
 *
 * <p>The multiplier is fractional (advanced.yml scales it from {@code MinSpeed} 1.0 to {@code MaxSpeed}
 * 4.0, and the Lucky perk multiplies by 4/3), but brew timers are integers, so the leftover fraction
 * is carried per brewing stand until it accumulates into a whole tick. A speed of 1.5 therefore
 * alternates zero and one extra tick, averaging the intended 1.5 timer-ticks per game tick.
 *
 * <p><b>The speed is resolved once per brew, not once per tick</b> — hence the {@link DoubleSupplier}
 * rather than a plain {@code double}. That is legacy's own behaviour (its task captured the speed in
 * its constructor, so levelling up mid-brew did not accelerate the brew already running), and it also
 * keeps the caller's owner lookup and config reads off a path that runs every tick for every brewing
 * stand in a loaded chunk.
 *
 * <p>Stands are keyed by their packed {@code BlockPos#asLong()}, treated here as an opaque long (the
 * same convention as {@link com.gmail.nossr50.util.PlacedBlockTracker}), which keeps this class free
 * of Minecraft types and unit-testable without the Knot classloader.
 */
public final class CatalysisTimer {

    /** Vanilla burns exactly one brew-timer tick per game tick; at this speed Catalysis is a no-op. */
    public static final double VANILLA_BREW_SPEED = 1.0;

    /**
     * The floor Catalysis may drive a running brew's timer to.
     *
     * <p><b>Load-bearing:</b> vanilla's tick treats {@code brewTime > 0} as "a brew is in progress"
     * and only crafts when its <i>own</i> decrement lands on exactly zero. Letting Catalysis reach
     * zero first would make vanilla take the other branch and start a <i>fresh</i> brew — burning
     * another blaze powder and resetting the timer to 400 — instead of finishing this one. Stopping
     * at one leaves vanilla's decrement to close it out, so the craft still fires on vanilla's terms.
     */
    public static final int MIN_BREW_TIME = 1;

    /**
     * A brew in progress: the speed captured when it started, and the timer ticks owed to it but not
     * yet whole.
     */
    private record Brew(double speed, double carriedTicks) {
    }

    /** Brewing-stand {@code BlockPos#asLong()} → the brew currently running on it. */
    private final Map<Long, Brew> brews = new ConcurrentHashMap<>();

    /**
     * The whole brew-timer ticks Catalysis should burn off this game tick, on top of the one vanilla
     * burns itself. Advances the stand's carried fraction as a side effect, so call this exactly once
     * per stand per tick, for as long as a brew is running there.
     *
     * @param standKey          the brewing stand's packed block position
     * @param brewSpeedSupplier the owner's brew-speed multiplier
     *                          ({@link AlchemyManager#calculateBrewSpeed}). Consulted <b>only</b> on
     *                          the first tick of a brew — see the class doc
     * @return the extra ticks to subtract, never negative
     */
    public int extraTicks(long standKey, DoubleSupplier brewSpeedSupplier) {
        Brew brew = brews.get(standKey);
        if (brew == null) {
            brew = new Brew(brewSpeedSupplier.getAsDouble(), 0.0);
        }

        if (brew.speed() <= VANILLA_BREW_SPEED) {
            // Nothing to accumulate, but keep the entry: it is what stops the supplier being
            // consulted again on every remaining tick of this brew.
            brews.put(standKey, brew);
            return 0;
        }

        final double owed = brew.carriedTicks() + (brew.speed() - VANILLA_BREW_SPEED);
        final int whole = (int) owed;
        brews.put(standKey, new Brew(brew.speed(), owed - whole));
        return whole;
    }

    /**
     * Apply {@code extraTicks} to a running brew's timer, clamped at {@link #MIN_BREW_TIME}. See that
     * constant for why the clamp is not optional.
     *
     * @param brewTime   the stand's current brew timer (expected to be above zero)
     * @param extraTicks the extra ticks from {@link #extraTicks}
     * @return the timer value to write back
     */
    public static int reducedBrewTime(int brewTime, int extraTicks) {
        return Math.max(MIN_BREW_TIME, brewTime - extraTicks);
    }

    /**
     * Forget a stand's brew — called once its timer reaches zero, so the next brew re-resolves the
     * owner's speed and starts from a whole tick rather than inheriting this one's rounding.
     */
    public void reset(long standKey) {
        brews.remove(standKey);
    }

    /** Drop every tracked stand (world close). */
    public void clear() {
        brews.clear();
    }

    /** How many stands are currently mid-brew. Exposed for tests and leak checks. */
    public int trackedStands() {
        return brews.size();
    }
}
