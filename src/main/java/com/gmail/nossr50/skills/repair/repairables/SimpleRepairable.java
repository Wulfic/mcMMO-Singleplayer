package com.gmail.nossr50.skills.repair.repairables;

import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import com.gmail.nossr50.util.skills.SkillUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Plain immutable {@link Repairable}. Ported MC-free (registry-path keyed). When the config does
 * not specify a minimum quantity ({@code minQuantity == -1}), it is resolved lazily from the
 * standard vanilla crafting-count table via
 * {@link SkillUtils#getRepairAndSalvageQuantities(String)} — matching legacy {@code SimpleRepairable}.
 */
public class SimpleRepairable implements Repairable {
    private final String itemMaterial;
    private final String repairMaterial;
    private final int minimumLevel;
    private final short maximumDurability;
    private final @Nullable String repairMaterialPrettyName;
    private final ItemType repairItemType;
    private final MaterialType repairMaterialType;
    private final double xpMultiplier;
    private final int minQuantity;

    protected SimpleRepairable(@NotNull String itemMaterial, @NotNull String repairMaterial,
            @Nullable String repairMaterialPrettyName, int minimumLevel, short maximumDurability,
            @NotNull ItemType repairItemType, @NotNull MaterialType repairMaterialType,
            double xpMultiplier, int minQuantity) {
        this.itemMaterial = itemMaterial;
        this.repairMaterial = repairMaterial;
        this.repairMaterialPrettyName = repairMaterialPrettyName;
        this.repairItemType = repairItemType;
        this.repairMaterialType = repairMaterialType;
        this.minimumLevel = minimumLevel;
        this.maximumDurability = maximumDurability;
        this.xpMultiplier = xpMultiplier;
        this.minQuantity = minQuantity;
    }

    @Override
    public @NotNull String getItemMaterial() {
        return itemMaterial;
    }

    @Override
    public @NotNull String getRepairMaterial() {
        return repairMaterial;
    }

    @Override
    public @Nullable String getRepairMaterialPrettyName() {
        return repairMaterialPrettyName;
    }

    @Override
    public @NotNull ItemType getRepairItemType() {
        return repairItemType;
    }

    @Override
    public @NotNull MaterialType getRepairMaterialType() {
        return repairMaterialType;
    }

    @Override
    public int getMinimumQuantity() {
        if (minQuantity == -1) {
            return Math.max(SkillUtils.getRepairAndSalvageQuantities(itemMaterial), 1);
        }
        return minQuantity;
    }

    @Override
    public short getMaximumDurability() {
        return maximumDurability;
    }

    @Override
    public short getBaseRepairDurability() {
        return (short) (maximumDurability / getMinimumQuantity());
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
