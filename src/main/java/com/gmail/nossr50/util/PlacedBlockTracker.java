package com.gmail.nossr50.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * Location-based registry of blocks a player placed by hand, ported (heavily simplified) from legacy
 * {@code util.blockmeta.UserBlockTracker} / {@code HashChunkManager}. A hand-placed block is
 * <em>ineligible</em> for gathering rewards (XP, bonus drops, treasure) so a player cannot farm XP by
 * re-mining blocks they placed; any block not placed by hand is <em>eligible</em> (the default).
 *
 * <p>MC-free by construction: it keys on a world's stringified registry key plus a packed block
 * position ({@code BlockPos#asLong()}, an opaque {@code long} to this class), so the whole
 * set/get/clear cycle is unit-testable outside the Knot harness. The MC-typed extraction (live world +
 * {@code BlockPos} → these two keys) lives in {@link BlockUtils#markPlaced}/
 * {@link BlockUtils#isRewardIneligible}, exactly where legacy's {@code BlockUtils#setUnnaturalBlock}
 * sat. Held for the JVM as a singleton on {@code McMMOMod} (sibling of {@link TransientEntityTracker}).
 *
 * <p>Its contents are per-world state: {@linkplain #restore loaded} from the world save at server
 * start and {@linkplain #snapshot written back} at autosave / server stop by
 * {@link com.gmail.nossr50.database.PlacedBlockStore}, then {@linkplain #clear() cleared} so the next
 * world session starts from that world's own flags.
 *
 * <p><b>Deliberate deviations from legacy</b> (each a documented gap, not an oversight):
 * <ul>
 *   <li><b>One flat file instead of per-chunk region files.</b> Legacy sharded the flags into
 *       {@code McMMOSimpleRegionFile}s loaded and evicted with their chunk; this keeps every flag
 *       resident and writes them in one document. Sound here because the set is bounded by
 *       <em>still-standing hand-placed</em> blocks (see below) rather than by world size, and a
 *       singleplayer world has exactly one player filling it.</li>
 *   <li><b>Only hand-placed blocks are tracked.</b> Because the sole writer is the {@code BlockItem.place}
 *       seam, world-gen / grown / fallen blocks are never marked, so unlike legacy this needs no
 *       "reset to natural" hooks to walk back over-marking — at the cost of not following a placed
 *       block a piston pushes to a new position (a rare edge, breadcrumbed).</li>
 *   <li><b>Memory is bounded by breaking, not chunk-unloading.</b> Every player block break
 *       {@link #setEligible clears} its position before any skill branch runs, so only still-standing
 *       placed blocks are retained; legacy additionally evicted on chunk unload. Blocks removed
 *       without a player break (creeper blast, fire, lava) leave a stale flag behind — harmless
 *       unless a natural block later occupies that exact position, and self-healing the first time
 *       one is broken there.</li>
 * </ul>
 */
public class PlacedBlockTracker {

    /** worldKey → set of packed ({@code BlockPos#asLong()}) positions known to be hand-placed. */
    private final @NotNull Map<String, Set<Long>> ineligibleByWorld = new ConcurrentHashMap<>();

    /** Mark a block position as hand-placed (ineligible for gathering rewards). Idempotent. */
    public void setIneligible(@NotNull String worldKey, long packedPos) {
        ineligibleByWorld.computeIfAbsent(worldKey, __ -> ConcurrentHashMap.newKeySet())
                .add(packedPos);
    }

    /** Clear a position back to natural (eligible) — e.g. once its block is broken. Idempotent. */
    public void setEligible(@NotNull String worldKey, long packedPos) {
        final Set<Long> positions = ineligibleByWorld.get(worldKey);
        if (positions != null) {
            positions.remove(packedPos);
        }
    }

    /** Whether the position is a tracked hand-placed block (so it should give no rewards). */
    public boolean isIneligible(@NotNull String worldKey, long packedPos) {
        final Set<Long> positions = ineligibleByWorld.get(worldKey);
        return positions != null && positions.contains(packedPos);
    }

    /** Whether the position may give gathering rewards (the default for any untracked block). */
    public boolean isEligible(@NotNull String worldKey, long packedPos) {
        return !isIneligible(worldKey, packedPos);
    }

    /**
     * Forget every tracked position — called at world close (server stop), <em>after</em> the flags
     * have been {@linkplain #snapshot() written} to the world save.
     */
    public void clear() {
        ineligibleByWorld.clear();
    }

    /**
     * A stable copy of every tracked flag, for persistence. Each world's positions are copied into a
     * {@code long[]} rather than handed out live, so a caller iterating the snapshot cannot observe a
     * half-applied concurrent mark. Worlds with no tracked positions are omitted.
     */
    public @NotNull Map<String, long[]> snapshot() {
        final Map<String, long[]> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<Long>> entry : ineligibleByWorld.entrySet()) {
            final Long[] boxed = entry.getValue().toArray(new Long[0]);
            if (boxed.length == 0) {
                continue; // a world whose last placed block was broken: nothing to write.
            }
            final long[] positions = new long[boxed.length];
            for (int i = 0; i < boxed.length; i++) {
                positions[i] = boxed[i];
            }
            copy.put(entry.getKey(), positions);
        }
        return copy;
    }

    /**
     * Replace every tracked flag with the given ones (the load half of {@link #snapshot()}). Any flag
     * held before the call is discarded, so a failed or partial load can never leave the tracker
     * holding another world's positions.
     */
    public void restore(@NotNull Map<String, long[]> byWorld) {
        ineligibleByWorld.clear();
        for (Map.Entry<String, long[]> entry : byWorld.entrySet()) {
            final Set<Long> positions = ConcurrentHashMap.<Long>newKeySet();
            for (long packedPos : entry.getValue()) {
                positions.add(packedPos);
            }
            if (!positions.isEmpty()) {
                ineligibleByWorld.put(entry.getKey(), positions);
            }
        }
    }

    /** Total tracked positions across all worlds (for tests / memory reasoning). */
    public int size() {
        int total = 0;
        for (Set<Long> positions : ineligibleByWorld.values()) {
            total += positions.size();
        }
        return total;
    }
}
