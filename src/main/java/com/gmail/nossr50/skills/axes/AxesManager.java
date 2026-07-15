package com.gmail.nossr50.skills.axes;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.skills.SkillUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Axes skill manager. Every Axes sub-skill decision core is live: Axe Mastery ({@link #axeMastery}),
 * Critical Strikes ({@link #criticalHit}), the Armor Impact / Greater Impact pair
 * ({@link #impactCheck} / {@link #greaterImpact}) and the Skull Splitter super ability
 * ({@link #canUseSkullSplitter} / {@link #skullSplitterDamage}).
 *
 * <p>The target-inspecting gates reach the entity through {@link PlatformLivingEntity} — armor via
 * {@link Axes#hasArmor}, knockback via
 * {@link PlatformLivingEntity#setVelocityAlongLookDirection} — so this class stays free of
 * {@code net.minecraft} types and unit-testable against mocked adapters.
 *
 * <p>Dropped: the PvP arms of {@code criticalHit} / {@code greaterImpact} (the
 * {@code target instanceof Player} defender notifications and the {@code criticalHitPVPModifier}
 * branch) — the attacking player is the only player in singleplayer, so the target is never one, and
 * the PVE modifier always applies. Also dropped: {@code ParticleEffectUtils.playGreaterImpactEffect}
 * (no particle adapter yet — the same deferral as Dodge and Rupture's bleed particles).
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
     * scaled by the player's Armor Impact rank.
     */
    public double getImpactDurabilityDamage() {
        return McMMOMod.getAdvancedConfig().getImpactDurabilityDamageMultiplier()
                * RankUtils.getRank(getPlayer(), SubSkillType.AXES_ARMOR_IMPACT);
    }

    /**
     * Critical Strikes: whether a crit may be rolled against {@code target}. Ports legacy
     * {@code AxesManager#canCriticalHit}.
     */
    public boolean canCriticalHit(@NotNull PlatformLivingEntity target) {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.AXES_CRITICAL_STRIKES)) {
            return false;
        }

        return target.isValid() && Permissions.isSubSkillEnabled(getPlayer(),
                SubSkillType.AXES_CRITICAL_STRIKES);
    }

    /**
     * Armor Impact: whether the target's armor may be chewed up. Ports legacy
     * {@code AxesManager#canImpact} — requires the target to <em>be</em> armored, which is what makes
     * this and {@link #canGreaterImpact} mutually exclusive.
     */
    public boolean canImpact(@NotNull PlatformLivingEntity target) {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.AXES_ARMOR_IMPACT)) {
            return false;
        }

        return target.isValid() && Permissions.isSubSkillEnabled(getPlayer(),
                SubSkillType.AXES_ARMOR_IMPACT) && Axes.hasArmor(target);
    }

    /**
     * Greater Impact: whether an unarmored target may be sent flying. Ports legacy
     * {@code AxesManager#canGreaterImpact} — the inverse armor test to {@link #canImpact}.
     */
    public boolean canGreaterImpact(@NotNull PlatformLivingEntity target) {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.AXES_GREATER_IMPACT)) {
            return false;
        }

        return target.isValid() && Permissions.isSubSkillEnabled(getPlayer(),
                SubSkillType.AXES_GREATER_IMPACT) && !Axes.hasArmor(target);
    }

    /**
     * Critical Strikes: roll a crit and return the <em>bonus</em> damage it adds. Ports legacy
     * {@code AxesManager#criticalHit}, which returned {@code (damage * modifier) - damage}, i.e. the
     * delta the caller adds on top of the damage so far — not the new total.
     *
     * <p>Legacy took the target only to pick the PVP vs PVE modifier and to notify a player defender;
     * both are dead in singleplayer (see the class javadoc), so the PVE modifier always applies and
     * the parameter is gone. The roll is scaled by the attack-cooldown charge, so a spam-clicked
     * swing crits proportionally less often.
     *
     * @param damage the damage accumulated so far (base + Axe Mastery + Greater Impact)
     * @return the additional damage the crit contributes, or {@code 0} if it did not proc
     */
    public double criticalHit(double damage) {
        if (!ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.AXES_CRITICAL_STRIKES, mmoPlayer,
                mmoPlayer.getAttackStrength())) {
            return 0;
        }

        if (mmoPlayer.useChatNotifications()) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Axes.Combat.CriticalHit");
        }

        return (damage * McMMOMod.getAdvancedConfig().getCriticalStrikesPVEModifier()) - damage;
    }

    /**
     * Armor Impact: roll independently against each armor piece the target wears and damage the ones
     * that proc. Ports legacy {@code AxesManager#impactCheck}.
     *
     * <p>Deals no damage of its own — the whole effect is durability wear, so unlike its Greater
     * Impact counterpart it returns nothing.
     *
     * @param target the entity being hit
     */
    public void impactCheck(@NotNull PlatformLivingEntity target) {
        final double durabilityDamage = getImpactDurabilityDamage();

        for (PlatformItem armor : target.getArmorPieces()) {
            if (ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.AXES_ARMOR_IMPACT, mmoPlayer,
                    mmoPlayer.getAttackStrength())) {
                SkillUtils.handleArmorDurabilityChange(armor, durabilityDamage, 1);
            }
        }
    }

    /**
     * Greater Impact: roll to send an unarmored target flying along the player's look direction and
     * return the flat bonus damage it adds. Ports legacy {@code AxesManager#greaterImpact}.
     *
     * @param target the entity being hit
     * @return the configured bonus damage if it procced, otherwise {@code 0}
     */
    public double greaterImpact(@NotNull PlatformLivingEntity target) {
        if (!ProbabilityUtil.isSkillRNGSuccessful(SubSkillType.AXES_GREATER_IMPACT, mmoPlayer,
                mmoPlayer.getAttackStrength())) {
            return 0;
        }

        target.setVelocityAlongLookDirection(getPlayer(),
                McMMOMod.getAdvancedConfig().getGreaterImpactModifier());

        if (mmoPlayer.useChatNotifications()) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Axes.Combat.GI.Proc");
        }

        return McMMOMod.getAdvancedConfig().getGreaterImpactBonusDamage();
    }
}
