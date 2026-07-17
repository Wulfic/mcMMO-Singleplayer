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
 * <p>The <b>fired-from distance bonus</b> now lives here too (see {@link #markFiredFrom} /
 * {@link #distanceXpBonusMultiplier}), unblocked by the move to per-hit combat XP (see
 * {@link com.gmail.nossr50.util.skills.CombatUtils#processCombatXP}) — it is a per-hit XP multiplier
 * and had nothing to multiply while the port paid per kill. Legacy stamped a Bukkit {@code Location}
 * on the arrow's metadata; the MC-free equivalent is {@link FiredFrom}, which carries exactly the two
 * things the multiplier asks of that Location — its world, and its coordinates. Keeping it MC-free is
 * what lets the whole stamp→measure cycle be unit-tested outside the Knot harness, and is why legacy's
 * {@code ArcheryManager.distanceXpBonusMultiplier} (a {@code static} needing no player) lands on this
 * class rather than the manager.
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

    /**
     * Where an arrow was loosed from, keyed on the arrow's UUID (legacy
     * {@code MetadataConstants.METADATA_KEY_ARROW_DISTANCE}, which held a Bukkit {@code Location}).
     */
    public static final String FIRED_FROM_KEY = "mcmmo:arrow_distance";

    /** Legacy's {@code Math.min(distance, 50)}: distance past this earns no further XP bonus. */
    private static final double MAX_XP_BONUS_DISTANCE = 50.0D;

    /**
     * An arrow's launch point: the MC-free stand-in for the Bukkit {@code Location} legacy stamped on
     * the arrow. Only the world and the coordinates matter — {@link #distanceXpBonusMultiplier} asks
     * the Location for nothing else.
     *
     * @param worldKey the world's registry key, stringified (legacy compared {@code Location#getWorld}
     *                 by identity; comparing keys is the same question without the MC type)
     */
    public record FiredFrom(String worldKey, double x, double y, double z) {
    }

    private Archery() {
    }

    /**
     * Record where an arrow was fired from, so a hit can pay distance-scaled XP (legacy's
     * {@code METADATA_KEY_ARROW_DISTANCE} stamp in {@code EntityListener#onProjectileLaunch}).
     *
     * @param arrowId the arrow
     * @param origin  the arrow's position at launch
     */
    public static void markFiredFrom(UUID arrowId, FiredFrom origin) {
        MetadataStore.set(arrowId, FIRED_FROM_KEY, origin);
    }

    /**
     * The XP multiplier a shot earns for its range: {@code 1 + min(distance, 50) *
     * Experience_Values.Archery.Distance_Multiplier}. Ports legacy's {@code static
     * ArcheryManager#distanceXpBonusMultiplier}. Backs both Archery and Crossbows — legacy's
     * {@code processCrossbowsCombat} calls the very same Archery static.
     *
     * <p>Both of legacy's bail-outs to a flat {@code 1} are kept:
     * <ul>
     *   <li><b>No launch mark.</b> Legacy calls this its "hacky fix — some plugins spawn arrows and
     *       assign them to players after the ProjectileLaunchEvent fires", i.e. an arrow that never
     *       passed the launch hook. Here it also covers an arrow whose mark has aged out (see
     *       {@code ProjectileListener}'s cleanup) or one restored from a saved world.</li>
     *   <li><b>A cross-world hit</b>, where the coordinates are not comparable and the "distance" would
     *       be meaningless.</li>
     * </ul>
     *
     * @param arrowId        the arrow that struck
     * @param targetWorldKey the struck entity's world registry key, stringified
     * @return the multiplier, {@code >= 1}
     */
    public static double distanceXpBonusMultiplier(UUID arrowId, String targetWorldKey,
            double targetX, double targetY, double targetZ) {
        final FiredFrom origin = MetadataStore.get(arrowId, FIRED_FROM_KEY, FiredFrom.class);
        if (origin == null || !origin.worldKey().equals(targetWorldKey)) {
            return 1;
        }

        final double dx = targetX - origin.x();
        final double dy = targetY - origin.y();
        final double dz = targetZ - origin.z();
        final double distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
        return 1 + Math.min(distance, MAX_XP_BONUS_DISTANCE) * distanceXpMultiplier();
    }

    /**
     * {@code Experience_Values.Archery.Distance_Multiplier} (0.025 as shipped), read live. Legacy
     * cached this in a {@code static final} at class-load, which is fragile here — the config is
     * installed into the {@link McMMOMod} service locator after the fact.
     */
    private static double distanceXpMultiplier() {
        final var config = McMMOMod.getExperienceConfig();
        return config == null ? 0 : config.getArcheryDistanceMultiplier();
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
