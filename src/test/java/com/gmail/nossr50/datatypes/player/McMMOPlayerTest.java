package com.gmail.nossr50.datatypes.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the Phase 10.1 stripped {@link McMMOPlayer} god-object MC-free. The player handle is a
 * mocked {@link PlatformPlayer} (a final adapter over {@code ServerPlayerEntity}, so it can't be
 * built without a running server), while the real bundled {@code config.yml} + {@code experience.yml}
 * are wired through {@link McMMOMod} so the XP pipeline runs against genuine curve/cap logic.
 *
 * <p>The headline case is {@code beginXpGain} → level-up, proving the ported XP chain actually
 * reaches the profile (the legacy XP-add lived inside the deferred {@code EventUtils.handleXpGainEvent}
 * and had to be retained explicitly). Both static configs are reset to {@code null} after each test
 * so classes that rely on an un-wired config (e.g. FormulaManagerTest) are not polluted.
 */
class McMMOPlayerTest {

    private static final UUID UID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private ExperienceConfig experienceConfig;
    private PlatformPlayer player;
    private PlayerProfile profile;
    private McMMOPlayer mmoPlayer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        experienceConfig = new ExperienceConfig(dataFolder);
        McMMOMod.setExperienceConfig(experienceConfig);
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        player = mock(PlatformPlayer.class);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.getUniqueId()).thenReturn(UID);
        when(player.isCreative()).thenReturn(false);

        profile = new PlayerProfile("TestPlayer", UID, 0);
        mmoPlayer = new McMMOPlayer(player, profile);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setAdvancedConfig(null);
    }

    @Test
    void constructorBackfillsProfileUuidWhenMissing() {
        final PlatformPlayer freshPlayer = mock(PlatformPlayer.class);
        when(freshPlayer.getName()).thenReturn("NoId");
        when(freshPlayer.getUniqueId()).thenReturn(UID);

        final PlayerProfile uuidLess = new PlayerProfile("NoId", null, 0);
        new McMMOPlayer(freshPlayer, uuidLess);

        assertEquals(UID, uuidLess.getUniqueId(), "ctor backfills the profile UUID from the player");
    }

    @Test
    void accessorsExposePlayerProfileAndName() {
        assertSame(player, mmoPlayer.getPlayer());
        assertSame(profile, mmoPlayer.getProfile());
        assertEquals("TestPlayer", mmoPlayer.getPlayerName());
    }

    @Test
    void beginXpGainAwardsXpAndLevelsUpTheSkill() {
        // Award just over one level's worth of raw XP (accounting for the config XP modifiers), so
        // the gain crosses exactly one level boundary.
        final int levelZeroCost = mmoPlayer.getXpToLevel(PrimarySkillType.MINING);
        final double modifier = experienceConfig.getFormulaSkillModifier(PrimarySkillType.MINING)
                * experienceConfig.getExperienceGainsGlobalMultiplier();
        final float award = (float) (levelZeroCost / modifier) + 1f;

        mmoPlayer.beginXpGain(PrimarySkillType.MINING, award, XPGainReason.PVE, XPGainSource.SELF);

        assertEquals(1, mmoPlayer.getSkillLevel(PrimarySkillType.MINING),
                "one level's worth of XP grants exactly one level");
        assertEquals(1, mmoPlayer.getPowerLevel(),
                "power level reflects the single MINING level");
        assertTrue(mmoPlayer.getSkillXpLevelRaw(PrimarySkillType.MINING)
                        < mmoPlayer.getXpToLevel(PrimarySkillType.MINING),
                "leftover XP after the level-up is below the next level's cost");
    }

    @Test
    void nonPositiveXpIsIgnored() {
        mmoPlayer.beginXpGain(PrimarySkillType.MINING, 0f, XPGainReason.PVE, XPGainSource.SELF);
        mmoPlayer.beginXpGain(PrimarySkillType.MINING, -50f, XPGainReason.PVE, XPGainSource.SELF);

        assertEquals(0, mmoPlayer.getSkillLevel(PrimarySkillType.MINING));
        assertEquals(0f, mmoPlayer.getSkillXpLevelRaw(PrimarySkillType.MINING));
    }

    @Test
    void creativePlayersGainNoXp() {
        when(player.isCreative()).thenReturn(true);

        mmoPlayer.beginXpGain(PrimarySkillType.MINING, 100_000f, XPGainReason.PVE,
                XPGainSource.SELF);

        assertEquals(0, mmoPlayer.getSkillLevel(PrimarySkillType.MINING));
        assertEquals(0f, mmoPlayer.getSkillXpLevelRaw(PrimarySkillType.MINING),
                "creative mode short-circuits the gain before it reaches the profile");
    }

    @Test
    void childSkillGainSplitsAcrossParents() {
        // SMELTING's parents are MINING and REPAIR. A small gain stays below the level-up threshold,
        // so it accumulates as raw XP on each parent.
        mmoPlayer.beginXpGain(PrimarySkillType.SMELTING, 50f, XPGainReason.PVE, XPGainSource.SELF);

        assertTrue(mmoPlayer.getSkillXpLevelRaw(PrimarySkillType.MINING) > 0f,
                "MINING (a SMELTING parent) received part of the split");
        assertTrue(mmoPlayer.getSkillXpLevelRaw(PrimarySkillType.REPAIR) > 0f,
                "REPAIR (a SMELTING parent) received part of the split");
    }

    @Test
    void freshPlayerHasNotReachedAnyCap() {
        // Default config caps are unlimited (Integer.MAX_VALUE), so a level-0 player is never capped.
        assertFalse(mmoPlayer.hasReachedPowerLevelCap());
        assertFalse(mmoPlayer.hasReachedLevelCap(PrimarySkillType.MINING));
    }

    @Test
    void modifyXpGainAppliesSkillAndGlobalMultipliers() {
        final float raw = 100f;
        final double expected = raw
                * experienceConfig.getFormulaSkillModifier(PrimarySkillType.MINING)
                * experienceConfig.getExperienceGainsGlobalMultiplier();

        assertEquals((float) expected, mmoPlayer.modifyXpGain(PrimarySkillType.MINING, raw), 0.001f);
    }

    @Test
    void abilityModeStateRoundTrips() {
        assertFalse(mmoPlayer.getAbilityMode(SuperAbilityType.SUPER_BREAKER));
        mmoPlayer.setAbilityMode(SuperAbilityType.SUPER_BREAKER, true);
        assertTrue(mmoPlayer.getAbilityMode(SuperAbilityType.SUPER_BREAKER));

        // abilityInformed seeds to true by design.
        assertTrue(mmoPlayer.getAbilityInformed(SuperAbilityType.SUPER_BREAKER));
        mmoPlayer.setAbilityInformed(SuperAbilityType.SUPER_BREAKER, false);
        assertFalse(mmoPlayer.getAbilityInformed(SuperAbilityType.SUPER_BREAKER));
    }

    @Test
    void toolPreparationModeRoundTripsAndResets() {
        assertFalse(mmoPlayer.getToolPreparationMode(ToolType.PICKAXE));
        mmoPlayer.setToolPreparationMode(ToolType.PICKAXE, true);
        assertTrue(mmoPlayer.getToolPreparationMode(ToolType.PICKAXE));

        mmoPlayer.resetToolPrepMode();
        assertFalse(mmoPlayer.getToolPreparationMode(ToolType.PICKAXE),
                "resetToolPrepMode clears every tool");
    }

    @Test
    void flagsToggle() {
        assertFalse(mmoPlayer.getGodMode());
        mmoPlayer.toggleGodMode();
        assertTrue(mmoPlayer.getGodMode());

        assertFalse(mmoPlayer.isDebugMode());
        mmoPlayer.toggleDebugMode();
        assertTrue(mmoPlayer.isDebugMode());

        assertTrue(mmoPlayer.useChatNotifications());
        mmoPlayer.toggleChatNotifications();
        assertFalse(mmoPlayer.useChatNotifications());
    }

    @Test
    void abilityUseTogglesAndDefaultsOn() {
        assertTrue(mmoPlayer.getAbilityUse());
        mmoPlayer.toggleAbilityUse();
        assertFalse(mmoPlayer.getAbilityUse());
    }

    // --- Super-ability cooldown / duration core (Phase 11.2) ----------------

    @Test
    void freshAbilityIsNotOnCooldown() {
        // A fresh profile has DATS 0, so (0 + cooldown) is far in the past → not on cooldown.
        assertFalse(mmoPlayer.isAbilityOnCooldown(SuperAbilityType.SUPER_BREAKER));
        assertTrue(mmoPlayer.calculateTimeRemaining(SuperAbilityType.SUPER_BREAKER) <= 0,
                "an ability never used is off cooldown");
    }

    @Test
    void recentlyDeactivatedAbilityIsOnCooldownForItsFullCooldown() {
        // Super Breaker's configured cooldown is 240s (config.yml). Deactivating "now" leaves ~240s.
        mmoPlayer.setAbilityDATS(SuperAbilityType.SUPER_BREAKER, System.currentTimeMillis());

        assertTrue(mmoPlayer.isAbilityOnCooldown(SuperAbilityType.SUPER_BREAKER));
        final int remaining = mmoPlayer.calculateTimeRemaining(SuperAbilityType.SUPER_BREAKER);
        assertTrue(remaining >= 238 && remaining <= 240,
                "remaining should be ~240s (the Super Breaker cooldown), was " + remaining);
    }

    @Test
    void activeAbilityIsNeverReportedOnCooldown() {
        // Even with a fresh-deactivation timestamp, an *active* ability is not "on cooldown".
        mmoPlayer.setAbilityDATS(SuperAbilityType.SUPER_BREAKER, System.currentTimeMillis());
        mmoPlayer.setAbilityMode(SuperAbilityType.SUPER_BREAKER, true);

        assertFalse(mmoPlayer.isAbilityOnCooldown(SuperAbilityType.SUPER_BREAKER),
                "an active ability is running, not cooling down");
    }

    @Test
    void resetAbilityModeClearsEveryActiveMode() {
        mmoPlayer.setAbilityMode(SuperAbilityType.SUPER_BREAKER, true);
        mmoPlayer.setAbilityMode(SuperAbilityType.BERSERK, true);

        mmoPlayer.resetAbilityMode();

        assertFalse(mmoPlayer.getAbilityMode(SuperAbilityType.SUPER_BREAKER));
        assertFalse(mmoPlayer.getAbilityMode(SuperAbilityType.BERSERK));
    }

    @Test
    void activationTicksScaleWithLevelThenCap() {
        // RetroMode defaults true; bundled advanced.yml RetroMode: IncreaseLevel = 50, CapLevel = 1000.
        // Super Breaker's Max_Seconds is 0 (no per-ability cap), so ticks = 2 + min(1000, level) / 50.
        final PrimarySkillType skill = PrimarySkillType.MINING;
        final SuperAbilityType ability = SuperAbilityType.SUPER_BREAKER;

        // Level 0 → base 2 ticks.
        assertEquals(2, mmoPlayer.calculateAbilityActivationTicks(skill, ability));

        mmoPlayer.addLevels(skill, 250); // 2 + 250/50 = 7
        assertEquals(7, mmoPlayer.calculateAbilityActivationTicks(skill, ability));

        mmoPlayer.addLevels(skill, 950); // level 1200, capped at 1000 → 2 + 1000/50 = 22
        assertEquals(22, mmoPlayer.calculateAbilityActivationTicks(skill, ability));
    }
}
