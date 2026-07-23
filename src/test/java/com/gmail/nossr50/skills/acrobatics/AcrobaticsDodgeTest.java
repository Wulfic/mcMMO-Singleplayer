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
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.subskills.acrobatics.DodgeResult;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.Misc;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    private McMMOPlayer mmoPlayer;
    private AcrobaticsManager manager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        experienceConfig = mock(ExperienceConfig.class);
        advancedConfig = mock(AdvancedConfig.class);
        player = mock(PlatformPlayer.class);

        lenient().when(advancedConfig.getDodgeDamageModifier()).thenReturn(2.0); // halves damage
        lenient().when(experienceConfig.getDodgeXPModifier()).thenReturn(120);
        lenient().when(player.getHealth()).thenReturn(20.0F);
        // Force the Dodge skill RNG to a certainty so processDodge is deterministic: a maxBonusLevel
        // of 0 short-circuits ProbabilityUtil to the ceiling, and the ceiling is 100%.
        lenient().when(advancedConfig.getMaximumProbability(SubSkillType.ACROBATICS_DODGE))
                .thenReturn(100.0);
        lenient().when(advancedConfig.getMaxBonusLevel(SubSkillType.ACROBATICS_DODGE))
                .thenReturn(0);

        McMMOMod.setExperienceConfig(experienceConfig);
        McMMOMod.setAdvancedConfig(advancedConfig);
        // Real rank plumbing, so the end-to-end processDodge cases clear canDodge()'s rank gate.
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(player);
        lenient().when(mmoPlayer.getSkillLevel(PrimarySkillType.ACROBATICS)).thenReturn(1000);
        manager = new AcrobaticsManager(mmoPlayer);
    }

    /** Seconds-granularity "now", the unit {@code McMMOPlayer#actualizeRespawnATS} stores. */
    private static int nowSeconds() {
        return (int) (System.currentTimeMillis() / 1000L);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
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

    // --- post-respawn XP grace period ---------------------------------------

    @Test
    void dodgeXpIsSuppressedImmediatelyAfterARespawn() {
        when(mmoPlayer.getRespawnATS()).thenReturn(nowSeconds());

        assertFalse(manager.isRespawnGracePeriodOver(),
                "respawning is a cheap reset for the per-mob dodge-XP tracker, so XP waits");
    }

    @Test
    void dodgeXpResumesOnceTheGracePeriodElapses() {
        when(mmoPlayer.getRespawnATS())
                .thenReturn(nowSeconds() - Misc.PLAYER_RESPAWN_COOLDOWN_SECONDS - 1);

        assertTrue(manager.isRespawnGracePeriodOver());
    }

    /**
     * The wiring case: the gate above is worthless unless {@link AcrobaticsManager#processDodge}
     * actually consults it. Runs the real entry point with the skill RNG pinned to certainty, so
     * dropping the {@code && isRespawnGracePeriodOver()} term turns this red.
     */
    @Test
    void processDodgeConsultsTheGraceGateNotJustTheAttackerEligibility() {
        when(mmoPlayer.getRespawnATS()).thenReturn(nowSeconds());
        final DodgeResult duringGrace = manager.processDodge(10.0, true);

        assertNotNull(duringGrace, "the dodge itself still fires — only the payout is gated");
        assertEquals(5.0, duringGrace.getModifiedDamage(), 1e-9, "damage is still halved");
        assertEquals(0.0F, duringGrace.getXpGain(), 1e-9,
                "an eligible attacker still pays nothing inside the post-respawn grace window");

        when(mmoPlayer.getRespawnATS())
                .thenReturn(nowSeconds() - Misc.PLAYER_RESPAWN_COOLDOWN_SECONDS - 1);
        final DodgeResult afterGrace = manager.processDodge(10.0, true);

        assertNotNull(afterGrace);
        assertEquals(1200.0F, afterGrace.getXpGain(), 1e-3, "once past the window the XP is paid");
    }

    @Test
    void theGraceBoundaryIsExactlyTheConfiguredCooldown() {
        when(mmoPlayer.getRespawnATS())
                .thenReturn(nowSeconds() - Misc.PLAYER_RESPAWN_COOLDOWN_SECONDS + 1);
        assertFalse(manager.isRespawnGracePeriodOver(), "one second short of the window");

        when(mmoPlayer.getRespawnATS())
                .thenReturn(nowSeconds() - Misc.PLAYER_RESPAWN_COOLDOWN_SECONDS);
        assertTrue(manager.isRespawnGracePeriodOver(), "exactly at the window → expired");
    }
}
