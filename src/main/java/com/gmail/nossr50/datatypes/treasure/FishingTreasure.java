package com.gmail.nossr50.datatypes.treasure;

import org.jetbrains.annotations.NotNull;

/**
 * A Treasure-Hunter fishing reward. Carries no drop chance or level of its own — a fishing catch's
 * reward is selected from the per-tier/per-rarity {@code Item_Drop_Rates} table, not this treasure's
 * {@link Treasure#getDropChance()} — so both collapse to {@code 0}, exactly as upstream.
 *
 * <p><b>Port note:</b> the legacy field was a live {@code org.bukkit.inventory.ItemStack}; it now
 * holds the MC-free {@link ItemSpec} blueprint the base {@link Treasure} carries (see {@link ItemSpec}
 * for why construction is deferred to spawn time).
 */
public class FishingTreasure extends Treasure {

    public FishingTreasure(@NotNull ItemSpec drop, int xp) {
        super(drop, xp, 0, 0);
    }
}
