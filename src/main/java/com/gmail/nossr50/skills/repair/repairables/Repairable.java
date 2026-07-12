package com.gmail.nossr50.skills.repair.repairables;

import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A definition of an item that mcMMO Repair can restore, plus the material it is repaired with.
 * Ported MC-free: the item and its repair material are identified by vanilla registry <em>path</em>
 * strings (e.g. {@code "diamond_pickaxe"}, {@code "diamond"}) rather than {@code org.bukkit.Material},
 * so the repair skill core stays free of Minecraft types and unit-testable. The live-item concerns
 * (durability read/write, inventory consumption) live in the anvil listener / platform adapter.
 */
public interface Repairable {
    /**
     * @return the registry path of the item this repairable describes
     */
    @NotNull String getItemMaterial();

    /**
     * @return the registry path of the material used to repair this item
     */
    @NotNull String getRepairMaterial();

    /**
     * @return the pretty name of the repair material, or {@code null} to derive one from its id
     */
    @Nullable String getRepairMaterialPrettyName();

    /**
     * @return the {@link ItemType} of this repairable (armor/tool/other; permissions only)
     */
    @NotNull ItemType getRepairItemType();

    /**
     * @return the {@link MaterialType} of this repairable's material (permissions / default material)
     */
    @NotNull MaterialType getRepairMaterialType();

    /**
     * The minimum quantity of repair materials, ignoring all other repair bonuses. Typically the
     * number of items needed to craft the item (2 for a sword, 3 for an axe, 5 for a helmet).
     *
     * @return the minimum number of items
     */
    int getMinimumQuantity();

    /**
     * @return the maximum durability of this item before it breaks
     */
    short getMaximumDurability();

    /**
     * The base repair durability on which bonuses are calculated: the maximum durability divided by
     * the minimum quantity.
     *
     * @return the base repair durability
     */
    short getBaseRepairDurability();

    /**
     * @return the minimum Repair level needed to repair this item, or 0 for no minimum
     */
    int getMinimumLevel();

    /**
     * @return the XP multiplier applied to the base Repair XP for this item
     */
    double getXpMultiplier();
}
