package com.gmail.nossr50.skills.axes;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Axes skill manager (Phase 10.3 port). Only the Axe Mastery / Impact-durability damage math and the
 * activation/unlock gates survive; the combat-effect bodies are dropped until the combat/entity +
 * equipment adapters land.
 *
 * <p>Dropped until the combat phase (all take a raw {@code LivingEntity} target):
 * <ul>
 *   <li>{@code criticalHit} — PVP/PVE modifier split + defender notifications;</li>
 *   <li>{@code impactCheck} / {@code greaterImpact} — mutate armor durability, set entity velocity,
 *       play particles;</li>
 *   <li>the {@code canCriticalHit} / {@code canImpact} / {@code canGreaterImpact} gates — they
 *       inspect the target's armor ({@code Axes.hasArmor}).</li>
 * </ul>
 *
 * <p>Skull Splitter (the super ability's AoE) is live — see {@link #canUseSkullSplitter} /
 * {@link #skullSplitterDamage}.
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
     * Skull Splitter: whether an active Skull Splitter should fire its AoE on this hit. Ports legacy
     * {@code AxesManager#canUseSkullSplitter}.
     *
     * @param target the entity that was hit
     * @return {@code true} when the ability is active, unlocked, permitted, and the target is a live
     * entity
     */
    public boolean canUseSkullSplitter(@NotNull PlatformLivingEntity target) {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.AXES_SKULL_SPLITTER)) {
            return false;
        }

        return target.isValid() && mmoPlayer.getAbilityMode(SuperAbilityType.SKULL_SPLITTER)
                && Permissions.skullSplitter(getPlayer());
    }

    /**
     * Skull Splitter: the damage each nearby entity takes when the super ability's AoE fires. Ports
     * the arithmetic half of legacy {@code AxesManager#skullSplitterCheck}, which passed
     * {@code (damage / Axes.skullSplitterModifier) * attackStrength} to
     * {@code CombatUtils#applyAbilityAoE}; the MC-typed half — finding the nearby entities and
     * dealing the damage — lives in
     * {@link com.gmail.nossr50.util.skills.CombatUtils#applyAbilityAoE}.
     *
     * <p>Legacy scales this by the attack-cooldown charge but does <em>not</em> scale the Swords
     * (Serrated Strikes) equivalent. Deliberate asymmetry, preserved.
     *
     * @param damage the damage the triggering hit dealt to the primary target
     * @return the per-entity AoE damage (before {@code applyAbilityAoE}'s floor of 1)
     */
    public double skullSplitterDamage(double damage) {
        return (damage / McMMOMod.getAdvancedConfig().getSkullSplitterModifier())
                * mmoPlayer.getAttackStrength();
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
