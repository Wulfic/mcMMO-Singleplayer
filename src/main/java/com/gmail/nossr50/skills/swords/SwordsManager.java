package com.gmail.nossr50.skills.swords;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.runnables.skills.RuptureTask;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Swords skill manager (Phase 10.3 port; Rupture added in §C). The Stab damage math, the
 * activation/unlock gates, and the Rupture bleed are live.
 *
 * <p>Still dropped, pending their adapters:
 * <ul>
 *   <li>{@code counterAttackChecks} / {@code canUseCounterAttack} — deals reflected damage through
 *       {@code CombatUtils} against a raw {@code LivingEntity}.</li>
 * </ul>
 * The static {@code Swords.counterAttackModifier} went with that body and was not ported;
 * {@code Swords.serratedStrikesModifier} became the live config read in
 * {@link #serratedStrikesDamage(double)} (the service-locator config is installed after class load,
 * so a cached static would be fragile — same call the ported {@code Axes} makes).
 */
public class SwordsManager extends SkillManager {
    public SwordsManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.SWORDS);
    }

    public boolean canActivateAbility() {
        return mmoPlayer.getToolPreparationMode(ToolType.SWORD) && Permissions.serratedStrikes(
                getPlayer());
    }

    public boolean canUseStab() {
        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SWORDS_STAB)
                && RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.SWORDS_STAB);
    }

    public boolean canUseRupture() {
        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SWORDS_RUPTURE)
                && RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.SWORDS_RUPTURE);
    }

    public boolean canUseSerratedStrike() {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.SWORDS_SERRATED_STRIKES)) {
            return false;
        }

        return mmoPlayer.getAbilityMode(SuperAbilityType.SERRATED_STRIKES);
    }

    /**
     * Rupture: a sword hit may start (or refresh) a bleed damage-over-time on the target. Ports
     * legacy {@code SwordsManager#processRupture}.
     *
     * <p>An already-bleeding target refreshes rather than stacking: only one {@link RuptureTask}
     * runs per target, parked on {@link MetadataStore} under {@link RuptureTask#RUPTURE_KEY}.
     *
     * <p>Legacy's PvP arms are dropped with the rest of PvP: a defending {@code Player} could block
     * to refuse the bleed and got a "you are bleeding" notification, and the tick damage/duration
     * were read from the {@code Against_Players} config branch. The only player in this port is the
     * attacker, and a sword swing cannot target its own wielder, so the target is never a player and
     * those branches would be dead code — hence the hardcoded {@code false} below.
     *
     * @param target the entity that was hit
     * @param attackStrengthScale committed attack-cooldown charge of the hit (0.0–1.0); scales the
     * odds so a spam-clicked swing is less likely to apply a bleed
     */
    public void processRupture(@NotNull PlatformLivingEntity target, double attackStrengthScale) {
        if (!canUseRupture()) {
            return;
        }

        final RuptureTask existingRupture = MetadataStore.get(target.getUniqueId(),
                RuptureTask.RUPTURE_KEY, RuptureTask.class);
        if (existingRupture != null) {
            existingRupture.refreshRupture();
            return; // Don't apply a second bleed.
        }

        final AdvancedConfig advancedConfig = McMMOMod.getAdvancedConfig();
        final double ruptureOdds =
                advancedConfig.getRuptureChanceToApplyOnHit(getRuptureRank()) * attackStrengthScale;
        if (!ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.SWORDS, mmoPlayer,
                ruptureOdds)) {
            return;
        }

        final RuptureTask ruptureTask = new RuptureTask(target,
                advancedConfig.getRuptureTickDamage(false, getRuptureRank()),
                advancedConfig.getRuptureDurationSeconds(false) * 20);

        // Mark before scheduling: the marker is what stops the next hit stacking a second bleed.
        MetadataStore.set(target.getUniqueId(), RuptureTask.RUPTURE_KEY, ruptureTask);
        McMMOMod.getScheduler().runTimer(ruptureTask, 1, 1);
    }

    private int getRuptureRank() {
        return RankUtils.getRank(getPlayer(), SubSkillType.SWORDS_RUPTURE);
    }

    /**
     * Serrated Strikes: the damage each nearby entity takes when the super ability's AoE fires.
     * Ports the arithmetic half of legacy {@code SwordsManager#serratedStrikes}, which passed
     * {@code damage / Swords.serratedStrikesModifier} to {@code CombatUtils#applyAbilityAoE}; the
     * MC-typed half — finding the nearby entities and dealing the damage — lives in
     * {@link com.gmail.nossr50.util.skills.CombatUtils#applyAbilityAoE}.
     *
     * <p>Unlike Skull Splitter, this is <em>not</em> scaled by attack strength: legacy scales only
     * the Axes one. Deliberate asymmetry, preserved.
     *
     * @param damage the damage the triggering hit dealt to the primary target
     * @return the per-entity AoE damage (before {@code applyAbilityAoE}'s floor of 1)
     */
    public double serratedStrikesDamage(double damage) {
        return damage / McMMOMod.getAdvancedConfig().getSerratedStrikesModifier();
    }

    public double getStabDamage() {
        int rank = RankUtils.getRank(getPlayer(), SubSkillType.SWORDS_STAB);

        if (rank > 0) {
            double baseDamage = McMMOMod.getAdvancedConfig().getStabBaseDamage();
            double rankMultiplier = McMMOMod.getAdvancedConfig().getStabPerRankMultiplier();
            return (baseDamage + (rank * rankMultiplier));
        }

        return 0;
    }
}
