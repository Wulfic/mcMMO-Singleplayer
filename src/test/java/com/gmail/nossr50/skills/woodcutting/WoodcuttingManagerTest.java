package com.gmail.nossr50.skills.woodcutting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
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

/**
 * Proves the Woodcutting numeric core (Phase 10.6) against the real bundled configs.
 *
 * <p>{@code experience.yml} Woodcutting log XP: Oak_Log = 70, Crimson_Stem = 35, Shroomlight = 100,
 * Nether_Wart_Block = 1; the {@code TreeFellerReducedXP} exploit toggle defaults on, so per-log XP
 * drops by {@code 5 * woodCount} but never below 1. {@code general.yml} Tree Feller threshold
 * defaults to 1000.
 */
class WoodcuttingManagerTest {

    private McMMOPlayer mmoPlayer;
    private WoodcuttingManager woodcuttingManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));

        PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000e1"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        woodcuttingManager = new WoodcuttingManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
        UserManager.clearAll();
    }

    private void atWoodcuttingLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.WOODCUTTING)).thenReturn(level);
    }

    @Test
    void treeFellerThresholdReadsGeneralConfigDefault() {
        assertEquals(1000, woodcuttingManager.getTreeFellerThreshold());
    }

    @Test
    void getExperienceFromLogReadsExperienceYml() {
        assertEquals(70, WoodcuttingManager.getExperienceFromLog("Oak_Log"));
        assertEquals(35, WoodcuttingManager.getExperienceFromLog("Crimson_Stem"));
        assertEquals(100, WoodcuttingManager.getExperienceFromLog("Shroomlight"));
        assertEquals(0, WoodcuttingManager.getExperienceFromLog("Stone"),
                "a non-log block gives no Woodcutting XP");
    }

    @Test
    void treeFellerXpReducesPerLogButNeverBelowOne() {
        // Oak_Log raw = 70, reduction on by default.
        assertEquals(70, WoodcuttingManager.processTreeFellerXPGains("Oak_Log", 0),
                "first log keeps full XP");
        assertEquals(45, WoodcuttingManager.processTreeFellerXPGains("Oak_Log", 5),
                "70 - 5*5 = 45");
        assertEquals(1, WoodcuttingManager.processTreeFellerXPGains("Oak_Log", 20),
                "70 - 100 floors at 1");
    }

    @Test
    void treeFellerXpGainsZeroForNonLogs() {
        assertEquals(0, WoodcuttingManager.processTreeFellerXPGains("Stone", 0),
                "0-XP blocks stay 0 regardless of reduction");
    }

    @Test
    void bonusDropGatesFailBeforeUnlockingHarvestLumber() {
        // At level 0 the player has not reached Harvest Lumber rank 1, so both activation gates
        // deterministically short-circuit to false (before any RNG roll).
        atWoodcuttingLevel(0);
        assertFalse(woodcuttingManager.checkHarvestLumberActivation("Oak_Log"),
                "no Harvest Lumber rank yet");
        assertFalse(woodcuttingManager.checkCleanCutsActivation("Oak_Log"),
                "no Harvest Lumber rank yet");
    }

    @Test
    void harvestLumberRollIsZeroForNonBonusMaterialEvenAtMaxLevel() {
        // Stone isn't listed under Bonus_Drops.Woodcutting, so the roll's cheap config gate rejects it
        // up front — deterministically 0 even for a maxed player (and without consuming the RNG stream).
        atWoodcuttingLevel(1000);
        assertEquals(0, woodcuttingManager.rollHarvestLumberBonusDropCount("stone"),
                "a non-bonus-drop block never rolls");
    }

    @Test
    void harvestLumberRollIsZeroBeforeUnlockingHarvestLumber() {
        // Oak_Log IS a configured bonus-drop material (config gate passes), so this exercises the rank
        // gate: at level 0 the player hasn't reached Harvest Lumber rank 1, so both activation gates
        // short-circuit before any RNG and the roll is deterministically 0.
        atWoodcuttingLevel(0);
        assertEquals(0, woodcuttingManager.rollHarvestLumberBonusDropCount("oak_log"),
                "no Harvest Lumber rank yet → no bonus drops");
    }
}
