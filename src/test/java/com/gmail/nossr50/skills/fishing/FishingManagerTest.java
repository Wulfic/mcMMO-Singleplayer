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
import com.gmail.nossr50.datatypes.treasure.EnchantmentTreasure;
import com.gmail.nossr50.datatypes.treasure.FishingTreasure;
import com.gmail.nossr50.datatypes.treasure.Rarity;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.ArrayList;
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
    void resolveMasterAnglerWaitTimesFromLureTicksMatchesTheLureLevelOverload() {
        // The Fabric seam hands us vanilla's waitTimeReductionTicks instead of an enchantment level.
        // That field is exactly lureLevel * 100, so both entry points must resolve identically —
        // if the conversion constant drifts, this catches it.
        for (int lureLevel = 0; lureLevel <= 4; lureLevel++) {
            assertEquals(fishingManager.resolveMasterAnglerWaitTimes(600, 6000, 3, false, lureLevel),
                    fishingManager.resolveMasterAnglerWaitTimesFromLureTicks(600, 6000, 3, false,
                            lureLevel * 100),
                    "lure level " + lureLevel + " must convert to " + (lureLevel * 100) + " ticks");
        }
    }

    @Test
    void resolveMasterAnglerWaitTimesFromLureTicksCarriesTheFullReduction() {
        // A tick reduction that is not a whole number of levels still applies in full — the reason the
        // port takes ticks rather than re-deriving a level (which would round the bonus away).
        FishingManager.MasterAnglerWaitTimes waitTimes =
                fishingManager.resolveMasterAnglerWaitTimesFromLureTicks(600, 6000, 1, false, 150);
        assertEquals(5820, waitTimes.maxWaitTicks(), "6000 - (30 rank bonus + 150 lure ticks)");
        assertEquals(590, waitTimes.minWaitTicks(), "lure never reduces the minimum wait");
        assertTrue(waitTimes.disableLure(), "a non-zero reduction must cancel vanilla's own subtraction");
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

    // fishing_treasures.yml Shake.CAVE_SPIDER, in config order: SPIDER_EYE 49%, STRING 49%,
    // COBWEB 1%, POTION|0|POISON 1% (the potion is deferred, so the loaded chances sum to 99, not 100).
    // The integer roll is caller-supplied, so the whole selection is deterministic.

    @Test
    void shakeRollWalksDropChancesInConfigOrder() {
        assertEquals("spider_eye",
                fishingManager.rollShakeTreasure("cave_spider", 0).orElseThrow().getDrop()
                        .getMaterialId(), "roll 0 lands in the first band");
        assertEquals("spider_eye",
                fishingManager.rollShakeTreasure("cave_spider", 48).orElseThrow().getDrop()
                        .getMaterialId(), "48 is the last roll inside the 49% first band");
        assertEquals("string",
                fishingManager.rollShakeTreasure("cave_spider", 49).orElseThrow().getDrop()
                        .getMaterialId(), "49 is the first roll of the second band");
        assertEquals("cobweb",
                fishingManager.rollShakeTreasure("cave_spider", 98).orElseThrow().getDrop()
                        .getMaterialId(), "98 lands in the 1% cobweb band");
    }

    @Test
    void shakeRollWinsNothingAboveTheSummedDropChances() {
        // 99 exceeds the loaded 99% total — the gap the deferred poison potion used to fill. Legacy
        // behaves the same way for any entity whose chances sum below 100 (chooseDrop returns null).
        assertTrue(fishingManager.rollShakeTreasure("cave_spider", 99).isEmpty());
    }

    @Test
    void shakeRollIsEmptyForAnEntityWithNoConfiguredDrops() {
        assertTrue(fishingManager.rollShakeTreasure("bat", 0).isEmpty());
    }

    @Test
    void shakeDamageIsAQuarterOfHealthFlooredAtOneAndCappedAtTen() {
        assertEquals(5.0, FishingManager.shakeDamage(20.0), 1.0e-9, "a zombie: 20/4");
        assertEquals(1.0, FishingManager.shakeDamage(2.0), 1.0e-9, "floored at 1, never 0.5");
        assertEquals(10.0, FishingManager.shakeDamage(100.0), 1.0e-9,
                "capped at 10 so a tanky mob survives more than four shakes");
    }

    @Test
    void awardShakeXpPaysTheFlatConfiguredValue() {
        // experience.yml: Experience_Values.Fishing.Shake = 50 (paid regardless of the drop's own XP,
        // which is 0 for every shipped shake entry).
        fishingManager.awardShakeXP();
        verify(mmoPlayer).beginXpGain(PrimarySkillType.FISHING, 50f, XPGainReason.PVE,
                XPGainSource.SELF);
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

    // ---- Magic Hunter ------------------------------------------------------------------------
    // fishing_treasures.yml Enchantment_Drop_Rates.Tier_1, in the enum's most-rare-first walk order:
    // MYTHIC 0.01, LEGENDARY 0.01, EPIC 0.01, RARE 0.10, UNCOMMON 1.00, COMMON 5.00 (sum 6.13).
    // This is a different table from Item_Drop_Rates, so these numbers deliberately differ from the
    // treasure-roll tests above.

    @Test
    void magicHunterRollLandsInTheRarestBandForTheLowestDice() {
        atFishingLevel(1);
        assertEquals(Optional.of(Rarity.MYTHIC), fishingManager.rollMagicHunterRarity(0.0),
                "diceRoll 0 always wins the rarest band");
    }

    @Test
    void magicHunterRollFallsThroughToCommon() {
        atFishingLevel(1);
        // 2.0 clears MYTHIC..UNCOMMON (cumulative 1.13) leaving 0.87, which lands inside COMMON (5.00).
        assertEquals(Optional.of(Rarity.COMMON), fishingManager.rollMagicHunterRarity(2.0));
    }

    @Test
    void magicHunterRollWinsNothingAboveTheSummedDropRates() {
        atFishingLevel(1);
        // 50.0 exceeds the 6.13 total across every band -> the treasure arrives unenchanted.
        assertTrue(fishingManager.rollMagicHunterRarity(50.0).isEmpty());
    }

    @Test
    void magicHunterRollWinsNothingAtLootTierZero() {
        atFishingLevel(0);
        // Tier_0 is absent from the config, so every band reads 0.0 and any positive roll misses.
        assertTrue(fishingManager.rollMagicHunterRarity(0.01).isEmpty(),
                "an unranked player cannot win an enchant band");
    }

    @Test
    void magicHunterRollUsesTheEnchantCurveNotTheItemCurve() {
        atFishingLevel(1);
        // 6.5 is inside the item curve's COMMON band (7.50) but past the whole enchant curve (6.13).
        assertTrue(fishingManager.rollFishingTreasure(6.5, 0, size -> 0).isPresent());
        assertTrue(fishingManager.rollMagicHunterRarity(6.5).isEmpty());
    }

    private static EnchantmentTreasure enchant(String id) {
        return new EnchantmentTreasure(id, 1);
    }

    @Test
    void magicHunterSelectionTakesEveryCandidateWhenEveryDrawSucceeds() {
        final List<EnchantmentTreasure> candidates =
                List.of(enchant("sharpness"), enchant("looting"), enchant("unbreaking"));

        assertEquals(candidates, fishingManager.selectMagicHunterEnchants(candidates,
                (selected, candidate) -> false, bound -> 0),
                "a draw of 0 is legacy's success, so all three land in order");
    }

    @Test
    void magicHunterSelectionHalvesTheOddsOnlyAfterAnAcceptance() {
        final List<EnchantmentTreasure> candidates =
                List.of(enchant("sharpness"), enchant("looting"), enchant("unbreaking"));
        final List<Integer> bounds = new ArrayList<>();

        // Succeeds only while the walk is still at 1-in-1, i.e. exactly the first candidate.
        final List<EnchantmentTreasure> selected = fishingManager.selectMagicHunterEnchants(
                candidates, (alreadySelected, candidate) -> false, bound -> {
                    bounds.add(bound);
                    return bound == 1 ? 0 : 1;
                });

        assertEquals(List.of(enchant("sharpness")), selected);
        assertEquals(List.of(1, 2, 2), bounds,
                "the 1-in-N counter doubles on an acceptance and never on a rejection");
    }

    @Test
    void magicHunterSelectionSkipsAConflictWithoutSpendingADraw() {
        final List<EnchantmentTreasure> candidates =
                List.of(enchant("sharpness"), enchant("looting"), enchant("unbreaking"));
        final List<Integer> bounds = new ArrayList<>();

        final List<EnchantmentTreasure> selected = fishingManager.selectMagicHunterEnchants(
                candidates,
                (alreadySelected, candidate) -> candidate.enchantmentId().equals("sharpness"),
                bound -> {
                    bounds.add(bound);
                    return 0;
                });

        assertEquals(List.of(enchant("looting"), enchant("unbreaking")), selected);
        assertEquals(List.of(1, 2), bounds,
                "the conflicting candidate short-circuits before the draw, so it consumes no roll");
    }

    @Test
    void magicHunterSelectionOffersTheRunningSelectionToTheConflictTest() {
        // The port's deviation from upstream: legacy only tested conflicts against enchantments
        // already on the item (none, on a freshly built treasure), so it could grant Sharpness,
        // Smite and Bane of Arthropods together. The running selection is now visible to the test.
        final List<EnchantmentTreasure> candidates =
                List.of(enchant("sharpness"), enchant("smite"), enchant("bane_of_arthropods"));

        final List<EnchantmentTreasure> selected = fishingManager.selectMagicHunterEnchants(
                candidates,
                (alreadySelected, candidate) -> !alreadySelected.isEmpty(),
                bound -> 0);

        assertEquals(List.of(enchant("sharpness")), selected,
                "once one damage enchant is picked the mutually exclusive siblings are skipped");
    }

    @Test
    void magicHunterSelectionOfNoCandidatesEnchantsNothing() {
        assertTrue(fishingManager.selectMagicHunterEnchants(List.of(),
                (selected, candidate) -> false, bound -> 0).isEmpty());
    }
}
