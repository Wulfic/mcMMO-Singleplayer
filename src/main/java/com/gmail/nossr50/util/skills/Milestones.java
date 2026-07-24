package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/**
 * Decides which "milestone" advancements a leveling event just earned. Minecraft-free and pure, so
 * it is exhaustively unit-testable; the caller ({@link com.gmail.nossr50.datatypes.player.McMMOPlayer})
 * feeds it the before/after numbers it already holds and grants each returned {@link MilestoneAward}
 * through the {@link com.gmail.nossr50.platform.PlatformPlayer} advancement seam.
 *
 * <p><b>Why advancements?</b> This is the port's optional support for the client-side
 * <em>Advancement Plaques</em> mod, which exposes no API — it re-skins the <em>vanilla advancement
 * toast</em>. So a "milestone plaque" is nothing more than a hidden vanilla advancement we grant at
 * the right moment: with Advancement Plaques installed the player sees a plaque, without it they see
 * a normal toast, and mcMMO carries no dependency on the mod either way.
 *
 * <p>Each award's {@link MilestoneAward#path()} is the advancement id under {@code mcmmo:milestone/…}
 * (bundled as datapack JSON in {@code data/mcmmo/advancement/milestone/}). {@code repeatable} awards
 * are re-granted via revoke+grant so the toast/plaque re-pops every time the milestone recurs (each
 * round-level bracket, each new rank); one-shot awards (maxing a skill, crossing a power tier) toast
 * only the first time.
 */
public final class Milestones {

    /**
     * Total power-level thresholds that each fire a one-shot plaque the first time they are crossed.
     * Referenced by the resource drift-guard test so the bundled {@code power/<tier>.json} files stay
     * in lock-step with what this class can emit.
     */
    static final int[] POWER_TIERS = {500, 1000, 2000, 3500, 5000, 10000};

    private Milestones() {
    }

    /**
     * One granted milestone advancement.
     *
     * @param path the id path under {@code mcmmo:milestone/} (e.g. {@code level/mining})
     * @param repeatable whether the toast/plaque should re-pop on recurrence (revoke+grant) rather
     *                   than firing only once
     */
    public record MilestoneAward(@NotNull String path, boolean repeatable) {
    }

    /**
     * Round-level and skill-maxed awards for a single skill's level change (from {@code oldLevel} to
     * {@code newLevel} in one XP event).
     *
     * <ul>
     *   <li><b>Maxed</b> ({@code maxed/<skill>}, one-shot): the level change crossed {@code levelCap}.</li>
     *   <li><b>Round level</b> ({@code level/<skill>}, repeatable): the level change crossed a multiple
     *       of {@code interval} that is strictly below the cap. The just-below-cap clamp keeps the last
     *       bracket from double-firing alongside the maxed plaque (e.g. cap 1000, interval 100: going
     *       950→1000 yields only the maxed award, not maxed + the 1000 bracket).</li>
     * </ul>
     *
     * @param skill the skill that leveled
     * @param oldLevel level before the event
     * @param newLevel level after the event
     * @param levelCap the skill's level cap ({@code <= 0} means "no cap", so no maxed award)
     * @param interval round-level bracket size ({@code <= 0} disables round-level awards)
     * @return awards earned (possibly empty), never {@code null}
     */
    public static @NotNull List<MilestoneAward> skillLevelAwards(@NotNull PrimarySkillType skill,
            int oldLevel, int newLevel, int levelCap, int interval) {
        final List<MilestoneAward> awards = new ArrayList<>(2);
        if (newLevel <= oldLevel) {
            return awards;
        }

        final boolean maxed = levelCap > 0 && newLevel >= levelCap && oldLevel < levelCap;
        if (maxed) {
            awards.add(new MilestoneAward("maxed/" + key(skill), false));
        }

        if (interval > 0) {
            // Clamp the post-level to just under the cap so the bracket that lands on/over the cap is
            // owned by the maxed award rather than producing a second plaque.
            final int clampedNew = (levelCap > 0) ? Math.min(newLevel, levelCap - 1) : newLevel;
            if (clampedNew / interval > oldLevel / interval) {
                awards.add(new MilestoneAward("level/" + key(skill), true));
            }
        }

        return awards;
    }

    /**
     * Power-level tier awards for a total-power change (from {@code oldPower} to {@code newPower}).
     * Emits a one-shot {@code power/<tier>} award for every {@link #POWER_TIERS} threshold crossed by
     * this change (a large multi-level burst can cross several at once).
     *
     * @param oldPower total power level before the event
     * @param newPower total power level after the event
     * @return awards earned (possibly empty), never {@code null}
     */
    public static @NotNull List<MilestoneAward> powerAwards(int oldPower, int newPower) {
        final List<MilestoneAward> awards = new ArrayList<>();
        if (newPower <= oldPower) {
            return awards;
        }
        for (int tier : POWER_TIERS) {
            if (oldPower < tier && newPower >= tier) {
                awards.add(new MilestoneAward("power/" + tier, false));
            }
        }
        return awards;
    }

    /**
     * The rank-unlock award for a skill. Emits a single repeatable {@code rank/<skill>} award when the
     * caller detected that at least one of the skill's sub-skills reached a new rank this level-up.
     *
     * @param skill the skill that leveled
     * @param unlockedNewRank whether a sub-skill of {@code skill} crossed into a higher rank
     * @return the award (one element) when {@code unlockedNewRank}, otherwise empty; never {@code null}
     */
    public static @NotNull List<MilestoneAward> rankAwards(@NotNull PrimarySkillType skill,
            boolean unlockedNewRank) {
        if (!unlockedNewRank) {
            return List.of();
        }
        return List.of(new MilestoneAward("rank/" + key(skill), true));
    }

    /** The lowercase id key for a skill (e.g. {@code MINING} → {@code mining}). */
    static @NotNull String key(@NotNull PrimarySkillType skill) {
        return skill.name().toLowerCase(Locale.ROOT);
    }
}
