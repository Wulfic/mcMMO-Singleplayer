package com.gmail.nossr50.skills.spears;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
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
 * Proves the Spears combat cores. Spear Mastery bonus is
 * {@code rank * Rank_Damage_Multiplier(0.4)}; Momentum duration is {@code 20 * rank * 2}. With
 * RetroMode on, Spear Mastery unlocks at level 50 (rank 1) and reaches rank 2 at level 150 in
 * {@code skillranks.yml}.
 *
 * <p>Momentum ({@link SpearsManager#processMomentum}) rolls the rank's configured chance scaled by
 * attack strength and, on success, gives the <em>attacker</em> Speed — the tests force the roll via
 * extreme attack-strength values and confirm the rank gate and the rank-0 crash guard. Both
 * sub-skills only reached a real swing in GitHub #7: until then
 * {@code EntityDamageListener#classifyMainHand} had no spear arm at all, so nothing in this class
 * was ever called in game.
 */
class SpearsManagerTest {

    private PlatformPlayer platformPlayer;
    private McMMOPlayer mmoPlayer;
    private SpearsManager spearsManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000b5"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        spearsManager = new SpearsManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private void atSpearsLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.SPEARS)).thenReturn(level);
    }

    @Test
    void spearMasteryBonusScalesWithRank() {
        atSpearsLevel(0); // rank 0 → 0
        assertEquals(0.0D, spearsManager.getSpearMasteryBonusDamage(), 1e-9, "rank 0 → 0");

        atSpearsLevel(50); // rank 1 → 1 * 0.4
        assertEquals(0.4D, spearsManager.getSpearMasteryBonusDamage(), 1e-9, "rank 1 → 0.4");

        atSpearsLevel(150); // rank 2 → 2 * 0.4
        assertEquals(0.8D, spearsManager.getSpearMasteryBonusDamage(), 1e-9, "rank 2 → 0.8");
    }

    @Test
    void momentumConstants() {
        assertEquals(40, SpearsManager.getMomentumTickDuration(1), "rank 1 → 20 * (1*2) = 40 ticks");
        assertEquals(80, SpearsManager.getMomentumTickDuration(2), "rank 2 → 20 * (2*2) = 80 ticks");
        assertEquals(2, SpearsManager.getMomentumStrength(), "Momentum strength is fixed at 2");
    }

    // --- Spear Mastery gate -------------------------------------------------

    @Test
    void spearMasteryGateFollowsTheRank() {
        atSpearsLevel(49); // rank 0 — not yet unlocked
        assertFalse(spearsManager.canUseSpearMastery(), "rank 0 → no Spear Mastery bonus");

        atSpearsLevel(50); // rank 1 — unlocked
        assertTrue(spearsManager.canUseSpearMastery(), "rank 1 → Spear Mastery applies");
    }

    // --- Momentum -----------------------------------------------------------
    // With RetroMode on, Momentum unlocks at level 1 (rank 1, 5% base chance) and reaches rank 2 at
    // level 100 in skillranks.yml. The roll is getMomentumChanceToApplyOnHit(rank) * attackStrength,
    // and isStaticSkillRNGSuccessful is deterministic at the extremes (>= 100 always succeeds, 0
    // never does), so an exaggerated attack strength forces the outcome — the same trick the
    // Cripple/Rupture/Axes tests use.

    private static final float FORCE_PROC = 100.0F;   // 5% * 100 = 500% → always succeeds
    private static final float FORCE_NO_PROC = 0.0F;  // 5% * 0 = 0% → never succeeds

    /**
     * The rank-0 crash guard (CONVERSION_TODO §F #9, Cripple's landmine repeated in Spears). Beyond
     * "no proc", this proves the rank-0 path never reaches
     * {@code AdvancedConfig#getMomentumChanceToApplyOnHit(0)}, whose {@code defaultMomentumValues[-1]}
     * default argument is evaluated eagerly and throws {@link ArrayIndexOutOfBoundsException}. Strip
     * the {@code hasUnlockedSubskill} gate from the manager and this fails with that crash, not with
     * an assertion.
     */
    @Test
    void momentumLockedBelowRankOneAppliesNothing() {
        atSpearsLevel(0); // rank 0 — Momentum not yet unlocked

        spearsManager.processMomentum(FORCE_PROC);

        verify(platformPlayer, never()).applySpeed(anyInt(), anyInt());
    }

    @Test
    void momentumAppliesSpeedOnASuccessfulRoll() {
        atSpearsLevel(1);  // rank 1 — unlocked
        when(platformPlayer.applySpeed(anyInt(), anyInt())).thenReturn(true);

        spearsManager.processMomentum(FORCE_PROC);

        // Rank 1: 20 * (1*2) = 40 ticks, at the fixed strength 2.
        verify(platformPlayer).applySpeed(40, 2);
    }

    @Test
    void momentumDurationScalesWithRank() {
        atSpearsLevel(100); // rank 2
        when(platformPlayer.applySpeed(anyInt(), anyInt())).thenReturn(true);

        spearsManager.processMomentum(FORCE_PROC);

        verify(platformPlayer).applySpeed(80, 2);
    }

    @Test
    void momentumDoesNothingOnAFailedRoll() {
        atSpearsLevel(1); // rank 1 — unlocked, but the roll cannot succeed

        spearsManager.processMomentum(FORCE_NO_PROC);

        verify(platformPlayer, never()).applySpeed(anyInt(), anyInt());
    }

}
