package com.gmail.nossr50.skills.smelting;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
