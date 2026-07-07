package com.gmail.nossr50.skills.archery;

import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.skills.RankUtils;

/**
 * Static helpers backing the Archery skill. Port note (Phase 10.3): only the Skill Shot damage math
 * survives here — it is pure config + rank arithmetic and therefore provable without a server.
 *
 * <p>Dropped until the combat phase:
 * <ul>
 *   <li>the arrow tracker ({@code trackedEntities}/{@code TrackedEntity}/{@code arrowRetrievalCheck})
 *       — keyed on Bukkit {@code LivingEntity} UUIDs and spawns {@code ItemStack} drops;</li>
 *   <li>{@code DISTANCE_XP_MULTIPLIER} and the fired-location distance bonus — needs projectile
 *       metadata and world/location adapters.</li>
 * </ul>
 *
 * <p>Config statics were made live reads: the legacy class cached
 * {@code skillShotMaxBonusDamage}/{@code DISTANCE_XP_MULTIPLIER} in {@code static final} fields at
 * class-load, which is fragile in the port where the config is installed into the {@link McMMOMod}
 * service locator after the fact. Values are now pulled on demand.
 */
public final class Archery {

    private Archery() {
    }

    /**
     * Applies the Skill Shot damage bonus to a raw arrow damage value, capped by the configured
     * maximum bonus.
     *
     * @param player    the shooter
     * @param oldDamage the raw damage of the arrow before Skill Shot
     * @return the boosted damage, never exceeding {@code oldDamage + skillShotDamageMax}
     */
    public static double getSkillShotBonusDamage(PlatformPlayer player, double oldDamage) {
        double damageBonusPercent = getDamageBonusPercent(player);
        double newDamage = oldDamage + (oldDamage * damageBonusPercent);
        double skillShotMaxBonusDamage = McMMOMod.getAdvancedConfig().getSkillShotDamageMax();
        return Math.min(newDamage, (oldDamage + skillShotMaxBonusDamage));
    }

    /**
     * The fractional damage bonus granted by the player's current Skill Shot rank.
     *
     * @param player the shooter
     * @return the bonus as a fraction of the base damage (e.g. {@code 0.10} for +10%)
     */
    public static double getDamageBonusPercent(PlatformPlayer player) {
        return ((RankUtils.getRank(player, SubSkillType.ARCHERY_SKILL_SHOT))
                * (McMMOMod.getAdvancedConfig().getSkillShotRankDamageMultiplier()) / 100.0D);
    }
}
