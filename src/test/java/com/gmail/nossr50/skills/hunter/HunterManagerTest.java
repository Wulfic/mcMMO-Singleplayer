package com.gmail.nossr50.skills.hunter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The MC-free half of Hunter as it stands after stage 2: the per-mob kill counters and the mastery
 * threshold arithmetic they feed.
 *
 * <p>Driven through a <b>real</b> {@link PlayerProfile} rather than a mocked one. The counters are the
 * skill's net-new persistence shape (the only open-ended key space in the profile), so a mocked
 * profile would prove the manager delegates and nothing about the thing that is actually new — the
 * cap, the dirty flag and the zero-default all live on the profile side of that call.
 */
class HunterManagerTest {

    private static final double EPSILON = 1.0E-9;

    private static final String ZOMBIE = "minecraft:zombie";
    private static final String CREEPER = "minecraft:creeper";

    private PlayerProfile profile;
    private HunterManager manager;

    @BeforeEach
    void setUp() {
        profile = new PlayerProfile("Steve", UUID.randomUUID(), 0);
        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getProfile()).thenReturn(profile);
        manager = new HunterManager(mmoPlayer);
    }

    // --- The threshold ladder -------------------------------------------------------------------

    @Test
    void masteryTierHoldsAtEveryThresholdBoundary() {
        // Asserted on BOTH sides of each threshold. A test that only checks the reached side passes
        // just as happily against a `>` mistyped as `>=` or an off-by-one table.
        assertEquals(0, manager.masteryTier(0));
        assertEquals(0, manager.masteryTier(499));
        assertEquals(1, manager.masteryTier(500));

        assertEquals(1, manager.masteryTier(2_499));
        assertEquals(2, manager.masteryTier(2_500));

        assertEquals(2, manager.masteryTier(9_999));
        assertEquals(3, manager.masteryTier(10_000));
    }

    @Test
    void masteryTierClampsAtTheTopTierAndNeverIndexesOffTheTable() {
        // A thousand times the last threshold. The failure this guards is not a wrong number, it is
        // an ArrayIndexOutOfBounds in the damage path stage 4 will hang off masteryDamageBonus.
        assertEquals(3, manager.masteryTier(10_000_000));
        assertEquals(3.0, manager.masteryDamageBonus(10_000_000), EPSILON);
    }

    @Test
    void aNegativeKillCountReadsAsNoMasteryRatherThanThrowing() {
        // Not reachable through incrementMobKills, but reachable through a hand-edited or corrupted
        // save file. Failing closed here means the worst case is "no bonus", never a negative one.
        assertEquals(0, manager.masteryTier(-1));
        assertEquals(0.0, manager.masteryDamageBonus(-500), EPSILON);
    }

    @Test
    void masteryDamageBonusIsTheRuledHalvedLadder() {
        // The 2026-07-30 ruling: +1 / +2 / +3, half the drafted +2/+4/+6. Restated as literals rather
        // than read off MASTERY_DAMAGE_BONUS, so a retune has to come through this test deliberately.
        assertEquals(0.0, manager.masteryDamageBonus(499), EPSILON);
        assertEquals(1.0, manager.masteryDamageBonus(500), EPSILON);
        assertEquals(2.0, manager.masteryDamageBonus(2_500), EPSILON);
        assertEquals(3.0, manager.masteryDamageBonus(10_000), EPSILON);
    }

    @Test
    void masteryDamageBonusNeverExceedsThreeDamage() {
        // The cap is a balance invariant, not an implementation detail: a bare fist is 1.0 base, so
        // +3.0 already makes a mastered punch 4x. At the drafted +6.0 it was a diamond sword.
        for (int kills : new int[] {0, 1, 499, 500, 2_499, 2_500, 9_999, 10_000, 1_000_000}) {
            final double bonus = manager.masteryDamageBonus(kills);
            assertTrue(bonus <= 3.0, "kills=" + kills + " paid " + bonus);
            assertTrue(bonus >= 0.0, "kills=" + kills + " paid " + bonus);
        }
    }

    @Test
    void theThresholdAndBonusTablesAreParallel() {
        // Index i of one belongs with index i of the other; a table edit that adds a threshold without
        // its bonus would otherwise fail at runtime, in the damage path, on somebody's 10,000th kill.
        assertEquals(HunterManager.MASTERY_THRESHOLDS.length,
                HunterManager.MASTERY_DAMAGE_BONUS.length);
    }

    // --- The counters ---------------------------------------------------------------------------

    @Test
    void anUnkilledMobCountsZeroRatherThanGoingMissing() {
        // The failure this pins is the one that bit Husbandry twice and Fishing once: an unlisted key
        // resolving to nothing. Here "nothing" must be 0, not null and not an exception.
        assertEquals(0, manager.getKills(ZOMBIE));
        assertEquals(0, manager.masteryTierAgainst(ZOMBIE));
        assertEquals(0.0, manager.masteryDamageBonusAgainst("minecraft:not_a_real_mob"), EPSILON);
    }

    @Test
    void killsAreCountedPerMobTypeAndNotPooled() {
        // This is the horizontal axis' entire premise, so it gets its own test: mastering zombies must
        // do nothing whatsoever to your creeper damage.
        for (int i = 0; i < 500; i++) {
            manager.recordKill(ZOMBIE);
        }
        manager.recordKill(CREEPER);

        assertEquals(500, manager.getKills(ZOMBIE));
        assertEquals(1, manager.getKills(CREEPER));
        assertEquals(1.0, manager.masteryDamageBonusAgainst(ZOMBIE), EPSILON);
        assertEquals(0.0, manager.masteryDamageBonusAgainst(CREEPER), EPSILON);
    }

    @Test
    void recordKillReturnsTheRunningTotal() {
        assertEquals(1, manager.recordKill(ZOMBIE));
        assertEquals(2, manager.recordKill(ZOMBIE));
        assertEquals(3, manager.recordKill(ZOMBIE));
    }

    // The "every kill dirties the profile" half of D-HU2 is pinned where it is observable rather than
    // by widening PlayerProfile's API for a flag getter: FlatFileProfileStoreTest
    // #aCountedKillAloneIsEnoughToMakeTheProfileSave drives a real save through a real store, which is
    // the property that actually matters (the kill survives a restart) rather than the flag behind it.

    @Test
    void theKillMapIsUnmodifiableFromOutside() {
        manager.recordKill(ZOMBIE);
        // A counter anything can rewrite is a counter that can move without dirtying the profile.
        assertThrows(UnsupportedOperationException.class, () -> manager.getAllKills().put(CREEPER, 9));
    }

    @Test
    void theMobTypeCapRefusesNewTypesButKeepsCountingKnownOnes() {
        // Vanilla has fewer than a hundred mobs, so this only binds on a heavily modded world -- which
        // is exactly when an unbounded disk-backed map stops being a feature.
        for (int i = 0; i < PlayerProfile.MAX_TRACKED_MOB_TYPES; i++) {
            manager.recordKill("test:mob_" + i);
        }
        assertEquals(PlayerProfile.MAX_TRACKED_MOB_TYPES, manager.getAllKills().size());

        assertEquals(0, manager.recordKill("test:one_too_many"));
        assertEquals(PlayerProfile.MAX_TRACKED_MOB_TYPES, manager.getAllKills().size());

        // The cap must not freeze the counters that already exist, or a modded world would stop the
        // skill dead rather than merely stop widening it.
        assertEquals(2, manager.recordKill("test:mob_0"));
    }

    // --- Threshold crossings (stage 3's notification trigger) -----------------------------------

    @Test
    void crossingAThresholdIsDetectedFromTheTierChangeNotTheExactCount() {
        assertTrue(manager.crossedMasteryThreshold(499, 500));
        assertFalse(manager.crossedMasteryThreshold(500, 501));
        assertFalse(manager.crossedMasteryThreshold(498, 499));
        assertTrue(manager.crossedMasteryThreshold(2_499, 2_500));

        // A bulk jump that skips a threshold entirely still counts as a crossing. Nothing does this
        // today; a command or a data fix would, and swallowing it silently is the failure mode.
        assertTrue(manager.crossedMasteryThreshold(0, 3_000));
        assertFalse(manager.crossedMasteryThreshold(10_000, 20_000));
    }
}
