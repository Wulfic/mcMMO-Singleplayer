package com.gmail.nossr50.skills.salvage.salvageables;

import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import org.jetbrains.annotations.NotNull;

/**
 * A definition of an item that mcMMO Salvage can break down, plus the material it yields. Ported
 * MC-free (registry-path keyed) — see {@link com.gmail.nossr50.skills.repair.repairables.Repairable}.
 */
public interface Salvageable {
    /**
     * @return the registry path of the item this salvageable describes
     */
    @NotNull String getItemMaterial();

    /**
     * @return the registry path of the material yielded when salvaging this item
     */
    @NotNull String getSalvageMaterial();

    /**
     * @return the {@link ItemType} of this salvageable (armor/tool/other; permissions only)
     */
    @NotNull ItemType getSalvageItemType();

    /**
     * @return the {@link MaterialType} of this salvageable's material (permissions / default material)
     */
    @NotNull MaterialType getSalvageMaterialType();

    /**
     * The maximum quantity of salvage materials, ignoring all other salvage bonuses. Typically the
     * number of items needed to craft the item (2 for a sword, 3 for an axe, 5 for a helmet).
     *
     * @return the maximum number of items
     */
    int getMaximumQuantity();

    /**
     * @return the maximum durability of this item before it breaks
     */
    short getMaximumDurability();

    /**
     * @return the base salvage durability: the maximum durability divided by the maximum quantity
     */
    short getBaseSalvageDurability();

    /**
     * @return the minimum Salvage level needed to salvage this item, or 0 for no minimum
     */
    int getMinimumLevel();

    /**
     * @return the XP multiplier for this salvageable (unused by mcMMO: Salvage grants no XP)
     */
    double getXpMultiplier();
}
