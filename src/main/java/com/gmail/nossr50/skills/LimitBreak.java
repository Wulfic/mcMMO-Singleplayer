package com.gmail.nossr50.skills;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.skills.RankUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Limit Break: the flat per-rank damage bonus every one of the eight combat skills carries
 * (legacy {@code CombatUtils#canUseLimitBreak} + {@code getLimitBreakDamage}). Server-free, so the
 * arithmetic is unit-testable; the call sites are {@link MeleeDamageBonus} for the six melee weapons
 * and {@code fabric.listeners.EntityDamageListener} for the three projectile arms.
 *
 * <h2>Why the armour-quality table is gone</h2>
 * Legacy nerfs the bonus against a lightly-armoured <em>player</em> — 75% off at armour tier ≤4, 50%
 * at ≤8, 25% at ≤12 — and passes a sentinel quality of {@code 1000} for anything that is not a
 * player, which skips all three tiers. In singleplayer the target is <b>never</b> a player, so only
 * the sentinel branch is reachable and the bonus is always the un-nerfed rank. The table and its
 * {@code getArmorQualityLevel}/{@code getArmorQuality} helpers are collapsed away rather than ported
 * as an unreachable branch — the same honest collapse made for Disarm, Iron Grip and
 * {@code CombatUtils#shouldBeAffected}'s player arm. Porting a branch nothing can enter is how the
 * §F dead-code defects were made.
 *
 * <p>The practical consequence is worth stating plainly, because it is the whole balance story:
 * <b>PVE receives the full bonus, not a nerfed one.</b> Rank N grants +N raw damage, and the ranks
 * unlock every 10 levels, so a level-100 weapon skill adds +10 per hit — more than a diamond sword's
 * base 7.
 *
 * <h2>The gate, and why "off" means invisible rather than merely inert</h2>
 * Legacy allows Limit Break against a mob only when {@code Skills.General.LimitBreak.AllowPVE} is
 * set, and that key ships {@code false}. In a game with no other players that means "off, always" —
 * which is exactly why the port previously shipped eight rank plaques announcing eight mechanics
 * that could never fire (TODO.md item 3.1). <b>It stays off by default</b>, because the un-nerfed
 * PVE bonus is a large power increase and that should be the player's choice, not a surprise.
 *
 * <p>What changed is that "off" is now honest. {@link #isEnabled()} is consulted by three surfaces,
 * not one:
 * <ul>
 *   <li>this class, so no damage is added;</li>
 *   <li>{@code SkillStatsRenderer}, so {@code /mcstats} does not list a sub-skill the player cannot
 *       use; and</li>
 *   <li>{@code McMMOPlayer#rankedSubSkillsOf}, so no "You can now use Swords Limit Break." plaque
 *       toasts for it.</li>
 * </ul>
 * Wiring the damage alone would have left the original defect exactly as it was — a dead mechanic
 * with a live surface — merely with the switch moved. <b>A toggle that silences the mechanic but not
 * its advertising is not a fix.</b>
 */
public final class LimitBreak {

    private LimitBreak() {
    }

    /**
     * Whether Limit Break is switched on at all, for every weapon and every surface.
     *
     * <p>Reads {@code Skills.General.LimitBreak.AllowPVE}. Legacy's condition was
     * {@code target instanceof Player || canApplyLimitBreakPVE()}; in singleplayer the left side is
     * never true, so this key alone decides it.
     *
     * @return whether the mechanic is enabled
     */
    public static boolean isEnabled() {
        final AdvancedConfig config = McMMOMod.getAdvancedConfig();
        // No config ⇒ nothing has loaded yet ⇒ treat as off. Unlike SkillGating's "no opinion means
        // on" default this errs to OFF, because this feature is opt-in: guessing "on" would apply
        // damage the player never asked for.
        return config != null && config.canApplyLimitBreakPVE();
    }

    /**
     * Whether {@code subSkillType} is one of the eight Limit Breaks, and therefore subject to
     * {@link #isEnabled()} on the display and plaque paths.
     *
     * <p>Matched on the enum-name suffix rather than a hand-kept list of eight constants, so a ninth
     * combat skill's Limit Break is covered the day it is declared. A hand-kept table would be one
     * more converse guard to remember — this port has been bitten by that repeatedly.
     *
     * @param subSkillType the sub-skill to test; {@code null} is not a Limit Break
     * @return whether this is a Limit Break sub-skill
     */
    public static boolean isLimitBreak(@Nullable SubSkillType subSkillType) {
        return subSkillType != null && subSkillType.name().endsWith("_LIMIT_BREAK");
    }

    /**
     * Whether this player's Limit Break for the given weapon may contribute damage right now.
     *
     * <p>{@link RankUtils#hasUnlockedSubskill} already folds in {@code SkillGating.isSubSkillEnabled},
     * so legacy's separate {@code Permissions.isSubSkillEnabled} call is redundant here — the
     * per-sub-skill kill switch is a chokepoint, not something each caller re-checks.
     *
     * @param mmoPlayer the attacking player's profile; {@code null} contributes nothing
     * @param limitBreak the weapon's {@code *_LIMIT_BREAK} sub-skill
     * @return whether the bonus applies
     */
    public static boolean canUse(@Nullable McMMOPlayer mmoPlayer, @NotNull SubSkillType limitBreak) {
        if (mmoPlayer == null) {
            return false;
        }
        // The PVE gate is the whole gate in singleplayer: legacy's `target instanceof Player ||
        // canApplyLimitBreakPVE()` can only ever be satisfied by its right-hand side here.
        if (!isEnabled()) {
            return false;
        }
        return RankUtils.hasUnlockedSubskill(mmoPlayer, limitBreak);
    }

    /**
     * The raw damage Limit Break adds, before any attack-strength scaling the call site applies.
     *
     * <p>Legacy returns {@code (int) rawDamageBoost} after the armour-quality mutation; with the
     * sentinel quality that reduces to the rank itself. A locked or disabled sub-skill is rank 0 and
     * contributes nothing, so this is safe to call unconditionally.
     *
     * @param mmoPlayer the attacking player's profile
     * @param limitBreak the weapon's {@code *_LIMIT_BREAK} sub-skill
     * @return the flat bonus damage, or {@code 0} when the gate is shut
     */
    public static int bonusDamage(@Nullable McMMOPlayer mmoPlayer,
            @NotNull SubSkillType limitBreak) {
        if (!canUse(mmoPlayer, limitBreak)) {
            return 0;
        }
        return RankUtils.getRank(mmoPlayer, limitBreak);
    }
}
