package com.gmail.nossr50.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.util.McTestRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exercises the MC-typed {@link ItemUtils} wrappers end-to-end against real vanilla {@link ItemStack}s:
 * this proves the id-path extraction ({@code Registries.ITEM.getId(item).getPath()}) lines up with the
 * keys the MC-free {@link MaterialMapStore} is tested on ({@link MaterialMapStoreTest}), so the two
 * layers actually connect. Runs under the {@code fabric-loader-junit} harness (see {@link
 * McTestRegistries}). Also covers {@link ItemUtils#isToolInHand}, the super-ability tool-raise gate.
 */
class ItemUtilsTest {

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    @Test
    void classifiesWeaponsAndTools() {
        assertTrue(ItemUtils.isAxe(new ItemStack(Items.DIAMOND_AXE)));
        assertFalse(ItemUtils.isAxe(new ItemStack(Items.DIAMOND_PICKAXE)));

        assertTrue(ItemUtils.isPickaxe(new ItemStack(Items.NETHERITE_PICKAXE)));
        assertTrue(ItemUtils.isShovel(new ItemStack(Items.IRON_SHOVEL)));
        assertTrue(ItemUtils.isHoe(new ItemStack(Items.GOLDEN_HOE)));

        assertTrue(ItemUtils.isSword(new ItemStack(Items.IRON_SWORD)));
        assertFalse(ItemUtils.isSword(new ItemStack(Items.IRON_SHOVEL)));

        assertTrue(ItemUtils.isBow(new ItemStack(Items.BOW)));
        assertTrue(ItemUtils.isCrossbow(new ItemStack(Items.CROSSBOW)));
        assertTrue(ItemUtils.isTrident(new ItemStack(Items.TRIDENT)));
        assertTrue(ItemUtils.isMace(new ItemStack(Items.MACE)));

        // Spears are real vanilla items from 1.21.11 (Items.WOODEN_SPEAR … NETHERITE_SPEAR, the
        // minecraft:spears tag, data/minecraft/damage_type/spear.json). The port believed otherwise
        // until GitHub #7 and left the whole skill unreachable — see MeleeWeaponClassificationTest,
        // which pins all seven against the registry rather than a list.
        //
        // Resolved through the registry, not as Items.IRON_SPEAR: the constant does not exist on the
        // mc/1.21.10 band, where naming it fails the BUILD rather than the assertion. A trident is
        // asserted not-a-spear either way — it exists on every supported version, so that half of
        // the check never lapses.
        assertFalse(ItemUtils.isSpear(new ItemStack(Items.TRIDENT)));
        McTestRegistries.optionalVanillaItem("iron_spear").ifPresent(spear -> {
            assertTrue(ItemUtils.isSpear(new ItemStack(spear)));
            assertFalse(ItemUtils.isSword(new ItemStack(spear)));
        });
    }

    @Test
    void classifiesToolTiers() {
        assertTrue(ItemUtils.isMinecraftTool(new ItemStack(Items.DIAMOND_AXE)));
        assertFalse(ItemUtils.isMinecraftTool(new ItemStack(Items.APPLE)));

        assertTrue(ItemUtils.isWoodTool(new ItemStack(Items.WOODEN_AXE)));
        assertTrue(ItemUtils.isStoneTool(new ItemStack(Items.STONE_PICKAXE)));
        assertTrue(ItemUtils.isIronTool(new ItemStack(Items.IRON_SHOVEL)));
        assertTrue(ItemUtils.isGoldTool(new ItemStack(Items.GOLDEN_HOE)));
        assertTrue(ItemUtils.isDiamondTool(new ItemStack(Items.DIAMOND_SWORD)));
        assertTrue(ItemUtils.isNetheriteTool(new ItemStack(Items.NETHERITE_PICKAXE)));

        assertFalse(ItemUtils.isWoodTool(new ItemStack(Items.STONE_PICKAXE)));
    }

    @Test
    void classifiesArmorAndEnchantable() {
        assertTrue(ItemUtils.isArmor(new ItemStack(Items.DIAMOND_CHESTPLATE)));
        assertTrue(ItemUtils.isLeatherArmor(new ItemStack(Items.LEATHER_BOOTS)));
        assertTrue(ItemUtils.isIronArmor(new ItemStack(Items.IRON_HELMET)));
        assertTrue(ItemUtils.isDiamondArmor(new ItemStack(Items.DIAMOND_CHESTPLATE)));
        assertTrue(ItemUtils.isNetheriteArmor(new ItemStack(Items.NETHERITE_LEGGINGS)));
        assertTrue(ItemUtils.isChainmailArmor(new ItemStack(Items.CHAINMAIL_HELMET)));
        assertFalse(ItemUtils.isArmor(new ItemStack(Items.DIAMOND_AXE)));

        assertTrue(ItemUtils.isEnchantable(new ItemStack(Items.DIAMOND_SWORD)));
    }

    @Test
    void unarmedIsBareHandWhenConfigUnavailable() {
        // No server session in unit tests → GeneralConfig is null, so isUnarmed collapses to the
        // empty-hand semantics (config-null branch). An empty stack is unarmed; a held tool is not.
        assertTrue(ItemUtils.isUnarmed(ItemStack.EMPTY));
        assertFalse(ItemUtils.isUnarmed(new ItemStack(Items.DIAMOND_AXE)));
    }

    @Test
    void toolTypeInHandMatchesHeldTool() {
        assertTrue(ItemUtils.isToolInHand(ToolType.AXE, new ItemStack(Items.DIAMOND_AXE)));
        assertFalse(ItemUtils.isToolInHand(ToolType.AXE, new ItemStack(Items.DIAMOND_PICKAXE)));

        assertTrue(ItemUtils.isToolInHand(ToolType.PICKAXE, new ItemStack(Items.STONE_PICKAXE)));
        assertTrue(ItemUtils.isToolInHand(ToolType.SHOVEL, new ItemStack(Items.IRON_SHOVEL)));
        assertTrue(ItemUtils.isToolInHand(ToolType.SWORD, new ItemStack(Items.NETHERITE_SWORD)));
        assertTrue(ItemUtils.isToolInHand(ToolType.HOE, new ItemStack(Items.GOLDEN_HOE)));
        assertTrue(ItemUtils.isToolInHand(ToolType.MACES, new ItemStack(Items.MACE)));

        // FISTS = bare empty hand (upstream Material.AIR check); BOW has no tool-raise (always false).
        assertTrue(ItemUtils.isToolInHand(ToolType.FISTS, ItemStack.EMPTY));
        assertFalse(ItemUtils.isToolInHand(ToolType.FISTS, new ItemStack(Items.DIAMOND_AXE)));
        assertFalse(ItemUtils.isToolInHand(ToolType.BOW, new ItemStack(Items.BOW)));
    }
}
