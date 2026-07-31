package com.gmail.nossr50.skills.hunter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    private McMMOPlayer mmoPlayer;
    private HunterManager manager;
    private Path dataFolder;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        this.dataFolder = dataFolder;
        profile = new PlayerProfile("Steve", UUID.randomUUID(), 0);
        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getProfile()).thenReturn(profile);
        // Trophy Hunter's rank gate reads the REAL bundled skillranks.yml, and RankConfig resolves
        // Standard vs RetroMode through the general config -- so both have to be wired for the rank
        // ladder to mean anything. RetroMode is the shipped default, hence the 100/300/600/900
        // unlock levels the tests below assert against.
        final GeneralConfig generalConfig = mock(GeneralConfig.class);
        lenient().when(generalConfig.getIsRetroMode()).thenReturn(true);
        McMMOMod.setGeneralConfig(generalConfig);
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        // A fully charged swing. ⚠️ Stubbed rather than left at Mockito's 0.0F: unstubbed, every
        // melee bonus below would scale to nothing and the tests would pass for the wrong reason.
        lenient().when(mmoPlayer.getAttackStrength()).thenReturn(1.0F);
        manager = new HunterManager(mmoPlayer);
        // ⚠️ Cleared on BOTH sides, not just after. McMMOMod's config holders are process-wide
        // statics on a JVM JUnit reuses across classes, so a mocked AdvancedConfig left behind by
        // some other test would answer 0.0 for the ranged multiplier and redden the asymmetry test
        // depending only on execution order. The same applies to the experience config, which
        // xpForTier reads: a mock left behind by another class answers 0.0F for every tier.
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
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

    // --- Per-hit delivery (stage 4) --------------------------------------------------------------

    @Test
    void aMissingAdvancedConfigLeavesTheRangedBonusIntactRatherThanDeletingIt() {
        // ⚠️ The direction of failure is the whole point. This value is a MULTIPLIER, so a defensive
        // 0.0 fallback would not fail safe — it would silently erase the entire ranged half of the
        // sub-skill whenever the config service was unavailable, and the symptom ("my bow stopped
        // getting the bonus") is indistinguishable from the feature never having worked.
        // Contrast StealthManager#getPadfootSpeedBonus, where the config value IS the bonus and 0 is
        // correctly "no effect".
        McMMOMod.setAdvancedConfig(null);
        for (int i = 0; i < 500; i++) {
            manager.recordKill(ZOMBIE);
        }

        assertEquals(1.0, manager.masteryDamageBonusForHit(ZOMBIE, false), EPSILON);
    }

    @Test
    void theAttackCooldownChargeScalesMeleeOnly() {
        // The D-HU4 asymmetry, pinned MC-free. A half-charged swing is worth half its mastery; a
        // loosed arrow is worth all of it, because there is no swing behind it to charge. Both sides
        // asserted from ONE charge value, so a scaling applied to the wrong branch cannot hide.
        when(mmoPlayer.getAttackStrength()).thenReturn(0.5F);
        for (int i = 0; i < 10_000; i++) {
            manager.recordKill(ZOMBIE);
        }

        assertEquals(1.5, manager.masteryDamageBonusForHit(ZOMBIE, true), EPSILON);
        assertEquals(3.0, manager.masteryDamageBonusForHit(ZOMBIE, false), EPSILON);
    }

    @Test
    void anUnmasteredCreatureIsWorthNothingOnEitherDeliveryPath() {
        // Zero has to stay exactly zero on both branches: multiplying it by a charge or by a config
        // knob must not produce a token bonus, and must not produce NaN if either is ever unset.
        assertEquals(0.0, manager.masteryDamageBonusForHit(ZOMBIE, true), EPSILON);
        assertEquals(0.0, manager.masteryDamageBonusForHit(ZOMBIE, false), EPSILON);
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

    // --- Stage 5: the tier rule -----------------------------------------------------------------

    @Test
    void everyTierBoundaryHoldsFromBothSides() {
        // Each threshold asserted on both sides. A `>` mistyped as `>=` -- or the health and damage
        // clauses swapped -- passes any test that only checks the qualifying side.
        assertEquals(4, HunterManager.deriveTier(true, 150.0D, 0.0D));
        assertEquals(3, HunterManager.deriveTier(true, 149.9D, 0.0D));

        assertEquals(3, HunterManager.deriveTier(true, 30.0D, 0.0D));
        assertEquals(2, HunterManager.deriveTier(true, 29.9D, 0.0D));

        assertEquals(3, HunterManager.deriveTier(true, 1.0D, 6.0D));
        assertEquals(2, HunterManager.deriveTier(true, 1.0D, 5.9D));

        assertEquals(2, HunterManager.deriveTier(false, 60.0D, 0.0D));
        assertEquals(1, HunterManager.deriveTier(false, 59.9D, 0.0D));
    }

    @Test
    void aNonHostileIsNeverPromotedPastTheSecondTierHoweverLargeOrAngryItIs() {
        // The rule fails LOW, deliberately, and this is where that is decided. A passive creature
        // with boss health and boss damage stops at T2: health alone cannot tell "tanky" from
        // "dangerous", and Hunter XP is the axis a mob farm attacks -- the safe direction for a
        // mistake is "pays too little".
        assertEquals(2, HunterManager.deriveTier(false, 10_000.0D, 10_000.0D));
        assertEquals(1, HunterManager.deriveTier(false, 1.0D, 10_000.0D),
                "attack damage must not promote a passive creature at all -- a frog hits for 10");
    }

    @Test
    void aValidOverrideWinsAndAnInvalidOneIsIgnoredRatherThanClamped() {
        final AdvancedConfig advanced = mock(AdvancedConfig.class);
        McMMOMod.setAdvancedConfig(advanced);

        // A zombie derives to 2.
        when(advanced.getHunterTierOverride("Zombie")).thenReturn(4);
        assertEquals(4, HunterManager.resolveTier("Zombie", true, 20.0D, 3.0D));

        // 0 is the config layer's "nothing usable here" -- a missing entry and a rejected one look
        // the same to this method on purpose, so both fall through to the derived tier.
        when(advanced.getHunterTierOverride("Zombie")).thenReturn(0);
        assertEquals(2, HunterManager.resolveTier("Zombie", true, 20.0D, 3.0D));

        // Not clamped to MAX_TIER: a hand-written 7 means the operator misread the scale, and
        // guessing "they meant boss" is a worse answer than the one the game can work out itself.
        when(advanced.getHunterTierOverride("Zombie")).thenReturn(7);
        assertEquals(2, HunterManager.resolveTier("Zombie", true, 20.0D, 3.0D));
    }

    @Test
    void aMissingConfigServiceFallsBackToTheDerivedTierRatherThanRefusingToResolve() {
        // setUp already cleared the holder. The point is that tier resolution has no dependency it
        // can fail on -- there is no code path where a mob resolves to "no tier".
        assertEquals(2, HunterManager.resolveTier("Zombie", true, 20.0D, 3.0D));
    }

    // --- Stage 5: what a tier pays --------------------------------------------------------------

    @Test
    void theShippedLadderIsWhatAnUnconfiguredBuildPays() {
        // ⚠️ The fallback direction is load-bearing and it is NOT zero. A defensive 0.0F here would
        // not fail safe -- it would silently stop the entire vertical axis of the skill, and the
        // player would kill for an hour and gain nothing with no error to point at. Same call as
        // Ranged_Damage_Multiplier's 1.0, and the opposite of StealthManager#getPadfootSpeedBonus,
        // where the config value IS the bonus.
        assertEquals(100.0F, HunterManager.xpForTier(1), EPSILON);
        assertEquals(300.0F, HunterManager.xpForTier(2), EPSILON);
        assertEquals(800.0F, HunterManager.xpForTier(3), EPSILON);
        assertEquals(1_500.0F, HunterManager.xpForTier(4), EPSILON);
    }

    @Test
    void aTierOutsideTheLadderPaysNothingRatherThanIndexingOffTheEnd() {
        assertEquals(0.0F, HunterManager.xpForTier(0), EPSILON);
        assertEquals(0.0F, HunterManager.xpForTier(HunterManager.MAX_TIER + 1), EPSILON);
        assertEquals(0.0F, HunterManager.xpForTier(-1), EPSILON);
    }

    @Test
    void theLadderRisesWithEveryTier() {
        // Not decoration: the two tables that price the skill (this one and the mastery bonuses) are
        // parallel arrays, and a tier that pays less than the one below it would invert the whole
        // vertical axis while every individual number still looked plausible in review.
        for (int tier = HunterManager.MIN_TIER; tier < HunterManager.MAX_TIER; tier++) {
            assertTrue(HunterManager.xpForTier(tier) < HunterManager.xpForTier(tier + 1),
                    "tier " + tier + " must pay less than tier " + (tier + 1));
        }
        assertEquals(HunterManager.MAX_TIER, HunterManager.DEFAULT_TIER_XP.length,
                "one XP value per tier, or xpForTier indexes off the end on somebody's boss kill");
    }

    @Test
    void awardingAKillRoutesThroughTheSharedXpPipeline() {
        // Through applyXpGain rather than a bespoke path, so Hunter inherits the skill multiplier,
        // the global modifier, diminishing returns and the XP bar like every other skill.
        assertEquals(300.0F, manager.awardKillXp(2), EPSILON);

        verify(mmoPlayer).beginXpGain(PrimarySkillType.HUNTER, 300.0F, XPGainReason.PVE,
                XPGainSource.SELF);
    }

    @Test
    void anInvalidTierPaysNothingAndDoesNotTouchThePipelineAtAll() {
        // Asserted as "never called", not as "awarded 0". A 0 XP gain still walks the whole pipeline
        // and can still flash an XP bar at a player who earned nothing.
        assertEquals(0.0F, manager.awardKillXp(0), EPSILON);

        verify(mmoPlayer, never()).beginXpGain(any(), anyFloat(), any(), any());
    }

    // --- Stage 6: Trophy Hunter -----------------------------------------------------------------

    /** Chance ceiling and scaling cap for Trophy Hunter, as a mocked advanced config. */
    private void trophyChance(double chanceMax) {
        final AdvancedConfig advanced = mock(AdvancedConfig.class);
        lenient().when(advanced.getMaximumProbability(SubSkillType.HUNTER_TROPHY_HUNTER))
                .thenReturn(chanceMax);
        // 0 makes every player count as fully scaled, so these tests are about the ceiling and the
        // rank gate rather than about the level ramp (which SkillManager#scaleToLevel owns).
        lenient().when(advanced.getMaxBonusLevel(SubSkillType.HUNTER_TROPHY_HUNTER)).thenReturn(0);
        McMMOMod.setAdvancedConfig(advanced);
    }

    @Test
    void eachTrophyHunterRankUnlocksExactlyOneMoreMobTier() {
        // 🔑 THE STAGE'S LOAD-BEARING INVARIANT: the rank number IS the mob tier. Driven against the
        // REAL bundled skillranks.yml rather than a stubbed ladder, so this asserts both halves at
        // once -- that the code indexes the tier off the rank, and that the shipped file actually
        // carries four ranks at 100 / 300 / 600 / 900. Either half alone proves nothing: a stubbed
        // ladder would pass against an empty config section, and a config read alone would not
        // notice canTrophyHunt comparing against the wrong number.
        final int[][] expected = {
                //          T1     T2     T3     T4
                /*   0 */ {0, 0, 0, 0},
                /* 100 */ {1, 0, 0, 0},
                /* 300 */ {1, 1, 0, 0},
                /* 600 */ {1, 1, 1, 0},
                /* 900 */ {1, 1, 1, 1},
        };
        final int[] levels = {0, 100, 300, 600, 900};

        for (int row = 0; row < levels.length; row++) {
            when(mmoPlayer.getSkillLevel(PrimarySkillType.HUNTER)).thenReturn(levels[row]);
            for (int tier = 1; tier <= 4; tier++) {
                assertEquals(expected[row][tier - 1] == 1, manager.canTrophyHunt(tier),
                        "Hunter " + levels[row] + " vs tier " + tier);
            }
        }
    }

    @Test
    void aTierOffTheScaleIsRefusedRatherThanTreatedAsTheNearestOne() {
        // Every tier reaching canTrophyHunt comes from MobTiers.tierOf, which cannot produce one of
        // these -- so an out-of-range value means something upstream broke, and quietly reading it
        // as tier 1 would hand a bonus roll to a creature nobody has priced. Asserted at the level
        // cap so the rank gate cannot be what makes it false.
        when(mmoPlayer.getSkillLevel(PrimarySkillType.HUNTER)).thenReturn(1_000);

        assertTrue(manager.canTrophyHunt(HunterManager.MAX_TIER), "the reference point still passes");
        assertFalse(manager.canTrophyHunt(0));
        assertFalse(manager.canTrophyHunt(-1));
        assertFalse(manager.canTrophyHunt(HunterManager.MAX_TIER + 1));
    }

    @Test
    void aLockedTierNeverRollsATrophyEvenAtACertaintyOfChance() {
        // The gate ordering that matters: rank first, RNG second. At a 100% ceiling the roll is a
        // constant true, so if the rank check were missing or ran second this would still pay out --
        // which is precisely the failure a farm would find first.
        trophyChance(100.0D);
        when(mmoPlayer.getSkillLevel(PrimarySkillType.HUNTER)).thenReturn(100); // rank 1 = tier 1

        assertTrue(manager.rollTrophyDrop(1), "the unlocked tier must roll at a 100% ceiling");
        assertFalse(manager.rollTrophyDrop(2), "a locked tier must not roll at any chance");
        assertFalse(manager.rollTrophyDrop(3));
        assertFalse(manager.rollTrophyDrop(4));
    }

    @Test
    void theChanceCeilingIsHonouredInBothDirections() {
        // Asserted at both extremes rather than at one: a roll hard-wired to true passes the 100%
        // case, and one hard-wired to false passes the 0% case. Only the pair pins the RNG.
        when(mmoPlayer.getSkillLevel(PrimarySkillType.HUNTER)).thenReturn(1_000);

        trophyChance(100.0D);
        assertTrue(manager.rollTrophyDrop(4), "a 100% ceiling must always roll");

        trophyChance(0.0D);
        assertFalse(manager.rollTrophyDrop(4), "a 0% ceiling must never roll");
    }

    @Test
    void theShippedTrophyChanceIsHalfAndTheTiersAllShareIt() {
        // Read off the REAL advanced.yml. ⚠️ 50, not the 100 Herbalism's and Mining's double drops
        // use: those are blocks, this is the mob economy, and rank 4 reaches bosses.
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        assertEquals(50.0D,
                McMMOMod.getAdvancedConfig().getMaximumProbability(
                        SubSkillType.HUNTER_TROPHY_HUNTER),
                EPSILON, "advanced.yml Skills.Hunter.TrophyHunter.ChanceMax");
    }
}
