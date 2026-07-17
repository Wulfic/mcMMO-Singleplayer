package com.gmail.nossr50.util;

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
 * sat. Held for the JVM as a singleton on {@code McMMOMod} (sibling of {@link TransientEntityTracker})
 * and {@linkplain #clear() cleared} at world close, since its contents are per-world session state.
 *
 * <p><b>Deliberate deviations from legacy</b> (each a documented gap, not an oversight):
 * <ul>
 *   <li><b>No cross-restart persistence.</b> Legacy serialised the flags to per-chunk region files
 *       ({@code McMMOSimpleRegionFile}); this holds them in memory only and drops them at world close,
 *       so a placed block re-mined <em>after</em> a restart pays out again. The in-session exploit
 *       (place ore → mine → repeat) is the one that matters and is fully closed; the cross-restart
 *       reset is a future slice.</li>
 *   <li><b>Only hand-placed blocks are tracked.</b> Because the sole writer is the {@code BlockItem.place}
 *       seam, world-gen / grown / fallen blocks are never marked, so unlike legacy this needs no
 *       "reset to natural" hooks to walk back over-marking — at the cost of not following a placed
 *       block a piston pushes to a new position (a rare edge, breadcrumbed).</li>
 *   <li><b>Memory is bounded by breaking, not chunk-unloading.</b> The break / blast / fell paths
 *       {@link #setEligible clear} a position once its block is removed, so only still-standing placed
 *       blocks are retained; legacy additionally evicted on chunk unload.</li>
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

    /** Forget every tracked position — called at world close (server stop). */
    public void clear() {
        ineligibleByWorld.clear();
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
