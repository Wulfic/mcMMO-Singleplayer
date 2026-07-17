package com.gmail.nossr50.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the MC-free {@link PlacedBlockTracker}: default eligibility, the ineligible/eligible
 * round trip, per-position and per-world isolation, idempotency, and {@code clear}. Positions are
 * packed the way {@code BlockPos#asLong()} packs them (the tracker treats the {@code long} as opaque),
 * so the isolation cases read as "a different block" / "the same block in another dimension".
 */
class PlacedBlockTrackerTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    private PlacedBlockTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new PlacedBlockTracker();
    }

    /** Replicates {@code BlockPos#asLong()}'s bit layout so a test position reads as (x, y, z). */
    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    @Test
    void anUntrackedBlockIsEligibleByDefault() {
        final long pos = pack(10, 64, -20);
        assertTrue(tracker.isEligible(OVERWORLD, pos));
        assertFalse(tracker.isIneligible(OVERWORLD, pos));
        assertEquals(0, tracker.size());
    }

    @Test
    void markingABlockMakesItIneligible() {
        final long pos = pack(10, 64, -20);
        tracker.setIneligible(OVERWORLD, pos);

        assertTrue(tracker.isIneligible(OVERWORLD, pos));
        assertFalse(tracker.isEligible(OVERWORLD, pos));
        assertEquals(1, tracker.size());
    }

    @Test
    void clearingAMarkRestoresEligibility() {
        final long pos = pack(10, 64, -20);
        tracker.setIneligible(OVERWORLD, pos);

        tracker.setEligible(OVERWORLD, pos);

        assertTrue(tracker.isEligible(OVERWORLD, pos));
        assertEquals(0, tracker.size());
    }

    @Test
    void positionsAreIsolatedWithinAWorld() {
        final long placed = pack(10, 64, -20);
        final long natural = pack(11, 64, -20); // the block next door
        tracker.setIneligible(OVERWORLD, placed);

        assertTrue(tracker.isIneligible(OVERWORLD, placed));
        assertTrue(tracker.isEligible(OVERWORLD, natural));
    }

    @Test
    void worldsAreIsolated() {
        final long pos = pack(10, 64, -20); // same coordinates, different dimension
        tracker.setIneligible(OVERWORLD, pos);

        assertTrue(tracker.isIneligible(OVERWORLD, pos));
        assertTrue(tracker.isEligible(NETHER, pos));
    }

    @Test
    void markingTheSameBlockTwiceKeepsOneEntry() {
        final long pos = pack(10, 64, -20);
        tracker.setIneligible(OVERWORLD, pos);
        tracker.setIneligible(OVERWORLD, pos);

        assertTrue(tracker.isIneligible(OVERWORLD, pos));
        assertEquals(1, tracker.size());
    }

    @Test
    void clearingAnUntrackedBlockIsANoOp() {
        tracker.setEligible(OVERWORLD, pack(10, 64, -20)); // world never seen: must not throw
        assertEquals(0, tracker.size());
    }

    @Test
    void clearForgetsEveryWorld() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));
        tracker.setIneligible(NETHER, pack(0, 70, 0));

        tracker.clear();

        assertTrue(tracker.isEligible(OVERWORLD, pack(10, 64, -20)));
        assertTrue(tracker.isEligible(NETHER, pack(0, 70, 0)));
        assertEquals(0, tracker.size());
    }

    @Test
    void sizeCountsAcrossWorlds() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));
        tracker.setIneligible(OVERWORLD, pack(11, 64, -20));
        tracker.setIneligible(NETHER, pack(0, 70, 0));

        assertEquals(3, tracker.size());
    }
}
