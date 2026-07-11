package com.gmail.nossr50.skills.acrobatics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.subskills.acrobatics.DodgeResult;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the deterministic half of the Acrobatics Dodge port (K1) — {@link
 * AcrobaticsManager#dodgeCheck}, with the RNG outcome and the attacker's XP-eligibility injected so the
 * damage-reduction, XP, fatal, and floor branches are all provable off a mocked {@link PlatformPlayer}.
 * The RNG roll and the per-mob anti-farm cap live one layer up ({@link AcrobaticsManager#processDodge}
 * and the listener) and are verified in-game.
 */
class AcrobaticsDodgeTest {

    private ExperienceConfig experienceConfig;
    private AdvancedConfig advancedConfig;
    private PlatformPlayer player;
    private AcrobaticsManager manager;

    @BeforeEach
    void setUp() {
        experienceConfig = mock(ExperienceConfig.class);
        advancedConfig = mock(AdvancedConfig.class);
        player = mock(PlatformPlayer.class);

        lenient().when(advancedConfig.getDodgeDamageModifier()).thenReturn(2.0); // halves damage
        lenient().when(experienceConfig.getDodgeXPModifier()).thenReturn(120);
        lenient().when(player.getHealth()).thenReturn(20.0F);

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
    void successfulDodgeAgainstEligibleAttackerHalvesDamageAndAwardsXp() {
        final DodgeResult result = manager.dodgeCheck(10.0, true, true);

        assertNotNull(result);
        assertEquals(5.0, result.getModifiedDamage(), 1e-9, "10 / 2.0 = 5");
        // baseDamage(10) * dodgeXPModifier(120) = 1200.
        assertEquals(1200.0F, result.getXpGain(), 1e-3);
    }

    @Test
    void dodgeAgainstIneligibleAttackerStillReducesDamageButPaysNoXp() {
        final DodgeResult result = manager.dodgeCheck(10.0, true, false);

        assertNotNull(result);
        assertEquals(5.0, result.getModifiedDamage(), 1e-9);
        assertEquals(0.0F, result.getXpGain(), 1e-9, "ineligible attacker → no XP, damage still cut");
    }

    @Test
    void failedRollDoesNotDodge() {
        assertNull(manager.dodgeCheck(10.0, false, true), "RNG failure → no dodge");
    }

    @Test
    void dodgeThatWouldStillLeaveAFatalHitIsSuppressed() {
        when(player.getHealth()).thenReturn(3.0F);

        // 10 / 2.0 = 5 reduced damage still kills a 3-HP player, so mcMMO must not soften it.
        assertNull(manager.dodgeCheck(10.0, true, true));
    }

    @Test
    void reducedDamageIsFlooredAtOne() {
        final DodgeResult result = manager.dodgeCheck(1.5, true, false);

        assertNotNull(result);
        assertEquals(1.0, result.getModifiedDamage(), 1e-9, "max(1.5 / 2.0, 1.0) = 1.0");
    }
}
