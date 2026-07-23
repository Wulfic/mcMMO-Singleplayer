package com.gmail.nossr50.util.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.repair.repairables.Repairable;
import com.gmail.nossr50.skills.repair.repairables.RepairableManager;
import com.gmail.nossr50.util.McTestRegistries;
import java.nio.file.Path;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Unit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the ported {@link SkillUtils} core: the pure cooldown-expiry math (no harness needed) and the
 * tool/armor durability change against real vanilla {@link ItemStack}s under the {@code
 * fabric-loader-junit} registry harness (see {@link McTestRegistries}). The Unbreaking damage-reduction
 * branch is not exercised here — enchantments live in the dynamic registry, which {@code
 * Bootstrap.initialize()} does not load — so only the deterministic paths (base change, unbreakable
 * no-op, max-durability cap) are asserted; the Unbreaking divisor is verified in-game (Phase 12).
 */
class SkillUtilsTest {

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    // --- cooldownExpired (pure math) ----------------------------------------

    @Test
    void cooldownExpiredWhenDeactivatedLongAgo() {
        long tenMinutesAgoSeconds = (System.currentTimeMillis() / 1000L) - 600L;
        assertTrue(SkillUtils.cooldownExpired(tenMinutesAgoSeconds, 60));
    }

    @Test
    void cooldownNotExpiredWhenRecent() {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        assertFalse(SkillUtils.cooldownExpired(nowSeconds, 300));
    }

    // --- handleDurabilityChange (real stacks) -------------------------------

    @Test
    void durabilityChangeAppliesDamage() {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        PlatformItem item = new PlatformItem(pick);
        assertEquals(0, item.getDurability());

        SkillUtils.handleDurabilityChange(item, 100);

        assertEquals(100, item.getDurability());
    }

    @Test
    void durabilityChangeSkipsUnbreakable() {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        PlatformItem item = new PlatformItem(pick);

        SkillUtils.handleDurabilityChange(item, 100);

        assertEquals(0, item.getDurability());
    }

    @Test
    void durabilityChangeCapsAtMaxDurability() {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        PlatformItem item = new PlatformItem(pick);
        int max = item.getMaxDurability();

        // A huge modifier must not push damage past the item's max durability.
        SkillUtils.handleDurabilityChange(item, max * 10.0);

        assertEquals(max, item.getDurability());
    }

    @Test
    void durabilityChangeRespectsMaxDamageModifierCap() {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        PlatformItem item = new PlatformItem(pick);
        int max = item.getMaxDurability();

        // maxDamageModifier of 0.5 caps a single change at half the item's max durability.
        SkillUtils.handleDurabilityChange(item, max, 0.5);

        assertEquals(max / 2, item.getDurability());
    }

    // --- RepairableManager max-durability override (legacy K4) ---------------

    @Test
    void durabilityChangeClampsToTheRepairableConfiguredMaxDurability() {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        PlatformItem item = new PlatformItem(pick);
        assertTrue(item.getMaxDurability() > 100, "vanilla diamond pickaxe outlasts the override");

        Repairable repairable = mock(Repairable.class);
        when(repairable.getMaximumDurability()).thenReturn((short) 100);
        RepairableManager repairableManager = mock(RepairableManager.class);
        when(repairableManager.getRepairable("diamond_pickaxe")).thenReturn(repairable);
        McMMOMod.setRepairableManager(repairableManager);
        try {
            SkillUtils.handleDurabilityChange(item, 5000);

            // The configured 100, not the vanilla 1561 — and looked up by bare registry path.
            assertEquals(100, item.getDurability());
            verify(repairableManager).getRepairable("diamond_pickaxe");
        } finally {
            McMMOMod.setRepairableManager(null);
        }
    }

    @Test
    void durabilityChangeFallsBackToVanillaMaxWhenTheItemIsNotRepairable() {
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        PlatformItem item = new PlatformItem(pick);
        int vanillaMax = item.getMaxDurability();

        RepairableManager repairableManager = mock(RepairableManager.class);
        when(repairableManager.getRepairable("diamond_pickaxe")).thenReturn(null);
        McMMOMod.setRepairableManager(repairableManager);
        try {
            SkillUtils.handleDurabilityChange(item, vanillaMax * 10.0);

            assertEquals(vanillaMax, item.getDurability());
        } finally {
            McMMOMod.setRepairableManager(null);
        }
    }

    @Test
    void armorDurabilityChangeAlsoHonoursTheRepairableOverride() {
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        PlatformItem item = new PlatformItem(chest);

        Repairable repairable = mock(Repairable.class);
        when(repairable.getMaximumDurability()).thenReturn((short) 40);
        RepairableManager repairableManager = mock(RepairableManager.class);
        when(repairableManager.getRepairable("diamond_chestplate")).thenReturn(repairable);
        McMMOMod.setRepairableManager(repairableManager);
        try {
            SkillUtils.handleArmorDurabilityChange(item, 5000, 1.0);

            assertEquals(40, item.getDurability());
        } finally {
            McMMOMod.setRepairableManager(null);
        }
    }

    @Test
    void armorDurabilityChangeAppliesReducedDamage() {
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        PlatformItem item = new PlatformItem(chest);

        // Armor formula with Unbreaking 0: factor = 0.6 + 0.4/1 = 1.0, so damage == modifier.
        SkillUtils.handleArmorDurabilityChange(item, 50, 1.0);

        assertEquals(50, item.getDurability());
    }

    // --- Super/Giga Breaker dig-speed boost orchestration -------------------
    // Only the MC-free decision layer is asserted here; the enchant/marker mutation on PlatformPlayer
    // needs the dynamic enchantment registry (absent in this harness) and is verified in-game.

    @Test
    void handleAbilitySpeedIncreaseAppliesTheConfiguredEnchantBuff(@TempDir Path dataFolder) {
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        try {
            PlatformPlayer player = mock(PlatformPlayer.class);

            SkillUtils.handleAbilitySpeedIncrease(player);

            // Bundled hidden.yml Options.EnchantmentBuffs=true; advanced.yml EnchantBuff=5.
            verify(player).applySuperAbilityDigBoost(5);
        } finally {
            McMMOMod.setAdvancedConfig(null);
        }
    }

    @Test
    void removeAbilityBoostMethodsDelegateToThePlayerAdapter() {
        PlatformPlayer player = mock(PlatformPlayer.class);

        SkillUtils.removeAbilityBuffFromMainHand(player);
        verify(player).removeSuperAbilityBoostFromMainHand();

        SkillUtils.removeAbilityBoostsFromInventory(player);
        verify(player).removeSuperAbilityBoostsFromInventory();
    }

    // --- getRepairAndSalvageQuantities (pure vanilla-count table) ------------

    @Test
    void quantitiesFollowStandardGearRecipeCounts() {
        assertEquals(2, SkillUtils.getRepairAndSalvageQuantities("diamond_sword"));
        assertEquals(3, SkillUtils.getRepairAndSalvageQuantities("iron_pickaxe"));
        assertEquals(3, SkillUtils.getRepairAndSalvageQuantities("golden_axe"));
        assertEquals(1, SkillUtils.getRepairAndSalvageQuantities("stone_shovel"));
        assertEquals(2, SkillUtils.getRepairAndSalvageQuantities("wooden_hoe"));
        assertEquals(5, SkillUtils.getRepairAndSalvageQuantities("iron_helmet"));
        assertEquals(8, SkillUtils.getRepairAndSalvageQuantities("diamond_chestplate"));
        assertEquals(7, SkillUtils.getRepairAndSalvageQuantities("leather_leggings"));
        assertEquals(4, SkillUtils.getRepairAndSalvageQuantities("golden_boots"));
    }

    @Test
    void quantitiesSpecialCaseNetheriteAndTrident() {
        // Netherite gear is crafted in units of four scraps regardless of shape.
        assertEquals(4, SkillUtils.getRepairAndSalvageQuantities("netherite_sword"));
        assertEquals(4, SkillUtils.getRepairAndSalvageQuantities("netherite_helmet"));
        // Trident repairs with 16 prismarine crystals.
        assertEquals(16, SkillUtils.getRepairAndSalvageQuantities("trident"));
    }

    @Test
    void quantitiesCoverNonGearRepairablesAndUnknownShapes() {
        assertEquals(2, SkillUtils.getRepairAndSalvageQuantities("shears"));
        assertEquals(1, SkillUtils.getRepairAndSalvageQuantities("flint_and_steel"));
        assertEquals(3, SkillUtils.getRepairAndSalvageQuantities("bow"));
        assertEquals(2, SkillUtils.getRepairAndSalvageQuantities("fishing_rod"));
        // Unknown shape returns 0 so the caller falls back to the config value / floor of 1.
        assertEquals(0, SkillUtils.getRepairAndSalvageQuantities("carrot_on_a_stick"));
    }
}
