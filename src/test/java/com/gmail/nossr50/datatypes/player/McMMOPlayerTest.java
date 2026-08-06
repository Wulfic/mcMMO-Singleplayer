package com.gmail.nossr50.datatypes.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
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
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.player.PlayerLevelUtils;
import com.gmail.nossr50.util.skills.SkillUtils;
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

    /**
     * {@code EarlyGameBoost.Enabled} — dead in this port until the 2026-08-06 wiring audit: the key,
     * the getter, the ModMenu switch and the {@code XPBar.Template.EarlyGameBoost} locale string all
     * shipped and no code read any of them.
     *
     * <p>The four cases below are the whole gate. They assert the <em>exact</em> bonus rather than
     * "more than before", because a boost applied twice, or applied before the global multiplier
     * instead of after, is invisible to a greater-than assertion.
     */
    @Test
    void earlyGameBoostTopsUpASkillStillAtLevelZero() {
        final float bonus = PlayerLevelUtils.earlyGameBonusXp(
                mmoPlayer.getXpToLevel(PrimarySkillType.MINING));
        assertTrue(bonus > 0f, "the shipped curve must cost enough for 5% to round above zero");

        assertEquals(100f + bonus,
                mmoPlayer.applySelfListenerModifiers(PrimarySkillType.MINING, 100f,
                        XPGainReason.PVE),
                "a level-0 skill gets a flat 5%-of-a-level top-up on every gain");
    }

    @Test
    void earlyGameBoostStopsAtTheFirstLevel() {
        profile.addLevels(PrimarySkillType.MINING, 1);

        assertEquals(100f,
                mmoPlayer.applySelfListenerModifiers(PrimarySkillType.MINING, 100f,
                        XPGainReason.PVE),
                "past the cutoff the gain is untouched");
    }

    /** Legacy's first check: {@code /addxp 500} must add 500, boost or no boost. */
    @Test
    void earlyGameBoostSkipsCommandGrantedXp() {
        assertEquals(100f,
                mmoPlayer.applySelfListenerModifiers(PrimarySkillType.MINING, 100f,
                        XPGainReason.COMMAND),
                "an admin-granted amount is exact by definition");
    }

    @Test
    void earlyGameBoostRespectsItsSwitch() {
        final ExperienceConfig disabled = spy(experienceConfig);
        when(disabled.isEarlyGameBoostEnabled()).thenReturn(false);
        McMMOMod.setExperienceConfig(disabled);

        assertEquals(100f,
                mmoPlayer.applySelfListenerModifiers(PrimarySkillType.MINING, 100f,
                        XPGainReason.PVE),
                "turning the switch off must actually reach the XP pipeline — the whole point of "
                        + "wiring it. A spy on the real config, because Mockito hands back null for "
                        + "the records this config returns elsewhere.");
    }

    /**
     * The boost must ride the pipeline, not just the helper: {@code applySelfListenerModifiers} is
     * only correct if {@code applyXpGain} actually calls it, and a seam nobody reaches is the exact
     * defect this whole pass is closing.
     */
    @Test
    void earlyGameBoostReachesTheProfileThroughBeginXpGain() {
        final double modifier = experienceConfig.getFormulaSkillModifier(PrimarySkillType.MINING)
                * experienceConfig.getExperienceGainsGlobalMultiplier();
        final float bonus = PlayerLevelUtils.earlyGameBonusXp(
                mmoPlayer.getXpToLevel(PrimarySkillType.MINING));

        mmoPlayer.beginXpGain(PrimarySkillType.MINING, 100f, XPGainReason.PVE, XPGainSource.SELF);

        assertEquals((float) (100f * modifier) + bonus,
                mmoPlayer.getSkillXpLevelRaw(PrimarySkillType.MINING), 0.001f,
                "the profile holds the multiplied gain PLUS the flat bonus — the bonus is added "
                        + "after the multipliers, so the global XP rate does not scale it");
    }

    // ---- Diminished returns (TODO 4(b)) -------------------------------------------------------
    //
    // Half-built since Phase 11: PlayerProfile#registerXpGain recorded every gain and
    // ClearRegisteredXPGainTask expired them every 60 ticks, and NOTHING read the rolling totals.
    // A whole anti-grind system, one call short.
    //
    // The shipped config for MINING makes the arithmetic exact and worth stating once:
    //   threshold 20000, Skill_Multiplier 1.0, global multiplier 1.0  =>  modifiedThreshold 20000
    //   overage = (registered - 20000) / 20000
    //   reduced = xp - xp*overage, floored at Guaranteed_Minimum_Percentage (0.05) * xp
    // Every case below asserts an exact value rather than "less than before", because a throttle
    // applied twice, or applied to the wrong operand, still produces "less".

    /** Enables the throttle on a spy of the real config — mock() hands back null for its records. */
    private ExperienceConfig withDiminishedReturnsOn() {
        final ExperienceConfig on = spy(experienceConfig);
        when(on.getDiminishedReturnsEnabled()).thenReturn(true);
        McMMOMod.setExperienceConfig(on);
        return on;
    }

    /**
     * The shipped default is {@code Enabled: false}, and this is the case that proves wiring the gate
     * did not silently retune anyone's world. A player 40000 XP over the threshold is untouched.
     */
    @Test
    void diminishedReturnsIsOffByDefaultSoAGrindedSkillIsUntouched() {
        profile.registerXpGain(PrimarySkillType.MINING, 60_000f);

        assertEquals(100f, mmoPlayer.applyDiminishedReturns(PrimarySkillType.MINING, 100f),
                "Diminished_Returns.Enabled ships false — the throttle must be inert until the "
                        + "player turns it on");
    }

    @Test
    void diminishedReturnsLeavesAGainUnderTheThresholdAlone() {
        withDiminishedReturnsOn();
        profile.registerXpGain(PrimarySkillType.MINING, 19_999f);

        assertEquals(100f, mmoPlayer.applyDiminishedReturns(PrimarySkillType.MINING, 100f),
                "under the threshold the gain is paid in full");
    }

    @Test
    void diminishedReturnsScalesAGainDownInProportionToTheOverage() {
        withDiminishedReturnsOn();
        // 30000 registered against a 20000 threshold = 50% over, so the gain pays 50%.
        profile.registerXpGain(PrimarySkillType.MINING, 30_000f);

        assertEquals(50f, mmoPlayer.applyDiminishedReturns(PrimarySkillType.MINING, 100f), 0.001f,
                "50% over the threshold pays 50% of the gain");
    }

    /**
     * Far enough over and the raw formula goes negative; the guaranteed minimum is what stops a
     * farmed skill paying nothing at all. Without the floor this returns 0 — so a test that only
     * checked "less than 100" would pass on a broken floor.
     */
    @Test
    void diminishedReturnsNeverFallsBelowTheGuaranteedMinimum() {
        withDiminishedReturnsOn();
        // 200% over: xp - xp*2 = -100, floored to 0.05 * 100.
        profile.registerXpGain(PrimarySkillType.MINING, 60_000f);

        assertEquals(5f, mmoPlayer.applyDiminishedReturns(PrimarySkillType.MINING, 100f), 0.001f,
                "the 5% floor holds however far over the threshold the window total is");
    }

    /** {@code Guaranteed_Minimum_Percentage: 0} removes the floor — the file says so, so prove it. */
    @Test
    void aZeroedGuaranteedMinimumLetsTheGainReachNothing() {
        final ExperienceConfig on = withDiminishedReturnsOn();
        when(on.getDiminishedReturnsCap()).thenReturn(0f);
        profile.registerXpGain(PrimarySkillType.MINING, 60_000f);

        assertEquals(0f, mmoPlayer.applyDiminishedReturns(PrimarySkillType.MINING, 100f), 0.001f,
                "with no floor a hard-farmed skill can be throttled all the way to zero — and never "
                        + "past it into a negative");
    }

    /** Legacy's own opt-out: a non-positive threshold turns the throttle off for that skill. */
    @Test
    void aNonPositiveThresholdDisablesTheThrottleForThatSkill() {
        final ExperienceConfig on = withDiminishedReturnsOn();
        when(on.getDiminishedReturnsThreshold(PrimarySkillType.MINING)).thenReturn(0);
        profile.registerXpGain(PrimarySkillType.MINING, 60_000f);

        assertEquals(100f, mmoPlayer.applyDiminishedReturns(PrimarySkillType.MINING, 100f),
                "threshold 0 means unthrottled, not throttled to nothing");
    }

    /**
     * The deliberate deviation from legacy. Both multipliers the threshold is divided by are ModMenu
     * sliders with a {@code 0.0} minimum, so a player can drive the divisor to zero from the settings
     * screen. Legacy divided anyway and handed the resulting {@code NaN}/{@code Infinity} to the
     * profile — which then persists to disk. Two cases because they fail differently: a zeroed skill
     * multiplier makes the threshold infinite, a zeroed global multiplier makes it zero.
     */
    @Test
    void aZeroedXpMultiplierMakesTheThrottleStepAsideRatherThanDivideByZero() {
        final ExperienceConfig on = withDiminishedReturnsOn();
        when(on.getFormulaSkillModifier(PrimarySkillType.MINING)).thenReturn(0.0D);
        profile.registerXpGain(PrimarySkillType.MINING, 60_000f);

        final float infiniteThreshold =
                mmoPlayer.applyDiminishedReturns(PrimarySkillType.MINING, 100f);
        assertTrue(Float.isFinite(infiniteThreshold), "a zeroed skill multiplier must not yield NaN");
        assertEquals(100f, infiniteThreshold);

        when(on.getFormulaSkillModifier(PrimarySkillType.MINING)).thenReturn(1.0D);
        when(on.getExperienceGainsGlobalMultiplier()).thenReturn(0.0D);

        final float zeroThreshold = mmoPlayer.applyDiminishedReturns(PrimarySkillType.MINING, 100f);
        assertTrue(Float.isFinite(zeroThreshold), "a zeroed global multiplier must not yield NaN");
        assertEquals(100f, zeroThreshold);
    }

    /** A negative gain must not be scaled towards zero — that would soften a penalty. */
    @Test
    void diminishedReturnsIgnoresANonPositiveGain() {
        withDiminishedReturnsOn();
        profile.registerXpGain(PrimarySkillType.MINING, 60_000f);

        assertEquals(-50f, mmoPlayer.applyDiminishedReturns(PrimarySkillType.MINING, -50f),
                "an XP subtraction passes through untouched");
    }

    /**
     * A child skill's gain is split to its parents before the XP path is reached, so the child has no
     * rolling total of its own and the parents are throttled instead. Agility shipped a
     * {@code Diminished_Returns.Threshold} row until 2026-08-06 that could never be read; this is the
     * behaviour that made it dead.
     */
    @Test
    void aChildSkillIsNeverThrottledDirectly() {
        withDiminishedReturnsOn();
        profile.registerXpGain(PrimarySkillType.AGILITY, 60_000f);

        assertEquals(100f, mmoPlayer.applyDiminishedReturns(PrimarySkillType.AGILITY, 100f),
                "AGILITY is a child of Parkour/Swimming/Flying — the throttle applies to them");
    }

    /** An admin grant is exact by definition, and must skip the throttle as well as the boost. */
    @Test
    void commandGrantedXpSkipsTheThrottleToo() {
        withDiminishedReturnsOn();
        profile.registerXpGain(PrimarySkillType.MINING, 60_000f);

        assertEquals(100f, mmoPlayer.applySelfListenerModifiers(PrimarySkillType.MINING, 100f,
                        XPGainReason.COMMAND),
                "/addxp 100 must add 100 no matter how hard the skill has been ground");
    }

    /**
     * The throttle must ride the real pipeline, not just its own helper — a seam nobody reaches is
     * the exact defect this whole pass exists to close. MINING is put past the early-game cutoff so
     * the boost contributes nothing and the arithmetic stays the helper's.
     *
     * <p>The second assertion is the one that keeps the window honest: the rolling total must grow by
     * the <em>throttled</em> amount, so a throttled skill also fills its own window more slowly.
     */
    @Test
    void diminishedReturnsReachesTheProfileThroughBeginXpGain() {
        withDiminishedReturnsOn();
        profile.addLevels(PrimarySkillType.MINING, 5);
        profile.registerXpGain(PrimarySkillType.MINING, 30_000f);

        mmoPlayer.beginXpGain(PrimarySkillType.MINING, 100f, XPGainReason.PVE, XPGainSource.SELF);

        assertEquals(50f, mmoPlayer.getSkillXpLevelRaw(PrimarySkillType.MINING), 0.001f,
                "the profile holds the throttled gain, not the raw one");
        assertEquals(30_050f, profile.getRegisteredXpGain(PrimarySkillType.MINING), 0.001f,
                "the rolling window records what was actually paid");
    }

    @Test
    void levellingAParentAlsoFiresItsChildSkillsMilestonePlaques() {
        // A child skill's level climbs without ever reaching the XP path — beginXpGain splits a child
        // gain to its parents and returns — so a milestone hook that only tracks the skill that
        // literally levelled makes the child's plaques silently stop firing forever. That is what
        // would have happened to Agility's ten sub-skill plaques when it became a child of
        // Parkour/Swimming/Flying, and the failure mode is silence, not an error.
        //
        // Agility = (Parkour + Swimming + Flying) / 3, so Parkour 299 -> 300 takes Agility 99 -> 100
        // and crosses the 100-level plaque interval for BOTH skills at once.
        profile.addLevels(PrimarySkillType.PARKOUR, 299);
        assertEquals(99, mmoPlayer.getSkillLevel(PrimarySkillType.AGILITY));

        final int levelCost = mmoPlayer.getXpToLevel(PrimarySkillType.PARKOUR);
        final double modifier = experienceConfig.getFormulaSkillModifier(PrimarySkillType.PARKOUR)
                * experienceConfig.getExperienceGainsGlobalMultiplier();
        mmoPlayer.beginXpGain(PrimarySkillType.PARKOUR, (float) (levelCost / modifier) + 1f,
                XPGainReason.PVE, XPGainSource.SELF);

        assertEquals(300, mmoPlayer.getSkillLevel(PrimarySkillType.PARKOUR));
        assertEquals(100, mmoPlayer.getSkillLevel(PrimarySkillType.AGILITY));
        // Each plaque is titled for the standing that skill actually reached, so the parent and the
        // child land on different tiers off the same XP gain (Parkour 300 = Adept, Agility 100 =
        // Apprentice) rather than sharing one vague "milestone" id.
        verify(player).grantMilestoneAdvancement("level/parkour/adept", true);
        verify(player).grantMilestoneAdvancement("level/agility/apprentice", true);
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

    // --- respawn exploit timestamp ------------------------------------------

    /**
     * Pins the unit contract, which is the whole trap here:
     * {@link com.gmail.nossr50.util.skills.SkillUtils#cooldownExpired} multiplies its timestamp by
     * {@link Misc#TIME_CONVERSION_FACTOR}, so storing millis would push every grace deadline ~31,000
     * years out and silently disable the payouts the timestamp gates, rather than failing loudly.
     */
    @Test
    void respawnTimestampIsStampedOnLoginInSecondsNotMillis() {
        final long nowSeconds = System.currentTimeMillis() / Misc.TIME_CONVERSION_FACTOR;

        // Within a couple of seconds of "now" — and, critically, ~1000x smaller than a millis value.
        assertTrue(Math.abs(nowSeconds - mmoPlayer.getRespawnATS()) <= 2,
                "ctor stamps the timestamp (legacy did it from PlayerProfileLoadingTask), in seconds");
        assertTrue(SkillUtils.cooldownExpired(mmoPlayer.getRespawnATS(), 0),
                "a seconds-unit timestamp is already in the past at a zero cooldown");
        assertFalse(SkillUtils.cooldownExpired(mmoPlayer.getRespawnATS(),
                        Misc.PLAYER_RESPAWN_COOLDOWN_SECONDS),
                "a fresh login is inside the grace window");
    }

    @Test
    void actualizeRespawnATSAdvancesTheTimestampPastAnOlderOne() {
        // Backdate past the window, then re-stamp the way PlayerSessionListener#onRespawn does.
        final int stale = (int) (System.currentTimeMillis() / Misc.TIME_CONVERSION_FACTOR)
                - (Misc.PLAYER_RESPAWN_COOLDOWN_SECONDS * 10);
        assertTrue(SkillUtils.cooldownExpired(stale, Misc.PLAYER_RESPAWN_COOLDOWN_SECONDS));

        mmoPlayer.actualizeRespawnATS();

        assertTrue(mmoPlayer.getRespawnATS() > stale, "a respawn moves the timestamp forward");
        assertFalse(SkillUtils.cooldownExpired(mmoPlayer.getRespawnATS(),
                        Misc.PLAYER_RESPAWN_COOLDOWN_SECONDS),
                "and re-opens the grace window");
    }

    // --- child-skill XP-bar progress --------------------------------------------------------------

    @Test
    void aChildSkillsProgressIsTheMeanOfItsParents() {
        // Agility earns no XP of its own, so there is no "progress through the current level" to
        // read off its profile — its bar has to average the three skills its level is averaged from.
        // Legacy returned a flat 1.0 here, which was invisible while every child bar was hidden but
        // means a permanently full bar now that Agility shows one.
        final double parkour = mmoPlayer.getProgressInCurrentSkillLevel(PrimarySkillType.PARKOUR);
        final double swimming = mmoPlayer.getProgressInCurrentSkillLevel(PrimarySkillType.SWIMMING);
        final double flying = mmoPlayer.getProgressInCurrentSkillLevel(PrimarySkillType.FLYING);

        assertEquals((parkour + swimming + flying) / 3.0,
                mmoPlayer.getProgressInCurrentSkillLevel(PrimarySkillType.AGILITY), 1.0E-9);
    }

    @Test
    void trainingOneParentMovesTheChildBarByAThird() {
        final double before = mmoPlayer.getProgressInCurrentSkillLevel(PrimarySkillType.AGILITY);

        mmoPlayer.beginXpGain(PrimarySkillType.PARKOUR, 10F, XPGainReason.PVE, XPGainSource.SELF);

        final double after = mmoPlayer.getProgressInCurrentSkillLevel(PrimarySkillType.AGILITY);
        assertTrue(after > before, "the child bar must actually move when a parent gains XP");
        // A third of the parent's own movement — one of three parents advanced.
        assertEquals(mmoPlayer.getProgressInCurrentSkillLevel(PrimarySkillType.PARKOUR) / 3.0,
                after, 1.0E-9);
    }

    @Test
    void aChildSkillsProgressIsNotPinnedAtFull() {
        // The specific regression: a flat 1.0 would make this assertion fail and the bar useless.
        assertTrue(mmoPlayer.getProgressInCurrentSkillLevel(PrimarySkillType.AGILITY) < 1.0,
                "a fresh player's Agility bar must not read as full");
    }
}
