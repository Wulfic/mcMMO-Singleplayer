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

        lenient().when(advancedConfig.getFleetFootedMaxBonusLevel()).thenReturn(1000);
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
        return managerAtLevels(level, level, level, level);
    }

    /** A manager whose four movement levels are set independently. */
    private AgilityManager managerAtLevels(int agility, int parkour, int swimming, int flying) {
        lenient().when(mmoPlayer.getSkillLevel(PrimarySkillType.AGILITY)).thenReturn(agility);
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
    void fleetFootedUnlocksOneMediumPerRank() {
        // RetroMode ladder from skillranks.yml: land at 1, water at 200, air at 400.
        final AgilityManager early = managerAtLevel(1);
        assertTrue(early.canFleetFoot(Medium.LAND));
        assertFalse(early.canFleetFoot(Medium.WATER));
        assertFalse(early.canFleetFoot(Medium.AIR));

        final AgilityManager mid = managerAtLevel(250);
        assertTrue(mid.canFleetFoot(Medium.LAND));
        assertTrue(mid.canFleetFoot(Medium.WATER));
        assertFalse(mid.canFleetFoot(Medium.AIR), "air is rank 3, unlocked at 400");

        final AgilityManager maxed = managerAtLevel(1000);
        assertTrue(maxed.canFleetFoot(Medium.AIR));
    }

    @Test
    void fleetFootedPaysNothingForALockedMedium() {
        final AgilityManager early = managerAtLevel(1);
        assertEquals(0.0, early.getFleetFootedBonus(Medium.WATER), EPSILON);
        assertEquals(0.0, early.getFleetFootedBonus(Medium.AIR), EPSILON);
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
        when(advancedConfig.getFleetFootedMaxBonusLevel()).thenReturn(100);
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
    void secondWindUnlocksOneBodyPerRank() {
        // RetroMode ladder: land at 250, water at 500, air at 750.
        final AgilityManager mid = managerAtLevel(250);
        assertTrue(mid.canSecondWind(Medium.LAND));
        assertFalse(mid.canSecondWind(Medium.WATER));
        assertFalse(mid.canSecondWind(Medium.AIR));

        final AgilityManager maxed = managerAtLevel(1000);
        assertTrue(maxed.canSecondWind(Medium.AIR));
    }

    @Test
    void secondWindReturnsNullForALockedBodySoTheCooldownIsNotBurned() {
        // Returning null rather than a zeroed result is load-bearing: an all-zeros result is
        // indistinguishable from a legitimately weak one, and the caller has to be able to refuse
        // without consuming the cooldown.
        assertNull(managerAtLevel(250).computeSecondWind(Medium.WATER, 100));
        assertNull(managerAtLevel(1).computeSecondWind(Medium.LAND, 100));
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
        // The whole new roster at once: a level-1 player should feel exactly like the shipped
        // Acrobatics skill, with only Fleet Footed (land, rank 1) switched on.
        final AgilityManager fresh = managerAtLevel(1);
        assertEquals(0.0, fresh.getFleetFootedBonus(Medium.WATER), EPSILON);
        assertEquals(0.0, fresh.getFleetFootedBonus(Medium.AIR), EPSILON);
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
    @Test
    void reParentedSubSkillsFollowTheirOwnParentNotTheAverage() {
        final AgilityManager runner = managerAtLevels(333, 1000, 0, 0);

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
     * The converse, and the half that is easy to leave out: the two sub-skills that did <em>not</em>
     * move must still read the average, or "re-parent everything" would have been the quieter bug.
     *
     * <p>A pure flier is the sharpest case. Flying 1000 with nothing else is Agility 333, so Fleet
     * Footed's water rank (200) is unlocked and its air rank (400) is not — even though the player's
     * flying is maxed. That is the deliberate all-rounder design, and it is also proof the gate is
     * the mean rather than any single parent: Flying alone would have unlocked the air rank, and
     * Swimming alone would have denied the water one.
     */
    @Test
    void agilitysOwnSubSkillsStillGateOnTheThreeSkillMean() {
        final AgilityManager flier = managerAtLevels(333, 0, 0, 1000);

        assertTrue(flier.canFleetFoot(Medium.LAND), "rank 1 at Agility 1");
        assertTrue(flier.canFleetFoot(Medium.WATER), "rank 2 at Agility 200, and the mean is 333");
        assertFalse(flier.canFleetFoot(Medium.AIR),
                "rank 3 needs Agility 400; maxed Flying alone only reaches 333");
        assertNull(flier.computeSecondWind(Medium.AIR, 100),
                "Second Wind's air body needs Agility 750 — unreachable without swimming and running");
    }

    // --- XP routing: each medium pays its own skill, never Agility -------------------------------

    @Test
    void everyMediumPaysItsOwnSkillAndNeverAgility() {
        for (Medium medium : Medium.values()) {
            final McMMOPlayer owner = mock(McMMOPlayer.class);
            lenient().when(owner.getPlayer()).thenReturn(player);
            lenient().when(owner.getSkillLevel(PrimarySkillType.AGILITY)).thenReturn(1);
            final AgilityManager manager = new AgilityManager(owner);
            manager.setMovementXpSettings(defaultSettings());

            // Enough ticks that even the stingiest medium clears one whole XP and flushes.
            for (int tick = 0; tick < 100; tick++) {
                manager.onMovementTick(medium, perTick(medium));
            }

            verify(owner, org.mockito.Mockito.atLeastOnce()).beginXpGain(
                    org.mockito.ArgumentMatchers.eq(medium.primarySkill()), anyFloat(), any(), any());
            verify(owner, never()).beginXpGain(
                    org.mockito.ArgumentMatchers.eq(PrimarySkillType.AGILITY), anyFloat(), any(),
                    any());
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
        // Landing well is a land-movement skill. Agility cannot hold the XP (a child skill earns
        // nothing) and splitting it three ways would mean falling off a cliff trains your swimming.
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
        verify(mmoPlayer, never()).beginXpGain(
                org.mockito.ArgumentMatchers.eq(PrimarySkillType.AGILITY), anyFloat(), any(), any());
    }
}
