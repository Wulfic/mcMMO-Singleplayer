package com.gmail.nossr50.skills.swords;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.runnables.skills.RuptureTask;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the Swords Stab damage path (Phase 10.3) against the real bundled configs. Stab damage is
 * {@code Base_Damage(1.0) + rank * Per_Rank_Multiplier(1.5)} for rank &gt; 0. With RetroMode on, Stab
 * unlocks at level 750 (rank 1) and reaches rank 2 at level 1000 in {@code skillranks.yml}.
 */
class SwordsManagerTest {

    private PlatformPlayer platformPlayer;
    private McMMOPlayer mmoPlayer;
    private SwordsManager swordsManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        swordsManager = new SwordsManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
        // Rupture parks tasks on the store and schedules them; drop both so cases stay independent.
        MetadataStore.clearAll();
        McMMOMod.getScheduler().cancelAll();
    }

    private void atSwordsLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.SWORDS)).thenReturn(level);
    }

    /**
     * Rupture's odds are {@code Chance_To_Apply_On_Hit.Rank_N * attackStrengthScale}, so an
     * exaggerated scale drives the probability past 100% and makes the roll deterministic
     * ({@code Probability.isSuccessfulRoll} is {@code value >= nextDouble(1.0)}). Rank 1 is 15%.
     */
    private static final double ALWAYS_RUPTURES = 10.0D;

    private PlatformLivingEntity mockTarget(UUID id) {
        final PlatformLivingEntity target = mock(PlatformLivingEntity.class);
        when(target.getUniqueId()).thenReturn(id);
        return target;
    }

    private static RuptureTask activeRuptureOn(UUID id) {
        return MetadataStore.get(id, RuptureTask.RUPTURE_KEY, RuptureTask.class);
    }

    @Test
    void ruptureLockedBeforeRankOne() {
        atSwordsLevel(0); // RetroMode skillranks.yml puts Rupture Rank_1 at level 1
        final UUID targetId = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

        assertFalse(swordsManager.canUseRupture(), "level 0 → Rupture locked");
        swordsManager.processRupture(mockTarget(targetId), ALWAYS_RUPTURES);

        assertNull(activeRuptureOn(targetId), "a locked Rupture must not start a bleed");
    }

    @Test
    void ruptureMarksTheTargetOnASuccessfulRoll() {
        atSwordsLevel(1); // rank 1
        final UUID targetId = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

        swordsManager.processRupture(mockTarget(targetId), ALWAYS_RUPTURES);

        assertNotNull(activeRuptureOn(targetId), "a successful roll parks a bleed on the target");
    }

    @Test
    void ruptureNeverAppliesOnAZeroStrengthHit() {
        atSwordsLevel(1);
        final UUID targetId = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

        // A swing with no attack-cooldown charge scales the odds to 0%.
        swordsManager.processRupture(mockTarget(targetId), 0.0D);

        assertNull(activeRuptureOn(targetId), "a 0% roll must not start a bleed");
    }

    @Test
    void ruptureRefreshesAnExistingBleedInsteadOfStacking() {
        atSwordsLevel(1);
        final UUID targetId = UUID.fromString("00000000-0000-0000-0000-0000000000c4");
        final RuptureTask existing = mock(RuptureTask.class);
        MetadataStore.set(targetId, RuptureTask.RUPTURE_KEY, existing);

        swordsManager.processRupture(mockTarget(targetId), ALWAYS_RUPTURES);

        verify(existing).refreshRupture();
        assertSame(existing, activeRuptureOn(targetId),
                "an already-bleeding target keeps its original task rather than stacking a second");
    }

    @Test
    void stabLockedBelowRankOne() {
        atSwordsLevel(749);
        assertFalse(swordsManager.canUseStab(), "level 749 → Stab locked");
        assertEquals(0.0D, swordsManager.getStabDamage(), 1e-9, "rank 0 → no Stab damage");
    }

    @Test
    void stabDamageScalesWithRank() {
        atSwordsLevel(750); // rank 1 → 1.0 + 1 * 1.5 = 2.5
        assertTrue(swordsManager.canUseStab(), "level 750 → Stab unlocked");
        assertEquals(2.5D, swordsManager.getStabDamage(), 1e-9, "rank 1 → 2.5");

        atSwordsLevel(1000); // rank 2 → 1.0 + 2 * 1.5 = 4.0
        assertEquals(4.0D, swordsManager.getStabDamage(), 1e-9, "rank 2 → 4.0");
    }

    /**
     * Serrated Strikes' AoE damage is {@code damage / DamageModifier(4.0)}, and — unlike Skull
     * Splitter's — is deliberately <em>not</em> scaled by attack strength, so a half-charged swing
     * spreads the same fraction. Pinned here because that asymmetry looks like an omission.
     */
    @Test
    void serratedStrikesDamageIsAQuarterAndIgnoresAttackStrength() {
        when(mmoPlayer.getAttackStrength()).thenReturn(0.5F);
        assertEquals(3.0D, swordsManager.serratedStrikesDamage(12.0D), 1e-9,
                "damage / 4.0, regardless of attack strength");

        when(mmoPlayer.getAttackStrength()).thenReturn(1.0F);
        assertEquals(3.0D, swordsManager.serratedStrikesDamage(12.0D), 1e-9,
                "a fully-charged swing spreads the same fraction");
    }

    /**
     * Counter Attack's reflected damage is {@code damage / DamageModifier(2.0)}. Like Serrated
     * Strikes — and unlike Rupture — it ignores attack strength: the counter is a reaction to being
     * hit, not a swing of the player's own, so there is no cooldown charge to scale by.
     */
    @Test
    void counterAttackDamageIsHalfAndIgnoresAttackStrength() {
        when(mmoPlayer.getAttackStrength()).thenReturn(0.25F);
        assertEquals(6.0D, swordsManager.counterAttackDamage(12.0D), 1e-9,
                "damage / 2.0, regardless of attack strength");

        when(mmoPlayer.getAttackStrength()).thenReturn(1.0F);
        assertEquals(6.0D, swordsManager.counterAttackDamage(12.0D), 1e-9,
                "a fully-charged player reflects the same fraction");
    }

    @Test
    void counterAttackGateNeedsUnlock() {
        atSwordsLevel(199); // one short of the RetroMode Rank_1 unlock
        assertFalse(swordsManager.canUseCounterAttack(), "locked below rank 1");

        atSwordsLevel(200); // CounterAttack Rank_1
        assertTrue(swordsManager.canUseCounterAttack(), "level 200 → Counter Attack unlocked");
    }

    @Test
    void serratedStrikeGateNeedsUnlockAndActiveAbility() {
        when(mmoPlayer.getAbilityMode(SuperAbilityType.SERRATED_STRIKES)).thenReturn(true);

        atSwordsLevel(49); // one short of the RetroMode Rank_1 unlock
        assertFalse(swordsManager.canUseSerratedStrike(), "locked below rank 1");

        atSwordsLevel(50); // Serrated Strikes Rank_1
        assertTrue(swordsManager.canUseSerratedStrike(), "unlocked + ability active → fires");

        when(mmoPlayer.getAbilityMode(SuperAbilityType.SERRATED_STRIKES)).thenReturn(false);
        assertFalse(swordsManager.canUseSerratedStrike(), "unlocked but ability not active");
    }
}
