package com.gmail.nossr50.skills.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import com.gmail.nossr50.skills.repair.repairables.Repairable;
import com.gmail.nossr50.skills.repair.repairables.RepairableFactory;
import com.gmail.nossr50.skills.salvage.salvageables.Salvageable;
import com.gmail.nossr50.skills.salvage.salvageables.SalvageableFactory;
import org.junit.jupiter.api.Test;

/**
 * MC-free coverage for the repair/salvage datatypes: base-durability math and the lazy
 * minimum-quantity resolution that falls back to the standard vanilla recipe-count table when the
 * config does not specify a quantity ({@code minQuantity == -1}).
 */
class RepairSalvageDatatypeTest {

    @Test
    void repairableResolvesMinimumQuantityFromRecipeCountWhenUnset() {
        // Diamond pickaxe: no explicit MinimumQuantity in the config -> table says 3 diamonds.
        Repairable pick = RepairableFactory.getRepairable("diamond_pickaxe", "diamond", null, 0,
                (short) 1561, ItemType.TOOL, MaterialType.DIAMOND, 1.0, -1);

        assertEquals(3, pick.getMinimumQuantity());
        // Base repair durability = maxDurability / minimumQuantity.
        assertEquals((short) (1561 / 3), pick.getBaseRepairDurability());
    }

    @Test
    void repairableHonoursExplicitMinimumQuantity() {
        // Trident config sets MinimumQuantity: 16 explicitly.
        Repairable trident = RepairableFactory.getRepairable("trident", "prismarine_crystals", null,
                0, (short) 250, ItemType.TOOL, MaterialType.OTHER, 3.0, 16);

        assertEquals(16, trident.getMinimumQuantity());
        assertEquals((short) (250 / 16), trident.getBaseRepairDurability());
    }

    @Test
    void salvageableComputesBaseDurabilityFromMaxQuantity() {
        Salvageable helmet = SalvageableFactory.getSalvageable("iron_helmet", "iron_ingot", 0, 5,
                (short) 165, ItemType.ARMOR, MaterialType.IRON, 2.0);

        assertEquals(5, helmet.getMaximumQuantity());
        assertEquals((short) (165 / 5), helmet.getBaseSalvageDurability());
    }
}
