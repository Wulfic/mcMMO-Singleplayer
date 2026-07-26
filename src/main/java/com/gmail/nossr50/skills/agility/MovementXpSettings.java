package com.gmail.nossr50.skills.agility;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import java.util.EnumMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * An immutable snapshot of the Agility movement-XP tuning, and the arithmetic that turns a tick of
 * travel into XP.
 *
 * <p><b>Distance is the sensor; time is the currency.</b> Agility pays per <em>second</em> of
 * qualifying travel, and each tick's distance is clamped at the medium's reference speed before it
 * is credited:
 *
 * <pre>
 *   refDist      = referenceSpeed / 20                  // blocks the reference speed covers in a tick
 *   creditedSecs = min(distance, refDist) / referenceSpeed
 *   xp           = baselineXpPerSecond * mediumMultiplier * creditedSecs
 * </pre>
 *
 * <p>Paying a flat XP-per-block instead would be wrong in three compounding ways, and the third is
 * the one that is easy to miss:
 * <ol>
 *   <li><b>Fast media firehose.</b> An elytra covers ground roughly 5× faster than sprinting and
 *       10× faster than swimming, so any shared per-block rate makes gliding level the skill an
 *       order of magnitude faster than swimming for strictly less effort.</li>
 *   <li><b>Every speed buff becomes an XP multiplier.</b> Depth Strider, Dolphin's Grace, Speed
 *       potions, ice boats and firework rockets all raise blocks-per-second.</li>
 *   <li><b>The skill accelerates its own levelling.</b> Fleet Footed makes you faster, which earns
 *       more XP per second, which levels Fleet Footed — a positive feedback loop inside a single
 *       skill. No value of a per-block constant fixes a feedback loop; only the clamp does.</li>
 * </ol>
 *
 * <p>Travelling at or above the reference speed pays the full rate and never more, which kills all
 * three at once. Travelling slower pays pro-rata, so a wade or a gentle jog still counts. Standing
 * still pays nothing. Sprint-jumping — about 27% faster than flat sprinting, and what players
 * actually do — pays exactly the same as sprinting; that is intended, not a rounding error.
 *
 * <p>This type is a snapshot rather than a set of live config reads because it is consulted 20×/s
 * per player. Re-reading the YAML tree every tick is the trap the Alchemy Catalysis brew hook fell
 * into; {@link AgilityManager} builds one of these lazily per player session, which is exactly the
 * lifetime of a loaded config.
 */
public final class MovementXpSettings {

    /** Server ticks per second — the rate at which movement is sampled. */
    public static final double TICKS_PER_SECOND = 20.0;

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
                config == null ? 30.0 : config.getMovementBaselineXpPerSecond(),
                speeds, multipliers);
    }

    /** Build an explicit settings snapshot. Test seam — production code uses {@link #fromConfig()}. */
    public static @NotNull MovementXpSettings of(double baselineXpPerSecond,
            @NotNull Map<Medium, Double> referenceSpeeds,
            @NotNull Map<Medium, Double> mediumMultipliers) {
        return new MovementXpSettings(baselineXpPerSecond,
                new EnumMap<>(referenceSpeeds), new EnumMap<>(mediumMultipliers));
    }

    private static double defaultReferenceSpeed(@NotNull Medium medium) {
        return switch (medium) {
            case LAND -> 5.61;
            case WATER -> 3.16;
            case AIR -> 30.0;
        };
    }

    private static double defaultMediumMultiplier(@NotNull Medium medium) {
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
     * speed.
     *
     * <p>The clamp is the whole balance model, so it lives here as pure arithmetic rather than
     * inside the tick handler — it is the part most likely to be got wrong and therefore the part
     * that most needs to be provable. Note it is independent of the player's level by construction:
     * if a level term ever appears in this method, the Fleet-Footed feedback loop is back.
     *
     * @param medium   the medium travelled this tick
     * @param distance horizontal distance moved this tick, in blocks
     * @return credited seconds; {@code 1/20} at or above the reference speed, pro-rata below it,
     *         and {@code 0} for a non-positive distance or a nonsensical reference speed
     */
    public double creditedSeconds(@NotNull Medium medium, double distance) {
        final double reference = referenceSpeed(medium);
        if (distance <= 0 || reference <= 0) {
            return 0.0;
        }
        final double perTickDistance = reference / TICKS_PER_SECOND;
        return Math.min(distance, perTickDistance) / reference;
    }

    /**
     * The XP this tick of travel earns.
     *
     * @param medium   the medium travelled this tick
     * @param distance horizontal distance moved this tick, in blocks
     * @return the XP earned, possibly fractional (the caller accumulates it)
     */
    public double xpFor(@NotNull Medium medium, double distance) {
        return baselineXpPerSecond * mediumMultiplier(medium) * creditedSeconds(medium, distance);
    }
}
