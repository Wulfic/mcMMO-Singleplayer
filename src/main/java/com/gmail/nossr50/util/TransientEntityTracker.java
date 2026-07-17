package com.gmail.nossr50.util;

import com.gmail.nossr50.datatypes.skills.subskills.taming.CallOfTheWildType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * Per-player registry of the animals a player has summoned with Call of the Wild, ported MC-free from
 * legacy {@code util.TransientEntityTracker}. It exists to enforce the per-type summon cap and to clean
 * a player's summons up when they log out or the summons expire.
 *
 * <p>Server-free by construction: it holds {@link TrackedSummon} handles, never {@code LivingEntity}, so
 * the whole count / add / remove / cleanup loop is unit-testable (the MC-typed {@code fabric.CotwSummon}
 * is the production handle). A second {@code entityId -> summon} index gives the combat-XP anti-farm
 * guard and death-eviction an O(1) lookup, the role legacy's {@code entityLookupCache} played.
 *
 * <p><b>Cap counting is by live summons, not tracked ones</b> ({@link TrackedSummon#isValid()}): a
 * summon killed in combat still sits in the map until its despawn task or the next cleanup evicts it,
 * exactly as legacy filtered {@code isValid()} in {@code getActiveSummonsForPlayerOfType}. So a stale
 * dead entry never inflates the cap, and death-eviction ({@link #evictByEntityId}) is a memory tidy, not
 * a correctness requirement.
 */
public class TransientEntityTracker {

    private final @NotNull Map<UUID, Set<TrackedSummon>> playerSummons = new ConcurrentHashMap<>();
    private final @NotNull Map<UUID, TrackedSummon> summonsByEntityId = new ConcurrentHashMap<>();

    /** Ensure a bucket exists for a player (legacy {@code initPlayer}); harmless if already present. */
    public void initPlayer(@NotNull UUID playerId) {
        playerSummons.computeIfAbsent(playerId, __ -> ConcurrentHashMap.newKeySet());
    }

    /** Register a freshly-spawned summon under its owner. */
    public void addSummon(@NotNull UUID playerId, @NotNull TrackedSummon summon) {
        playerSummons.computeIfAbsent(playerId, __ -> ConcurrentHashMap.newKeySet()).add(summon);
        summonsByEntityId.put(summon.getEntityId(), summon);
    }

    /**
     * How many <em>live</em> summons of one type the player currently has, for the cap check. Filters on
     * {@link TrackedSummon#isValid()}, so summons that died since being tracked do not count.
     */
    public int countActiveOfType(@NotNull UUID playerId, @NotNull CallOfTheWildType type) {
        final Set<TrackedSummon> summons = playerSummons.get(playerId);
        if (summons == null) {
            return 0;
        }
        int count = 0;
        for (TrackedSummon summon : summons) {
            if (summon.getCallOfTheWildType() == type && summon.isValid()) {
                count++;
            }
        }
        return count;
    }

    /** Whether the given entity is a tracked summon — the combat-XP anti-farm guard's O(1) question. */
    public boolean isTransient(@NotNull UUID entityId) {
        return summonsByEntityId.containsKey(entityId);
    }

    /**
     * Drop a summon from the tracker <em>without</em> despawning it — used when the entity has already
     * left the world (killed in combat, chunk gone). Idempotent.
     */
    public void evictByEntityId(@NotNull UUID entityId) {
        final TrackedSummon summon = summonsByEntityId.remove(entityId);
        if (summon == null) {
            return;
        }
        for (Set<TrackedSummon> summons : playerSummons.values()) {
            if (summons.remove(summon)) {
                break;
            }
        }
    }

    /** Drop a summon the caller has just despawned itself (its despawn task firing). Idempotent. */
    public void removeSummon(@NotNull UUID playerId, @NotNull TrackedSummon summon) {
        summonsByEntityId.remove(summon.getEntityId());
        final Set<TrackedSummon> summons = playerSummons.get(playerId);
        if (summons != null) {
            summons.remove(summon);
        }
    }

    /**
     * Despawn every summon a player owns and forget them — legacy {@code cleanupPlayer}, called on
     * logout. Iterates a copy so {@link TrackedSummon#despawn(boolean)} can remove from the live set.
     */
    public void cleanupPlayer(@NotNull UUID playerId) {
        final Set<TrackedSummon> summons = playerSummons.remove(playerId);
        if (summons == null) {
            return;
        }
        for (TrackedSummon summon : new HashSet<>(summons)) {
            summonsByEntityId.remove(summon.getEntityId());
            summon.despawn(false);
        }
    }
}
