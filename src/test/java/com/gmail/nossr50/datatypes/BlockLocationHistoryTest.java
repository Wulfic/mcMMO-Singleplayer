package com.gmail.nossr50.datatypes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Covers the bounded MRU fall-location history that backs Acrobatics' anti-farm check.
 */
class BlockLocationHistoryTest {

    @Test
    void remembersAddedKeys() {
        final BlockLocationHistory history = new BlockLocationHistory(5);
        history.add(42L);

        assertTrue(history.contains(42L));
        assertFalse(history.contains(43L));
    }

    @Test
    void evictsLeastRecentlyAddedWhenFull() {
        final BlockLocationHistory history = new BlockLocationHistory(3);
        history.add(1L);
        history.add(2L);
        history.add(3L);
        history.add(4L); // pushes out the oldest (1L)

        assertFalse(history.contains(1L), "oldest key evicted once over capacity");
        assertTrue(history.contains(2L));
        assertTrue(history.contains(3L));
        assertTrue(history.contains(4L));
    }

    @Test
    void duplicateKeyStaysUntilBothCopiesEvicted() {
        final BlockLocationHistory history = new BlockLocationHistory(2);
        history.add(7L);
        history.add(7L); // same block twice → count 2
        history.add(8L); // window is size 2: evicts one 7L, leaving one 7L and 8L

        assertTrue(history.contains(7L), "still present — only one of the two copies was evicted");
        assertTrue(history.contains(8L));

        history.add(9L); // evicts the second 7L
        assertFalse(history.contains(7L), "both copies now gone");
        assertTrue(history.contains(8L));
        assertTrue(history.contains(9L));
    }
}
