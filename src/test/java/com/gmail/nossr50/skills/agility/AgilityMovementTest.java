package com.gmail.nossr50.skills.agility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The MC-free half of Agility's three new movement domains: the XP accumulator, the per-medium rank
 * gating, and every sub-skill's scaling and clamp.
 *
 * <p>Rank plumbing is real ({@link RankConfig} loaded from the bundled {@code skillranks.yml}) so the
 * per-medium unlock ladder is exercised as shipped rather than mocked into always-true — the whole
 * point of Fleet Footed and Second Wind carrying one rank per medium is that a mid-level player has
 * some and not others, and a mocked gate would never catch getting that mapping backwards.
 */
class AgilityMovementTest {

    private static final double EPSILON = 1.0E-9;

    private AdvancedConfig advancedConfig;
    private ExperienceConfig experienceConfig;
    private PlatformPlayer player;
    private McMMOPlayer mmoPlayer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        advancedConfig = mock(AdvancedConfig.class);
        experienceConfig = mock(ExperienceConfig.class);
        player = mock(PlatformPlayer.class);

        lenient().when(advancedConfig.getFleetFootedMaxBonusLevel(Medium.LAND)).thenReturn(1000);
        lenient().when(advancedConfig.getFleetFootedMaxBonusLevel(Medium.WATER)).thenReturn(1000);
        lenient().when(advancedConfig.getFleetFootedMaxBonusLevel(Medium.AIR)).thenReturn(1000);
        lenient().when(advancedConfig.getFleetFootedMaxBonus(Medium.LAND)).thenReturn(0.20);
        lenient().when(advancedConfig.getFleetFootedMaxBonus(Medium.WATER)).thenReturn(0.50);
        lenient().when(advancedConfig.getFleetFootedMaxBonus(Medium.AIR)).thenReturn(0.15);
        lenient().when(advancedConfig.getAthleteMaxBonusLevel()).thenReturn(1000);
        lenient().when(advancedConfig.getAthleteMaxExhaustionReduction()).thenReturn(0.5);
        lenient().when(advancedConfig.getLeadLungsMaxBonusLevel()).thenReturn(1000);
        lenient().when(advancedConfig.getLeadLungsMaxAirTopUpPerTick()).thenReturn(0.75);
        lenient().when(advancedConfig.getGlideMaxBonusLevel()).thenReturn(1000);
        lenient().when(advancedConfig.getGlideMaxDescentReduction()).thenReturn(0.5);
        lenient().when(advancedConfig.getSmashBonusDamage()).thenReturn(2.0);
        lenient().when(advancedConfig.getSmashKnockbackStrength()).thenReturn(0.8);
        lenient().when(advancedConfig.getSecondWindDartRange()).thenReturn(6.0);
        lenient().when(advancedConfig.getSecondWindDartDamage()).thenReturn(6.0);
        lenient().when(advancedConfig.getSecondWindDartKnockback()).thenReturn(1.5);
        lenient().when(advancedConfig.getSecondWindAquamanAmplifier()).thenReturn(1);
        lenient().when(advancedConfig.getSecondWindLimitlessBoost()).thenReturn(1.2);
        lenient().when(advancedConfig.getSolarWingsRepairPerInterval()).thenReturn(1);
        lenient().when(advancedConfig.getSolarWingsIntervalTicks()).thenReturn(100);
        lenient().when(advancedConfig.getSolarWingsGroundedMultiplier()).thenReturn(2);

        McMMOMod.setAdvancedConfig(advancedConfig);
        McMMOMod.setExperienceConfig(experienceConfig);
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));

        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(player);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
    }

    /**
     * A manager for an <b>all-rounder</b> at {@code level}: equal in Parkour, Swimming and Flying,
     * which makes Agility (their mean) exactly {@code level} too.
     *
     * <p>All four are stubbed because the 2026-08-10 re-parenting split which level each sub-skill
     * reads — Fleet Footed and Second Wind still gate on Agility, while Athlete/Smash/Dodge gate on
     * Parkour, Lead Lungs/Lake Raider on Swimming and Glide/Solar Wings on Flying. Stubbing them to
     * one number keeps every scaling and clamp assertion in this class meaning exactly what it did
     * before the move: those tests are about the ladder, not about which skill feeds it.
     *
     * <p>Which level each sub-skill actually reads is proved by
     * {@link #reParentedSubSkillsFollowTheirOwnParentNotTheAverage()}, where the three parents
     * deliberately disagree.
     */
    private AgilityManager managerAtLevel(int level) {
        return managerAtLevels(level, level, level);
    }

    /**
     * A manager whose three movement levels are set independently.
     *
     * <p>A fourth {@code agility} stub stood first in this list until 2026-08-17. It was the wrong
     * answer every test here existed to catch; the constant is gone, so reading the mean is now a
     * compile error rather than a number. ⚠️ That makes the REMAINING discriminator the only one:
     * the three parents must be set to <b>disagree</b>, or a test cannot tell which of them a gate
     * or a ramp actually read. A fixture with all three equal proves nothing about routing.
     */
    private AgilityManager managerAtLevels(int parkour, int swimming, int flying) {
        lenient().when(mmoPlayer.getSkillLevel(PrimarySkillType.PARKOUR)).thenReturn(parkour);
        lenient().when(mmoPlayer.getSkillLevel(PrimarySkillType.SWIMMING)).thenReturn(swimming);
        lenient().when(mmoPlayer.getSkillLevel(PrimarySkillType.FLYING)).thenReturn(flying);
        final AgilityManager manager = new AgilityManager(mmoPlayer);
        manager.setMovementXpSettings(defaultSettings());
        return manager;
    }

    private static MovementXpSettings defaultSettings() {
        final Map<Medium, Double> speeds = new EnumMap<>(Medium.class);
        speeds.put(Medium.LAND, 5.61);
        speeds.put(Medium.WATER, 3.16);
        speeds.put(Medium.AIR, 30.0);
        final Map<Medium, Double> multipliers = new EnumMap<>(Medium.class);
        multipliers.put(Medium.LAND, 1.0);
        multipliers.put(Medium.WATER, 1.15);
        multipliers.put(Medium.AIR, 0.6);
        return MovementXpSettings.of(30.0, speeds, multipliers);
    }

    private static double perTick(Medium medium) {
        return defaultSettings().referenceSpeed(medium) / MovementXpSettings.TICKS_PER_SECOND;
    }

    // --- onMovementTick: accumulate, flush whole XP ---------------------------------------------

    @Test
    void aSingleTickAccumulatesRatherThanPayingFractionalXp() {
        final AgilityManager manager = managerAtLevel(1);
        // One tick at the land reference speed is worth 30/20 = 1.5 XP under this fixture's pinned
        // baseline, so the first tick pays 1 and banks 0.5 — it must never hand a fraction to the XP
        // pipeline. Land pays Parkour, never Agility: Agility is a child skill, and a gain addressed
        // to it would be split three ways and quietly train swimming and flying too.
        assertEquals(1F, manager.onMovementTick(Medium.LAND, perTick(Medium.LAND)), EPSILON);
        verify(mmoPlayer).beginXpGain(PrimarySkillType.PARKOUR, 1F, XPGainReason.PVE,
                XPGainSource.SELF);
    }

    @Test
    void theBankedRemainderIsNotLost() {
        final AgilityManager manager = managerAtLevel(1);
        // 1.5 XP per tick: pays 1, banks .5 -> pays 2 (1.5 + .5), banks 0 -> pays 1, banks .5 ...
        // Over four ticks that is 6 XP total, which is exactly 4 x 1.5 with nothing truncated away.
        float total = 0;
        for (int tick = 0; tick < 4; tick++) {
            total += manager.onMovementTick(Medium.LAND, perTick(Medium.LAND));
        }
        assertEquals(6F, total, EPSILON);
    }

    @Test
    void aTinyMovementPaysNothingUntilItAddsUp() {
        final AgilityManager manager = managerAtLevel(1);
        // A hundredth of a tick's travel is worth 0.015 XP — nothing should reach the pipeline yet.
        assertEquals(0F, manager.onMovementTick(Medium.LAND, perTick(Medium.LAND) / 100), EPSILON);
        verify(mmoPlayer, never()).beginXpGain(any(), anyFloat(), any(), any());
    }

    @Test
    void standingStillNeverPays() {
        final AgilityManager manager = managerAtLevel(1000);
        for (Medium medium : Medium.values()) {
            assertEquals(0F, manager.onMovementTick(medium, 0.0), EPSILON);
        }
        verify(mmoPlayer, never()).beginXpGain(any(), anyFloat(), any(), any());
    }

    @Test
    void aRocketBoostedTickPaysNoMoreThanACruisingOne() {
        // The clamp, asserted through the manager rather than the settings object, because this is
        // the path that actually runs in game.
        final AgilityManager cruising = managerAtLevel(1000);
        final AgilityManager boosted = managerAtLevel(1000);

        assertEquals(cruising.onMovementTick(Medium.AIR, perTick(Medium.AIR)),
                boosted.onMovementTick(Medium.AIR, perTick(Medium.AIR) * 20), EPSILON);
    }

    // --- Fleet Footed: per-medium rank gating + scaling ------------------------------------------

    @Test
    void fleetFootedUnlocksInEveryMediumAtLevelOneOfThatMediumsSkill() {
        // Flattened on 2026-08-17 with the Agility retirement: skillranks.yml now carries
        // FleetFooted Rank_1: 1 under Parkour, Swimming AND Flying. It used to be one 3-rank ladder
        // at 1/200/400 read against the mean of the three, which is why the numbers here changed.
        final AgilityManager early = managerAtLevel(1);
        for (Medium medium : Medium.values()) {
            assertTrue(early.canFleetFoot(medium),
                    () -> medium + " unlocks at 1 in its own skill, and this player has 1 in all of "
                            + "them — a surviving mean-of-three gate would deny water and air here");
        }

        // Level 0 is the discriminating case: without it this test would pass against a gate that
        // was simply always true.
        final AgilityManager none = managerAtLevels(0, 0, 0);
        for (Medium medium : Medium.values()) {
            assertFalse(none.canFleetFoot(medium), () -> medium + " is locked below level 1");
        }
    }

    @Test
    void fleetFootedPaysNothingForALockedMedium() {
        // A pure runner: Parkour maxed, never swum, never flown. Under the retired mean-of-three gate
        // this player's Agility 333 would have paid a water bonus to someone who has never been in
        // water.
        final AgilityManager runner = managerAtLevels(1000, 0, 0);
        assertEquals(0.0, runner.getFleetFootedBonus(Medium.WATER), EPSILON);
        assertEquals(0.0, runner.getFleetFootedBonus(Medium.AIR), EPSILON);
        assertTrue(runner.getFleetFootedBonus(Medium.LAND) > 0.0,
                "the medium they actually trained must still pay, or this asserts nothing");
    }

    @Test
    void fleetFootedScalesLinearlyToItsPerMediumCap() {
        assertEquals(0.20, managerAtLevel(1000).getFleetFootedBonus(Medium.LAND), EPSILON);
        assertEquals(0.10, managerAtLevel(500).getFleetFootedBonus(Medium.LAND), EPSILON);
        // Water's own cap is different, and it is the water number that must be used.
        assertEquals(0.50, managerAtLevel(1000).getFleetFootedBonus(Medium.WATER), EPSILON);
        assertEquals(0.15, managerAtLevel(1000).getFleetFootedBonus(Medium.AIR), EPSILON);
    }

    @Test
    void fleetFootedNeverExceedsItsCapAboveTheBonusLevel() {
        when(advancedConfig.getFleetFootedMaxBonusLevel(any(Medium.class))).thenReturn(100);
        assertEquals(0.20, managerAtLevel(1000).getFleetFootedBonus(Medium.LAND), EPSILON,
                "level far past MaxBonusLevel must clamp, not keep scaling");
    }

    // --- Athlete -------------------------------------------------------------------------------

    @Test
    void athleteIsLockedBelowItsUnlockLevel() {
        assertEquals(1.0, managerAtLevel(1).getAthleteExhaustionMultiplier(), EPSILON,
                "locked -> exhaustion unchanged");
    }

    @Test
    void athleteScalesTowardsItsCap() {
        assertEquals(0.5, managerAtLevel(1000).getAthleteExhaustionMultiplier(), EPSILON);
        assertEquals(0.75, managerAtLevel(500).getAthleteExhaustionMultiplier(), EPSILON);
    }

    @Test
    void athleteCanNeverMakeSprintingFree() {
        // A config that asks for a 100% (or absurd) reduction must still leave sprinting with a cost;
        // a multiplier of 0 would remove hunger from the game for anyone who levels this skill.
        when(advancedConfig.getAthleteMaxExhaustionReduction()).thenReturn(5.0);
        final double multiplier = managerAtLevel(1000).getAthleteExhaustionMultiplier();
        assertTrue(multiplier > 0, "multiplier was " + multiplier);
        assertEquals(0.05, multiplier, EPSILON, "clamped to the 0.95 max reduction");
    }

    // --- Lead Lungs ----------------------------------------------------------------------------

    @Test
    void leadLungsIsLockedBelowItsUnlockLevel() {
        assertEquals(0.0, managerAtLevel(1).getLeadLungsAirTopUpPerTick(), EPSILON);
        assertEquals(0, managerAtLevel(1).consumeLeadLungsAirTopUp());
    }

    @Test
    void leadLungsAccumulatesFractionalAirIntoWholeTicks() {
        // Vanilla spends one air per tick and air is an integer, so a 0.75/tick top-up has to bank:
        // flooring every tick would return 0 forever and the sub-skill would do nothing at all.
        final AgilityManager manager = managerAtLevel(1000);
        assertEquals(0.75, manager.getLeadLungsAirTopUpPerTick(), EPSILON);

        assertEquals(0, manager.consumeLeadLungsAirTopUp(), "0.75 banked");
        assertEquals(1, manager.consumeLeadLungsAirTopUp(), "1.50 -> pay 1, bank 0.5");
        assertEquals(1, manager.consumeLeadLungsAirTopUp(), "1.25 -> pay 1, bank 0.25");
        assertEquals(1, manager.consumeLeadLungsAirTopUp(), "1.00 -> pay 1, bank 0");
    }

    @Test
    void leadLungsCanNeverGrantInfiniteBreath() {
        when(advancedConfig.getLeadLungsMaxAirTopUpPerTick()).thenReturn(2.0);
        assertEquals(0.95, managerAtLevel(1000).getLeadLungsAirTopUpPerTick(), EPSILON,
                "a top-up of 1.0 would exactly cancel vanilla's drain — clamp below it");
    }

    // --- Glide ---------------------------------------------------------------------------------

    @Test
    void glideIsLockedBelowItsUnlockLevel() {
        assertEquals(0.0, managerAtLevel(1).getGlideDescentReduction(), EPSILON);
    }

    @Test
    void glideScalesAndClampsBelowTotalNegation() {
        assertEquals(0.5, managerAtLevel(1000).getGlideDescentReduction(), EPSILON);
        when(advancedConfig.getGlideMaxDescentReduction()).thenReturn(1.0);
        assertEquals(0.9, managerAtLevel(1000).getGlideDescentReduction(), EPSILON,
                "a reduction of 1.0 would pin the player at altitude and make landing impossible");
    }

    // --- Solar Wings ---------------------------------------------------------------------------

    @Test
    void solarWingsIsLockedBelowItsUnlockLevel() {
        assertFalse(managerAtLevel(500).canSolarWings(), "unlocks at 750 in RetroMode");
        assertEquals(0, managerAtLevel(500).getSolarWingsRepairAmount(true));
        assertTrue(managerAtLevel(1000).canSolarWings());
    }

    @Test
    void solarWingsRepairsFasterOnTheGround() {
        final AgilityManager manager = managerAtLevel(1000);
        assertEquals(1, manager.getSolarWingsRepairAmount(false));
        assertEquals(2, manager.getSolarWingsRepairAmount(true));
    }

    // --- Second Wind ----------------------------------------------------------------------------

    @Test
    void everySecondWindBodyUnlocksAt250OfItsOwnMediumsSkill() {
        // Flattened on 2026-08-17: SecondWind Rank_1: 250 under Parkour, Swimming AND Flying. It used
        // to be one 3-rank ladder at 250/500/750 read against the mean of the three.
        final AgilityManager justUnder = managerAtLevel(249);
        for (Medium medium : Medium.values()) {
            assertFalse(justUnder.canSecondWind(medium), () -> medium + " is locked at 249");
        }

        final AgilityManager atThreshold = managerAtLevel(250);
        for (Medium medium : Medium.values()) {
            assertTrue(atThreshold.canSecondWind(medium),
                    () -> medium + " unlocks at exactly 250 of its own skill — under the retired "
                            + "ladder only the land body was live at this level");
        }
    }

    @Test
    void secondWindReturnsNullForALockedBodySoTheCooldownIsNotBurned() {
        // Returning null rather than a zeroed result is load-bearing: an all-zeros result is
        // indistinguishable from a legitimately weak one, and the caller has to be able to refuse
        // without consuming the cooldown.
        //
        // A runner at Parkour 1000 / Swimming 0 / Flying 0: Dart is live, the other two bodies are
        // not. Asserting a live one alongside the null ones is what stops this passing against a
        // computeSecondWind that returns null unconditionally.
        final AgilityManager runner = managerAtLevels(1000, 0, 0);
        assertNull(runner.computeSecondWind(Medium.WATER, 100));
        assertNull(runner.computeSecondWind(Medium.AIR, 100));
        assertNotNull(runner.computeSecondWind(Medium.LAND, 100));

        assertNull(managerAtLevel(1).computeSecondWind(Medium.LAND, 100),
                "below 250 in every skill, no body resolves");
    }

    @Test
    void secondWindResolvesADifferentBodyPerMedium() {
        final AgilityManager manager = managerAtLevel(1000);

        final SecondWindResult dart = manager.computeSecondWind(Medium.LAND, 100);
        assertNotNull(dart);
        assertEquals(Medium.LAND, dart.medium());
        assertEquals(0, dart.durationTicks(), "the land lunge is instantaneous");
        assertEquals(6.0, dart.dartRange(), EPSILON);
        assertEquals(6.0, dart.dartDamage(), EPSILON);

        final SecondWindResult aquaman = manager.computeSecondWind(Medium.WATER, 100);
        assertNotNull(aquaman);
        assertEquals(100, aquaman.durationTicks());
        assertEquals(1.0, aquaman.magnitude(), EPSILON, "effect amplifier");

        final SecondWindResult limitless = manager.computeSecondWind(Medium.AIR, 100);
        assertNotNull(limitless);
        assertEquals(100, limitless.durationTicks());
        assertEquals(1.2, limitless.magnitude(), EPSILON, "forward boost");
    }

    // --- Smash / Lake Raider gates ---------------------------------------------------------------

    @Test
    void smashAndLakeRaiderAreLockedBelowTheirUnlockLevels() {
        final AgilityManager early = managerAtLevel(1);
        assertFalse(early.canSmash(), "Smash unlocks at 150 in RetroMode");
        assertFalse(early.rollSmash(), "a locked sub-skill must never roll");
        assertFalse(early.canLakeRaider(), "Lake Raider unlocks at 500 in RetroMode");
        assertFalse(early.rollLakeRaiderSuccess());

        final AgilityManager maxed = managerAtLevel(1000);
        assertTrue(maxed.canSmash());
        assertTrue(maxed.canLakeRaider());
    }

    @Test
    void smashRollsAtTheConfiguredCeiling() {
        // Pin the RNG: a maxBonusLevel of 0 short-circuits ProbabilityUtil to the ceiling, so a
        // ceiling of 100 always succeeds and 0 never does. Same lever the Dodge tests use.
        when(advancedConfig.getMaxBonusLevel(SubSkillType.PARKOUR_SMASH)).thenReturn(0);

        when(advancedConfig.getMaximumProbability(SubSkillType.PARKOUR_SMASH)).thenReturn(100.0);
        assertTrue(managerAtLevel(1000).rollSmash());

        when(advancedConfig.getMaximumProbability(SubSkillType.PARKOUR_SMASH)).thenReturn(0.0);
        assertFalse(managerAtLevel(1000).rollSmash());
    }

    @Test
    void lakeRaiderPicksTheFirstTreasureWhoseStaticRollWins() {
        final AgilityManager manager = managerAtLevel(1000);
        // The main roll gates everything: a lost main roll must not consult the table at all.
        assertTrue(manager.rollLakeRaiderTreasure(java.util.List.of(), true, chance -> true)
                .isEmpty(), "no candidates -> nothing");
    }

    @Test
    void lakeRaiderPaysNothingWhenTheMainRollFails() {
        final AgilityManager manager = managerAtLevel(1000);
        final com.gmail.nossr50.datatypes.treasure.ExcavationTreasure treasure =
                new com.gmail.nossr50.datatypes.treasure.ExcavationTreasure(
                        new com.gmail.nossr50.datatypes.treasure.ItemSpec("diamond", 1), 0, 100.0, 0);

        assertTrue(manager.rollLakeRaiderTreasure(java.util.List.of(treasure), false,
                chance -> true).isEmpty(), "lost main roll -> no treasure even at 100% drop chance");
        assertEquals(treasure, manager.rollLakeRaiderTreasure(java.util.List.of(treasure), true,
                chance -> true).orElse(null));
    }

    @Test
    void everyMovementSubSkillIsInertForABrandNewPlayer() {
        // The whole new roster at once: a level-1 player should feel essentially like vanilla.
        //
        // ⚠️ Fleet Footed is the deliberate exception, and it grew on 2026-08-17. It unlocks at level
        // 1 in EVERY medium now, not just on land — flattening the ladder extended to water and air
        // the always-on-ness that land already had. What keeps that from being a real buff at level 1
        // is the SCALING, not the gate: the bonus is a linear ramp to MaxBonusLevel 1000, so a fresh
        // player gets a fraction of a percent. That is what is asserted here, because asserting the
        // gate is off would now be asserting something false.
        final AgilityManager fresh = managerAtLevel(1);
        for (Medium medium : Medium.values()) {
            assertTrue(fresh.canFleetFoot(medium), () -> medium + " is unlocked from level 1");
            assertTrue(fresh.getFleetFootedBonus(medium) < 0.001,
                    () -> medium + " pays a negligible bonus at level 1, not its cap: got "
                            + fresh.getFleetFootedBonus(medium));
        }
        assertEquals(1.0, fresh.getAthleteExhaustionMultiplier(), EPSILON);
        assertEquals(0.0, fresh.getLeadLungsAirTopUpPerTick(), EPSILON);
        assertEquals(0.0, fresh.getGlideDescentReduction(), EPSILON);
        assertFalse(fresh.canSolarWings());
        assertFalse(fresh.canSmash());
        assertFalse(fresh.canLakeRaider());
        assertNull(fresh.computeSecondWind(Medium.LAND, 100));
    }

    // --- re-parenting (2026-08-10): which LEVEL each sub-skill reads ----------------------------

    /**
     * The load-bearing guard for the 2026-08-10 move, and the reason it is worth having: a sub-skill's
     * parent is derived from its enum name prefix and <b>nothing reports getting it wrong</b> — a
     * constant renamed back to {@code AGILITY_*} silently re-gates onto the average with no error
     * anywhere, which is exactly how GitHub #4 shipped.
     *
     * <p>So the three parents are set to deliberately disagree. This player is a pure runner:
     * Parkour 1000, Swimming 0, Flying 0, hence Agility 333. Every Parkour sub-skill must be live and
     * every Swimming and Flying one must be dead — an assertion that is only satisfiable if each
     * reads its own parent, and that a mean-of-three gate fails in both directions at once.
     */
    /**
     * The <b>ramp</b>, as distinct from the gate — and the reason the two need separate tests.
     *
     * <p>⚠️ The 2026-08-10 re-parenting moved every movement sub-skill's <em>unlock</em> onto the
     * medium's own skill and left its <em>scaling</em> reading {@code AGILITY}, the mean of all three.
     * Nothing failed, because a gate and a ramp read the player's level in different places:
     * {@code reParentedSubSkillsFollowTheirOwnParentNotTheAverage} above proves the gates and would
     * pass with every ramp still on the mean.
     *
     * <p>⚠️ <b>The fixture had to be rebuilt when {@code AGILITY} was retired on 2026-08-17, and
     * a mechanical edit here would have produced a VACUOUS test.</b> It used to set all three parents
     * to 1000 while stubbing Agility at 333: the mean was the wrong answer and it was a third of the
     * right one. With no Agility to stub, three equal parents make every ramp max out <em>whichever
     * parent it reads</em> — the assertions would all pass against a manager that scaled everything
     * on Parkour. So the parents are now made to <b>disagree</b>: one specialist per medium, each
     * maxed in one skill and at zero in the other two. Only that discriminates.
     */
    @Test
    void everyScaledPassiveRampsOnItsOwnParentNotAnotherMediums() {
        final AgilityManager runner = managerAtLevels(1000, 0, 0);
        assertEquals(0.20, runner.getFleetFootedBonus(Medium.LAND), EPSILON,
                "land Fleet Footed must ramp on Parkour 1000 — the full 0.20");
        assertEquals(0.0, runner.getFleetFootedBonus(Medium.WATER), EPSILON,
                "a runner who has never swum must get nothing in water; 0.50 here means the water "
                        + "ramp read Parkour");
        assertEquals(0.0, runner.getFleetFootedBonus(Medium.AIR), EPSILON,
                "0.15 here means the air ramp read Parkour");
        assertEquals(0.5, runner.getAthleteExhaustionMultiplier(), EPSILON,
                "Athlete must ramp on Parkour 1000 — the full 0.5 reduction");
        assertEquals(0.0, runner.getLeadLungsAirTopUpPerTick(), EPSILON,
                "Lead Lungs must ramp on Swimming, which is 0");
        assertEquals(0.0, runner.getGlideDescentReduction(), EPSILON,
                "Glide must ramp on Flying, which is 0");

        final AgilityManager swimmer = managerAtLevels(0, 1000, 0);
        assertEquals(0.50, swimmer.getFleetFootedBonus(Medium.WATER), EPSILON,
                "water Fleet Footed must ramp on Swimming 1000 — the full 0.50");
        assertEquals(0.0, swimmer.getFleetFootedBonus(Medium.LAND), EPSILON);
        assertEquals(0.75, swimmer.getLeadLungsAirTopUpPerTick(), EPSILON,
                "Lead Lungs must ramp on Swimming 1000");
        assertEquals(1.0, swimmer.getAthleteExhaustionMultiplier(), EPSILON,
                "Athlete is Parkour's and this player has none — no reduction at all");

        final AgilityManager flier = managerAtLevels(0, 0, 1000);
        assertEquals(0.15, flier.getFleetFootedBonus(Medium.AIR), EPSILON,
                "air Fleet Footed must ramp on Flying 1000 — the full 0.15");
        assertEquals(0.0, flier.getFleetFootedBonus(Medium.LAND), EPSILON);
        assertEquals(0.5, flier.getGlideDescentReduction(), EPSILON,
                "Glide must ramp on Flying 1000");
        assertEquals(0.0, flier.getLeadLungsAirTopUpPerTick(), EPSILON,
                "Lead Lungs is Swimming's and this player has none");
    }

    /**
     * The movement manager is keyed <b>nominally</b> on {@code PARKOUR} (ruling A-8), and this pins
     * that the inherited {@link com.gmail.nossr50.skills.SkillManager#skill} field is read by
     * nothing in it.
     *
     * <p>⚠️ <b>Why this needs its own test.</b> Before 2026-08-17 the manager was keyed on the
     * retired {@code AGILITY}, so a passive that reached for the inherited {@code getSkillLevel()}
     * or the two-argument {@code scaleToLevel} scaled on the <em>mean</em> — wrong for everyone,
     * which is how phase C caught four of them at once. Keyed on PARKOUR the same mistake scales a
     * swimmer's and a flier's perks on their <b>Parkour</b> level, which is <em>correct for a
     * runner</em> and silently wrong for the other two. The failure got quieter, not rarer.
     *
     * <p>A swimmer with Parkour 0 is the discriminator: every water number must be full.
     */
    @Test
    void theNominalParkourKeyIsReadByNothing() {
        final AgilityManager swimmer = managerAtLevels(0, 1000, 0);

        assertEquals(0.50, swimmer.getFleetFootedBonus(Medium.WATER), EPSILON,
                "a swimmer with Parkour 0 must still get the full water bonus; 0.0 here means "
                        + "something scaled on the manager's nominal PARKOUR key");
        assertEquals(0.75, swimmer.getLeadLungsAirTopUpPerTick(), EPSILON,
                "Lead Lungs must be full for a maxed swimmer regardless of their Parkour");
        assertTrue(swimmer.canFleetFoot(Medium.WATER),
                "the water gate must read Swimming, not the manager's nominal key");
        assertNotNull(swimmer.computeSecondWind(Medium.WATER, 100),
                "Aquaman must unlock on Swimming 1000 with Parkour at 0");
    }

    @Test
    void reParentedSubSkillsFollowTheirOwnParentNotTheAverage() {
        final AgilityManager runner = managerAtLevels(1000, 0, 0);

        // Parkour's own: unlocked by running, despite Agility sitting at 333.
        assertTrue(runner.canDodge(), "Dodge is gated on Parkour 1 and this player has Parkour 1000");
        assertTrue(runner.canAthlete(), "Athlete unlocks at Parkour 50");
        assertTrue(runner.canSmash(), "Smash unlocks at Parkour 150");
        assertTrue(runner.canSnowWalk(), "Snow Walker unlocks at Parkour 100");

        // Swimming's and Flying's: dead, because this player has never swum or flown. Under the old
        // Agility gate, Agility 333 would have switched Lead Lungs (250) on for a player who has
        // never been underwater.
        assertFalse(runner.canLeadLungs(), "Lead Lungs is gated on Swimming, which is 0");
        assertFalse(runner.canLakeRaider(), "Lake Raider is gated on Swimming, which is 0");
        assertFalse(runner.canGlide(), "Glide is gated on Flying, which is 0");
        assertFalse(runner.canSolarWings(), "Solar Wings is gated on Flying, which is 0");
    }

    /**
     * The 2026-08-17 Agility retirement, in the one assertion that can catch it going wrong.
     *
     * <p>This <b>replaces</b> {@code agilitysOwnSubSkillsStillGateOnTheThreeSkillMean}, which pinned
     * the exact opposite and was correct until the child skill was dropped. Fleet Footed and Second
     * Wind used to be one 3-rank sub-skill apiece read against the mean of Parkour/Swimming/Flying;
     * each rank is now a single-rank sub-skill of its own medium's parent.
     *
     * <p>A <b>pure flier</b> is still the sharpest case, and the verdict inverts. Flying 1000 with
     * nothing else was Agility 333 — which unlocked the <em>water</em> rank for a player who had never
     * been in water, and denied the <em>air</em> rank (400) to one whose flying was maxed. Both halves
     * were backwards, and neither reported anything. Now the air bodies are live and the land and
     * water ones are dead, which is only satisfiable if each reads its own parent.
     *
     * <p>⚠️ This fails in both directions on purpose: a mean-of-three gate would light up water, and
     * a single shared gate would light up all three. The old constants coming back would produce no
     * error anywhere — the same silent shape as GitHub #4.
     */
    @Test
    void movementSubSkillsFollowTheirOwnMediumsParentNotTheAverage() {
        final AgilityManager flier = managerAtLevels(0, 0, 1000);

        assertTrue(flier.canFleetFoot(Medium.AIR),
                "air Fleet Footed is gated on Flying, which is maxed — under the retired mean gate "
                        + "this needed Agility 400 and a pure flier could never reach it");
        assertNotNull(flier.computeSecondWind(Medium.AIR, 100),
                "Limitless is gated on Flying, which is maxed — under the retired mean gate it "
                        + "needed Agility 750 and was unreachable without also running and swimming");

        assertFalse(flier.canFleetFoot(Medium.WATER),
                "water Fleet Footed is gated on Swimming, which is 0 — the mean of three would have "
                        + "wrongly unlocked it at Agility 333");
        assertNull(flier.computeSecondWind(Medium.WATER, 100),
                "Aquaman is gated on Swimming, which is 0");
        assertNull(flier.computeSecondWind(Medium.LAND, 100),
                "Dart is gated on Parkour, which is 0");
    }

    /**
     * The Second Wind half of the retirement, from the specialist's seat: each medium's body is
     * unlocked by that medium's own skill and by nothing else.
     *
     * <p>Three one-medium players rather than one all-rounder, because an all-rounder satisfies this
     * for free — every body would be live and the assertion could not tell a per-parent gate from a
     * shared one.
     */
    @Test
    void eachSecondWindBodyIsUnlockedOnlyByItsOwnMediumsSkill() {
        final AgilityManager runner = managerAtLevels(1000, 0, 0);
        assertNotNull(runner.computeSecondWind(Medium.LAND, 100), "Parkour 1000 unlocks Dart");
        assertNull(runner.computeSecondWind(Medium.WATER, 100), "Swimming 0 denies Aquaman");
        assertNull(runner.computeSecondWind(Medium.AIR, 100), "Flying 0 denies Limitless");

        final AgilityManager swimmer = managerAtLevels(0, 1000, 0);
        assertNotNull(swimmer.computeSecondWind(Medium.WATER, 100), "Swimming 1000 unlocks Aquaman");
        assertNull(swimmer.computeSecondWind(Medium.LAND, 100), "Parkour 0 denies Dart");
        assertNull(swimmer.computeSecondWind(Medium.AIR, 100), "Flying 0 denies Limitless");
    }

    // --- XP routing: each medium pays its own skill and no other --------------------------------

    /**
     * ⚠️ The negative half used to be <em>"and never AGILITY"</em>. That constant was retired on
     * 2026-08-17 and naming it is a compile error, so the negative is now asserted against the
     * <b>other two mediums</b> — which is strictly stronger: paying the wrong medium is a mistake
     * that still compiles, and it is the one the manager's nominal PARKOUR key makes easy.
     */
    @Test
    void everyMediumPaysItsOwnSkillAndNoOtherMediums() {
        for (Medium medium : Medium.values()) {
            final McMMOPlayer owner = mock(McMMOPlayer.class);
            lenient().when(owner.getPlayer()).thenReturn(player);
            final AgilityManager manager = new AgilityManager(owner);
            manager.setMovementXpSettings(defaultSettings());

            // Enough ticks that even the stingiest medium clears one whole XP and flushes.
            for (int tick = 0; tick < 100; tick++) {
                manager.onMovementTick(medium, perTick(medium));
            }

            verify(owner, org.mockito.Mockito.atLeastOnce()).beginXpGain(
                    org.mockito.ArgumentMatchers.eq(medium.primarySkill()), anyFloat(), any(), any());
            for (Medium other : Medium.values()) {
                if (other.primarySkill() == medium.primarySkill()) {
                    continue;
                }
                verify(owner, never()).beginXpGain(
                        org.mockito.ArgumentMatchers.eq(other.primarySkill()), anyFloat(), any(),
                        any());
            }
        }
    }

    @Test
    void eachMediumBanksItsOwnRemainder() {
        // The accumulator is per-medium because each one now pays a different skill. With a single
        // shared remainder, part of a second's swimming would be flushed into Parkour the moment the
        // player climbed out and sprinted — small, but wrong every time the medium changes.
        final AgilityManager manager = managerAtLevel(1);

        // Two-fifths of a tick in each: land banks 0.6 XP, water banks 0.69. Neither reaches a whole
        // XP on its own, so both must pay nothing — but 0.6 + 0.69 = 1.29 does, so a shared ledger
        // would make the second call pay 1. That is the whole discriminator; a full tick in each
        // would pay the same either way and prove nothing.
        assertEquals(0F, manager.onMovementTick(Medium.LAND, perTick(Medium.LAND) * 0.4), EPSILON);
        assertEquals(0F, manager.onMovementTick(Medium.WATER, perTick(Medium.WATER) * 0.4), EPSILON);
        verify(mmoPlayer, never()).beginXpGain(any(), anyFloat(), any(), any());
    }

    @Test
    void fallAndDodgeXpGoToParkourRatherThanBeingSplitAcrossAllThreeDomains() {
        // Landing well is a land-movement skill, and splitting the XP three ways would mean falling
        // off a cliff trains your swimming.
        assertEquals(PrimarySkillType.PARKOUR, AgilityManager.EPISODIC_XP_SKILL);

        // The shipped fall multipliers, plus enough health to survive — without either the mocked
        // player takes a fatal fall, mcMMO bows out, and the test would pass by awarding nothing.
        lenient().when(experienceConfig.getFallXPModifier()).thenReturn(600);
        lenient().when(experienceConfig.getRollXPModifier()).thenReturn(600);
        lenient().when(player.getHealth()).thenReturn(20F);
        final AgilityManager manager = managerAtLevel(1);
        manager.processFallDamage(10.0);

        verify(mmoPlayer, org.mockito.Mockito.atLeastOnce()).beginXpGain(
                org.mockito.ArgumentMatchers.eq(PrimarySkillType.PARKOUR), anyFloat(), any(), any());
        // ⚠️ The negative was "never AGILITY" until that constant was retired on 2026-08-17.
        // Asserted against Swimming and Flying instead: "split across all three domains" is what the
        // test name claims is NOT happening, and those two are the halves that would show it.
        verify(mmoPlayer, never()).beginXpGain(
                org.mockito.ArgumentMatchers.eq(PrimarySkillType.SWIMMING), anyFloat(), any(), any());
        verify(mmoPlayer, never()).beginXpGain(
                org.mockito.ArgumentMatchers.eq(PrimarySkillType.FLYING), anyFloat(), any(), any());
    }
}
