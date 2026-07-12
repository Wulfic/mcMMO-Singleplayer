package com.gmail.nossr50.skills.alchemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.alchemy.PotionStage;
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
 * Proves the Alchemy numeric cores (Phase 10.3) against the real bundled configs.
 *
 * <p>RetroMode is on by default: Catalysis unlocks at level 0, Concoctions rank 1 at level 0.
 * advanced.yml Catalysis: MinSpeed 1.0, MaxSpeed 4.0, MaxBonusLevel(RetroMode) 1000. The Lucky
 * modifier is 4/3.
 */
class AlchemyManagerTest {

    private McMMOPlayer mmoPlayer;
    private AlchemyManager alchemyManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));

        PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000d1"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        alchemyManager = new AlchemyManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
        UserManager.clearAll();
    }

    private void atAlchemyLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.ALCHEMY)).thenReturn(level);
    }

    @Test
    void brewSpeedIsMinSpeedAtLevelZeroAndMaxSpeedAtMaxBonusLevel() {
        atAlchemyLevel(0);
        assertEquals(1.0, alchemyManager.calculateBrewSpeed(false), 1.0e-9, "MinSpeed at level 0");
        atAlchemyLevel(1000);
        assertEquals(4.0, alchemyManager.calculateBrewSpeed(false), 1.0e-9,
                "MaxSpeed at the max-bonus level");
    }

    @Test
    void brewSpeedScalesLinearlyBetweenMinAndMax() {
        atAlchemyLevel(500); // halfway -> 1.0 + (4.0-1.0)*500/1000 = 2.5
        assertEquals(2.5, alchemyManager.calculateBrewSpeed(false), 1.0e-9);
    }

    @Test
    void luckyModifierMultipliesTheResultByFourThirds() {
        atAlchemyLevel(500);
        assertEquals(2.5 * (4.0 / 3.0), alchemyManager.calculateBrewSpeed(true), 1.0e-9);
    }

    @Test
    void tierReflectsConcoctionsRank() {
        atAlchemyLevel(0);
        // Concoctions Rank_1 unlocks at level 0, so tier is at least 1 immediately
        assertTrue(alchemyManager.getTier() >= 1, "Concoctions rank 1 unlocked at level 0");
    }

    @Test
    void handlePotionBrewSuccessesGainsPerStageXpTimesAmount() {
        // experience.yml Stage_2 = 1111; brewing 2 potions -> 2222 XP, PVE/PASSIVE (legacy source).
        alchemyManager.handlePotionBrewSuccesses(PotionStage.TWO, 2);
        verify(mmoPlayer).beginXpGain(PrimarySkillType.ALCHEMY, 2222f, XPGainReason.PVE,
                XPGainSource.PASSIVE);
    }
}
