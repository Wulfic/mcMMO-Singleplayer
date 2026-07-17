package com.gmail.nossr50.skills.archery;

import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.skills.RankUtils;
import java.util.UUID;

/**
 * Static helpers backing the Archery skill. Port note (Phase 10.3): only the Skill Shot damage math
 * survives here — it is pure config + rank arithmetic and therefore provable without a server.
 *
 * <p>Since then the <b>Arrow Retrieval tracker</b> has landed here too (see {@link
 * #incrementTrackerValue}/{@link #arrowRetrievalCheck}). Legacy kept it as a
 * {@code Map<UUID, TrackedEntity>} whose values were <em>scheduled runnables</em>: each
 * {@code TrackedEntity} held a live Bukkit {@code LivingEntity} and re-ran every 12000 ticks purely to
 * notice the entity had become invalid and evict itself. That whole class collapses to an {@code int}
 * on the shared {@link MetadataStore} side-table, which is already keyed by entity {@link UUID} — the
 * same substitution Rupture made for legacy's {@code RuptureTaskMeta}. Keeping the count MC-free (a
 * bare UUID rather than a platform entity) is what lets the increment/consume cycle be unit-tested
 * without the Knot harness.
 *
 * <p>Known deviation from legacy's eviction runnable: a tracked mob that despawns without dying keeps
 * its (few bytes of) count until {@link MetadataStore#clearAll()} runs at server stop, whereas legacy
 * evicted it within 12000 ticks. The counterpart is a small behavioural <em>improvement</em>: legacy
 * dropped the count when the entity merely unloaded with its chunk, so a player who shot a mob, walked
 * away and came back lost the arrows they had earned; here the count survives until the mob dies.
 *
 * <p>Config statics were made live reads: the legacy class cached
 * {@code skillShotMaxBonusDamage}/{@code DISTANCE_XP_MULTIPLIER} in {@code static final} fields at
 * class-load, which is fragile in the port where the config is installed into the {@link McMMOMod}
 * service locator after the fact. Values are now pulled on demand.
 *
 * <p>Still unported: {@code DISTANCE_XP_MULTIPLIER} and the fired-location distance bonus. It is a
 * <em>per-hit</em> XP multiplier; the port's combat XP is now paid per hit (see
 * {@link com.gmail.nossr50.util.skills.CombatUtils#processCombatXP}), so it is no longer blocked on
 * an XP-model decision — it just needs the fired-from location stamped at launch (CONVERSION_TODO §C).
 */
public final class Archery {

    /**
     * Marks an arrow whose Arrow Retrieval roll succeeded at launch (legacy
     * {@code MetadataConstants.METADATA_KEY_TRACKED_ARROW}). Keyed on the arrow's UUID.
     */
    public static final String TRACKED_ARROW_KEY = "mcmmo:tracked_arrow";

    /**
     * Running count of tracked arrows that have struck an entity, keyed on that entity's UUID
     * (legacy's {@code Archery.trackedEntities} map + {@code TrackedEntity#arrowCount}).
     */
    public static final String ARROW_COUNT_KEY = "mcmmo:tracked_arrow_count";

    private Archery() {
    }

    /**
     * Record one more retrievable arrow stuck in {@code entityId} (legacy
     * {@code incrementTrackerValue}).
     *
     * @param entityId the struck entity
     */
    static void incrementTrackerValue(UUID entityId) {
        final Integer current = MetadataStore.get(entityId, ARROW_COUNT_KEY, Integer.class);
        MetadataStore.set(entityId, ARROW_COUNT_KEY, current == null ? 1 : current + 1);
    }

    /**
     * Consume the tracked-arrow count for a dying entity (legacy {@code arrowRetrievalCheck}, whose
     * spawn half lives in {@code ProjectileListener} — the drop needs a world and an item stack, this
     * does not).
     *
     * <p>Consuming rather than peeking is deliberate: legacy used {@code Map#remove}, so the arrows
     * are handed out exactly once even if the entity's death is processed twice.
     *
     * @param entityId the entity that died
     * @return how many arrows to drop; {@code 0} when nothing was tracked
     */
    public static int arrowRetrievalCheck(UUID entityId) {
        final Integer count = MetadataStore.get(entityId, ARROW_COUNT_KEY, Integer.class);
        if (count == null) {
            return 0;
        }
        MetadataStore.remove(entityId, ARROW_COUNT_KEY);
        return count;
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
