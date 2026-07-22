package com.gmail.nossr50.datatypes.treasure;

import org.jetbrains.annotations.NotNull;

/**
 * A drop that can be shaken off a hooked mob by the Fishing <b>Shake</b> sub-skill.
 *
 * <p><b>Port note:</b> the legacy field was a live {@code org.bukkit.inventory.ItemStack}; it now holds
 * the MC-free {@link ItemSpec} blueprint the base {@link Treasure} carries (see {@link ItemSpec} for
 * why construction is deferred to spawn time).
 *
 * <p><b>Only {@link Treasure#getDropChance()} is consulted</b> when a drop is selected — legacy's
 * {@code Fishing.chooseDrop} walks the entity's list accumulating drop chances and ignores both
 * {@code Drop_Level} and the per-treasure {@code XP} (Shake pays the flat
 * {@code Experience_Values.Fishing.Shake} instead). Both fields are still parsed and carried here so
 * the datatype matches the config the operator writes; see CONVERSION_TODO §F for the dead-knob note.
 */
public class ShakeTreasure extends Treasure {

    public ShakeTreasure(@NotNull ItemSpec drop, int xp, double dropChance, int dropLevel) {
        super(drop, xp, dropChance, dropLevel);
    }
}
