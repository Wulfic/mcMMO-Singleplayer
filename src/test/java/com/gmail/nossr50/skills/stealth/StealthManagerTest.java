package com.gmail.nossr50.skills.stealth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The MC-free half of Stealth: the sneak-XP accumulator, the rank gating, and every sub-skill's
 * scaling and clamp.
 *
 * <p>Rank plumbing is real ({@link RankConfig} loaded from the bundled {@code skillranks.yml})
 * rather than mocked into always-true, so the shipped unlock ladder is exercised as players will
 * actually meet it — a mocked gate would never catch an unlock level being authored in Standard
 * units when the config is read in RetroMode.
 */
class StealthManagerTest {

    private static final double EPSILON = 1.0E-9;

    /** The shipped sneak reference speed, restated so a retune has to come through this file. */
    private static final double SNEAK_REFERENCE = 1.295;

    /** Blocks covered in one tick at exactly the sneak reference speed. */
    private static final double PER_TICK = SNEAK_REFERENCE / 20.0;

    private AdvancedConfig advancedConfig;
    private PlatformPlayer player;
    private McMMOPlayer mmoPlayer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        advancedConfig = mock(AdvancedConfig.class);
        player = mock(PlatformPlayer.class);

        lenient().when(advancedConfig.getPadfootMaxBonusLevel()).thenReturn(1000);
        lenient().when(advancedConfig.getPadfootMaxSneakSpeedBonus()).thenReturn(0.7);
        lenient().when(advancedConfig.getAssassinMaxBonusLevel()).thenReturn(1000);
        lenient().when(advancedConfig.getAssassinMaxDamageBonus()).thenReturn(1.0);
        lenient().when(advancedConfig.getAssassinNoDamageWindowTicks()).thenReturn(100);
        lenient().when(advancedConfig.getSmokeBombDurationTicks()).thenReturn(100);

        McMMOMod.setAdvancedConfig(advancedConfig);
        McMMOMod.setExperienceConfig(mock(ExperienceConfig.class));
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));

        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(player);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
    }

    /** A manager for a player at {@code level}, with the sneak tuning pinned to the defaults. */
    private StealthManager managerAtLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.STEALTH)).thenReturn(level);
        final StealthManager manager = new StealthManager(mmoPlayer);
        manager.setXpSettings(StealthXpSettings.of(25.0, SNEAK_REFERENCE));
        return manager;
    }

    // --- onSneakTick: accumulate, flush whole XP -------------------------------------------------

    @Test
    void aSingleTickAccumulatesRatherThanPayingFractionalXp() {
        final StealthManager manager = managerAtLevel(1);
        // One tick at the reference speed is worth 25/20 = 1.25 XP, so the first tick pays 1 and
        // banks 0.25. A fraction must never reach the XP pipeline: at 20 ticks a second it would
        // churn the level-up check, the diminished-returns ledger and the profile dirty flag for
        // nothing. Stealth is standalone, so unlike Agility the gain is addressed to its own skill.
        assertEquals(1F, manager.onSneakTick(PER_TICK), EPSILON);
        verify(mmoPlayer).beginXpGain(PrimarySkillType.STEALTH, 1F, XPGainReason.PVE,
                XPGainSource.SELF);
    }

    @Test
    void theBankedRemainderIsNotLost() {
        final StealthManager manager = managerAtLevel(1);
        // 1.25/tick: pays 1 (banks .25), 1 (banks .5), 1 (banks .75), 2 (banks 0) = 5 over four
        // ticks, which is exactly 4 x 1.25 with nothing truncated away.
        float total = 0;
        for (int tick = 0; tick < 4; tick++) {
            total += manager.onSneakTick(PER_TICK);
        }
        assertEquals(5F, total, EPSILON);
    }

    @Test
    void aTinyMovementPaysNothingUntilItAddsUp() {
        final StealthManager manager = managerAtLevel(1);
        // A hundredth of a tick's travel is worth 0.0125 XP — nothing may be paid out yet, and
        // nothing may be lost either.
        assertEquals(0F, manager.onSneakTick(PER_TICK / 100.0), EPSILON);
        verify(mmoPlayer, never()).beginXpGain(any(), anyFloat(), any(), any());
    }

    @Test
    void standingStillPaysNothing() {
        final StealthManager manager = managerAtLevel(1);
        for (int tick = 0; tick < 100; tick++) {
            assertEquals(0F, manager.onSneakTick(0.0), EPSILON);
        }
        verify(mmoPlayer, never()).beginXpGain(any(), anyFloat(), any(), any());
    }

    // --- the anti-feedback-loop property --------------------------------------------------------

    @Test
    void padfootCannotRaiseItsOwnXpRate() {
        // The whole reason Stealth uses the speed-normalised model. A maxed Padfoot player sneaks
        // more than three times faster (sneaking_speed 0.3 -> 1.0), so if XP were paid per block
        // they would earn more than three times the XP per second and Padfoot would level itself.
        // Under the clamp both players earn identically per tick; the maxed one just covers more
        // ground doing it.
        final StealthManager beginner = managerAtLevel(1);
        final StealthManager maxed = managerAtLevel(1000);

        final double beginnerDistance = PER_TICK;
        final double maxedDistance = PER_TICK * (1.0 / 0.3); // same tick, 3.33x the ground covered

        assertEquals(beginner.creditedSeconds(beginnerDistance),
                maxed.creditedSeconds(maxedDistance), EPSILON);
    }

    // --- Padfoot ---------------------------------------------------------------------------------

    @Test
    void padfootUnlocksImmediatelyAndScalesToTheCap() {
        // Rank 1 at level 1 (skillranks.yml): the skill's identity should not be gated behind
        // levelling up the very thing it makes less tedious.
        assertTrue(managerAtLevel(1).canPadfoot());
        assertEquals(0.7 / 1000, managerAtLevel(1).getPadfootSpeedBonus(), EPSILON);
        assertEquals(0.35, managerAtLevel(500).getPadfootSpeedBonus(), EPSILON);
        assertEquals(0.7, managerAtLevel(1000).getPadfootSpeedBonus(), EPSILON);
    }

    @Test
    void padfootStopsScalingPastTheMaxBonusLevel() {
        assertEquals(0.7, managerAtLevel(5000).getPadfootSpeedBonus(), EPSILON);
    }

    @Test
    void padfootAtMaxLandsExactlyAtWalkingSpeed() {
        // 0.3 (the vanilla sneaking_speed default) + 0.7 = 1.0, and 1.0 is that attribute's
        // vanilla-clamped maximum, i.e. full walking speed. Pins the relationship between the
        // shipped default and the vanilla constant it was chosen against — if someone raises
        // MaxSneakSpeedBonus expecting to sneak faster than walking, vanilla silently clamps it and
        // this test is the record of why.
        assertEquals(1.0, 0.3 + managerAtLevel(1000).getPadfootSpeedBonus(), EPSILON);
    }

    // --- Assassin --------------------------------------------------------------------------------

    @Test
    void assassinIsLockedBelowItsRank() {
        // skillranks.yml puts Assassin at RetroMode 150.
        assertFalse(managerAtLevel(149).canAssassin());
        assertTrue(managerAtLevel(150).canAssassin());
    }

    @Test
    void assassinReadyTruthTable() {
        final StealthManager manager = managerAtLevel(1000);
        // Sneaking and untouched for the full window: a backstab.
        assertTrue(manager.assassinReady(true, 100));
        assertTrue(manager.assassinReady(true, Long.MAX_VALUE));
        // Exactly at the window boundary counts; one tick short does not.
        assertFalse(manager.assassinReady(true, 99));
        // Not sneaking is never a backstab, however long since you were hit.
        assertFalse(manager.assassinReady(false, Long.MAX_VALUE));
        // Just been hit: no backstab even while sneaking.
        assertFalse(manager.assassinReady(true, 0));
    }

    @Test
    void assassinReadyIsFalseWhileTheSubSkillIsLocked() {
        // The rank gate lives inside the predicate, so a low-level player cannot backstab no matter
        // how long they have gone unhit. Without this the listener would have to remember to check
        // canAssassin() separately, and one call site forgetting is a silent free damage bonus.
        assertFalse(managerAtLevel(1).assassinReady(true, Long.MAX_VALUE));
    }

    @Test
    void assassinDamageMultiplierScalesFromOne() {
        assertEquals(1.0, managerAtLevel(1).getAssassinDamageMultiplier(), 0.01);
        assertEquals(1.5, managerAtLevel(500).getAssassinDamageMultiplier(), EPSILON);
        assertEquals(2.0, managerAtLevel(1000).getAssassinDamageMultiplier(), EPSILON);
    }

    @Test
    void assassinMultiplierIsNeverBelowOne() {
        // A hostile config must be incapable of turning the backstab into a damage *penalty*, so
        // callers can apply the multiplier unconditionally on a ready gate.
        when(advancedConfig.getAssassinMaxDamageBonus()).thenReturn(-5.0);
        assertEquals(1.0, managerAtLevel(1000).getAssassinDamageMultiplier(), EPSILON);
        // ...and a locked sub-skill is a plain 1.0, not a zero that would delete the hit entirely.
        assertEquals(1.0, managerAtLevel(1).getAssassinDamageMultiplier(), 0.01);
    }

    @Test
    void aNegativeNoDamageWindowCannotInvertTheGate() {
        // A negative window would make `ticksSinceLastHit >= window` true even immediately after
        // being hit, turning "unhit for a while" into "always". Clamped at zero instead.
        when(advancedConfig.getAssassinNoDamageWindowTicks()).thenReturn(-50);
        assertEquals(0, managerAtLevel(1000).getAssassinNoDamageWindowTicks());
        assertTrue(managerAtLevel(1000).assassinReady(true, 0));
    }

    // --- Smoke Bomb ------------------------------------------------------------------------------

    @Test
    void smokeBombIsLockedBelowItsRank() {
        // skillranks.yml puts Smoke Bomb at RetroMode 250.
        assertFalse(managerAtLevel(249).canSmokeBomb());
        assertTrue(managerAtLevel(250).canSmokeBomb());
    }

    @Test
    void smokeBombDurationIsFlooredAtOneTick() {
        // A zeroed config must produce a very short ability, not one that burns a full cooldown and
        // applies a zero-duration effect — which reads to a player as "the button is broken".
        when(advancedConfig.getSmokeBombDurationTicks()).thenReturn(0);
        assertEquals(1, managerAtLevel(1000).getSmokeBombDurationTicks());
    }

    // --- budget ----------------------------------------------------------------------------------

    @Test
    void sneakingDeliberatelyBreachesTheEightyHourGuardrailAndNothingElseMay() {
        // ⚠️ This test ASSERTED hours >= 80 until GitHub #6, and it did its job: doubling the
        // baseline reddened it. The 80h floor is not gone — it is waived here, once, for this skill,
        // by an explicit ruling. Sneaking halves your speed and demands constant attention, so it is
        // priced for what it costs the player, not by a guardrail that measures distance covered.
        //
        // Total XP to RetroMode level 1000 on the shipped linear curve (base 1020, multiplier 20) is
        // 10N^2 + 1010N = 11,010,000.
        final long xpToMax = 10L * 1000 * 1000 + 1010L * 1000;
        assertEquals(11_010_000L, xpToMax);

        final double hours = xpToMax / StealthXpSettings.DEFAULT_BASELINE_XP_PER_SECOND / 3600.0;
        // A floor is still a floor, just a lower one: ~61h at the ruled 50 XP/s. This is what stops
        // the next "make it faster" from landing without a fresh ruling, which is the whole value the
        // deleted assertion had.
        assertTrue(hours >= 60.0,
                "Stealth maxes in " + Math.round(hours) + "h, under the 60h floor ruled for #6");
        assertTrue(hours <= 65.0,
                "Stealth maxes in " + Math.round(hours) + "h — the #6 ruling was ~61h, so the "
                        + "baseline has been changed without re-deriving what it buys");

        // ...and it should still out-earn Agility on land, which is the slower, more passive skill.
        assertTrue(StealthXpSettings.DEFAULT_BASELINE_XP_PER_SECOND
                        > com.gmail.nossr50.skills.movement.MovementXpSettings
                                .DEFAULT_BASELINE_XP_PER_SECOND,
                "sneaking is more tedious than sprinting and should pay more per second");
    }
}
