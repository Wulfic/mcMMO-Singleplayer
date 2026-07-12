package com.gmail.nossr50.skills.salvage.salvageables;

import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import org.jetbrains.annotations.NotNull;

/**
 * Builds {@link Salvageable}s from their (registry-path keyed) definition. Mirrors legacy
 * {@code SalvageableFactory}.
 */
public final class SalvageableFactory {

    private SalvageableFactory() {
    }

    public static @NotNull Salvageable getSalvageable(@NotNull String itemMaterial,
            @NotNull String salvageMaterial, int minimumLevel, int maximumQuantity,
            short maximumDurability, @NotNull ItemType salvageItemType,
            @NotNull MaterialType salvageMaterialType, double xpMultiplier) {
        return new SimpleSalvageable(itemMaterial, salvageMaterial, minimumLevel, maximumQuantity,
                maximumDurability, salvageItemType, salvageMaterialType, xpMultiplier);
    }
}
