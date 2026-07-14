package com.gmail.nossr50.skills.woodcutting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.skills.woodcutting.TreeFellerTraversal.BlockClassifier;
import com.gmail.nossr50.skills.woodcutting.TreeFellerTraversal.FelledBlock;
import com.gmail.nossr50.skills.woodcutting.TreeFellerTraversal.TreeBlockType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exercises the MC-free Tree Feller search in pure integer space (no live world needed). Trees are
 * described as a coordinate→type map; anything unmapped classifies as {@link TreeBlockType#OTHER}.
 * These lock in the trunk-vs-branch behaviour, the threshold cutoff, dedup, and the rule that the
 * triggering (starting) block is never itself in the fell set.
 */
class TreeFellerTraversalTest {

    /** A mutable tree definition that doubles as the classifier. */
    private static final class MockTree implements BlockClassifier {
        private final Map<Long, TreeBlockType> blocks = new HashMap<>();

        MockTree put(int x, int y, int z, TreeBlockType type) {
            blocks.put(key(x, y, z), type);
            return this;
        }

        MockTree log(int x, int y, int z) {
            return put(x, y, z, TreeBlockType.LOG);
        }

        MockTree leaf(int x, int y, int z) {
            return put(x, y, z, TreeBlockType.LEAF);
        }

        @Override
        public TreeBlockType classify(int x, int y, int z) {
            return blocks.getOrDefault(key(x, y, z), TreeBlockType.OTHER);
        }

        private static long key(int x, int y, int z) {
            return (((long) x) & 0x3FFFFFF) << 38 | (((long) z) & 0x3FFFFFF) << 12 | (((long) y) & 0xFFF);
        }
    }

    private static long countOfType(List<FelledBlock> felled, TreeBlockType type) {
        return felled.stream().filter(b -> b.type() == type).count();
    }

    @Test
    void nothingAroundStartFellsNothing() {
        final List<FelledBlock> felled =
                TreeFellerTraversal.collect(0, 0, 0, 100, new MockTree());
        assertTrue(felled.isEmpty(), "an all-OTHER neighbourhood fells nothing");
    }

    @Test
    void startingBlockItselfIsNeverFelled() {
        // The start is a log but wholly isolated — legacy leaves the triggering block to the normal
        // break, so processTree only walks neighbours and adds none of them here.
        final MockTree tree = new MockTree().log(0, 0, 0);
        final List<FelledBlock> felled = TreeFellerTraversal.collect(0, 0, 0, 100, tree);
        assertTrue(felled.isEmpty(), "the starting block is excluded from the fell set");
    }

    @Test
    void straightTrunkFellsEveryLogAbove() {
        // Start at (0,0,0); logs stacked directly above it.
        final MockTree tree = new MockTree().log(0, 1, 0).log(0, 2, 0).log(0, 3, 0);
        final List<FelledBlock> felled = TreeFellerTraversal.collect(0, 0, 0, 100, tree);

        assertEquals(3, felled.size(), "all three logs above the start are felled");
        assertEquals(3, countOfType(felled, TreeBlockType.LOG));
        assertEquals(0, countOfType(felled, TreeBlockType.LEAF));
    }

    @Test
    void trunkWithCanopyFellsLogsAndLeaves() {
        // Two-log trunk, a leaf directly above the top log, and two leaves flanking it.
        final MockTree tree = new MockTree()
                .log(0, 1, 0).log(0, 2, 0)
                .leaf(0, 3, 0).leaf(1, 2, 0).leaf(-1, 2, 0);
        final List<FelledBlock> felled = TreeFellerTraversal.collect(0, 0, 0, 100, tree);

        assertEquals(2, countOfType(felled, TreeBlockType.LOG), "both trunk logs felled");
        assertEquals(3, countOfType(felled, TreeBlockType.LEAF), "the canopy leaves felled");
    }

    @Test
    void neverReturnsDuplicateCoordinates() {
        // A leaf ball reachable from multiple sweep positions must still appear at most once.
        final MockTree tree = new MockTree().log(0, 1, 0).log(0, 2, 0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                tree.leaf(dx, 3, dz); // a 3x3 leaf slab above the trunk, overlapping search cubes
            }
        }
        final List<FelledBlock> felled = TreeFellerTraversal.collect(0, 0, 0, 100, tree);

        final Set<Long> seen = new HashSet<>();
        for (FelledBlock block : felled) {
            assertTrue(seen.add(MockTree.key(block.x(), block.y(), block.z())),
                    "coordinate " + block + " felled more than once");
        }
    }

    @Test
    void thresholdStopsRunawayPropagation() {
        // A big solid cube of logs: without the cap this would fell all 1000 blocks.
        final MockTree tree = new MockTree();
        for (int x = -5; x <= 4; x++) {
            for (int y = 1; y <= 10; y++) {
                for (int z = -5; z <= 4; z++) {
                    tree.log(x, y, z);
                }
            }
        }
        final int threshold = 50;
        final List<FelledBlock> felled = TreeFellerTraversal.collect(0, 0, 0, threshold, tree);

        assertTrue(felled.size() > threshold, "the search reached the cap");
        assertFalse(felled.size() >= 1000, "the cap stopped it well short of the whole 1000-log cube");
    }
}
