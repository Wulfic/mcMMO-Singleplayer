package com.gmail.nossr50.util.skills;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
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

    /**
     * The named standing a skill level falls into, ascending. A level plaque is titled after its
     * tier ("Master Miner") rather than a bare "Mining Milestone", which is the whole point of the
     * ladder: the toast renders <em>only</em> the frame line and the title (the vanilla
     * {@code AdvancementToast} never reads the description), so the title is the only place a
     * milestone can say anything specific about itself.
     *
     * <p>Parallel to {@link #TIER_THRESHOLDS} — index {@code i} of one belongs with index {@code i}
     * of the other.
     */
    static final String[] TIER_KEYS =
            {"apprentice", "adept", "expert", "master", "grandmaster"};

    /**
     * The minimum skill level for each {@link #TIER_KEYS} standing.
     *
     * <p>The first entry is a floor, not a gate: with the shipped {@code Level_Interval} of 100 the
     * earliest possible level plaque lands exactly on 100, but a smaller configured interval can fire
     * one below that, and such a plaque is still "apprentice" rather than no tier at all. That is why
     * {@link #tierKey} starts at the lowest tier and only ever climbs — no configured interval can
     * produce a level the ladder has no name for.
     */
    static final int[] TIER_THRESHOLDS = {100, 250, 500, 750, 1000};

    private Milestones() {
    }

    /**
     * The named standing {@code level} falls into — the lowest tier whose threshold it has reached,
     * floored at the first tier (see {@link #TIER_THRESHOLDS}).
     *
     * @param level a skill level
     * @return the matching key from {@link #TIER_KEYS}, never {@code null}
     */
    static @NotNull String tierKey(int level) {
        String tier = TIER_KEYS[0];
        for (int i = 0; i < TIER_THRESHOLDS.length; i++) {
            if (level >= TIER_THRESHOLDS[i]) {
                tier = TIER_KEYS[i];
            }
        }
        return tier;
    }

    /**
     * One granted milestone advancement.
     *
     * @param path the id path under {@code mcmmo:milestone/} (e.g. {@code level/mining/master})
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
     *   <li><b>Round level</b> ({@code level/<skill>/<tier>}, repeatable): the level change crossed a
     *       multiple of {@code interval} that is strictly below the cap. The just-below-cap clamp keeps
     *       the last bracket from double-firing alongside the maxed plaque (e.g. cap 1000, interval 100:
     *       going 950→1000 yields only the maxed award, not maxed + the 1000 bracket).
     *       <p>The tier comes from the <em>post-event</em> level (see {@link #tierKey}), so the plaque is
     *       titled for the standing the player actually holds now. Crossings inside one tier reuse that
     *       tier's id — that is what {@code repeatable} is for, and it is honest: at level 400 you are
     *       still an Adept.</li>
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
                awards.add(new MilestoneAward(
                        "level/" + key(skill) + "/" + tierKey(newLevel), true));
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
     * One sub-skill's rank movement across a single level-up, as observed by the caller.
     *
     * @param subSkill the sub-skill that was tracked
     * @param oldRank its rank before the level-up loop ({@code 0} = not yet unlocked)
     * @param newRank its rank after
     */
    public record RankChange(@NotNull SubSkillType subSkill, int oldRank, int newRank) {
    }

    /**
     * The rank awards for a level-up, one per sub-skill that actually climbed.
     *
     * <p>This is deliberately <em>per sub-skill</em> rather than one "a rank went up somewhere in
     * Mining" plaque: the toast can only ever show a title, so naming the ability
     * ("Super Breaker Unlocked") is the difference between a plaque that tells the player something
     * and one that does not. A first unlock and a later rank are separate ids
     * ({@code rank/<sub_skill>/unlocked} vs {@code …/improved}) because those are different events and
     * a single shared id could only be titled vaguely enough to cover both.
     *
     * <p>Every award is repeatable: ranks climb many times over a skill's life (Skill Shot alone has
     * 20), and even a first unlock can recur if hardcore stat loss drops the player back below it.
     *
     * @param changes the tracked sub-skills and their before/after ranks; entries that did not climb
     *                are ignored
     * @return one award per sub-skill that gained rank (possibly empty), never {@code null}
     */
    public static @NotNull List<MilestoneAward> rankAwards(@NotNull List<RankChange> changes) {
        final List<MilestoneAward> awards = new ArrayList<>();
        for (RankChange change : changes) {
            if (change.newRank() <= change.oldRank()) {
                continue;
            }
            final String stage = (change.oldRank() <= 0) ? "unlocked" : "improved";
            awards.add(new MilestoneAward("rank/" + key(change.subSkill()) + "/" + stage, true));
        }
        return awards;
    }

    /** The lowercase id key for a skill (e.g. {@code MINING} → {@code mining}). */
    static @NotNull String key(@NotNull PrimarySkillType skill) {
        return skill.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The lowercase id key for a sub-skill (e.g. {@code MINING_SUPER_BREAKER} →
     * {@code mining_super_breaker}). The parent prefix is kept: it is what makes the id unique, since
     * sub-skill names repeat across skills ({@code MINING_DOUBLE_DROPS} and
     * {@code HERBALISM_DOUBLE_DROPS} are both "Double Drops").
     */
    static @NotNull String key(@NotNull SubSkillType subSkill) {
        return subSkill.name().toLowerCase(Locale.ROOT);
    }
}
