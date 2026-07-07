package com.gmail.nossr50.skills.axes;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;

/**
 * Axes skill manager (Phase 10.3 port). Only the Axe Mastery / Impact-durability damage math and the
 * activation/unlock gates survive; the combat-effect bodies are dropped until the combat/entity +
 * equipment adapters land.
 *
 * <p>Dropped until the combat phase (all take a raw {@code LivingEntity} target):
 * <ul>
 *   <li>{@code criticalHit} — PVP/PVE modifier split + defender notifications;</li>
 *   <li>{@code impactCheck} / {@code greaterImpact} / {@code skullSplitterCheck} — mutate armor
 *       durability, set entity velocity, play particles, or deal AoE via {@code CombatUtils};</li>
 *   <li>the {@code canCriticalHit} / {@code canImpact} / {@code canGreaterImpact} /
 *       {@code canUseSkullSplitter} gates — they inspect the target entity ({@code Axes.hasArmor}).</li>
 * </ul>
 */
public class AxesManager extends SkillManager {
    public AxesManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.AXES);
    }

    public boolean canActivateAbility() {
        return mmoPlayer.getToolPreparationMode(ToolType.AXE) && Permissions.skullSplitter(
                getPlayer());
    }

    public boolean canUseAxeMastery() {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.AXES_AXE_MASTERY)) {
            return false;
        }

        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.AXES_AXE_MASTERY);
    }

    /**
     * Handle the effects of the Axe Mastery ability.
     *
     * @return the Axe Mastery bonus damage if the RNG activation succeeds, otherwise {@code 0}
     */
    public double axeMastery() {
        if (ProbabilityUtil.isNonRNGSkillActivationSuccessful(SubSkillType.AXES_AXE_MASTERY,
                mmoPlayer)) {
            return Axes.getAxeMasteryBonusDamage(getPlayer());
        }

        return 0;
    }

    /**
     * The armor-durability damage Impact applies per successful proc: the configured multiplier
     * scaled by the player's Armor Impact rank. Pure math; the {@code impactCheck} body that walks
     * the target's armor slots and applies this is deferred to the combat phase.
     */
    public double getImpactDurabilityDamage() {
        return McMMOMod.getAdvancedConfig().getImpactDurabilityDamageMultiplier()
                * RankUtils.getRank(getPlayer(), SubSkillType.AXES_ARMOR_IMPACT);
    }
}
