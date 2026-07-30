package com.gmail.nossr50.skills.hunter;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.skills.SkillManager;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Hunter — the mob-knowledge skill. The more of one creature you have personally killed, the better
 * you get at killing <em>that</em> creature.
 *
 * <p><b>MC-free by construction</b>, like every other manager: this class owns the arithmetic and the
 * gates, the platform layer decides what died and who killed it.
 *
 * <h2>Two axes, deliberately independent</h2>
 * <table>
 *   <caption>Hunter's two progression axes</caption>
 *   <tr><th>Axis</th><th>Currency</th><th>Reward</th></tr>
 *   <tr><td><b>Mob mastery</b> (horizontal)</td><td>kills of <em>one</em> mob type</td>
 *       <td>flat bonus damage against that mob only, at three fixed thresholds, never reset</td></tr>
 *   <tr><td><b>Hunter level</b> (vertical)</td><td>XP from any qualifying kill</td>
 *       <td>increased loot, unlocked per mob tier, on the normal {@code 10N² + 1010N} curve</td></tr>
 * </table>
 *
 * <p>Killing 10,000 zombies makes you a zombie specialist; killing 200 of everything makes you a
 * generalist with better drops. Neither substitutes for the other, and that separation is the reason
 * the skill is worth building — it must survive any balance pass intact.
 *
 * <h2>⚠️ Mob Mastery does not fit {@code RankUtils}, and must not be forced through it</h2>
 * Every other sub-skill in the mod unlocks on <em>skill level</em> via {@code skillranks.yml}.
 * Mastery unlocks on a <em>per-mob counter</em>, which no rank config can express — so the tier
 * resolver lives here, on the manager. Routing it through {@code RankUtils} would produce a sub-skill
 * whose rank display lies.
 *
 * <h2>Stage 2 scope — read this before adding to the class</h2>
 * This is the skill's plumbing stage: the kill counters and the threshold arithmetic they feed.
 * <b>Nothing calls {@link #masteryDamageBonus} yet.</b> It lands on the
 * {@code EntityDamageListener#onModifyAppliedDamage} seam in stage 4, and it must run <b>last</b> in
 * that chain — after Sprint Smash and after Stealth Assassin — because Assassin multiplies the whole
 * melee total, so a Hunter bonus applied first would be multiplied along with it. XP
 * ({@code xpForKill}), the tier table, Trophy Hunter's loot re-roll and Quarry Sense are stages 5–7
 * and are deliberately absent rather than stubbed.
 *
 * @see <a href="file:../../../../../../../plans/new-skills/hunter.md">plans/new-skills/hunter.md</a>
 */
public class HunterManager extends SkillManager {

    /**
     * Kills of one mob type needed for each mastery tier, ascending.
     *
     * <p>Fixed rather than configurable-per-tier for now: three numbers a player can learn are worth
     * more than three knobs nobody turns, and the whole point of the horizontal axis is that it means
     * the same thing for every mob. Parallel to {@link #MASTERY_DAMAGE_BONUS} — index {@code i} of one
     * belongs with index {@code i} of the other.
     *
     * <p>The gap between hand-killing and farming these numbers <em>is</em> the feature: at a
     * sustained ~6 kills/min, 10,000 kills is roughly 28 hours; a gold farm produces 3,000+/hour and
     * would do it in an afternoon. Stage 1's spawn-origin gate is what separates the two, and it is a
     * prerequisite of this counter rather than a follow-up to it.
     */
    public static final int[] MASTERY_THRESHOLDS = {500, 2_500, 10_000};

    /**
     * Bonus damage at each {@link #MASTERY_THRESHOLDS} tier: half a heart, one heart, one and a half.
     *
     * <p><b>Halved from the drafted +2/+4/+6 by user ruling on 2026-07-30</b>, and the reason is worth
     * keeping: this is a <em>flat</em> add, so it is proportionally worst for the strongest weapon and
     * absurd for the weakest. At +6.0 a bare fist hits for 7.0 — a diamond sword's worth of punch from
     * kill counts alone, on top of whatever Unarmed already adds, compounding with Stealth Assassin. At
     * +3.0 the multipliers are netherite 1.375×, wooden 1.75×, bare fist 4×.
     */
    public static final double[] MASTERY_DAMAGE_BONUS = {1.0, 2.0, 3.0};

    public HunterManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.HUNTER);
    }

    // --- Mob mastery: the per-mob counters ------------------------------------------------------

    /**
     * How many of {@code mobId} this player has killed.
     *
     * @param mobId the mob's raw registry id, e.g. {@code minecraft:zombie}
     */
    public int getKills(@NotNull String mobId) {
        return mmoPlayer.getProfile().getMobKills(mobId);
    }

    /**
     * Count one kill and hand back the new total.
     *
     * <p>The caller is responsible for the gate — a farmed mob must never reach here. Attribution,
     * transient summons, player-built golems and spawn origin are all decided in the platform layer
     * where the entity is; this method's contract is simply "this kill counts".
     *
     * @param mobId the mob's raw registry id
     * @return the count after this kill
     */
    public int recordKill(@NotNull String mobId) {
        return mmoPlayer.getProfile().incrementMobKills(mobId);
    }

    /** Every mob type this player has killed, and how often. Unmodifiable. */
    public @NotNull Map<String, Integer> getAllKills() {
        return mmoPlayer.getProfile().getAllMobKills();
    }

    // --- Mob mastery: the threshold arithmetic --------------------------------------------------

    /**
     * The mastery tier a kill count has reached: {@code 0} for none, then 1–3.
     *
     * <p>Walks the whole table upward rather than returning on the first match, so the tiers cannot be
     * got wrong by reordering them and a count past the last threshold clamps at the top tier instead
     * of running off the end.
     *
     * @param killsOfThisMob kills of the mob in question; a negative count reads as none
     */
    public int masteryTier(int killsOfThisMob) {
        int tier = 0;
        for (int i = 0; i < MASTERY_THRESHOLDS.length; i++) {
            if (killsOfThisMob >= MASTERY_THRESHOLDS[i]) {
                tier = i + 1;
            }
        }
        return tier;
    }

    /** The mastery tier this player has reached against {@code mobId}. */
    public int masteryTierAgainst(@NotNull String mobId) {
        return masteryTier(getKills(mobId));
    }

    /**
     * The flat bonus damage a kill count is worth, capped at the top tier.
     *
     * <p>Indexed off {@link #masteryTier} rather than compared against the thresholds a second time,
     * so the two can never disagree about where a tier begins.
     *
     * @param killsOfThisMob kills of the mob being hit
     * @return {@code 0.0} below the first threshold, else that tier's bonus
     */
    public double masteryDamageBonus(int killsOfThisMob) {
        final int tier = masteryTier(killsOfThisMob);
        return tier <= 0 ? 0.0 : MASTERY_DAMAGE_BONUS[tier - 1];
    }

    /** The flat bonus damage this player currently gets against {@code mobId}. */
    public double masteryDamageBonusAgainst(@NotNull String mobId) {
        return masteryDamageBonus(getKills(mobId));
    }

    /**
     * Whether this kill just crossed a mastery threshold — the trigger for stage 3's notification.
     *
     * <p>Expressed as "did the tier change" rather than "is the count exactly a threshold" on purpose:
     * a single kill is the only thing that moves the counter today, but a future bulk grant (a command,
     * a data fix) that skipped a threshold would otherwise swallow the notification silently.
     *
     * @param killsBefore the count before the kill
     * @param killsAfter  the count after it
     */
    public boolean crossedMasteryThreshold(int killsBefore, int killsAfter) {
        return masteryTier(killsAfter) > masteryTier(killsBefore);
    }
}
