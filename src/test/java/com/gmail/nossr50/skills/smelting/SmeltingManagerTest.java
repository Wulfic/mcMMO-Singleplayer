package com.gmail.nossr50.skills.smelting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;

/**
 * Proves the Smelting rank-multiplier cores (Phase 10.3) against the real bundled configs. Fuel
 * Efficiency multiplies burn time by {1,2,3,4} for ranks {0,1,2,3}; Understanding the Art multiplies
 * vanilla XP by {@code max(1, rank)}. With RetroMode on, Fuel Efficiency unlocks at level 100
 * (rank 1 → 500 → 750) and Understanding the Art at level 100 (rank 1 → 250 → 350 → 500).
 */
class SmeltingManagerTest {

    private PlatformPlayer platformPlayer;
    private McMMOPlayer mmoPlayer;
    private SmeltingManager smeltingManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000c2"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        smeltingManager = new SmeltingManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
        UserManager.clearAll();
    }

    private void atSmeltingLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.SMELTING)).thenReturn(level);
    }

    @Test
    void fuelEfficiencyMultipliesBurnTimeByRankTier() {
        atSmeltingLevel(0); // rank 0 → x1
        assertEquals(1, smeltingManager.getFuelEfficiencyMultiplier(), "rank 0 → x1");
        assertEquals(100, smeltingManager.fuelEfficiency(100), "rank 0 → burn time unchanged");

        atSmeltingLevel(100); // rank 1 → x2
        assertEquals(2, smeltingManager.getFuelEfficiencyMultiplier(), "rank 1 → x2");
        assertEquals(200, smeltingManager.fuelEfficiency(100), "rank 1 → doubled");

        atSmeltingLevel(500); // rank 2 → x3
        assertEquals(300, smeltingManager.fuelEfficiency(100), "rank 2 → tripled");
    }

    @Test
    void fuelEfficiencyIsClampedAndZeroSafe() {
        atSmeltingLevel(750); // rank 3 → x4
        assertEquals(0, smeltingManager.fuelEfficiency(0), "non-positive burn time → 0");
        assertEquals(Short.MAX_VALUE, smeltingManager.fuelEfficiency(Short.MAX_VALUE),
                "clamped to Short.MAX_VALUE");
    }

    @Test
    void vanillaXpBoostUsesMaxOneOrRank() {
        atSmeltingLevel(0); // rank 0 → max(1,0) = 1
        assertEquals(100, smeltingManager.vanillaXPBoost(100), "rank 0 → x1");

        atSmeltingLevel(250); // rank 2 → x2
        assertEquals(200, smeltingManager.vanillaXPBoost(100), "rank 2 → x2");
    }

    @Test
    void vanillaXpBoostMultiplierIsOneUntilTheFirstRankIsWorthIt() {
        // What the furnace-extract hook actually consumes. It has to be exactly 1 below rank 2,
        // because 1 is the "leave vanilla alone" signal the orb hook checks for: max(1, rank) makes
        // rank 0 and rank 1 both x1, so nothing is boosted until Understanding the Art rank 2.
        atSmeltingLevel(0);
        assertEquals(1, smeltingManager.getVanillaXpBoostMultiplier(), "rank 0 → vanilla XP");

        atSmeltingLevel(100); // RetroMode rank 1 → max(1,1) = 1
        assertEquals(1, smeltingManager.getVanillaXpBoostMultiplier(), "rank 1 is still x1");

        atSmeltingLevel(250); // RetroMode rank 2 → x2
        assertEquals(2, smeltingManager.getVanillaXpBoostMultiplier(), "rank 2 → x2");
    }

    @Test
    void awardSmeltingXpLooksUpPerMaterialXpAndGainsIt() {
        // experience.yml default: Experience_Values.Smelting.Iron_Ore = 25.
        smeltingManager.awardSmeltingXP("Iron_Ore");
        verify(mmoPlayer).beginXpGain(PrimarySkillType.SMELTING, 25f, XPGainReason.PVE,
                XPGainSource.SELF);
    }

    @Test
    void awardSmeltingXpIsNoOpForMaterialWithNoConfiguredXp() {
        // A non-ore (e.g. cooking food) has no Smelting entry → resolves to 0 XP → no gain.
        smeltingManager.awardSmeltingXP("Raw_Beef");
        verify(mmoPlayer, never()).beginXpGain(ArgumentMatchers.any(), ArgumentMatchers.anyFloat(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    void isSmeltableTracksWhetherTheInputCarriesSmeltingXp() {
        // Legacy ItemUtils.isSmeltable was literally "Smelting.getSmeltXP(item) >= 1". This is the
        // gate that keeps Fuel Efficiency off a furnace that is only cooking food.
        assertTrue(SmeltingManager.isSmeltable("Iron_Ore"), "ore carries Smelting XP");
        assertTrue(SmeltingManager.isSmeltable("Ancient_Debris"), "ore carries Smelting XP");
        assertFalse(SmeltingManager.isSmeltable("Raw_Beef"), "food carries none");
        assertFalse(SmeltingManager.isSmeltable("Iron_Ingot"),
                "the smelt RESULT is not itself smeltable — the gate reads the input slot");
    }

    @Test
    void hasRoomForSecondSmeltMatchesTheLegacyStackBound() {
        // Legacy tested the PRE-merge count against maxStackSize - 2; our seam is post-merge, where
        // the count is one higher, so the same test is "count < maxCount".
        assertTrue(SmeltingManager.hasRoomForSecondSmelt(1, 64), "fresh single result → room");
        assertTrue(SmeltingManager.hasRoomForSecondSmelt(63, 64),
                "legacy's boundary: 62 pre-merge ≤ 64-2 → still allowed");
        assertFalse(SmeltingManager.hasRoomForSecondSmelt(64, 64),
                "a full stack must not overflow");
        assertFalse(SmeltingManager.hasRoomForSecondSmelt(1, 1),
                "an unstackable result can never take a second copy");
    }

    @Test
    void secondSmeltCannotTriggerBelowTheBonusCurve() {
        atSmeltingLevel(0); // 0% chance at level 0 (SecondSmelt ChanceMax 50 @ MaxBonusLevel 1000).
        assertFalse(smeltingManager.canSecondSmelt("Iron_Ingot"), "no roll can succeed at level 0");
    }

    @Test
    void secondSmeltRequiresTheRESULTToBeBonusDropEnabled() {
        // config.yml keys Bonus_Drops.Smelting by the smelt RESULT (Iron_Ingot), not the ore that
        // went in. Cooked_Beef has no entry, so the config gate must veto it outright — while the
        // enabled material still wins sometimes at the top of the bonus curve (50%).
        atSmeltingLevel(1000); // RetroMode MaxBonusLevel → ChanceMax 50%.

        int enabledWins = 0;
        int disabledWins = 0;
        for (int i = 0; i < 200; i++) {
            if (smeltingManager.canSecondSmelt("Iron_Ingot")) {
                enabledWins++;
            }
            if (smeltingManager.canSecondSmelt("Cooked_Beef")) {
                disabledWins++;
            }
        }

        assertTrue(enabledWins > 0, "a bonus-drop-enabled result should win some of 200 rolls at 50%");
        assertEquals(0, disabledWins, "a result with no Bonus_Drops.Smelting entry never doubles");
    }

    @Test
    void boostFuelTimeAppliesTheFuelEfficiencyTier() {
        atSmeltingLevel(0);
        assertEquals(1600, smeltingManager.boostFuelTime(1600), "rank 0 → vanilla burn time");

        atSmeltingLevel(100); // rank 1 → x2
        assertEquals(3200, smeltingManager.boostFuelTime(1600), "rank 1 → doubled");
    }
}
