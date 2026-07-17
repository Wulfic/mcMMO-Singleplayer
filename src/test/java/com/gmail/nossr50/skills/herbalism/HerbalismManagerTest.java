package com.gmail.nossr50.skills.herbalism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.herbalism.HerbalismManager.GreenThumbReplant;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the Herbalism numeric core (Phase 10.3) against the real bundled configs.
 *
 * <p>{@code experience.yml} Herbalism XP: Sweet_Berry_Bush = 50, Wheat = 60. {@code
 * ExploitFix.LimitTallPlantFarming} defaults on; {@code cactus} is capped at 3 broken blocks.
 */
class HerbalismManagerTest {

    private McMMOPlayer mmoPlayer;
    private HerbalismManager herbalismManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));

        PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000e2"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        herbalismManager = new HerbalismManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
        UserManager.clearAll();
    }

    private void atHerbalismLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.HERBALISM)).thenReturn(level);
    }

    @Test
    void canUseHylianLuckIsAlwaysUnlockedSinceItHasNoRanks() {
        // HERBALISM_HYLIAN_LUCK declares no rank count, so RankUtils treats it as always unlocked
        // (curRank == -1) regardless of skill level; only the permission gate applies.
        atHerbalismLevel(0);
        assertTrue(herbalismManager.canUseHylianLuck());
        atHerbalismLevel(1000);
        assertTrue(herbalismManager.canUseHylianLuck());
    }

    @Test
    void canDoubleDropFailsBeforeUnlockingIt() {
        atHerbalismLevel(0);
        assertFalse(herbalismManager.canDoubleDrop(), "no Double Drops rank yet");
    }

    // --- Bonus-drop eligibility gate (deterministic; the RNG roll is verified in-game) ----------

    @Test
    void bonusDropsIneligibleBeforeDoubleDropsUnlock() {
        atHerbalismLevel(0); // below the level-1 Double Drops unlock
        assertFalse(herbalismManager.isBonusDropsEligible("minecraft:wheat"),
                "double drops locked at level 0 -> not eligible even for a configured plant");
    }

    @Test
    void bonusDropsEligibleForConfiguredPlantOnceDoubleDropsUnlock() {
        atHerbalismLevel(1); // Double Drops unlocks at herbalism level 1.
        // Wheat is listed true under Bonus_Drops.Herbalism in config.yml.
        assertTrue(herbalismManager.isBonusDropsEligible("minecraft:wheat"),
                "configured plant at unlock level -> eligible");
    }

    @Test
    void bonusDropsIneligibleForUnconfiguredBlock() {
        atHerbalismLevel(1);
        // Stone is not under Bonus_Drops.Herbalism -> getDoubleDropsEnabled false.
        assertFalse(herbalismManager.isBonusDropsEligible("minecraft:stone"),
                "block absent from Bonus_Drops.Herbalism -> not eligible");
    }

    @Test
    void bonusDropsIneligibleForLilyPadDespiteBeingConfigured() {
        atHerbalismLevel(1);
        // config.yml lists Lily_Pad: true, but GeneralConfig hard-disables it (Spigot exploit),
        // so the eligibility gate must still veto it.
        assertFalse(herbalismManager.isBonusDropsEligible("minecraft:lily_pad"),
                "lily pad is vetoed despite being listed under Bonus_Drops.Herbalism");
    }

    @Test
    void rollBonusDropCountTriplesWhileGreenTerraActive() {
        // Force the RNG gate to succeed by driving the underlying probability to a certainty:
        // HERBALISM_DOUBLE_DROPS at a maxed skill level yields the ceiling probability (always
        // succeeds), so the roll is deterministic here.
        atHerbalismLevel(10000);
        when(mmoPlayer.getAbilityMode(SuperAbilityType.GREEN_TERRA)).thenReturn(true);
        assertEquals(2, herbalismManager.rollBonusDropCount(),
                "Green Terra active -> triple drops (2 extra copies)");
        when(mmoPlayer.getAbilityMode(SuperAbilityType.GREEN_TERRA)).thenReturn(false);
        assertEquals(1, herbalismManager.rollBonusDropCount(),
                "no Green Terra -> double drops (1 extra copy)");
    }

    @Test
    void isBizarreAgeableMatchesLegacySet() {
        assertTrue(herbalismManager.isBizarreAgeable("cactus"));
        assertTrue(herbalismManager.isBizarreAgeable("minecraft:kelp"));
        assertTrue(herbalismManager.isBizarreAgeable("sugar_cane"));
        assertTrue(herbalismManager.isBizarreAgeable("bamboo"));
        assertFalse(herbalismManager.isBizarreAgeable("wheat"));
    }

    // --- Maturity-gate divert (which broken ageables reward on maturity, not the placed flag) ---

    @Test
    void isMaturityGatedCropAcceptsFarmCrops() {
        // Every replantable single-block crop that grants Herbalism XP in experience.yml.
        assertTrue(herbalismManager.isMaturityGatedCrop("minecraft:wheat"));
        assertTrue(herbalismManager.isMaturityGatedCrop("carrots"));
        assertTrue(herbalismManager.isMaturityGatedCrop("potatoes"));
        assertTrue(herbalismManager.isMaturityGatedCrop("beetroots"));
        assertTrue(herbalismManager.isMaturityGatedCrop("nether_wart"));
        assertTrue(herbalismManager.isMaturityGatedCrop("cocoa"));
        assertTrue(herbalismManager.isMaturityGatedCrop("sweet_berry_bush"));
    }

    @Test
    void isMaturityGatedCropRejectsBizarreAgeablesAndChorus() {
        // Bizarre ageables and chorus stay on the normal placed-flag gathering path (deferred
        // multi-block plants whose age can't be trusted for maturity).
        assertFalse(herbalismManager.isMaturityGatedCrop("cactus"));
        assertFalse(herbalismManager.isMaturityGatedCrop("sugar_cane"));
        assertFalse(herbalismManager.isMaturityGatedCrop("bamboo"));
        assertFalse(herbalismManager.isMaturityGatedCrop("minecraft:kelp"));
        assertFalse(herbalismManager.isMaturityGatedCrop("chorus_flower"));
        assertFalse(herbalismManager.isMaturityGatedCrop("chorus_plant"));
    }

    @Test
    void isMaturityGatedCropRejectsBlocksWithoutHerbalismXp() {
        // A block that grants no Herbalism XP is never maturity-gated (the ageable-ness check is the
        // listener's getAgeableState job; this predicate only rejects bizarre/chorus + non-XP blocks).
        assertFalse(herbalismManager.isMaturityGatedCrop("minecraft:stone"));
        assertFalse(herbalismManager.isMaturityGatedCrop("dirt"));
    }

    @Test
    void isAgeableMatureSpecialCasesSweetBerryBush() {
        assertFalse(herbalismManager.isAgeableMature("sweet_berry_bush", 1, 3),
                "age 1 is too young");
        assertTrue(herbalismManager.isAgeableMature("sweet_berry_bush", 2, 3));
        assertTrue(herbalismManager.isAgeableMature("sweet_berry_bush", 3, 3));
    }

    @Test
    void isAgeableMatureRequiresMaxNonZeroAgeForOrdinaryCrops() {
        assertFalse(herbalismManager.isAgeableMature("wheat", 0, 7), "age 0 never counts");
        assertFalse(herbalismManager.isAgeableMature("wheat", 6, 7), "not yet at max age");
        assertTrue(herbalismManager.isAgeableMature("wheat", 7, 7));
    }

    @Test
    void getBerryBushXpRewardMatchesLegacyMultipliers() {
        // Sweet_Berry_Bush raw XP = 50.
        assertEquals(50, herbalismManager.getBerryBushXpReward("sweet_berry_bush", 2),
                "age 2 = normal XP");
        assertEquals(100, herbalismManager.getBerryBushXpReward("sweet_berry_bush", 3),
                "age 3 = double XP");
        assertEquals(0, herbalismManager.getBerryBushXpReward("sweet_berry_bush", 1),
                "not old enough");
        assertEquals(0, herbalismManager.getBerryBushXpReward("wheat", 3),
                "not a sweet berry bush at all");
    }

    @Test
    void getExperienceFromPlantReadsExperienceYml() {
        assertEquals(50, HerbalismManager.getExperienceFromPlant("Wheat"));
        assertEquals(0, HerbalismManager.getExperienceFromPlant("Stone"),
                "a non-plant block gives no Herbalism XP");
    }

    @Test
    void applyTallPlantXpCapLimitsCactusButNotWheat() {
        // Cactus limit is 3 broken blocks; 10 cactus blocks at 10 XP each should cap at 3*10=30.
        assertEquals(30, herbalismManager.applyTallPlantXpCap(100, 10, "cactus"));
        // Wheat isn't a tall-plant limited material, so the full sum passes through.
        assertEquals(100, herbalismManager.applyTallPlantXpCap(100, 10, "wheat"));
    }

    @Test
    void resolveGreenThumbReplantRestartsImmatureCropsAtZero() {
        atHerbalismLevel(0);
        Optional<GreenThumbReplant> result =
                herbalismManager.resolveGreenThumbReplant("wheat", false, false);
        assertTrue(result.isPresent());
        assertEquals(0, result.get().finalAge());
        assertTrue(result.get().isImmature());
    }

    @Test
    void resolveGreenThumbReplantRejectsBizarreAgeables() {
        assertTrue(herbalismManager.resolveGreenThumbReplant("cactus", true, false).isEmpty());
    }

    @Test
    void resolveGreenThumbReplantRejectsUnknownCrops() {
        assertTrue(herbalismManager.resolveGreenThumbReplant("stone", true, false).isEmpty());
    }

    @Test
    void resolveGreenThumbReplantMatureCarrotUsesGreenThumbStageDirectly() {
        // At level 0 the Green Thumb rank/stage is 0, and Green Terra isn't active.
        atHerbalismLevel(0);
        Optional<GreenThumbReplant> result =
                herbalismManager.resolveGreenThumbReplant("carrots", true, false);
        assertTrue(result.isPresent());
        assertEquals(0, result.get().finalAge());
        assertFalse(result.get().isImmature());
    }

    @Test
    void resolveGreenThumbReplantCocoaNeedsStageTwo() {
        // General.RetroMode.Enabled defaults true, so skillranks.yml's RetroMode Green Thumb
        // Rank_1 (level 250) applies, giving stage 1.
        atHerbalismLevel(250);
        assertEquals(0,
                herbalismManager.resolveGreenThumbReplant("cocoa", true, false).get().finalAge(),
                "stage 1 alone isn't enough for cocoa");
        // Green Terra active boosts the effective stage by one (capped at the highest rank), so
        // stage 1 -> 2, which is enough for cocoa.
        assertEquals(1,
                herbalismManager.resolveGreenThumbReplant("cocoa", true, true).get().finalAge());
    }

    @Test
    void resolveGreenThumbReplantSweetBerryBushCapsAtOne() {
        atHerbalismLevel(0);
        assertEquals(1,
                herbalismManager.resolveGreenThumbReplant("sweet_berry_bush", true, true).get()
                        .finalAge(),
                "Green Terra active always caps sweet berry bush replant at age 1");
    }

    @Test
    void getGreenThumbReplantMaterialMatchesLegacyTable() {
        assertEquals(Optional.of("carrot"),
                HerbalismManager.getGreenThumbReplantMaterial("carrots"));
        assertEquals(Optional.of("wheat_seeds"),
                HerbalismManager.getGreenThumbReplantMaterial("wheat"));
        assertEquals(Optional.of("sweet_berries"),
                HerbalismManager.getGreenThumbReplantMaterial("sweet_berry_bush"));
        assertEquals(Optional.empty(), HerbalismManager.getGreenThumbReplantMaterial("stone"));
    }

    @Test
    void isGreenTerraActiveReadsAbilityMode() {
        when(mmoPlayer.getAbilityMode(SuperAbilityType.GREEN_TERRA)).thenReturn(true);
        assertTrue(herbalismManager.isGreenTerraActive());
    }

    @Test
    void rollGreenThumbReplantBypassesRngWhileGreenTerraActive() {
        // At level 0 the Green Thumb RNG can't succeed on its own; Green Terra must force the roll
        // (legacy's greenTerra bypass in processGreenThumbPlants), independent of the probability.
        atHerbalismLevel(0);
        when(mmoPlayer.getAbilityMode(SuperAbilityType.GREEN_TERRA)).thenReturn(true);
        assertTrue(herbalismManager.rollGreenThumbReplant(),
                "Green Terra active -> replant roll always succeeds");
    }
}
