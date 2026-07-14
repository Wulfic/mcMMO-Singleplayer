package com.gmail.nossr50.skills.woodcutting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * The pure, MC-free heart of Tree Feller: given a starting block coordinate and a way to classify
 * any coordinate as a log, a non-wood tree part (leaf/wart/mushroom cap), or neither, it decides the
 * ordered set of blocks the ability should fell. This is a faithful port of legacy
 * {@code WoodcuttingManager#processTree} / {@code processTreeFellerTargetBlock}, lifted out of the
 * Bukkit {@code Block}/{@code BlockFace} world into integer space so the (fiddly, load-bearing)
 * trunk-vs-branch search + threshold cutoff can be unit-tested without a live world. The MC-typed
 * half — reading block states, breaking blocks, spawning drops/XP — lives in
 * {@link TreeFellerProcessor}, which supplies the {@link BlockClassifier} over a real world.
 *
 * <p><b>Deliberate deviation from legacy:</b> the legacy {@code processTreeFellerTargetBlock} also
 * skips blocks the {@code UserBlockTracker} marks ineligible (player-placed, to stop XP farming).
 * That tracker isn't ported yet (see {@code BlockUtils} class doc), so every classified block is
 * eligible here — consistent with the rest of the port, which has no placed-block tracking.
 */
public final class TreeFellerTraversal {

    /** How a coordinate participates in a tree for Tree Feller purposes. */
    public enum TreeBlockType {
        /** A log — grants Woodcutting XP and continues the recursive search from itself. */
        LOG,
        /** A non-wood tree part (leaves, mushroom cap, nether wart block) — felled but not a search seed. */
        LEAF,
        /** Anything else — not part of the tree; never felled. */
        OTHER
    }

    /** Classifies the block at an integer world coordinate. */
    @FunctionalInterface
    public interface BlockClassifier {
        @NotNull TreeBlockType classify(int x, int y, int z);
    }

    /** A block Tree Feller decided to fell, in discovery order. */
    public record FelledBlock(int x, int y, int z, @NotNull TreeBlockType type) {}

    /**
     * The flat (dx, dz) cylinder searched around a log at y=0, radius ~2: the (0,0) centre and the
     * four (±2, ±2) corners are omitted. Copied verbatim from legacy {@code directions}.
     */
    private static final int[][] DIRECTIONS = {
            {-2, -1}, {-2, 0}, {-2, 1},
            {-1, -2}, {-1, -1}, {-1, 0}, {-1, 1}, {-1, 2},
            {0, -2}, {0, -1}, {0, 1}, {0, 2},
            {1, -2}, {1, -1}, {1, 0}, {1, 1}, {1, 2},
            {2, -1}, {2, 0}, {2, 1},
    };

    private TreeFellerTraversal() {
    }

    /**
     * Collects the blocks Tree Feller should fell, starting the search from {@code (startX, startY,
     * startZ)} — the block the player broke to trigger the ability. The starting block itself is
     * never included (legacy leaves it to the normal break that triggered the ability); the search
     * runs over its neighbours.
     *
     * @param startX the broken block's X
     * @param startY the broken block's Y
     * @param startZ the broken block's Z
     * @param threshold the Tree Feller propagation cap ({@code GeneralConfig.getTreeFellerThreshold})
     * @param classifier classifies any coordinate as LOG / LEAF / OTHER
     * @return the felled blocks in discovery order (empty if nothing around the start is a tree)
     */
    public static @NotNull List<FelledBlock> collect(int startX, int startY, int startZ,
            int threshold, @NotNull BlockClassifier classifier) {
        final Traversal traversal = new Traversal(threshold, classifier);
        traversal.processTree(startX, startY, startZ);
        return new ArrayList<>(traversal.felled.values());
    }

    /** A packed coordinate used as the dedup key + recursion seed (MC-free stand-in for a BlockPos). */
    private record Coord(int x, int y, int z) {}

    /**
     * Holds the mutable per-run state (the felled set and the threshold flag) that legacy carried on
     * the manager instance. One instance per {@link #collect} call, so it's inherently thread-safe.
     */
    private static final class Traversal {
        private final int threshold;
        private final BlockClassifier classifier;
        // LinkedHashMap: dedups a coordinate reached by multiple neighbours (legacy's HashSet) while
        // preserving discovery order so the caller's drop loop is deterministic.
        private final Map<Coord, FelledBlock> felled = new LinkedHashMap<>();
        private boolean reachedThreshold = false;

        private Traversal(int threshold, BlockClassifier classifier) {
            this.threshold = threshold;
            this.classifier = classifier;
        }

        /**
         * Legacy {@code processTree}: search the neighbourhood of a log, then recurse into every log
         * found. If the block directly above is a log we're inside a TRUNK — search only the flat
         * cylinder at this level; otherwise we're at a BRANCH/TOP — search the block below and a full
         * cube one step up and down. The {@code reachedThreshold} flag short-circuits every loop.
         */
        private void processTree(int x, int y, int z) {
            final List<Coord> futureCenters = new ArrayList<>();

            if (processTarget(x, y + 1, z, futureCenters)) {
                // TRUNK: another log sits above, so only sweep the flat cylinder at this height.
                for (int[] dir : DIRECTIONS) {
                    processTarget(x + dir[0], y, z + dir[1], futureCenters);
                    if (reachedThreshold) {
                        return;
                    }
                }
            } else {
                // BRANCH / TOP: no log above — also cover the block below and extend the cylinder a
                // block up and down (catches leaf balls and the top log of the trunk).
                processTarget(x, y - 1, z, futureCenters);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int[] dir : DIRECTIONS) {
                        processTarget(x + dir[0], y + dy, z + dir[1], futureCenters);
                        if (reachedThreshold) {
                            return;
                        }
                    }
                }
            }

            for (Coord futureCenter : futureCenters) {
                if (reachedThreshold) {
                    return;
                }
                processTree(futureCenter.x(), futureCenter.y(), futureCenter.z());
            }
        }

        /**
         * Legacy {@code processTreeFellerTargetBlock}: add the block at a coordinate to the felled set
         * if it's part of a tree, and report whether it was a log (so the caller keeps it as a future
         * search centre). Exceeding the threshold trips the cutoff flag but — matching legacy — does
         * not stop this one block from being added.
         *
         * @return {@code true} iff the coordinate was a not-yet-seen log
         */
        private boolean processTarget(int x, int y, int z, @NotNull List<Coord> futureCenters) {
            final Coord coord = new Coord(x, y, z);
            if (felled.containsKey(coord)) {
                return false;
            }

            // Without this, Tree Feller would propagate through leaves until the cap is hit.
            if (felled.size() > threshold) {
                reachedThreshold = true;
            }

            final TreeBlockType type = classifier.classify(x, y, z);
            switch (type) {
                case LOG -> {
                    felled.put(coord, new FelledBlock(x, y, z, TreeBlockType.LOG));
                    futureCenters.add(coord);
                    return true;
                }
                case LEAF -> {
                    felled.put(coord, new FelledBlock(x, y, z, TreeBlockType.LEAF));
                    return false;
                }
                default -> {
                    return false;
                }
            }
        }
    }
}
