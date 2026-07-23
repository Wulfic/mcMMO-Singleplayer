package com.gmail.nossr50.skills.herbalism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.skills.herbalism.MultiBlockPlantTraversal.PlantCoord;
import com.gmail.nossr50.util.MaterialMapStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the MC-free multi-block plant traversal — the three search shapes legacy
 * {@code getBrokenHerbalismBlocks} picks between (chorus flood fill, cactus column, vertical scan)
 * against a hand-built world map, with no Minecraft registry in sight.
 *
 * <p>The chorus and cactus cases are also the regression tests for the upstream bug this port fixes:
 * legacy pre-added the origin to the same set its recursive helpers used as a visited-set, so the
 * very first {@code add} failed and both traversals returned the origin alone.
 */
class MultiBlockPlantTraversalTest {

    /** A sparse world: coordinates absent from the map read back as {@code stone}. */
    private Map<String, String> world;
    private MaterialMapStore materials;

    @BeforeEach
    void setUp() {
        world = new HashMap<>();
        materials = new MaterialMapStore();
    }

    private void put(int x, int y, int z, String path) {
        world.put(x + "," + y + "," + z, path);
    }

    private List<PlantCoord> collect(int x, int y, int z) {
        final String originPath = world.getOrDefault(x + "," + y + "," + z, "stone");
        return MultiBlockPlantTraversal.collect(x, y, z, originPath, materials,
                (bx, by, bz) -> world.getOrDefault(bx + "," + by + "," + bz, "stone"));
    }

    @Test
    void verticalScanCollectsTheWholeSugarCaneColumnAboveTheBreak() {
        put(0, 64, 0, "sugar_cane");
        put(0, 65, 0, "sugar_cane");
        put(0, 66, 0, "sugar_cane");
        // Break the bottom: the two canes above it come down too.
        final List<PlantCoord> broken = collect(0, 64, 0);

        assertEquals(3, broken.size());
        assertEquals(new PlantCoord(0, 64, 0, "sugar_cane"), broken.get(0),
                "the origin must always be first — the tall-plant XP cap keys off it");
        assertEquals(65, broken.get(1).y());
        assertEquals(66, broken.get(2).y());
    }

    @Test
    void verticalScanStopsAtTheFirstNonPlantAndNeverSearchesDownwards() {
        put(0, 63, 0, "sugar_cane"); // below the break: stays standing, it still has its support
        put(0, 64, 0, "sugar_cane");
        put(0, 65, 0, "sugar_cane");
        // 0,66,0 is stone (the sparse default), so the scan ends there.
        final List<PlantCoord> broken = collect(0, 64, 0);

        assertEquals(2, broken.size());
        assertTrue(broken.stream().allMatch(coord -> coord.y() >= 64),
                "a break must not take the blocks below it down");
    }

    @Test
    void hangingPlantsScanDownwardsInstead() {
        put(0, 64, 0, "weeping_vines_plant");
        put(0, 63, 0, "weeping_vines_plant");
        put(0, 62, 0, "weeping_vines_plant");
        put(0, 65, 0, "weeping_vines_plant"); // above the break: still attached to the ceiling
        final List<PlantCoord> broken = collect(0, 64, 0);

        assertEquals(3, broken.size());
        assertTrue(broken.stream().allMatch(coord -> coord.y() <= 64),
                "weeping vines hang, so a break takes the vines *below* it");
    }

    @Test
    void twistingVinesScanUpwardsBecauseTheyGrowUpwards() {
        // PORT regression: legacy listed the misspelled "twisted_vines_plant" in the *hanging* set, so
        // twisting vines were treated as a single block. They grow upwards (Direction.UP), so a break
        // detaches the column above.
        put(0, 64, 0, "twisting_vines_plant");
        put(0, 65, 0, "twisting_vines_plant");
        put(0, 63, 0, "twisting_vines_plant");
        final List<PlantCoord> broken = collect(0, 64, 0);

        assertEquals(2, broken.size());
        assertTrue(broken.stream().allMatch(coord -> coord.y() >= 64));
    }

    @Test
    void cactusColumnIsCollectedInBothDirections() {
        // Regression for the upstream bug: this returned the origin alone before the fix.
        put(0, 64, 0, "cactus");
        put(0, 65, 0, "cactus");
        put(0, 66, 0, "cactus_flower");
        put(0, 63, 0, "cactus");
        final List<PlantCoord> broken = collect(0, 65, 0);

        assertEquals(4, broken.size(), "a broken cactus takes the whole column, flower included");
        assertEquals(new PlantCoord(0, 65, 0, "cactus"), broken.get(0));
        assertTrue(broken.contains(new PlantCoord(0, 66, 0, "cactus_flower")));
        assertTrue(broken.contains(new PlantCoord(0, 63, 0, "cactus")));
    }

    @Test
    void chorusTreeFloodFillsUpAndSidewaysButNotDown() {
        // Regression for the upstream bug: this returned the origin alone before the fix.
        put(0, 64, 0, "chorus_plant"); // the broken root
        put(0, 65, 0, "chorus_plant");
        put(1, 65, 0, "chorus_plant"); // a branch east
        put(1, 66, 0, "chorus_flower"); // its tip
        put(0, 63, 0, "chorus_plant"); // below the break — holds the tree up, not brought down
        final List<PlantCoord> broken = collect(0, 64, 0);

        assertEquals(4, broken.size());
        assertEquals(new PlantCoord(0, 64, 0, "chorus_plant"), broken.get(0));
        assertTrue(broken.contains(new PlantCoord(1, 66, 0, "chorus_flower")),
                "the flood fill must reach a flower tip two steps out");
        assertFalse(broken.contains(new PlantCoord(0, 63, 0, "chorus_plant")),
                "the chorus search never goes downwards");
    }

    @Test
    void chorusFlowerOriginUsesTheVerticalScanNotTheFloodFill() {
        // Only chorus_plant is a "branch", so breaking a lone flower falls through to the plain scan
        // (faithful to legacy's isChorusBranch gate).
        put(0, 64, 0, "chorus_flower");
        put(1, 64, 0, "chorus_plant"); // a sideways neighbour the flood fill would have found
        final List<PlantCoord> broken = collect(0, 64, 0);

        assertEquals(1, broken.size());
        assertEquals("chorus_flower", broken.get(0).path());
    }

    @Test
    void chorusFloodFillIsCappedSoAHugeTreeCannotStallTheServer() {
        // A solid 12x12x12 cube of chorus — far more than any real tree, and more than the 256 cap.
        for (int x = 0; x < 12; x++) {
            for (int y = 64; y < 76; y++) {
                for (int z = 0; z < 12; z++) {
                    put(x, y, z, "chorus_plant");
                }
            }
        }
        final List<PlantCoord> broken = collect(0, 64, 0);

        assertTrue(broken.size() <= 258,
                "the flood fill must stop near the 256 cap, got " + broken.size());
        assertTrue(broken.size() > 200, "…but should still have collected a substantial tree");
    }

    @Test
    void singleBlockPlantsAndOrdinaryBlocksAreRecognisedAsStandalone() {
        assertTrue(MultiBlockPlantTraversal.isOneBlockPlant("stone", materials));
        assertTrue(MultiBlockPlantTraversal.isOneBlockPlant("air", materials));
        assertTrue(MultiBlockPlantTraversal.isOneBlockPlant("wheat", materials));
        assertTrue(MultiBlockPlantTraversal.isOneBlockPlant("twisted_vines_plant", materials),
                "the misspelled legacy key must no longer match anything");

        assertFalse(MultiBlockPlantTraversal.isOneBlockPlant("sugar_cane", materials));
        assertFalse(MultiBlockPlantTraversal.isOneBlockPlant("cactus", materials));
        assertFalse(MultiBlockPlantTraversal.isOneBlockPlant("chorus_plant", materials));
        assertFalse(MultiBlockPlantTraversal.isOneBlockPlant("weeping_vines_plant", materials));
        assertFalse(MultiBlockPlantTraversal.isOneBlockPlant("twisting_vines_plant", materials));
    }

    @Test
    void chorusClassificationSeparatesBranchFromTip() {
        assertTrue(MultiBlockPlantTraversal.isChorusTree("chorus_plant"));
        assertTrue(MultiBlockPlantTraversal.isChorusTree("chorus_flower"));
        assertFalse(MultiBlockPlantTraversal.isChorusTree("sugar_cane"));

        assertTrue(MultiBlockPlantTraversal.isChorusBranch("chorus_plant"));
        assertFalse(MultiBlockPlantTraversal.isChorusBranch("chorus_flower"),
                "a flower tip is not an entry point into the flood fill");
    }

    @Test
    void anUnreadableCoordinateEndsTheScanRatherThanThrowing() {
        put(0, 64, 0, "sugar_cane");
        put(0, 65, 0, "sugar_cane");
        final List<PlantCoord> broken = MultiBlockPlantTraversal.collect(0, 64, 0, "sugar_cane",
                materials,
                (x, y, z) -> y == 65 ? "sugar_cane" : null); // everything else unloaded

        assertEquals(2, broken.size());
    }

    @Test
    void aTallGrassBreakCollectsItsUpperHalf() {
        // Double plants are why the capture has to happen pre-break: vanilla replaces the other half
        // with air synchronously, so by the AFTER seam a live read would find nothing.
        put(0, 64, 0, "tall_grass");
        put(0, 65, 0, "tall_grass");
        final List<PlantCoord> broken = collect(0, 64, 0);

        assertEquals(2, broken.size());
    }

    @Test
    void aSingleBlockOriginReturnsOnlyItself() {
        put(0, 64, 0, "poppy");
        final List<PlantCoord> broken = collect(0, 64, 0);

        assertEquals(1, broken.size());
        assertEquals("poppy", broken.get(0).path());
    }
}
