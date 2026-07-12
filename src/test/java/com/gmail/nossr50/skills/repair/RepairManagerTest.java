package com.gmail.nossr50.skills.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.ItemType;
import com.gmail.nossr50.datatypes.skills.MaterialType;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.repair.repairables.Repairable;
import com.gmail.nossr50.skills.repair.repairables.RepairableFactory;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the Repair numeric cores (Phase 10.3) against the real bundled configs.
 *
 * <p>RetroMode is on by default (skillranks.yml): Repair Mastery unlocks at level 1, Super Repair at
 * 400, Arcane Forging ranks at {100,250,350,500,650,750,850,1000}. advanced.yml: Repair Mastery
 * MaxBonusPercentage 200.0, MaxBonusLevel(RetroMode) 1000; Arcane Forging Keep_Enchants_Chance
 * Rank_1 10.0, Downgrades_Chance Rank_1 75.0.
 */
class RepairManagerTest {

    private McMMOPlayer mmoPlayer;
    private RepairManager repairManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));

        PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        repairManager = new RepairManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
        UserManager.clearAll();
    }

    private void atRepairLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.REPAIR)).thenReturn(level);
    }

    @Test
    void percentageRepairedIsTheDurabilityFractionOfMax() {
        assertEquals(0.25f, repairManager.getPercentageRepaired((short) 1000, (short) 500,
                (short) 2000), 1.0e-6f);
        assertEquals(1.0f, repairManager.getPercentageRepaired((short) 2000, (short) 0,
                (short) 2000), 1.0e-6f);
    }

    @Test
    void repairMasteryScalesTheRepairAmountWithSkillLevel() {
        // Repair Mastery unlocked at level 1. At level 500 skillLevelBonusCalc =
        // (200/1000)*(500/100) = 1.0 (below the 2.0 cap) -> base 100 gains +100 -> 200 repaired.
        atRepairLevel(500);
        assertEquals((short) 800, repairManager.repairCalculate((short) 1000, 100, false),
                "1000 damage - 200 repaired = 800");
    }

    @Test
    void repairMasteryBonusIsCappedAtMaxBonusPercentage() {
        // At level 1500 skillLevelBonusCalc = 3.0 but caps at 2.0 -> base 100 gains +200 -> 300.
        atRepairLevel(1500);
        assertEquals((short) 700, repairManager.repairCalculate((short) 1000, 100, false),
                "capped bonus: 1000 - 300 = 700");
    }

    @Test
    void withoutRepairMasteryUnlockedTheBaseAmountIsUsed() {
        atRepairLevel(0); // Repair Mastery not yet unlocked (unlocks at 1)
        assertEquals((short) 900, repairManager.repairCalculate((short) 1000, 100, false),
                "no mastery: 1000 - 100 = 900");
    }

    @Test
    void superRepairDoublesTheRepairAmount() {
        atRepairLevel(0); // isolate Super Repair from Repair Mastery
        assertEquals((short) 800, repairManager.repairCalculate((short) 1000, 100, true),
                "Super Repair doubles 100 -> 200 repaired -> 1000 - 200 = 800");
    }

    @Test
    void arcaneForgingChancesLadderWithRank() {
        atRepairLevel(0);
        assertEquals(0, repairManager.getArcaneForgingRank(), "no Arcane Forging rank below 100");
        assertEquals(0.0, repairManager.getKeepEnchantChance(), 1.0e-9);

        atRepairLevel(100); // Arcane Forging rank 1 (RetroMode)
        assertEquals(1, repairManager.getArcaneForgingRank());
        assertEquals(10.0, repairManager.getKeepEnchantChance(), 1.0e-9);
        assertEquals(75.0, repairManager.getDowngradeEnchantChance(), 1.0e-9);
    }

    @Test
    void awardRepairXpUsesTheDurabilityFractionMaterialFactorAndBase() {
        // Iron repairable: max durability 250, XP multiplier 1.0. experience.yml Repair.Base = 1000.0,
        // Repair.Iron = 2.5. Fully repaired (250 -> 0 damage): 1.0 * 1.0 * 1000 * 2.5 = 2500 XP.
        final Repairable ironPick = RepairableFactory.getRepairable("iron_pickaxe", "iron_ingot",
                null, 0, (short) 250, ItemType.TOOL, MaterialType.IRON, 1.0, 2);

        repairManager.awardRepairXp((short) 250, (short) 0, ironPick);

        verify(mmoPlayer).beginXpGain(PrimarySkillType.REPAIR, 2500.0f, XPGainReason.PVE,
                XPGainSource.SELF);
    }

    @Test
    void checkConfirmationArmsOnFirstClickThenProceedsOnSecond() {
        // Confirm_Required defaults true. A stale last-use (0) is expired, so the first click only
        // arms + returns false; recording "now" as the last use makes the next click within the 3s
        // window return true (proceed with the repair).
        repairManager.setLastAnvilUse(0);
        assertFalse(repairManager.checkConfirmation(true), "first click merely arms the repair");

        repairManager.setLastAnvilUse((int) (System.currentTimeMillis() / 1000L));
        assertTrue(repairManager.checkConfirmation(true),
                "a second click within the window proceeds");
    }

    @Test
    void anvilPlacementAndUsageStateRoundTrips() {
        assertEquals(false, repairManager.getPlacedAnvil());
        repairManager.togglePlacedAnvil();
        assertEquals(true, repairManager.getPlacedAnvil());

        repairManager.setLastAnvilUse(42);
        assertEquals(42, repairManager.getLastAnvilUse());
    }
}
