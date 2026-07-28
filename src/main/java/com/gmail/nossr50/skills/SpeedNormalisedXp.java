package com.gmail.nossr50.skills;

/**
 * The speed-normalised travel-XP formula, shared by every skill that pays for distance moved.
 *
 * <p><b>Distance is the sensor; time is the currency.</b> A tick's distance is clamped at a
 * reference speed before it is credited, so travel pays per <em>second</em> rather than per block:
 *
 * <pre>
 *   refDist      = referenceSpeed / 20                  // blocks the reference speed covers in a tick
 *   creditedSecs = min(distance, refDist) / referenceSpeed
 *   xp           = baselineXpPerSecond * multiplier * creditedSecs
 * </pre>
 *
 * <p>Paying a flat XP-per-block instead would be wrong in three compounding ways, and the third is
 * the one that is easy to miss:
 * <ol>
 *   <li><b>Fast media firehose.</b> An elytra covers ground roughly 5× faster than sprinting and
 *       10× faster than swimming, so any shared per-block rate makes gliding level a skill an order
 *       of magnitude faster than swimming for strictly less effort.</li>
 *   <li><b>Every speed buff becomes an XP multiplier.</b> Depth Strider, Dolphin's Grace, Speed
 *       potions, ice boats and firework rockets all raise blocks-per-second.</li>
 *   <li><b>The skill accelerates its own levelling.</b> A speed sub-skill makes you faster, which
 *       earns more XP per second, which levels that sub-skill — a positive feedback loop inside a
 *       single skill. No value of a per-block constant fixes a feedback loop; only the clamp does.
 *       Agility's Fleet Footed and Stealth's Padfoot are both exactly this shape.</li>
 * </ol>
 *
 * <p>Travelling at or above the reference speed pays the full rate and never more, which kills all
 * three at once. Travelling slower pays pro-rata, so a wade or a gentle jog still counts. Standing
 * still pays nothing.
 *
 * <p><b>Why this is its own class.</b> Agility built the formula first, inside a per-medium config
 * snapshot keyed by its own {@code Medium} enum. Stealth needs the identical arithmetic against a
 * single sneak reference speed, and the two ways of sharing it that do not involve this class are
 * both bad: threading a {@code SNEAK} constant through {@code Medium} leaks a Stealth concept into
 * Agility's accumulator array, medium classifier, Fleet Footed rank ladder and stats renderer; and
 * copying the arithmetic duplicates the single most important formula in both skills, where the two
 * copies would eventually disagree about something as quiet as the sign of a guard. So the formula
 * lives here — <b>no skill, no medium, no config, no level term</b> — and each skill owns only its
 * own tuning.
 *
 * <p>The absence of a level parameter is the load-bearing property, not an omission: if one ever
 * appears in this class, the feedback loop above is back for every caller at once.
 */
public final class SpeedNormalisedXp {

    private SpeedNormalisedXp() {
    }

    /** Server ticks per second — the rate at which movement is sampled. */
    public static final double TICKS_PER_SECOND = 20.0;

    /**
     * How many seconds of travel a tick's distance is worth, clamped at {@code referenceSpeed}.
     *
     * @param distance       horizontal distance moved this tick, in blocks
     * @param referenceSpeed blocks per second at which travel pays its full rate
     * @return credited seconds; {@code 1/20} at or above the reference speed, pro-rata below it, and
     *         {@code 0} for a non-positive distance or a nonsensical reference speed
     */
    public static double creditedSeconds(double distance, double referenceSpeed) {
        if (distance <= 0 || referenceSpeed <= 0) {
            return 0.0;
        }
        final double perTickDistance = referenceSpeed / TICKS_PER_SECOND;
        return Math.min(distance, perTickDistance) / referenceSpeed;
    }

    /**
     * The XP a tick of travel earns.
     *
     * @param baselineXpPerSecond the skill's XP per second of qualifying travel
     * @param multiplier          the weighting for this kind of travel
     * @param distance            horizontal distance moved this tick, in blocks
     * @param referenceSpeed      blocks per second at which travel pays its full rate
     * @return the XP earned, possibly fractional (the caller accumulates it)
     */
    public static double xpFor(double baselineXpPerSecond, double multiplier, double distance,
            double referenceSpeed) {
        return baselineXpPerSecond * multiplier * creditedSeconds(distance, referenceSpeed);
    }
}
