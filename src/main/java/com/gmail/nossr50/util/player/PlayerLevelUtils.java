package com.gmail.nossr50.util.player;

/**
 * The early-game XP boost: while a skill is still below its cutoff, every gain in that skill carries
 * a flat bonus so the very first level does not feel like a wall.
 *
 * <p>Port of legacy {@code util.player.PlayerLevelUtils}, kept Minecraft-free (it takes levels and
 * XP amounts, never a player entity) so both callers — the XP pipeline in {@code McMMOPlayer} and
 * the boss-bar title in {@code ExperienceBarWrapper} — can ask the same question, and so the
 * arithmetic is unit-testable without a world.
 *
 * <p><b>The feature was dead in this port until the 2026-08-06 wiring audit.</b>
 * {@code EarlyGameBoost.Enabled} shipped in {@code experience.yml}, had a getter, had a ModMenu
 * switch and a {@code XPBar.Template.EarlyGameBoost} locale string — and no code read any of it.
 *
 * <p>⚠️ <b>The cutoff is 1, and that is upstream's real value, not a placeholder.</b> Upstream's
 * per-skill cutoff calculation (level cap × a multiplier, or 50/5 by retro mode) is commented out in
 * its own source; the live {@code getEarlyGameCutoff} returns a hard-coded {@code 1}. So the boost
 * applies to a skill sitting at level 0 and stops the moment it reaches level 1. Faithfully copied
 * rather than "fixed" — widening the cutoff is a balance decision, not a wiring one, and it would
 * change the XP curve of all 27 skills for anyone already playing.
 */
public final class PlayerLevelUtils {

    /**
     * The skill level at or above which the boost no longer applies. See the class note: upstream's
     * {@code getEarlyGameCutoff} is a hard-coded {@code 1} for every skill.
     */
    public static final int EARLY_GAME_CUTOFF = 1;

    /** The fraction of a full level the boost adds to each qualifying gain (legacy's {@code 0.05}). */
    private static final double EARLY_GAME_BONUS_FRACTION = 0.05D;

    private PlayerLevelUtils() {
    }

    /**
     * Whether a skill at {@code skillLevel} is still in its early game.
     *
     * <p>Says nothing about whether the boost is <i>enabled</i> — legacy split those deliberately, and
     * both callers need the level question on its own (the boss bar also recolours on it).
     */
    public static boolean qualifiesForEarlyGameBoost(int skillLevel) {
        return skillLevel < EARLY_GAME_CUTOFF;
    }

    /**
     * The bonus XP a qualifying gain earns: 5% of one full level, truncated — legacy
     * {@code (int) (mmoPlayer.getXpToLevel(skill) * 0.05)}.
     *
     * <p>Note this is a flat addition per <em>gain</em>, not a multiplier: twenty awards of any size
     * are worth a level on their own while the skill is at 0, which is the whole point of the
     * mechanic. Returns 0 for a non-positive requirement so a misconfigured curve cannot subtract XP.
     */
    public static int earlyGameBonusXp(int xpToLevel) {
        if (xpToLevel <= 0) {
            return 0;
        }
        return (int) (xpToLevel * EARLY_GAME_BONUS_FRACTION);
    }
}
