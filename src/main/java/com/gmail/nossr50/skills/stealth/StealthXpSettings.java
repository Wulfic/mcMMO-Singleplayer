package com.gmail.nossr50.skills.stealth;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SpeedNormalisedXp;
import org.jetbrains.annotations.NotNull;

/**
 * An immutable snapshot of Stealth's sneak-XP tuning.
 *
 * <p>Stealth pays per <em>second</em> of qualifying sneak-travel, with each tick's distance clamped
 * at the sneak reference speed — the same model Agility's movement domains use, applied through the
 * shared {@link SpeedNormalisedXp}. It is not merely consistency for its own sake: Stealth has the
 * self-levelling feedback loop in a sharper form than Agility does. {@link StealthManager Padfoot}
 * raises the {@code sneaking_speed} attribute from its vanilla {@code 0.3} toward {@code 1.0}, so a
 * maxed Padfoot player covers ground <b>more than three times</b> faster than an unranked one —
 * against Fleet Footed's 20%. Paying per block would make Padfoot a 3.3× XP multiplier on itself.
 *
 * <p>Under the clamp a maxed Padfoot player earns the same XP per <em>second</em> as a beginner and
 * simply covers more ground doing it. That is the intended reward: the sub-skill saves you time, it
 * does not print XP.
 *
 * <p>Where the numbers come from, so nobody has to re-derive them:
 * <ul>
 *   <li>{@link #DEFAULT_SNEAK_REFERENCE_SPEED} is vanilla walk speed (≈4.317 b/s) times the
 *       {@code sneaking_speed} attribute's default of {@code 0.3} — bytecode-verified from
 *       {@code EntityAttributes}, where it is a {@code ClampedEntityAttribute(0.3, 0.0, 1.0)}. The
 *       0.3 factor is exact; the walk speed is the conventional figure and is still a §G
 *       measurement item.</li>
 *   <li>{@link #DEFAULT_BASELINE_XP_PER_SECOND} is picked from a time-to-max, not by feel:
 *       11,010,000 XP to RetroMode 1000 on the shipped linear curve, at 50 XP/s, is ≈61 hours of
 *       continuous sneaking. That is deliberately the fastest continuous earner in the mod and
 *       deliberately below the ≥80 h guardrail every other skill is held to (GitHub #6, ruled by
 *       the user; doubled from the original 25 XP/s ≈122 h). Sneaking halves your speed and demands
 *       constant attention, so it is priced for what it costs the player rather than by a guardrail
 *       that measures distance.</li>
 * </ul>
 *
 * <p>A snapshot rather than live config reads because it is consulted 20×/s per player — the
 * Alchemy Catalysis per-tick-config-read trap. {@link StealthManager} builds one lazily per player
 * session, which is exactly the lifetime of a loaded config.
 */
public final class StealthXpSettings {

    /**
     * XP per second of qualifying sneak-travel, absent config.
     *
     * <p>This constant and {@link #DEFAULT_SNEAK_REFERENCE_SPEED} are the single source of Stealth's
     * XP defaults; {@code ExperienceConfig} reads them rather than keeping a second copy, because a
     * config fallback that disagrees with the class it feeds is a silent balance bug in exactly the
     * tests that do not wire a config.
     *
     * <p>⚠️ It must equal {@code experience.yml}'s shipped value, and the ModMenu editor's "reset to
     * default" reads it too. Those three drifted apart once already (the editor offered 30.0 long
     * after the YAML was halved to 15.0), so
     * {@code ExperienceConfigTest#shippedSneakBaselineMatchesTheConstant} now pins the pair.
     */
    public static final double DEFAULT_BASELINE_XP_PER_SECOND = 50.0;

    /**
     * Blocks per second at which sneak-travel pays its full rate: vanilla walk speed × the
     * {@code sneaking_speed} attribute default of 0.3.
     */
    public static final double DEFAULT_SNEAK_REFERENCE_SPEED = 1.295;

    private final double baselineXpPerSecond;
    private final double sneakReferenceSpeed;

    private StealthXpSettings(double baselineXpPerSecond, double sneakReferenceSpeed) {
        this.baselineXpPerSecond = baselineXpPerSecond;
        this.sneakReferenceSpeed = sneakReferenceSpeed;
    }

    /**
     * Snapshot the current {@link ExperienceConfig}, falling back to the documented defaults when no
     * config is wired (unit tests, and between world sessions) so sneak XP is never silently zero
     * because of load ordering.
     */
    public static @NotNull StealthXpSettings fromConfig() {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        return config == null
                ? new StealthXpSettings(DEFAULT_BASELINE_XP_PER_SECOND,
                        DEFAULT_SNEAK_REFERENCE_SPEED)
                : new StealthXpSettings(config.getSneakBaselineXpPerSecond(),
                        config.getSneakReferenceSpeed());
    }

    /** Build an explicit settings snapshot. Test seam — production uses {@link #fromConfig()}. */
    public static @NotNull StealthXpSettings of(double baselineXpPerSecond,
            double sneakReferenceSpeed) {
        return new StealthXpSettings(baselineXpPerSecond, sneakReferenceSpeed);
    }

    public double baselineXpPerSecond() {
        return baselineXpPerSecond;
    }

    public double sneakReferenceSpeed() {
        return sneakReferenceSpeed;
    }

    /**
     * Seconds of sneak-travel credited for a tick's distance — the speed clamp.
     *
     * @param distance horizontal distance moved this tick, in blocks
     * @return credited seconds; {@code 1/20} at or above the reference speed, pro-rata below it
     */
    public double creditedSeconds(double distance) {
        return SpeedNormalisedXp.creditedSeconds(distance, sneakReferenceSpeed);
    }

    /**
     * The XP this tick of sneak-travel earns.
     *
     * @param distance horizontal distance moved this tick, in blocks
     * @return the XP earned, possibly fractional (the caller accumulates it)
     */
    public double xpFor(double distance) {
        return SpeedNormalisedXp.xpFor(baselineXpPerSecond, 1.0, distance, sneakReferenceSpeed);
    }
}
