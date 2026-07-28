package com.gmail.nossr50.skills.agility;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SpeedNormalisedXp;
import java.util.EnumMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * An immutable snapshot of the Agility movement-XP tuning — the <em>per-medium</em> half of the
 * speed-normalised travel-XP model.
 *
 * <p>The arithmetic itself lives in {@link SpeedNormalisedXp}, which owns the clamp and the reasons
 * for it; this class owns only Agility's tuning of it: one reference speed and one multiplier per
 * {@link Medium}, on a shared baseline. Stealth's Padfoot has the same self-levelling feedback loop
 * Fleet Footed does and applies the same clamp against a single sneak reference speed, so the
 * formula had to stop being a private detail of this class.
 *
 * <p>Sprint-jumping — about 27% faster than flat sprinting, and what players actually do — pays
 * exactly the same as sprinting; that is the clamp working, not a rounding error.
 *
 * <p>This type is a snapshot rather than a set of live config reads because it is consulted 20×/s
 * per player. Re-reading the YAML tree every tick is the trap the Alchemy Catalysis brew hook fell
 * into; {@link AgilityManager} builds one of these lazily per player session, which is exactly the
 * lifetime of a loaded config.
 */
public final class MovementXpSettings {

    /**
     * Server ticks per second — the rate at which movement is sampled.
     *
     * <p>Kept as an alias of {@link SpeedNormalisedXp#TICKS_PER_SECOND} rather than a second
     * constant: a tick rate that disagreed between the two would mis-scale every derived budget
     * number silently.
     */
    public static final double TICKS_PER_SECOND = SpeedNormalisedXp.TICKS_PER_SECOND;

    /**
     * XP per second of travel before the per-medium multiplier, when {@code experience.yml} does not
     * say otherwise.
     *
     * <p>Halved from the original 30.0 on 2026-07-27. Movement is the most passive source in the mod
     * — it pays for playing the game normally — so it should not out-earn a skill that has to be
     * worked at. Note that <b>walking is not a medium at all</b> and neither is crouched movement:
     * the only ways to earn this are sprinting, swimming and gliding.
     *
     * <p>This constant, {@link #defaultReferenceSpeed} and {@link #defaultMediumMultiplier} are the
     * single source of the movement-XP defaults; {@code ExperienceConfig} reads them rather than
     * keeping a second copy, because a config fallback that disagrees with the class it feeds is a
     * silent balance bug in exactly the tests that don't wire a config.
     */
    public static final double DEFAULT_BASELINE_XP_PER_SECOND = 15.0;

    private final double baselineXpPerSecond;
    private final Map<Medium, Double> referenceSpeeds;
    private final Map<Medium, Double> mediumMultipliers;

    private MovementXpSettings(double baselineXpPerSecond,
            @NotNull Map<Medium, Double> referenceSpeeds,
            @NotNull Map<Medium, Double> mediumMultipliers) {
        this.baselineXpPerSecond = baselineXpPerSecond;
        this.referenceSpeeds = referenceSpeeds;
        this.mediumMultipliers = mediumMultipliers;
    }

    /**
     * Snapshot the current {@link ExperienceConfig}. Falls back to the documented defaults when no
     * config is wired (unit tests, and between world sessions), so this never returns {@code null}
     * and movement XP is never silently zero because of load ordering.
     */
    public static @NotNull MovementXpSettings fromConfig() {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        final Map<Medium, Double> speeds = new EnumMap<>(Medium.class);
        final Map<Medium, Double> multipliers = new EnumMap<>(Medium.class);
        for (Medium medium : Medium.values()) {
            speeds.put(medium, config == null
                    ? defaultReferenceSpeed(medium)
                    : config.getMovementReferenceSpeed(medium));
            multipliers.put(medium, config == null
                    ? defaultMediumMultiplier(medium)
                    : config.getMovementMediumMultiplier(medium));
        }
        return new MovementXpSettings(
                config == null
                        ? DEFAULT_BASELINE_XP_PER_SECOND
                        : config.getMovementBaselineXpPerSecond(),
                speeds, multipliers);
    }

    /** Build an explicit settings snapshot. Test seam — production code uses {@link #fromConfig()}. */
    public static @NotNull MovementXpSettings of(double baselineXpPerSecond,
            @NotNull Map<Medium, Double> referenceSpeeds,
            @NotNull Map<Medium, Double> mediumMultipliers) {
        return new MovementXpSettings(baselineXpPerSecond,
                new EnumMap<>(referenceSpeeds), new EnumMap<>(mediumMultipliers));
    }

    /**
     * Blocks per second a medium pays its full rate at, absent config. Land is the well-known
     * vanilla sprint speed; Water and Air are still estimates pending in-game measurement.
     */
    public static double defaultReferenceSpeed(@NotNull Medium medium) {
        return switch (medium) {
            case LAND -> 5.61;
            case WATER -> 3.16;
            case AIR -> 30.0;
        };
    }

    /** The per-medium weighting on {@link #DEFAULT_BASELINE_XP_PER_SECOND}, absent config. */
    public static double defaultMediumMultiplier(@NotNull Medium medium) {
        return switch (medium) {
            case LAND -> 1.0;
            case WATER -> 1.15;
            case AIR -> 0.6;
        };
    }

    public double baselineXpPerSecond() {
        return baselineXpPerSecond;
    }

    public double referenceSpeed(@NotNull Medium medium) {
        return referenceSpeeds.getOrDefault(medium, defaultReferenceSpeed(medium));
    }

    public double mediumMultiplier(@NotNull Medium medium) {
        return mediumMultipliers.getOrDefault(medium, defaultMediumMultiplier(medium));
    }

    /**
     * How many seconds of travel this tick's distance is worth, clamped at the medium's reference
     * speed. See {@link SpeedNormalisedXp#creditedSeconds} for why the clamp exists.
     *
     * @param medium   the medium travelled this tick
     * @param distance horizontal distance moved this tick, in blocks
     * @return credited seconds; {@code 1/20} at or above the reference speed, pro-rata below it,
     *         and {@code 0} for a non-positive distance or a nonsensical reference speed
     */
    public double creditedSeconds(@NotNull Medium medium, double distance) {
        return SpeedNormalisedXp.creditedSeconds(distance, referenceSpeed(medium));
    }

    /**
     * The XP this tick of travel earns.
     *
     * @param medium   the medium travelled this tick
     * @param distance horizontal distance moved this tick, in blocks
     * @return the XP earned, possibly fractional (the caller accumulates it)
     */
    public double xpFor(@NotNull Medium medium, double distance) {
        return SpeedNormalisedXp.xpFor(baselineXpPerSecond, mediumMultiplier(medium), distance,
                referenceSpeed(medium));
    }
}
