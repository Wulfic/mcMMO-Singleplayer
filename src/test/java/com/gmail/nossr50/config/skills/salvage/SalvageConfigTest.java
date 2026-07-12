package com.gmail.nossr50.config.skills.salvage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.skills.salvage.salvageables.Salvageable;
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
 * Exercises {@link SalvageConfig} end-to-end against the real bundled {@code salvage.vanilla.yml},
 * under the {@code fabric-loader-junit} ("Knot") harness — mirror of {@link com.gmail.nossr50.config.skills.repair.RepairConfig}'s
 * test. MC-typed at load (live registry existence + path, vanilla max-durability, {@link com.gmail.nossr50.util.ItemUtils}
 * classification), so it needs {@link McTestRegistries}. Note Salvage grants no XP — the parsed
 * {@code XpMultiplier} is retained for fidelity but unused by the skill.
 */
class SalvageConfigTest {

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    private static Salvageable findByItem(List<Salvageable> salvageables, String itemPath) {
        return salvageables.stream()
                .filter(s -> s.getItemMaterial().equals(itemPath))
                .findFirst()
                .orElse(null);
    }

    @Test
    void loadsBundledSalvageables(@TempDir Path dir) {
        final List<Salvageable> salvageables = new SalvageConfig(dir).getLoadedSalvageables();
        assertFalse(salvageables.isEmpty(), "bundled salvage.vanilla.yml should yield salvageables");
    }

    @Test
    void diamondSwordResolvedFaithfully(@TempDir Path dir) {
        final Salvageable sword =
                findByItem(new SalvageConfig(dir).getLoadedSalvageables(), "diamond_sword");
        assertNotNull(sword, "diamond_sword is a bundled salvageable");

        // No explicit SalvageMaterial in the yml -> derived from the auto-classified DIAMOND family.
        assertEquals("diamond", sword.getSalvageMaterial());
        assertEquals(MaterialType.DIAMOND, sword.getSalvageMaterialType());
        assertEquals(ItemType.TOOL, sword.getSalvageItemType());
        assertEquals((short) new ItemStack(Items.DIAMOND_SWORD).getMaxDamage(),
                sword.getMaximumDurability());
        assertEquals(50, sword.getMinimumLevel());
        // MaximumQuantity: 2 is set explicitly in the yml.
        assertEquals(2, sword.getMaximumQuantity());
        assertEquals((short) (sword.getMaximumDurability() / 2), sword.getBaseSalvageDurability());
    }

    @Test
    void explicitMaximumQuantityHonored(@TempDir Path dir) {
        final List<Salvageable> salvageables = new SalvageConfig(dir).getLoadedSalvageables();
        // These name MaximumQuantity explicitly, matching the vanilla recipe counts.
        assertEquals(3, findByItem(salvageables, "diamond_pickaxe").getMaximumQuantity());
        assertEquals(5, findByItem(salvageables, "diamond_helmet").getMaximumQuantity());
        assertEquals(8, findByItem(salvageables, "diamond_chestplate").getMaximumQuantity());
    }

    @Test
    void everyLoadedSalvageableResolvesToRealItems(@TempDir Path dir) {
        // Invariant: nothing garbage/AIR-backed leaks past the notSupported skip.
        for (Salvageable s : new SalvageConfig(dir).getLoadedSalvageables()) {
            assertTrue(Materials.isItem(s.getItemMaterial()),
                    "loaded salvageable item must be a real registry item: " + s.getItemMaterial());
            assertTrue(Materials.isItem(s.getSalvageMaterial()),
                    "salvage material must be a real registry item: " + s.getSalvageMaterial());
        }
    }

    @Test
    void unknownItemNamesAreSkipped(@TempDir Path bundledDir, @TempDir Path customDir)
            throws IOException {
        final int bundledCount = new SalvageConfig(bundledDir).getLoadedSalvageables().size();

        // Seed a user config carrying one bogus (non-registry) item; it must be dropped, not loaded.
        Files.writeString(customDir.resolve(SalvageConfig.FILENAME),
                "Salvageables:\n"
                + "    NOT_A_REAL_ITEM_XYZ:\n"
                + "        SalvageMaterial: DIAMOND\n"
                + "        MaximumQuantity: 2\n");
        final List<Salvageable> loaded = new SalvageConfig(customDir).getLoadedSalvageables();

        assertEquals(bundledCount, loaded.size(), "the bogus item is skipped, not loaded");
        assertNull(findByItem(loaded, "not_a_real_item_xyz"),
                "no salvageable is keyed on the unknown item");
    }
}
