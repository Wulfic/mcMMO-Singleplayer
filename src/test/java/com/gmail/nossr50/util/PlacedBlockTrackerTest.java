package com.gmail.nossr50.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    // --- snapshot / restore (the persistence half, K9) ----------------------

    @Test
    void snapshotOfAnEmptyTrackerHasNoWorlds() {
        assertTrue(tracker.snapshot().isEmpty());
    }

    @Test
    void snapshotGroupsPositionsByWorld() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));
        tracker.setIneligible(OVERWORLD, pack(11, 64, -20));
        tracker.setIneligible(NETHER, pack(0, 70, 0));

        final Map<String, long[]> snapshot = tracker.snapshot();

        assertEquals(Set.of(OVERWORLD, NETHER), snapshot.keySet());
        assertEquals(Set.of(pack(10, 64, -20), pack(11, 64, -20)),
                Arrays.stream(snapshot.get(OVERWORLD)).boxed().collect(Collectors.toSet()));
        assertArrayEquals(new long[] { pack(0, 70, 0) }, snapshot.get(NETHER));
    }

    /**
     * A world whose last placed block was broken must not be written out: an empty entry is pure
     * file weight, and {@code restore} would drop it again on the way back in.
     */
    @Test
    void snapshotOmitsAWorldWhoseFlagsWereAllCleared() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));
        tracker.setIneligible(NETHER, pack(0, 70, 0));

        tracker.setEligible(OVERWORLD, pack(10, 64, -20));

        assertEquals(Set.of(NETHER), tracker.snapshot().keySet());
    }

    /** The snapshot is a copy — mutating the tracker afterwards must not rewrite what was captured. */
    @Test
    void snapshotIsDetachedFromLaterMarks() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));
        final Map<String, long[]> snapshot = tracker.snapshot();

        tracker.setIneligible(OVERWORLD, pack(99, 64, -20));

        assertArrayEquals(new long[] { pack(10, 64, -20) }, snapshot.get(OVERWORLD));
    }

    @Test
    void restoreRoundTripsASnapshot() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));
        tracker.setIneligible(NETHER, pack(0, 70, 0));
        final Map<String, long[]> snapshot = tracker.snapshot();

        final PlacedBlockTracker reloaded = new PlacedBlockTracker();
        reloaded.restore(snapshot);

        assertTrue(reloaded.isIneligible(OVERWORLD, pack(10, 64, -20)));
        assertTrue(reloaded.isIneligible(NETHER, pack(0, 70, 0)));
        assertTrue(reloaded.isEligible(OVERWORLD, pack(11, 64, -20)));
        assertEquals(2, reloaded.size());
    }

    /**
     * Restore replaces rather than merges, so a partial or failed load can never leave the tracker
     * holding flags from the world session before it.
     */
    @Test
    void restoreDiscardsWhateverWasTrackedBefore() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));

        tracker.restore(Map.of(NETHER, new long[] { pack(0, 70, 0) }));

        assertTrue(tracker.isEligible(OVERWORLD, pack(10, 64, -20)));
        assertTrue(tracker.isIneligible(NETHER, pack(0, 70, 0)));
        assertEquals(1, tracker.size());
    }

    @Test
    void restoringAnEmptyMapClearsTheTracker() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));

        tracker.restore(Map.of());

        assertEquals(0, tracker.size());
    }

    /** A restored world stays writable — the loaded set must not be an immutable view. */
    @Test
    void aRestoredWorldAcceptsFurtherMarks() {
        tracker.restore(Map.of(OVERWORLD, new long[] { pack(10, 64, -20) }));

        tracker.setIneligible(OVERWORLD, pack(11, 64, -20));
        tracker.setEligible(OVERWORLD, pack(10, 64, -20));

        assertTrue(tracker.isIneligible(OVERWORLD, pack(11, 64, -20)));
        assertTrue(tracker.isEligible(OVERWORLD, pack(10, 64, -20)));
    }
}
