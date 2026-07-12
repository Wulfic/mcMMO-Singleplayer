package com.gmail.nossr50.skills.repair.repairables;

import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds {@link Repairable}s from their (registry-path keyed) definition. Mirrors legacy
 * {@code RepairableFactory}.
 */
public final class RepairableFactory {

    private RepairableFactory() {
    }

    public static @NotNull Repairable getRepairable(@NotNull String itemMaterial,
            @NotNull String repairMaterial, @Nullable String repairMaterialPrettyName,
            int minimumLevel, short maximumDurability, @NotNull ItemType repairItemType,
            @NotNull MaterialType repairMaterialType, double xpMultiplier, int minQuantity) {
        return new SimpleRepairable(itemMaterial, repairMaterial, repairMaterialPrettyName,
                minimumLevel, maximumDurability, repairItemType, repairMaterialType, xpMultiplier,
                minQuantity);
    }
}
