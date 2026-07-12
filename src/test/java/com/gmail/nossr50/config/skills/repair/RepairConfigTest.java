package com.gmail.nossr50.config.skills.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.skills.repair.repairables.Repairable;
import com.gmail.nossr50.util.McTestRegistries;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link RepairConfig} end-to-end against the real bundled {@code repair.vanilla.yml}. The
 * config is MC-typed at load time — it resolves each entry against the live item registry, reads the
 * item's vanilla max-durability, and auto-classifies an item probe via {@link com.gmail.nossr50.util.ItemUtils}
 * — so it only loads meaningfully once Minecraft's registries are populated. Runs under the
 * {@code fabric-loader-junit} ("Knot") harness (see {@link McTestRegistries}), which the config's own
 * javadoc promises it is tested by. The construction copies the bundled default to a {@code @TempDir}
 * and loads it, so these assertions pin the actual shipped table, not a hand-rolled fixture.
 */
class RepairConfigTest {

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    private static Repairable findByItem(List<Repairable> repairables, String itemPath) {
        return repairables.stream()
                .filter(r -> r.getItemMaterial().equals(itemPath))
                .findFirst()
                .orElse(null);
    }

    @Test
    void loadsBundledRepairables(@TempDir Path dir) {
        final List<Repairable> repairables = new RepairConfig(dir).getLoadedRepairables();
        assertFalse(repairables.isEmpty(), "bundled repair.vanilla.yml should yield repairables");
    }

    @Test
    void diamondSwordResolvedFaithfully(@TempDir Path dir) {
        final Repairable sword =
                findByItem(new RepairConfig(dir).getLoadedRepairables(), "diamond_sword");
        assertNotNull(sword, "diamond_sword is a bundled repairable");

        // No explicit RepairMaterial in the yml -> derived from the auto-classified DIAMOND family.
        assertEquals("diamond", sword.getRepairMaterial());
        assertEquals(MaterialType.DIAMOND, sword.getRepairMaterialType());
        assertEquals(ItemType.TOOL, sword.getRepairItemType());
        // Durability comes from the live vanilla item, not the config.
        assertEquals((short) new ItemStack(Items.DIAMOND_SWORD).getMaxDamage(),
                sword.getMaximumDurability());
        // MinimumQuantity is unset in the yml (-1) -> resolved from the recipe-count table: sword = 2.
        assertEquals(2, sword.getMinimumQuantity());
        // Base repair durability = maxDurability / minimumQuantity.
        assertEquals((short) (sword.getMaximumDurability() / 2), sword.getBaseRepairDurability());
    }

    @Test
    void recipeCountFillsUnsetMinimumQuantity(@TempDir Path dir) {
        final List<Repairable> repairables = new RepairConfig(dir).getLoadedRepairables();
        // None of these name a MinimumQuantity in the yml -> all fall through to the recipe table.
        assertEquals(3, findByItem(repairables, "diamond_pickaxe").getMinimumQuantity());
        assertEquals(5, findByItem(repairables, "diamond_helmet").getMinimumQuantity());
        assertEquals(8, findByItem(repairables, "diamond_chestplate").getMinimumQuantity());
    }

    @Test
    void explicitRepairMaterialAndQuantityHonored(@TempDir Path dir) {
        // SHIELD sets RepairMaterial: OAK_PLANKS and MinimumQuantity: 6 explicitly in the yml.
        final Repairable shield =
                findByItem(new RepairConfig(dir).getLoadedRepairables(), "shield");
        assertNotNull(shield, "shield is a bundled repairable with explicit fields");
        assertEquals("oak_planks", shield.getRepairMaterial());
        assertEquals(6, shield.getMinimumQuantity());
    }

    @Test
    void everyLoadedRepairableResolvesToRealItems(@TempDir Path dir) {
        // Invariant: nothing garbage/AIR-backed leaks past the notSupported skip — every loaded
        // repairable is keyed on a real vanilla item, as is its repair material.
        for (Repairable r : new RepairConfig(dir).getLoadedRepairables()) {
            assertTrue(Materials.isItem(r.getItemMaterial()),
                    "loaded repairable item must be a real registry item: " + r.getItemMaterial());
            assertTrue(Materials.isItem(r.getRepairMaterial()),
                    "repair material must be a real registry item: " + r.getRepairMaterial());
        }
    }

    @Test
    void unknownItemNamesAreSkipped(@TempDir Path bundledDir, @TempDir Path customDir)
            throws IOException {
        final int bundledCount = new RepairConfig(bundledDir).getLoadedRepairables().size();

        // Seed a user config carrying one bogus (non-registry) item. ConfigLoader loads the existing
        // file and back-fills the bundled entries around it, so the only difference from a clean load
        // is the bogus key — which must be dropped as unsupported, not loaded as a garbage repairable.
        Files.writeString(customDir.resolve(RepairConfig.FILENAME),
                "Repairables:\n"
                + "    NOT_A_REAL_ITEM_XYZ:\n"
                + "        RepairMaterial: DIAMOND\n"
                + "        MaximumDurability: 100\n");
        final List<Repairable> loaded = new RepairConfig(customDir).getLoadedRepairables();

        assertEquals(bundledCount, loaded.size(), "the bogus item is skipped, not loaded");
        assertNull(findByItem(loaded, "not_a_real_item_xyz"),
                "no repairable is keyed on the unknown item");
    }
}
