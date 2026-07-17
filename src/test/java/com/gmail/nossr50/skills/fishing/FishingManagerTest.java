package com.gmail.nossr50.skills.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.config.treasure.FishingTreasureConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.treasure.FishingTreasure;
import com.gmail.nossr50.datatypes.treasure.Rarity;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;

/**
 * Proves the Fishing numeric cores (Phase 10.8) against the real bundled configs.
 *
 * <p>RetroMode is on by default (skillranks.yml): Shake ranks 1..8 unlock at fishing levels
 * {150,200,250,300,400,500,600,700}; Master Angler ranks unlock at {1,200,300,400,600,700,800,900};
 * Treasure Hunter ranks unlock at {1,250,350,500,650,750,850,1000}; Magic Hunter rank 1 unlocks at
 * 200. advanced.yml: ShakeChance % = {15,20,25,35,45,55,65,75}, VanillaXPMultiplier =
 * {1,2,3,3,4,4,5,5}, MasterAngler Tick_Reduction_Per_Rank {Min:10,Max:30}, Boat_Tick_Reduction
 * {Min:10,Max:30}, Tick_Reduction_Caps {Min:40,Max:100}.
 */
class FishingManagerTest {

    private McMMOPlayer mmoPlayer;
    private FishingManager fishingManager;
    private FishingTreasureConfig fishingTreasureConfig;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));
        fishingTreasureConfig = new FishingTreasureConfig(dataFolder);
        McMMOMod.setFishingTreasureConfig(fishingTreasureConfig);

        PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        fishingManager = new FishingManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setFishingTreasureConfig(null);
        UserManager.clearAll();
    }

    private void atFishingLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.FISHING)).thenReturn(level);
    }

    @Test
    void lootTierLaddersWithTreasureHunterRank() {
        atFishingLevel(0);
        assertEquals(0, fishingManager.getLootTier(), "below rank 1 -> tier 0");
        atFishingLevel(1);
        assertEquals(1, fishingManager.getLootTier(), "1 -> rank 1");
        atFishingLevel(1000);
        assertEquals(8, fishingManager.getLootTier(), "1000 -> max rank 8");
    }

    @Test
    void shakeChanceScalesWithShakeRank() {
        atFishingLevel(150); // Shake rank 1
        assertEquals(15.0, fishingManager.getShakeChance(), 1.0e-9);
        atFishingLevel(700); // Shake rank 8 (max)
        assertEquals(75.0, fishingManager.getShakeChance(), 1.0e-9);
    }

    @Test
    void canShakeGatedOnRankAndPermission() {
        atFishingLevel(0);
        assertFalse(fishingManager.canShake(), "no Shake rank yet");
        atFishingLevel(150);
        assertTrue(fishingManager.canShake(), "rank 1 unlocked");
    }

    @Test
    void canMasterAnglerGatedOnUnlockLevel() {
        atFishingLevel(0);
        assertFalse(fishingManager.canMasterAngler());
        atFishingLevel(1);
        assertTrue(fishingManager.canMasterAngler(), "Master Angler rank 1 unlocks at level 1");
    }

    @Test
    void vanillaXpBoostScalesWithLootTier() {
        atFishingLevel(1); // Treasure Hunter rank 1 -> VanillaXPMultiplier x1
        assertEquals(10, fishingManager.handleVanillaXpBoost(10));
        atFishingLevel(1000); // Treasure Hunter rank 8 -> VanillaXPMultiplier x5
        assertEquals(50, fishingManager.handleVanillaXpBoost(10));
    }

    @Test
    void magicHunterRequiresBothMagicHunterAndTreasureHunterRanks() {
        atFishingLevel(0);
        assertFalse(fishingManager.isMagicHunterEnabled());
        atFishingLevel(200); // unlocks Magic Hunter rank 1 and Treasure Hunter rank 2
        assertTrue(fishingManager.isMagicHunterEnabled());
    }

    @Test
    void reducedTicksNeverGoBelowTheConfiguredBounds() {
        assertEquals(100, fishingManager.getReducedTicks(150, 50, 40), "150-50=100 is above the 40 floor");
        assertEquals(40, fishingManager.getReducedTicks(150, 200, 40), "150-200 is negative, floored at 40");
    }

    @Test
    void masterAnglerWaitReductionScalesWithRankAndBoatBonus() {
        assertEquals(10, fishingManager.getMasterAnglerTickMinWaitReduction(1, false));
        assertEquals(20, fishingManager.getMasterAnglerTickMinWaitReduction(1, true), "boat adds +10");
        assertEquals(30, fishingManager.getMasterAnglerTickMaxWaitReduction(1, false, 0));
        assertEquals(60, fishingManager.getMasterAnglerTickMaxWaitReduction(1, true, 0), "boat adds +30");
        assertEquals(130, fishingManager.getMasterAnglerTickMaxWaitReduction(1, false, 100),
                "lure bonus adds on top");
    }

    @Test
    void resolveMasterAnglerWaitTimesAppliesLureAndFloorsAtLowerBound() {
        FishingManager.MasterAnglerWaitTimes waitTimes =
                fishingManager.resolveMasterAnglerWaitTimes(600, 6000, 1, false, 0);
        assertEquals(590, waitTimes.minWaitTicks(), "600 - 10 (rank 1 min reduction)");
        assertEquals(5970, waitTimes.maxWaitTicks(), "6000 - 30 (rank 1 max reduction)");
        assertFalse(waitTimes.disableLure());

        FishingManager.MasterAnglerWaitTimes floored =
                fishingManager.resolveMasterAnglerWaitTimes(50, 100, 8, true, 4);
        assertEquals(fishingManager.getMasterAnglerMinWaitLowerBound(), floored.minWaitTicks(),
                "min reduction overwhelms the ticks, floors at the configured lower bound");
        assertTrue(floored.disableLure(), "lureLevel > 0 signals the caller to disable vanilla Lure");
    }

    @Test
    void resolveMasterAnglerWaitTimesCorrectsInvertedBounds() {
        // A pathological config where max ends up below min gets nudged back above it.
        FishingManager.MasterAnglerWaitTimes waitTimes =
                fishingManager.resolveMasterAnglerWaitTimes(1000, 1010, 8, false, 0);
        assertTrue(waitTimes.maxWaitTicks() >= waitTimes.minWaitTicks());
    }

    @Test
    void exploitDetectionTracksRepeatedCastsAtTheSameSpot() {
        // First cast establishes the spot (counter=1); the configured OverFishLimit is 10, so 9 more
        // casts at the same spot (counter=10) trips the limit.
        for (int i = 0; i < 10; i++) {
            fishingManager.processExploiting(0, 64, 0);
        }
        assertTrue(fishingManager.isExploitingFishing(), "10 casts at the same spot trips the limit");
    }

    @Test
    void exploitDetectionResetsWhenTheCastMoves() {
        for (int i = 0; i < 10; i++) {
            fishingManager.processExploiting(0, 64, 0);
        }
        assertTrue(fishingManager.isExploitingFishing());

        fishingManager.processExploiting(500, 64, 500);
        assertFalse(fishingManager.isExploitingFishing(), "moving the cast resets the streak");
    }

    @Test
    void isFishingTooOftenDetectsRapidRecast() {
        assertFalse(fishingManager.isFishingTooOften(), "first catch, nothing to compare against");
        assertTrue(fishingManager.isFishingTooOften(), "immediate recast within the same millisecond window");
    }

    @Test
    void awardFishingXpUsesTheCaughtMaterialsXpTable() {
        // experience.yml: Experience_Values.Fishing.Cod = 100, Salmon = 600.
        fishingManager.awardFishingXP("Cod");
        verify(mmoPlayer).beginXpGain(PrimarySkillType.FISHING, 100f, XPGainReason.PVE,
                XPGainSource.SELF);

        fishingManager.awardFishingXP("Salmon");
        verify(mmoPlayer).beginXpGain(PrimarySkillType.FISHING, 600f, XPGainReason.PVE,
                XPGainSource.SELF);
    }

    @Test
    void awardFishingXpIsANoOpForMaterialsWithNoConfiguredXp() {
        // Stone is not in the Fishing XP table -> getXp returns 0 -> no award.
        fishingManager.awardFishingXP("Stone");
        verify(mmoPlayer, never()).beginXpGain(ArgumentMatchers.any(), ArgumentMatchers.anyFloat(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    // fishing_treasures.yml Item_Drop_Rates.Tier_1, in the enum's most-rare-first walk order:
    // MYTHIC 0.01, LEGENDARY 0.01, EPIC 0.10, RARE 0.25, UNCOMMON 1.25, COMMON 7.50 (sum 9.12).
    // Level 1 -> Treasure Hunter rank 1 -> loot tier 1. The bucket picker (size -> index) fixes which
    // reward in the chosen rarity band is returned, so the whole roll is deterministic.

    @Test
    void treasureRollLandsInTheRarestBandForTheLowestDice() {
        atFishingLevel(1);
        // diceRoll 0.0 is <= MYTHIC's 0.01 immediately -> first MYTHIC reward.
        final Optional<FishingTreasure> rolled = fishingManager.rollFishingTreasure(0.0, 0, size -> 0);
        assertTrue(rolled.isPresent(), "diceRoll 0 always wins the rarest band");
        assertSame(fishingTreasureConfig.fishingRewards.get(Rarity.MYTHIC).get(0), rolled.get());
    }

    @Test
    void treasureRollFallsThroughToCommon() {
        atFishingLevel(1);
        // diceRoll 5.0 clears MYTHIC..UNCOMMON (cumulative 1.62) then lands in COMMON (7.50).
        final Optional<FishingTreasure> rolled = fishingManager.rollFishingTreasure(5.0, 0, size -> 0);
        assertTrue(rolled.isPresent());
        assertSame(fishingTreasureConfig.fishingRewards.get(Rarity.COMMON).get(0), rolled.get());
    }

    @Test
    void treasureRollWinsNothingAboveTheSummedDropRates() {
        atFishingLevel(1);
        // 50.0 exceeds the 9.12 total across every band -> no treasure.
        assertTrue(fishingManager.rollFishingTreasure(50.0, 0, size -> 0).isEmpty());
    }

    @Test
    void luckOfTheSeaScalesAMissDownIntoAHit() {
        atFishingLevel(1);
        // No luck: 10.0 clears every band (leftover 0.88 after COMMON) -> miss.
        assertTrue(fishingManager.rollFishingTreasure(10.0, 0, size -> 0).isEmpty(),
                "without luck a roll of 10 wins nothing at tier 1");
        // Lure_Modifier 4.0, luck 10 -> scaled 10 * (1 - 10*4/100) = 6.0 -> lands in COMMON.
        final Optional<FishingTreasure> withLuck =
                fishingManager.rollFishingTreasure(10.0, 10, size -> 0);
        assertTrue(withLuck.isPresent(), "Luck of the Sea scales the same roll down into COMMON");
        assertSame(fishingTreasureConfig.fishingRewards.get(Rarity.COMMON).get(0), withLuck.get());
    }

    @Test
    void treasureRollHonoursTheBucketPicker() {
        atFishingLevel(1);
        final List<FishingTreasure> common = fishingTreasureConfig.fishingRewards.get(Rarity.COMMON);
        assertTrue(common.size() > 1, "COMMON must have several rewards for this to prove anything");
        assertSame(common.get(0),
                fishingManager.rollFishingTreasure(5.0, 0, size -> 0).orElseThrow());
        assertSame(common.get(common.size() - 1),
                fishingManager.rollFishingTreasure(5.0, 0, size -> size - 1).orElseThrow(),
                "the picker chooses which reward within the rolled rarity band");
    }

    @Test
    void awardFishingTreasureXpAddsTheTreasuresXp() {
        fishingManager.awardFishingTreasureXP(250);
        verify(mmoPlayer).beginXpGain(PrimarySkillType.FISHING, 250f, XPGainReason.PVE,
                XPGainSource.SELF);
    }

    @Test
    void awardFishingTreasureXpIsANoOpForZeroXp() {
        fishingManager.awardFishingTreasureXP(0);
        verify(mmoPlayer, never()).beginXpGain(ArgumentMatchers.any(), ArgumentMatchers.anyFloat(),
                ArgumentMatchers.any(), ArgumentMatchers.any());
    }
}
