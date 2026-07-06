package com.gmail.nossr50.util.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies the config-key formatter reproduces mcMMO's PascalCase-underscore key form when fed a
 * vanilla registry path (lower_snake), matching the legacy output that came from Bukkit enum names
 * (UPPER_SNAKE).
 */
class ConfigStringUtilsTest {

    @Test
    void materialPathBecomesPascalUnderscoreKey() {
        assertEquals("Diamond_Ore", ConfigStringUtils.getMaterialConfigString("diamond_ore"));
        assertEquals("Coal_Ore", ConfigStringUtils.getMaterialConfigString("coal_ore"));
        assertEquals("Sculk_Vein", ConfigStringUtils.getMaterialConfigString("sculk_vein"));
    }

    @Test
    void singleWordMaterialIsCapitalized() {
        assertEquals("Sculk", ConfigStringUtils.getMaterialConfigString("sculk"));
        assertEquals("Stone", ConfigStringUtils.getMaterialConfigString("stone"));
    }

    @Test
    void namespaceIsStrippedBeforeFormatting() {
        assertEquals("Sculk_Vein", ConfigStringUtils.getMaterialConfigString("minecraft:sculk_vein"));
        assertEquals("Wolf", ConfigStringUtils.getConfigEntityTypeString("minecraft:wolf"));
    }

    @Test
    void entityPathBecomesPascalUnderscoreKey() {
        assertEquals("Wolf", ConfigStringUtils.getConfigEntityTypeString("wolf"));
        assertEquals("Ocelot", ConfigStringUtils.getConfigEntityTypeString("ocelot"));
        assertEquals("Zombie_Villager",
                ConfigStringUtils.getConfigEntityTypeString("zombie_villager"));
    }

    @Test
    void stringUtilsHelpers() {
        assertEquals("Coal", StringUtils.getCapitalized("COAL"));
        assertEquals("Coal", StringUtils.getCapitalized("coal"));
        assertEquals("Diamond Ore", StringUtils.getPrettyString("diamond_ore"));
        assertEquals("Tree Feller", StringUtils.getPrettyString("TREE_FELLER"));
        assertTrue(StringUtils.isInt("42"));
        assertFalse(StringUtils.isInt("4.2"));
        assertTrue(StringUtils.isDouble("4.2"));
        assertFalse(StringUtils.isDouble("abc"));
    }
}
