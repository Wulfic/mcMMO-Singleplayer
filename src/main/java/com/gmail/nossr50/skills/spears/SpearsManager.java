package com.gmail.nossr50.skills.spears;

import static com.gmail.nossr50.util.skills.RankUtils.getRank;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.Permissions;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;

/**
 * Spears skill manager. Carries the Spear Mastery bonus-damage math (composed into the melee hit
 * total by {@link com.gmail.nossr50.skills.MeleeDamageBonus}) and the Momentum on-hit Speed effect
 * (driven from {@code fabric.listeners.EntityDamageListener} once the hit is settled) — the same
 * two-part split Maces uses for Crush and Cripple.
 *
 * <p>Legacy's {@code mockSpigotMatch} / {@code swiftnessEffectType} registry-lookup plumbing is
 * dropped: it existed to resolve the Speed {@code PotionEffectType} across Bukkit versions, whereas
 * the vanilla constant {@code StatusEffects.SPEED} is fixed. The Speed application itself lives
 * behind {@link com.gmail.nossr50.platform.PlatformPlayer#applySpeed}, keeping this class MC-free
 * and unit-testable.
 *
 * <p>{@code SPEARS_SPEARS_LIMIT_BREAK} is not driven from here, but it <em>is</em> implemented — see
 * {@link com.gmail.nossr50.skills.LimitBreak}, which all eight combat skills share, applied from
 * {@code MeleeDamageBonus} rather than from any per-skill manager. It ships switched off.
 */
public class SpearsManager extends SkillManager {
    public SpearsManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.SPEARS);
    }

    public static int getMomentumTickDuration(int momentumRank) {
        return 20 * (momentumRank * 2);
    }

    public static int getMomentumStrength() {
        return 2;
    }

    /**
     * Whether Spear Mastery's flat bonus damage applies. Same gate shape as Swords Stab: the
     * permission node <em>and</em> an actual rank, since the node alone does not imply one.
     */
    public boolean canUseSpearMastery() {
        return Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SPEARS_SPEAR_MASTERY)
                && RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.SPEARS_SPEAR_MASTERY);
    }

    public double getSpearMasteryBonusDamage() {
        return McMMOMod.getAdvancedConfig().getSpearMasteryRankDamageMultiplier()
                * getRank(getPlayer(), SubSkillType.SPEARS_SPEAR_MASTERY);
    }

    /**
     * Momentum: a spear hit may grant the attacker a short Speed burst. Ports legacy
     * {@code SpearsManager#potentiallyApplyMomentum}, called from {@code processSpearsCombat} after
     * the damage is committed and before the combat XP is paid.
     *
     * <p>⚠️ The rank gate is first and is load-bearing, not cosmetic — the same rank-0 landmine
     * Cripple carries (CONVERSION_TODO §F #9). {@code getMomentumChanceToApplyOnHit(0)} evaluates
     * {@code defaultMomentumValues[-1]} eagerly as the {@code getDouble} default argument and throws
     * {@link ArrayIndexOutOfBoundsException}, so a spear-swinging player below the Momentum unlock
     * would crash on hit if this were gated on the permission node alone.
     *
     * <p>The odds are scaled by the attacker's captured attack-cooldown charge. Legacy rolled flat
     * odds on every hit ({@code potentiallyApplyMomentum()} takes no argument), which paid a
     * spam-clicker the full per-hit chance; this port already made the same call for Cripple, and the
     * two on-hit procs staying the same shape matters more than matching an unscaled roll.
     *
     * @param attackStrength the attacker's captured attack-cooldown charge, scaling the roll
     */
    public void processMomentum(float attackStrength) {
        if (!RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.SPEARS_MOMENTUM)) {
            return; // see the rank-0 landmine above.
        }
        if (!Permissions.isSubSkillEnabled(getPlayer(), SubSkillType.SPEARS_MOMENTUM)) {
            return;
        }

        final int momentumRank = RankUtils.getRank(getPlayer(), SubSkillType.SPEARS_MOMENTUM);
        final double momentumOdds =
                McMMOMod.getAdvancedConfig().getMomentumChanceToApplyOnHit(momentumRank)
                        * attackStrength;

        if (!ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.SPEARS, mmoPlayer,
                momentumOdds)) {
            return;
        }

        // Vanilla owns the "is this an upgrade?" comparison legacy's canMomentumBeApplied hand-rolled
        // — see PlatformPlayer#applySpeed. A false means an existing Speed already beat this one, so
        // nothing changed and there is nothing to announce.
        if (!getPlayer().applySpeed(getMomentumTickDuration(momentumRank), getMomentumStrength())) {
            return;
        }

        if (mmoPlayer.useChatNotifications()) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Spears.SubSkill.Momentum.Activated");
        }
    }
}
