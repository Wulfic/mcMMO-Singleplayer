package com.gmail.nossr50.skills.maces;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the Maces combat cores. Crush damage is {@code 0.5 + rank * 1.0} for rank &gt; 0
 * (config-free); with RetroMode on it unlocks at level 100 (rank 1) and reaches rank 2 at level 250 in
 * {@code skillranks.yml}. Cripple ({@link MacesManager#processCripple}) rolls the rank's configured
 * chance scaled by attack strength and, on success, applies mob Slowness — the tests force the roll via
 * extreme attack-strength values and confirm the rank gate, the already-slowed skip and the §F #9
 * crash guard.
 */
class MacesManagerTest {

    private PlatformPlayer platformPlayer;
    private McMMOPlayer mmoPlayer;
    private MacesManager macesManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000b4"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        macesManager = new MacesManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private void atMacesLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.MACES)).thenReturn(level);
    }

    @Test
    void crushDamageScalesWithRank() {
        atMacesLevel(0); // rank 0 → 0
        assertEquals(0.0D, macesManager.getCrushDamage(), 1e-9, "rank 0 → 0");

        atMacesLevel(100); // rank 1 → 0.5 + 1 = 1.5
        assertEquals(1.5D, macesManager.getCrushDamage(), 1e-9, "rank 1 → 1.5");

        atMacesLevel(250); // rank 2 → 0.5 + 2 = 2.5
        assertEquals(2.5D, macesManager.getCrushDamage(), 1e-9, "rank 2 → 2.5");
    }

    @Test
    void crippleConstantsDependOnTargetType() {
        assertEquals(20, MacesManager.getCrippleTickDuration(true), "player target → 20 ticks");
        assertEquals(30, MacesManager.getCrippleTickDuration(false), "mob target → 30 ticks");
        assertEquals(1, MacesManager.getCrippleStrength(true), "player target → strength 1");
        assertEquals(2, MacesManager.getCrippleStrength(false), "mob target → strength 2");
    }

    // --- Cripple ------------------------------------------------------------
    // With RetroMode on, Cripple unlocks at level 50 (rank 1, 10% base chance) in skillranks.yml.
    // The roll is getCrippleChanceToApplyOnHit(rank) * attackStrength, and isStaticSkillRNGSuccessful
    // is deterministic at the extremes (>= 100 always succeeds, 0 never does), so an exaggerated
    // attack strength forces the outcome — the same trick the Rupture/Axes tests use.

    private static final float FORCE_PROC = 100.0F;   // 10% * 100 = 1000% → always succeeds
    private static final float FORCE_NO_PROC = 0.0F;  // 10% * 0 = 0% → never succeeds

    /**
     * §F #9 guard: below the Cripple unlock the roll must be skipped entirely. Beyond "no proc", this
     * proves the rank-0 path never reaches {@code getCrippleChanceToApplyOnHit(0)}, whose
     * {@code defaultCrippleValues[-1]} default argument throws {@link ArrayIndexOutOfBoundsException}.
     * Strip the {@code hasUnlockedSubskill} gate in the manager and this test fails with that crash.
     */
    @Test
    void crippleLockedBelowRankOneAppliesNothing() {
        atMacesLevel(49); // rank 0 — Cripple not yet unlocked
        final PlatformLivingEntity target = mock(PlatformLivingEntity.class);
        when(target.hasSlowness()).thenReturn(false);

        macesManager.processCripple(target, FORCE_PROC);

        verify(target, never()).applySlowness(anyInt(),
                anyInt());
    }

    @Test
    void crippleSkipsAnAlreadySlowedTarget() {
        atMacesLevel(50); // rank 1 — unlocked
        final PlatformLivingEntity target = mock(PlatformLivingEntity.class);
        when(target.hasSlowness()).thenReturn(true); // already crippled — don't re-apply

        macesManager.processCripple(target, FORCE_PROC);

        verify(target, never()).applySlowness(anyInt(),
                anyInt());
    }

    @Test
    void crippleAppliesMobSlownessOnASuccessfulRoll() {
        atMacesLevel(50); // rank 1 — unlocked
        final PlatformLivingEntity target = mock(PlatformLivingEntity.class);
        when(target.hasSlowness()).thenReturn(false);

        macesManager.processCripple(target, FORCE_PROC);

        // Mob target values (the player-target branch is dead in singleplayer): 30 ticks, strength 2.
        verify(target).applySlowness(30, 2);
    }

    @Test
    void crippleDoesNothingWhenTheRollFails() {
        atMacesLevel(50); // rank 1 — unlocked, but the roll is forced to fail
        final PlatformLivingEntity target = mock(PlatformLivingEntity.class);
        when(target.hasSlowness()).thenReturn(false);

        macesManager.processCripple(target, FORCE_NO_PROC);

        verify(target, never()).applySlowness(anyInt(),
                anyInt());
    }
}
