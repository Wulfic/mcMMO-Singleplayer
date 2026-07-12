package com.gmail.nossr50.skills.salvage.salvageables;

import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import org.jetbrains.annotations.NotNull;

/**
 * Plain immutable {@link Salvageable}. Ported MC-free (registry-path keyed).
 */
public class SimpleSalvageable implements Salvageable {
    private final String itemMaterial;
    private final String salvageMaterial;
    private final int maximumQuantity;
    private final int minimumLevel;
    private final short maximumDurability;
    private final short baseSalvageDurability;
    private final ItemType salvageItemType;
    private final MaterialType salvageMaterialType;
    private final double xpMultiplier;

    protected SimpleSalvageable(@NotNull String itemMaterial, @NotNull String salvageMaterial,
            int minimumLevel, int maximumQuantity, short maximumDurability,
            @NotNull ItemType salvageItemType, @NotNull MaterialType salvageMaterialType,
            double xpMultiplier) {
        this.itemMaterial = itemMaterial;
        this.salvageMaterial = salvageMaterial;
        this.salvageItemType = salvageItemType;
        this.salvageMaterialType = salvageMaterialType;
        this.minimumLevel = minimumLevel;
        this.maximumQuantity = maximumQuantity;
        this.maximumDurability = maximumDurability;
        this.baseSalvageDurability = (short) (maximumDurability / maximumQuantity);
        this.xpMultiplier = xpMultiplier;
    }

    @Override
    public @NotNull String getItemMaterial() {
        return itemMaterial;
    }

    @Override
    public @NotNull String getSalvageMaterial() {
        return salvageMaterial;
    }

    @Override
    public @NotNull ItemType getSalvageItemType() {
        return salvageItemType;
    }

    @Override
    public @NotNull MaterialType getSalvageMaterialType() {
        return salvageMaterialType;
    }

    @Override
    public int getMaximumQuantity() {
        return maximumQuantity;
    }

    @Override
    public short getMaximumDurability() {
        return maximumDurability;
    }

    @Override
    public short getBaseSalvageDurability() {
        return baseSalvageDurability;
    }

    @Override
    public int getMinimumLevel() {
        return minimumLevel;
    }

    @Override
    public double getXpMultiplier() {
        return xpMultiplier;
    }
}
