package com.gmail.nossr50.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.skills.ToolType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exercises the MC-typed {@link ItemUtils} wrappers end-to-end against real vanilla {@link ItemStack}s:
 * this proves the id-path extraction ({@code Registries.ITEM.getId(item).getPath()}) lines up with the
 * keys the MC-free {@link MaterialMapStore} is tested on ({@link MaterialMapStoreTest}), so the two
 * layers actually connect. Runs under the {@code fabric-loader-junit} harness (see {@link
 * McTestRegistries}). Also covers {@link ToolType#inHand(ItemStack)}, the super-ability tool-raise gate.
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
        assertTrue(ToolType.AXE.inHand(new ItemStack(Items.DIAMOND_AXE)));
        assertFalse(ToolType.AXE.inHand(new ItemStack(Items.DIAMOND_PICKAXE)));

        assertTrue(ToolType.PICKAXE.inHand(new ItemStack(Items.STONE_PICKAXE)));
        assertTrue(ToolType.SHOVEL.inHand(new ItemStack(Items.IRON_SHOVEL)));
        assertTrue(ToolType.SWORD.inHand(new ItemStack(Items.NETHERITE_SWORD)));
        assertTrue(ToolType.HOE.inHand(new ItemStack(Items.GOLDEN_HOE)));
        assertTrue(ToolType.MACES.inHand(new ItemStack(Items.MACE)));

        // FISTS = bare empty hand (upstream Material.AIR check); BOW has no tool-raise (always false).
        assertTrue(ToolType.FISTS.inHand(ItemStack.EMPTY));
        assertFalse(ToolType.FISTS.inHand(new ItemStack(Items.DIAMOND_AXE)));
        assertFalse(ToolType.BOW.inHand(new ItemStack(Items.BOW)));
    }
}
