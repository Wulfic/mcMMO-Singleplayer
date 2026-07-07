package com.gmail.nossr50.skills.crossbows;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.SkillManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;

/**
 * Crossbows skill manager (Phase 10.3 port) — the twin of {@link com.gmail.nossr50.skills.archery.Archery}.
 * Only the Powered Shot damage path and the Trick Shot bounce-count read survive; every arrow-physics
 * body is dropped until the projectile/entity + metadata adapters land.
 *
 * <p>Dropped until the combat phase:
 * <ul>
 *   <li>{@code handleRicochet} / {@code spawnReflectedArrow} — reflect a Bukkit {@code Arrow} off a
 *       hit-block normal, spawning a new arrow and copying its state + metadata;</li>
 *   <li>the whole {@code Crossbows} helper ({@code processCrossbows}/{@code processTrickShot}) that
 *       fed them off a {@code ProjectileHitEvent} — not ported.</li>
 * </ul>
 *
 * <p>Config statics use live {@link McMMOMod#getAdvancedConfig()} reads (same fragility fix as
 * {@code Archery}). Note {@code getPoweredShotDamageMax()} intentionally reads the Archery Skill Shot
 * cap in the bundled config — the legacy getter shares that key.
 */
public class CrossbowsManager extends SkillManager {
    public CrossbowsManager(McMMOPlayer mmoPlayer) {
        super(mmoPlayer, PrimarySkillType.CROSSBOWS);
    }

    /** Trick Shot's maximum ricochet bounces equals the player's rank in the sub-skill. */
    public int getTrickShotMaxBounceCount() {
        return RankUtils.getRank(mmoPlayer, SubSkillType.CROSSBOWS_TRICK_SHOT);
    }

    public double getPoweredShotBonusDamage(PlatformPlayer player, double oldDamage) {
        double damageBonusPercent = getDamageBonusPercent(player);
        double newDamage = oldDamage + (oldDamage * damageBonusPercent);
        return Math.min(newDamage,
                (oldDamage + McMMOMod.getAdvancedConfig().getPoweredShotDamageMax()));
    }

    public double getDamageBonusPercent(PlatformPlayer player) {
        return ((RankUtils.getRank(player, SubSkillType.CROSSBOWS_POWERED_SHOT))
                * (McMMOMod.getAdvancedConfig().getPoweredShotRankDamageMultiplier()) / 100.0D);
    }

    /**
     * Calculates the damage to deal after Powered Shot has been applied.
     *
     * @param oldDamage the raw damage value of this bolt before we modify it
     * @return the boosted damage if Powered Shot activates, otherwise {@code oldDamage} unchanged
     */
    public double poweredShot(double oldDamage) {
        if (ProbabilityUtil.isNonRNGSkillActivationSuccessful(SubSkillType.CROSSBOWS_POWERED_SHOT,
                mmoPlayer)) {
            return getPoweredShotBonusDamage(getPlayer(), oldDamage);
        } else {
            return oldDamage;
        }
    }
}
