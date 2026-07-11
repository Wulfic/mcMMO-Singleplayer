package com.gmail.nossr50.datatypes;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Bounded most-recently-used history of block positions, used by Acrobatics' anti-exploit check so a
 * player can't farm Roll XP by repeatedly falling onto the same block.
 *
 * <p>Port note (Phase 12 / K2): the legacy version keyed on Bukkit {@code Location} and used a Guava
 * {@code HashMultiset}. Retargeted MC-free — a block position packs losslessly into a {@code long}
 * (vanilla {@code BlockPos#asLong()}), so the history is keyed on that primitive with a plain
 * {@link HashMap} multiset + insertion-ordered {@link ArrayDeque}. No Minecraft or Guava types, so it
 * stays unit-testable. The multiset (count per key) is retained rather than a plain set because the
 * same block can legitimately appear multiple times in the window; a single removal must not evict a
 * key that was added twice.
 */
public class BlockLocationHistory {
    private final ArrayDeque<Long> orderedKeys = new ArrayDeque<>();
    private final Map<Long, Integer> counts = new HashMap<>();
    private final int maxSize;

    public BlockLocationHistory(int maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * Adds a block-position key to the history. When the window is full, the least-recently-added key
     * is evicted first.
     *
     * @param blockKey the packed block position ({@code BlockPos#asLong()})
     */
    public void add(long blockKey) {
        orderedKeys.addFirst(blockKey);
        counts.merge(blockKey, 1, Integer::sum);
        if (orderedKeys.size() > maxSize) {
            final long evicted = orderedKeys.removeLast();
            counts.computeIfPresent(evicted, (k, v) -> v == 1 ? null : v - 1);
        }
    }

    /**
     * @param blockKey the packed block position to search for
     * @return {@code true} if that position is currently within the recorded window
     */
    public boolean contains(long blockKey) {
        return counts.containsKey(blockKey);
    }
}
