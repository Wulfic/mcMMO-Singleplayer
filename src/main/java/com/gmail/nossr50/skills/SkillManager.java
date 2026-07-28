package com.gmail.nossr50.skills;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.platform.PlatformPlayer;

/**
 * Base class every {@code *Manager} extends. Holds the owning {@link McMMOPlayer} and the
 * {@link PrimarySkillType} the manager is for, and exposes the handful of helpers the concrete
 * skill managers reach through the base.
 *
 * <p>Port note (Phase 10.1): the Bukkit {@code org.bukkit.entity.Player} return of
 * {@link #getPlayer()} is retargeted to the {@link PlatformPlayer} adapter. The combat helper
 * {@code getXPGainReason(LivingEntity, Entity)} is dropped for now — see the breadcrumb at the
 * bottom of the class.
 */
public abstract class SkillManager {
    protected McMMOPlayer mmoPlayer;
    protected PrimarySkillType skill;

    public SkillManager(McMMOPlayer mmoPlayer, PrimarySkillType skill) {
        this.mmoPlayer = mmoPlayer;
        this.skill = skill;
    }

    public PlatformPlayer getPlayer() {
        return mmoPlayer.getPlayer();
    }

    public int getSkillLevel() {
        return mmoPlayer.getSkillLevel(skill);
    }

    /**
     * Applies XP to a player, provides SELF as an XpGainSource source
     *
     * @param xp amount of XP to apply
     * @param xpGainReason the reason for the XP gain
     * @deprecated use applyXpGain(float, XPGainReason, XPGainSource)
     */
    @Deprecated(forRemoval = true)
    public void applyXpGain(float xp, XPGainReason xpGainReason) {
        mmoPlayer.beginXpGain(skill, xp, xpGainReason, XPGainSource.SELF);
    }

    /**
     * Applies XP to a player
     *
     * @param xp amount of XP to apply
     * @param xpGainReason the reason for the XP gain
     * @param xpGainSource the source of the XP
     */
    public void applyXpGain(float xp, XPGainReason xpGainReason, XPGainSource xpGainSource) {
        mmoPlayer.beginXpGain(skill, xp, xpGainReason, xpGainSource);
    }

    /**
     * Scale a maximum bonus linearly with the player's level in this skill, capping at
     * {@code maxBonusLevel} — the ladder every level-scaled passive uses.
     *
     * <p>Lives on the base class because Agility and Stealth between them have a dozen sub-skills
     * shaped exactly like this, and a second copy of the ladder is a second place for the
     * degenerate-config guards below to be got wrong.
     *
     * @param maxBonus      the value reached at {@code maxBonusLevel} and beyond
     * @param maxBonusLevel the level at which scaling stops
     * @return the scaled value, never above {@code maxBonus}
     */
    protected double scaleToLevel(double maxBonus, int maxBonusLevel) {
        if (maxBonus <= 0) {
            return 0.0;
        }
        if (maxBonusLevel <= 0) {
            return maxBonus; // Degenerate config: treat everyone as maxed rather than dividing by 0.
        }
        final double progress = Math.min(1.0, (double) getSkillLevel() / maxBonusLevel);
        return maxBonus * progress;
    }

    // PORT Phase 10.3: getXPGainReason(LivingEntity target, Entity damager) — dropped. The legacy
    // signature took raw Bukkit entities and returned PVP when both target and damager are players,
    // PVE otherwise. It needs a platform/ entity adapter for the (non-living) damager, which lands
    // with the combat skills. Singleplayer has no other players, so once re-added it collapses to
    // XPGainReason.PVE in practice.
}
