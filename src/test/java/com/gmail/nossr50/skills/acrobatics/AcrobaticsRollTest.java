package com.gmail.nossr50.skills.acrobatics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.subskills.acrobatics.RollResult;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the deterministic half of the Acrobatics Roll port (K2) — the parts that do not roll the
 * skill RNG (that lives in {@link AcrobaticsManager#processFallDamage} and is verified in-game). The
 * RNG outcome is injected into {@link AcrobaticsManager#rollCheck} so the damage-reduction, XP, fatal,
 * and exploit branches are all provable off a mocked {@link PlatformPlayer}.
 */
class AcrobaticsRollTest {

    private ExperienceConfig experienceConfig;
    private AdvancedConfig advancedConfig;
    private PlatformPlayer player;
    private AcrobaticsManager manager;

    @BeforeEach
    void setUp() {
        experienceConfig = mock(ExperienceConfig.class);
        advancedConfig = mock(AdvancedConfig.class);
        player = mock(PlatformPlayer.class);

        // Default: exploit prevention off (so canGainRollXP never throttles) and no exploiting state.
        lenient().when(experienceConfig.isAcrobaticsExploitingPrevented()).thenReturn(false);
        lenient().when(experienceConfig.getRollXPModifier()).thenReturn(80);
        lenient().when(experienceConfig.getFallXPModifier()).thenReturn(120);
        lenient().when(experienceConfig.getFeatherFallXPModifier()).thenReturn(2.0);
        lenient().when(advancedConfig.getRollDamageThreshold()).thenReturn(7.0); // threshold * 2 = 14
        lenient().when(player.hasFeatherFallingBoots()).thenReturn(false);
        lenient().when(player.hasEnderPearlInEitherHand()).thenReturn(false);
        lenient().when(player.isInsideVehicle()).thenReturn(false);
        lenient().when(player.getFeetBlockKey()).thenReturn(1234L);
        lenient().when(player.isSneaking()).thenReturn(false);

        McMMOMod.setExperienceConfig(experienceConfig);
        McMMOMod.setAdvancedConfig(advancedConfig);

        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(player);
        manager = new AcrobaticsManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setAdvancedConfig(null);
    }

    @Test
    void successfulRollReducesDamageByThresholdAndAwardsRollXp() {
        when(player.getHealth()).thenReturn(20.0F);

        final RollResult result = manager.rollCheck(20.0, false, true);

        assertNotNull(result);
        assertTrue(result.isRollSuccess(), "RNG success + survivable → roll succeeds");
        assertEquals(6.0, result.getModifiedDamage(), 1e-9, "20 - (7*2) = 6");
        assertFalse(result.isExploiting());
        // min(20, damage=20) * rollXPModifier(80) = 1600, truncated to int.
        assertEquals(1600.0F, result.getXpGain(), 1e-3);
        assertFalse(result.isGraceful());
    }

    @Test
    void survivedFallWithoutRollStillAwardsFallXp() {
        when(player.getHealth()).thenReturn(20.0F);

        // rngSuccess=false → the roll proc fails, but the (non-fatal) fall still yields fall XP.
        final RollResult result = manager.rollCheck(10.0, false, false);

        assertNotNull(result);
        assertFalse(result.isRollSuccess());
        assertEquals(0.0, result.getModifiedDamage(), 1e-9, "10 - 14 floored to 0");
        // min(20, 10) * fallXPModifier(120) = 1200.
        assertEquals(1200.0F, result.getXpGain(), 1e-3);
    }

    @Test
    void fatalFallReturnsNullSoMcmmoDoesNotInterfere() {
        when(player.getHealth()).thenReturn(5.0F);

        assertNull(manager.rollCheck(20.0, false, true),
                "both modified (6) and base (20) damage kill a 5-HP player → null");
    }

    @Test
    void gracefulFlagIsCarriedThrough() {
        when(player.getHealth()).thenReturn(20.0F);

        final RollResult result = manager.rollCheck(20.0, true, true);

        assertNotNull(result);
        assertTrue(result.isGraceful());
    }

    @Test
    void exploitingPlayerRollsButEarnsNoXp() {
        when(experienceConfig.isAcrobaticsExploitingPrevented()).thenReturn(true);
        when(player.hasEnderPearlInEitherHand()).thenReturn(true); // classic fall-XP farm
        when(player.getHealth()).thenReturn(20.0F);

        final RollResult result = manager.rollCheck(20.0, false, true);

        assertNotNull(result);
        assertTrue(result.isRollSuccess(), "damage reduction still applies");
        assertTrue(result.isExploiting());
        assertEquals(0.0F, result.getXpGain(), 1e-9, "exploiting suppresses the XP award");
    }

    @Test
    void featherFallingBootsMultiplyRollXp() {
        when(player.hasFeatherFallingBoots()).thenReturn(true);

        // min(20, 20) * rollXPModifier(80) * featherFall(2.0) = 3200.
        assertEquals(3200.0F, manager.calculateRollXP(20.0, true), 1e-3);
    }

    @Test
    void rollXpClampsDamageAtTwenty() {
        // Damage above 20 is clamped, so a 1000-damage fall pays the same as a 20-damage one.
        assertEquals(1600.0F, manager.calculateRollXP(1000.0, true), 1e-3);
    }
}
