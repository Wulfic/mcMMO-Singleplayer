package com.gmail.nossr50.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the MC-free {@link MaterialMapStore} classification tables against real vanilla
 * registry-id paths. This is the shared classification core that {@code ItemUtils}/{@code BlockUtils}
 * (and the repair/salvage/potion configs, and the super-ability tool detection) all delegate to, so
 * these membership checks are proven here without any Minecraft registry.
 */
class MaterialMapStoreTest {

    private MaterialMapStore store;

    @BeforeEach
    void setUp() {
        store = new MaterialMapStore();
    }

    @Test
    void classifiesToolTypesByIdPath() {
        assertTrue(store.isAxe("diamond_axe"));
        assertTrue(store.isAxe("netherite_axe"));
        assertFalse(store.isAxe("diamond_pickaxe"));

        assertTrue(store.isPickAxe("netherite_pickaxe"));
        assertFalse(store.isPickAxe("stone_axe"));

        assertTrue(store.isSword("iron_sword"));
        assertFalse(store.isSword("iron_shovel"));
    }

    @Test
    void toolAndArmorRollUpSetsAreDisjointAndCorrect() {
        assertTrue(store.isTool("diamond_axe"));
        assertTrue(store.isTool("netherite_pickaxe"));
        assertFalse(store.isTool("apple"));
        assertFalse(store.isTool("diamond_chestplate"));

        assertTrue(store.isArmor("diamond_boots"));
        assertFalse(store.isArmor("diamond_axe"));
    }

    @Test
    void classifiesMaterialTierSubsets() {
        assertTrue(store.isWoodTool("wooden_axe"));
        assertFalse(store.isWoodTool("diamond_axe"));

        assertTrue(store.isNetheriteTool("netherite_hoe"));
        assertTrue(store.isDiamondArmor("diamond_boots"));
        assertFalse(store.isDiamondArmor("iron_boots"));
    }

    @Test
    void classifiesOres() {
        assertTrue(store.isOre("diamond_ore"));
        assertTrue(store.isOre("coal_ore"));
        assertFalse(store.isOre("stone"));
    }

    @Test
    void classifiesBlockSetsByIdPath() {
        // Block classifiers (formerly Material-typed) now take the registry-path String too.
        assertTrue(store.isMultiBlockPlant("cactus"));
        assertTrue(store.isMultiBlockPlant("sugar_cane"));
        assertFalse(store.isMultiBlockPlant("stone"));
    }

    @Test
    void tierMapCoversArmorAndDefaultsToOne() {
        assertEquals(6, store.getTier("diamond_boots"));
        assertEquals(2, store.getTier("iron_boots"));
        // Tools aren't in the tier map, and unknown ids fall back to tier 1.
        assertEquals(1, store.getTier("diamond_axe"));
        assertEquals(1, store.getTier("not_a_real_item"));
    }

    @Test
    void unknownIdIsNeverClassified() {
        assertFalse(store.isAxe("not_a_real_item"));
        assertFalse(store.isTool("not_a_real_item"));
        assertFalse(store.isArmor("not_a_real_item"));
        assertFalse(store.isOre("not_a_real_item"));
    }

    @Test
    void classifiesHylianLuckFlowers() {
        // Exactly the nine small flowers legacy TreasureConfig lists individually for the Flowers group.
        assertTrue(store.isHylianLuckFlower("poppy"));
        assertTrue(store.isHylianLuckFlower("allium"));
        assertTrue(store.isHylianLuckFlower("white_tulip"));
        // Not one of the nine (oxeye_daisy is a small flower in vanilla, but legacy omits it).
        assertFalse(store.isHylianLuckFlower("oxeye_daisy"));
        assertFalse(store.isHylianLuckFlower("oak_sapling"));
        assertFalse(store.isHylianLuckFlower("stone"));
    }

    @Test
    void classifiesMultiBlockPlantsByGrowthDirection() {
        // Grow upwards from the ground: a break takes the column above it.
        assertTrue(store.isMultiBlockPlant("sugar_cane"));
        assertTrue(store.isMultiBlockPlant("bamboo"));
        assertTrue(store.isMultiBlockPlant("chorus_plant"));
        assertTrue(store.isMultiBlockPlant("tall_grass"));

        // Hang downwards from a ceiling: a break takes the vines below it.
        assertTrue(store.isMultiBlockHangingPlant("weeping_vines_plant"));
        assertTrue(store.isMultiBlockHangingPlant("cave_vines_plant"));
        assertTrue(store.isMultiBlockHangingPlant("pale_hanging_moss"));

        // PORT (upstream bug, fixed): legacy listed the misspelled "twisted_vines_plant" as a hanging
        // plant. The real block is `twisting_vines_plant` and it grows *upwards*.
        assertTrue(store.isMultiBlockPlant("twisting_vines_plant"));
        assertFalse(store.isMultiBlockHangingPlant("twisting_vines_plant"));
        assertFalse(store.isMultiBlockHangingPlant("twisted_vines_plant"));
        assertFalse(store.isMultiBlockPlant("twisted_vines_plant"));

        assertFalse(store.isMultiBlockPlant("stone"));
        assertFalse(store.isMultiBlockHangingPlant("stone"));
    }

    @Test
    void classifiesHylianLuckBushBlocks() {
        // The non-sapling members of the Bushes group (saplings come from the live SAPLINGS tag).
        assertTrue(store.isHylianLuckBushBlock("fern"));
        assertTrue(store.isHylianLuckBushBlock("short_grass"));
        assertTrue(store.isHylianLuckBushBlock("dead_bush"));
        assertFalse(store.isHylianLuckBushBlock("oak_sapling"));
        assertFalse(store.isHylianLuckBushBlock("poppy"));
    }
}
