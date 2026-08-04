package com.gmail.nossr50.skills.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.treasure.HusbandryTreasure;
import com.gmail.nossr50.datatypes.treasure.ItemSpec;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.skills.SkillRenames;
import com.gmail.nossr50.util.skills.SkillTools;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stage 0 of Husbandry: the skill is registered and it can price all six of its XP verbs. Nothing
 * awards any of it yet — the trigger layer lands in stages 1–6.
 *
 * <p>The default fixture runs against the <b>real bundled {@code experience.yml}</b> and a real
 * {@link McMMOPlayer}, not mocks, because at this stage the config file <em>is</em> the feature:
 * every number a player will ever earn lives in that YAML, and a mocked config would prove the
 * getters compile while a mis-indented {@code Animal_Breeding} block shipped a skill that pays zero
 * for everything. Tests that need a value the shipped file does not contain — a negative, an absent
 * config — swap a mock in locally and say so.
 */
class HusbandryManagerTest {

    private static final UUID UID = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    /** The shipped experience.yml values, restated so a retune has to come through this test. */
    private static final int CHICKEN_BREED_XP = 300;
    private static final int COW_BREED_XP = 350;
    private static final int HORSE_BREED_XP = 1200;
    /** The mount tier the horse family sets, and what nautilus and happy ghast are priced at. */
    private static final int MOUNT_BREED_XP = 1200;
    private static final int SNIFFER_BREED_XP = 1500;

    private McMMOPlayer mmoPlayer;
    private PlayerProfile profile;
    private HusbandryManager manager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        // Real rank plumbing: the stage-1 sub-skill gates run through RankUtils, and mocking it
        // would prove the gate compiles rather than that skillranks.yml actually unlocks anything.
        McMMOMod.setRankConfig(new RankConfig(dataFolder));

        final PlatformPlayer player = mock(PlatformPlayer.class);
        lenient().when(player.getName()).thenReturn("Farmer");
        lenient().when(player.getUniqueId()).thenReturn(UID);
        lenient().when(player.isCreative()).thenReturn(false);

        profile = new PlayerProfile("Farmer", UID, 0);
        mmoPlayer = new McMMOPlayer(player, profile);
        manager = mmoPlayer.getHusbandryManager();
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setRankConfig(null);
    }

    /** Rebinds a mocked config for the cases the shipped file cannot express. */
    private HusbandryManager managerWithConfig(ExperienceConfig config) {
        McMMOMod.setExperienceConfig(config);
        return manager;
    }

    private void setHusbandryLevel(int level) {
        profile.modifySkill(PrimarySkillType.HUSBANDRY, level);
    }

    /**
     * An {@link AdvancedConfig} whose Twins RNG is a certainty in one direction.
     *
     * <p>A {@code maxBonusLevel} of 0 short-circuits {@code ProbabilityUtil} straight to the
     * ceiling, so the ceiling alone decides the outcome — 100 always procs, 0 never does.
     */
    private AdvancedConfig advancedWithTwinsChance(double ceiling) {
        final AdvancedConfig advanced = mock(AdvancedConfig.class);
        lenient().when(advanced.getMaximumProbability(SubSkillType.HUSBANDRY_TWINS))
                .thenReturn(ceiling);
        lenient().when(advanced.getMaxBonusLevel(SubSkillType.HUSBANDRY_TWINS)).thenReturn(0);
        McMMOMod.setAdvancedConfig(advanced);
        return advanced;
    }

    /** The same certainty trick for Accelerated Growth's double-feed roll. */
    private AdvancedConfig advancedWithDoubleFeedChance(double ceiling) {
        final AdvancedConfig advanced = mock(AdvancedConfig.class);
        lenient().when(
                        advanced.getMaximumProbability(SubSkillType.HUSBANDRY_ACCELERATED_GROWTH))
                .thenReturn(ceiling);
        lenient().when(advanced.getMaxBonusLevel(SubSkillType.HUSBANDRY_ACCELERATED_GROWTH))
                .thenReturn(0);
        McMMOMod.setAdvancedConfig(advanced);
        return advanced;
    }

    // --- Registration ---------------------------------------------------------------------------

    @Test
    void theSkillIsWiredEndToEndOnARealPlayer() {
        // Pins the initManager case and the typed getter together. Without the case the getter
        // returns null and every Husbandry call site NPEs at runtime while compiling perfectly.
        assertNotNull(manager, "McMMOPlayer must build a HusbandryManager for HUSBANDRY");
        assertSame(manager, mmoPlayer.getHusbandryManager(), "the manager is cached, not rebuilt");
    }

    @Test
    void husbandryIsAGatheringSkillAndNotAChild() {
        assertTrue(new SkillTools().getGatheringSkills().contains(PrimarySkillType.HUSBANDRY),
                "four of the six verbs are gathering, and the other two produce what they harvest");
        // A child skill earns no XP of its own and splits any award into its parents, which would
        // silently discard every number this class computes.
        assertFalse(SkillTools.isChildSkill(PrimarySkillType.HUSBANDRY));
    }

    @Test
    void everySkillResolvesToASubSkillSetRatherThanNull() {
        // Husbandry was the first skill in the mod to have ZERO sub-skills (stage 0 shipped none),
        // and the only thing between that and an NPE is that buildPrimarySkillChildrenMap pre-seeds
        // an empty set for every PrimarySkillType before filling it, while SkillStatsRenderer feeds
        // the result straight into `new ArrayList<>(...)`. That pre-seed looks like dead
        // initialization and is exactly what a cleanup pass deletes.
        //
        // Anchored on the whole enum rather than on Husbandry, because Husbandry stopped being the
        // empty one the moment stage 1 gave it two sub-skills -- a test pinned to whichever skill
        // happens to be empty today stops testing anything the day that changes, silently.
        final SkillTools skillTools = new SkillTools();
        for (PrimarySkillType skill : PrimarySkillType.values()) {
            assertNotNull(skillTools.getSubSkills(skill),
                    () -> skill + " must map to a set, not to null");
        }
    }

    @Test
    void husbandryOwnsExactlyTheSubSkillsThisStageShips() {
        // Sub-skill constants land with the stage that implements them and no earlier: a constant
        // with no ranks, no config and no behaviour reads as half-wired to everything that iterates
        // the enum, /mcstats included. This fails if a later stage's constant is added early.
        assertEquals(
                java.util.Set.of(SubSkillType.HUSBANDRY_MULTI_BREED, SubSkillType.HUSBANDRY_TWINS,
                        SubSkillType.HUSBANDRY_ACCELERATED_GROWTH,
                        SubSkillType.HUSBANDRY_BOUNTIFUL_HARVEST,
                        SubSkillType.HUSBANDRY_BEEKEEPER,
                        SubSkillType.HUSBANDRY_SELECTIVE_BREEDING,
                        SubSkillType.HUSBANDRY_BROOD,
                        SubSkillType.HUSBANDRY_HIDDEN_BOUNTY,
                        SubSkillType.HUSBANDRY_HERDSMANS_CALL),
                new SkillTools().getSubSkills(PrimarySkillType.HUSBANDRY));
    }

    // --- Breed: the per-species table ------------------------------------------------------------

    @Test
    void breedingPaysTheShippedPerSpeciesRate() {
        assertEquals(CHICKEN_BREED_XP, manager.getBreedXp("Chicken"));
        assertEquals(COW_BREED_XP, manager.getBreedXp("Cow"));
        assertEquals(HORSE_BREED_XP, manager.getBreedXp("Horse"));
        assertEquals(SNIFFER_BREED_XP, manager.getBreedXp("Sniffer"));
    }

    @Test
    void theTwoBreedableMountsAddedSinceThePlanArePriced() {
        // ⚠️ Both of these were absent from the table until 2026-07-30, and an unlisted species
        // resolves to 0 — so breeding either paid nothing AND raising the young paid nothing, the raise
        // verb being a multiple of the breed value. Neither is exotic: HappyGhastEntity shipped in
        // 1.21.6 and was simply missed when the roster was written, and NautilusEntity is new in
        // 1.21.11, breeding through NautilusBrain's BreedTask straight into the criterion mcMMO hooks.
        //
        // Asserted by name rather than folded into the spread test above, because the failure this
        // guards is a MISSING KEY, which no inequality between other species can detect.
        assertEquals(MOUNT_BREED_XP, manager.getBreedXp("Nautilus"),
                "breeding a nautilus must pay the mount rate, not the unlisted-species zero");
        assertEquals(MOUNT_BREED_XP, manager.getBreedXp("Happy_Ghast"),
                "breeding a happy ghast must pay the mount rate, not the unlisted-species zero");
    }

    @Test
    void theTableIsAnActualSpreadAndNotOneRepeatedNumber() {
        // The whole reason breeding is per-species rather than flat is that a breeding item's cost
        // spans two orders of magnitude. A table that had been flattened by a bad edit would still
        // satisfy every equality above if they all happened to be retuned together; this will not.
        assertTrue(manager.getBreedXp("Sniffer") > manager.getBreedXp("Horse"),
                "a torchflower seed is dearer than a golden carrot");
        assertTrue(manager.getBreedXp("Horse") > manager.getBreedXp("Cow"),
                "a golden carrot is dearer than wheat");
        assertTrue(manager.getBreedXp("Cow") > manager.getBreedXp("Chicken"),
                "wheat is dearer than the seeds you get for free while farming it");
    }

    @Test
    void anUnpricedSpeciesPaysNothing() {
        // The table IS the definition of what this skill rewards. A mob from a future version or
        // another mod must not silently start paying a number nobody chose.
        assertEquals(0F, manager.getBreedXp("Not_A_Real_Animal"));
        assertEquals(0F, manager.getBreedXp(""));
        assertEquals(0F, manager.getBreedXp(null));
    }

    @Test
    void aNegativeConfiguredRateIsClampedRatherThanPaidOut() {
        final ExperienceConfig broken = mock(ExperienceConfig.class);
        when(broken.getHusbandryBreedXp("Cow")).thenReturn(-500);
        assertEquals(0F, managerWithConfig(broken).getBreedXp("Cow"),
                "a mistyped config must not hand out negative XP");
    }

    @Test
    void breedingPaysNothingWhenNoConfigIsBound() {
        // Unlike the flat verbs there is no per-species fallback to fall back TO, so this is 0 by
        // construction rather than by omission.
        McMMOMod.setExperienceConfig(null);
        assertEquals(0F, manager.getBreedXp("Cow"));
    }

    // --- Breed: the award path --------------------------------------------------------------------

    @Test
    void breedingAwardsThePricedXpAndReportsIt() {
        setHusbandryLevel(0);
        final HusbandryManager.BreedAward award = manager.onBreed("Cow", 0L);
        assertEquals(COW_BREED_XP, award.xp());
        assertTrue(award.paid());
        assertFalse(award.capReached());
        assertTrue(profile.getSkillXpLevelRaw(PrimarySkillType.HUSBANDRY) > 0,
                "onBreed must actually move the player's XP, not just compute a number");
    }

    @Test
    void breedingAnUnpricedSpeciesAwardsNothingAtAll() {
        setHusbandryLevel(0);
        final float before = profile.getSkillXpLevelRaw(PrimarySkillType.HUSBANDRY);
        assertEquals(0F, manager.onBreed("Not_A_Real_Animal", 0L).xp());
        assertEquals(before, profile.getSkillXpLevelRaw(PrimarySkillType.HUSBANDRY),
                "an unpriced species must not reach the XP pipeline at all");
    }

    // --- Breed: the per-window award cap (GitHub #3) -----------------------------------------------

    /** The shipped ExploitFix.Husbandry values, restated so a retune has to come through this test. */
    private static final int AWARD_CAP = 8;
    private static final int WINDOW_SECONDS = 30;
    private static final long WINDOW_TICKS = WINDOW_SECONDS * 20L;

    @Test
    void theShippedAwardCapMatchesTheBundledConfig() {
        // The cap doubled from the old four when it moved off the breeding and onto the payout
        // (GitHub #3), and the window is vanilla's own love duration rather than a tuned number:
        // AnimalEntity#lovePlayer sets loveTicks = 600, so one handful of feed's whole burst of
        // breedings lands inside a single window. Pinned so a retune has to read that reasoning.
        assertEquals(AWARD_CAP, manager.getBreedXpAwardsPerWindow());
        assertEquals(WINDOW_SECONDS, manager.getBreedXpAwardWindowSeconds());
        assertEquals(WINDOW_TICKS, manager.getBreedXpAwardWindowTicks());
        assertTrue(manager.isBreedXpAwardCapped());
    }

    @Test
    void theCapPaysExactlyNBreedingsInAWindowAndRefusesTheRest() {
        setHusbandryLevel(0);
        for (int i = 0; i < AWARD_CAP; i++) {
            assertTrue(manager.onBreed("Cow", 100L).paid(),
                    "breeding " + (i + 1) + " of " + AWARD_CAP + " is inside the cap");
        }

        final float afterTheCap = profile.getSkillXpLevelRaw(PrimarySkillType.HUSBANDRY);
        final HusbandryManager.BreedAward refused = manager.onBreed("Cow", 100L);
        assertFalse(refused.paid(), "the " + (AWARD_CAP + 1) + "th breeding in a window pays nothing");
        assertEquals(0F, refused.xp());
        assertEquals(afterTheCap, profile.getSkillXpLevelRaw(PrimarySkillType.HUSBANDRY),
                "a refused breeding must not reach the XP pipeline at all");
    }

    @Test
    void theWindowExpiresAndTheNextBreedingPaysAgain() {
        setHusbandryLevel(0);
        for (int i = 0; i <= AWARD_CAP; i++) {
            manager.onBreed("Cow", 100L);
        }
        assertFalse(manager.onBreed("Cow", 100L + WINDOW_TICKS - 1).paid(),
                "one tick short of the window is still inside it");
        assertTrue(manager.onBreed("Cow", 100L + WINDOW_TICKS).paid(),
                "the window is measured in world ticks and it has now elapsed");
    }

    @Test
    void anUnpricedSpeciesDoesNotBurnAnAwardSlot() {
        // ⚠️ The ordering trap. If the cap were claimed before the price were read, a pen of animals
        // the table does not price would throttle the cows standing next to them -- and there would be
        // nothing anywhere to say why.
        setHusbandryLevel(0);
        for (int i = 0; i < 50; i++) {
            manager.onBreed("Not_A_Real_Animal", 100L);
        }
        for (int i = 0; i < AWARD_CAP; i++) {
            assertTrue(manager.onBreed("Cow", 100L).paid(),
                    "the whole cap must still be available after 50 unpriced breedings");
        }
    }

    @Test
    void theCapAnnouncesItselfOnceAndOnlyOncePerWindow() {
        // A gate that silently pays nothing is indistinguishable from a broken one -- the lesson both
        // GitHub #4 and #5 turned on. A gate that says so on every breeding in a hundred-cow pen is
        // worse, so exactly one breeding per window carries the flag.
        setHusbandryLevel(0);
        for (int i = 0; i < AWARD_CAP; i++) {
            assertFalse(manager.onBreed("Cow", 100L).capReached(),
                    "a breeding that pays must never claim the cap was reached");
        }
        assertTrue(manager.onBreed("Cow", 100L).capReached(), "the first refusal announces itself");
        for (int i = 0; i < 20; i++) {
            assertFalse(manager.onBreed("Cow", 100L).capReached(),
                    "every later refusal in the same window stays quiet");
        }

        // A fresh window rearms the announcement, or a player who kept breeding for an hour would be
        // told once at the start and never again. Exactly AWARD_CAP breedings here, so the assertion
        // below is the window's genuine FIRST refusal rather than a later quiet one.
        for (int i = 0; i < AWARD_CAP; i++) {
            manager.onBreed("Cow", 100L + WINDOW_TICKS);
        }
        assertTrue(manager.onBreed("Cow", 100L + WINDOW_TICKS).capReached(),
                "the next window announces its own first refusal");
    }

    @Test
    void aClockThatRunsBackwardsReopensTheWindowRatherThanLockingThePlayerOut() {
        // /time set, or the player stepping through a portal into a dimension with its own count.
        // Of the two ways to be wrong about a backwards clock, refusing to reset is much the worse:
        // the player would stop earning breed XP for as long as the clock stayed behind, silently.
        setHusbandryLevel(0);
        for (int i = 0; i <= AWARD_CAP; i++) {
            manager.onBreed("Cow", 1_000_000L);
        }
        assertTrue(manager.onBreed("Cow", 5L).paid(),
                "a window that appears to start in the future counts as expired");
    }

    @Test
    void aZeroCapOrAZeroWindowDisablesTheGateEntirely() {
        setHusbandryLevel(0);

        // ⚠️ A SPY on the real config, not a bare mock. onBreed's paying branch runs the whole XP
        // pipeline, which reads the formula type and the level curve straight back out of
        // ExperienceConfig -- a wholesale mock answers those with null and the test dies inside
        // FormulaManager, nowhere near the cap it was aiming at.
        final ExperienceConfig shipped = McMMOMod.getExperienceConfig();

        final ExperienceConfig noCap = spy(shipped);
        doReturn(0).when(noCap).getHusbandryBreedXpAwardsPerWindow();
        managerWithConfig(noCap);
        assertFalse(manager.isBreedXpAwardCapped());
        for (int i = 0; i < AWARD_CAP * 3; i++) {
            assertTrue(manager.onBreed("Cow", 100L).paid(), "Awards_Per_Window: 0 means no cap");
        }

        final ExperienceConfig noWindow = spy(shipped);
        doReturn(0).when(noWindow).getHusbandryBreedXpAwardWindowSeconds();
        managerWithConfig(noWindow);
        assertFalse(manager.isBreedXpAwardCapped());
        for (int i = 0; i < AWARD_CAP * 3; i++) {
            assertTrue(manager.onBreed("Cow", 100L).paid(), "Window_Seconds: 0 means no cap");
        }
    }

    // --- Sub-skill: Twins -------------------------------------------------------------------------

    @Test
    void twinsIsLockedAtLevelZeroAndUnlocksAtLevelOne() {
        setHusbandryLevel(0);
        assertFalse(manager.canTwins(), "rank 1 unlocks at level 1, so level 0 has nothing");
        setHusbandryLevel(1);
        assertTrue(manager.canTwins(), "breeding is the skill's entry verb; its sub-skills unlock at 1");
    }

    @Test
    void twinsNeverProcsWhileLockedEvenAtACertainChance() {
        // The gate and the roll are separate conditions, so pin them separately: with the RNG forced
        // to a certainty, a proc here could only come from the rank gate having been dropped.
        advancedWithTwinsChance(100.0);
        setHusbandryLevel(0);
        assertFalse(manager.rollTwins(), "a locked sub-skill must not proc at any chance");
    }

    @Test
    void twinsProcsOnACertaintyAndNeverOnAZeroChance() {
        setHusbandryLevel(1000);

        advancedWithTwinsChance(100.0);
        assertTrue(manager.rollTwins(), "a 100% ceiling must always proc");

        advancedWithTwinsChance(0.0);
        assertFalse(manager.rollTwins(), "a 0% ceiling must never proc");
    }

    @Test
    void theShippedTwinsChanceIsCappedWellBelowCertainty() {
        // The wiki says 100% at max level. That is deliberately not what ships: doubling every breed
        // at max is a food and mob-population firehose on its own, and it MULTIPLIES with
        // Multi-Breed rather than adding to it. Pinned so a "restore the wiki value" edit has to
        // come through this test and read the reasoning.
        final double ceiling = McMMOMod.getAdvancedConfig()
                .getMaximumProbability(SubSkillType.HUSBANDRY_TWINS);
        assertEquals(25.0, ceiling, "advanced.yml Skills.Husbandry.Twins.ChanceMax");
    }

    // --- Sub-skill: Multi-Breed -------------------------------------------------------------------

    @Test
    void multiBreedReachesNobodyWhileLocked() {
        setHusbandryLevel(0);
        assertFalse(manager.canMultiBreed());
        assertEquals(0.0, manager.getMultiBreedRadius(), "a locked sub-skill must sweep nothing");
    }

    @Test
    void multiBreedRadiusGrowsFromTheBaseToTheMaximumWithLevel() {
        setHusbandryLevel(1);
        final double atUnlock = manager.getMultiBreedRadius();
        assertEquals(HusbandryManager.DEFAULT_MULTI_BREED_BASE_RADIUS, atUnlock, 0.05,
                "at level 1 of 1000 the scaled part is negligible; the base is what a player gets");

        setHusbandryLevel(1000);
        assertEquals(HusbandryManager.DEFAULT_MULTI_BREED_MAX_RADIUS, manager.getMultiBreedRadius(),
                1e-9, "RetroMode MaxBonusLevel is 1000, so 1000 is the top of the ladder");

        // Asserted OFF both endpoints as well: a formula that ignored the level entirely would
        // satisfy one of the two assertions above and read identically at the other.
        setHusbandryLevel(500);
        final double halfway = manager.getMultiBreedRadius();
        assertTrue(halfway > atUnlock && halfway < HusbandryManager.DEFAULT_MULTI_BREED_MAX_RADIUS,
                "half-levelled reach must sit strictly between the two ends, was " + halfway);
    }

    @Test
    void multiBreedRadiusIsHardClampedWhateverTheConfigSays() {
        // This number sizes an entity sweep that runs every time any player feeds any animal, so a
        // mistyped MaxRadius must not turn one wheat into an eight-chunk scan.
        final AdvancedConfig absurd = mock(AdvancedConfig.class);
        lenient().when(absurd.getMultiBreedBaseRadius()).thenReturn(4.0);
        lenient().when(absurd.getMultiBreedMaxRadius()).thenReturn(4000.0);
        lenient().when(absurd.getMultiBreedMaxBonusLevel()).thenReturn(100);
        McMMOMod.setAdvancedConfig(absurd);

        setHusbandryLevel(1000);
        assertEquals(HusbandryManager.HARD_MAX_MULTI_BREED_RADIUS, manager.getMultiBreedRadius(),
                1e-9);
    }

    @Test
    void theRadiusIsTheOnlyBoundOnTheSpread() {
        // GitHub #3: MaxAdditionalAnimals is gone. It capped how many animals ONE ITEM could set in
        // love, which taxed the mechanic instead of the reward and — fatally — bounded XP per item
        // rather than per unit of time, so a wheat farm walked straight through it. The gate now
        // lives on the payout (see the award-cap tests above), leaving the radius alone out here.
        //
        // Pinned as a config-surface assertion rather than a comment: a user who deliberately tuned
        // the removed key is left with an edited-looking value the game no longer reads, and the
        // orphan warning is the only thing that says so. (That the shipped advanced.yml no longer
        // DEFINES the key is pinned by ConfigLoaderTest, which asserts nothing is stranded in a file
        // mcMMO authored itself.)
        assertTrue(SkillRenames.legacyConfigPaths()
                        .containsKey("Skills.Husbandry.MultiBreed.MaxAdditionalAnimals"),
                "a user who tuned the removed key must be warned it is being ignored");
        setHusbandryLevel(1000);
        assertEquals(HusbandryManager.DEFAULT_MULTI_BREED_MAX_RADIUS, manager.getMultiBreedRadius(),
                1e-9, "the radius is what bounds the spread now, and it still scales");
    }

    @Test
    void theShippedMultiBreedDefaultsMatchTheBundledConfig() {
        final AdvancedConfig shipped = McMMOMod.getAdvancedConfig();
        assertEquals(HusbandryManager.DEFAULT_MULTI_BREED_BASE_RADIUS,
                shipped.getMultiBreedBaseRadius());
        assertEquals(HusbandryManager.DEFAULT_MULTI_BREED_MAX_RADIUS,
                shipped.getMultiBreedMaxRadius());
    }

    // --- Raise ------------------------------------------------------------------------------------

    @Test
    void raisingPaysTheSameAsBreedingAtTheShippedMultiplier() {
        assertEquals(1.0, manager.getRaiseMultiplier());
        assertEquals(COW_BREED_XP, manager.getRaiseXp("Cow"));
        assertEquals(SNIFFER_BREED_XP, manager.getRaiseXp("Sniffer"));
    }

    @Test
    void theRaiseMultiplierActuallyMultiplies() {
        // Asserted OFF the shipped 1.0 on purpose. Every assertion above is blind to the
        // multiplication itself — at a multiplier of one, dropping it entirely reads identically.
        final ExperienceConfig tuned = mock(ExperienceConfig.class);
        when(tuned.getHusbandryBreedXp("Cow")).thenReturn(COW_BREED_XP);
        when(tuned.getHusbandryRaiseMultiplier()).thenReturn(0.5);

        final HusbandryManager tunedManager = managerWithConfig(tuned);
        assertEquals(COW_BREED_XP * 0.5F, tunedManager.getRaiseXp("Cow"));
        assertEquals(COW_BREED_XP, tunedManager.getBreedXp("Cow"), "breeding is not scaled");
    }

    @Test
    void raisingAnUnpricedSpeciesPaysNothingEvenAtAHugeMultiplier() {
        final ExperienceConfig tuned = mock(ExperienceConfig.class);
        when(tuned.getHusbandryBreedXp("Not_A_Real_Animal")).thenReturn(0);
        lenient().when(tuned.getHusbandryRaiseMultiplier()).thenReturn(100.0);
        assertEquals(0F, managerWithConfig(tuned).getRaiseXp("Not_A_Real_Animal"),
                "zero times anything is still zero, and it must stay that way");
    }

    @Test
    void aNegativeRaiseMultiplierIsClamped() {
        final ExperienceConfig broken = mock(ExperienceConfig.class);
        when(broken.getHusbandryBreedXp("Cow")).thenReturn(COW_BREED_XP);
        when(broken.getHusbandryRaiseMultiplier()).thenReturn(-2.0);

        final HusbandryManager brokenManager = managerWithConfig(broken);
        assertEquals(0.0, brokenManager.getRaiseMultiplier());
        assertEquals(0F, brokenManager.getRaiseXp("Cow"));
    }

    @Test
    void raisingCreditsTheBreedRateAndAnUnpricedSpeciesCreditsNothing() {
        setHusbandryLevel(500);
        final float before = profile.getSkillXpLevelRaw(PrimarySkillType.HUSBANDRY);

        assertEquals(COW_BREED_XP, manager.onRaise("Cow"));
        assertTrue(profile.getSkillXpLevelRaw(PrimarySkillType.HUSBANDRY) > before,
                "onRaise must actually bank the XP, not just compute it");

        final float afterCow = profile.getSkillXpLevelRaw(PrimarySkillType.HUSBANDRY);
        assertEquals(0F, manager.onRaise("Not_A_Real_Animal"));
        assertEquals(afterCow, profile.getSkillXpLevelRaw(PrimarySkillType.HUSBANDRY),
                "an unpriced species must bank nothing at all");
    }

    // --- Feed -------------------------------------------------------------------------------------

    @Test
    void feedingPaysTheFlatRateForAPricedSpecies() {
        setHusbandryLevel(500);
        final float before = profile.getSkillXpLevelRaw(PrimarySkillType.HUSBANDRY);

        assertEquals(HusbandryManager.DEFAULT_FEED_BABY_XP, manager.onFeedBaby("Cow"));
        assertTrue(profile.getSkillXpLevelRaw(PrimarySkillType.HUSBANDRY) > before);
    }

    @Test
    void feedingAnUnpricedSpeciesPaysNothingEvenThoughTheRateIsFlat() {
        // The rate is flat, so nothing in the payout itself knows about species -- the gate is a
        // deliberate extra read of the BREEDING table. It matters because vanilla routes animals
        // through the feed path that this skill never rewards otherwise (a dolphin takes fish
        // through it), and because the table is what stops a modded mob paying a number nobody set.
        setHusbandryLevel(500);
        final float before = profile.getSkillXpLevelRaw(PrimarySkillType.HUSBANDRY);

        assertEquals(0F, manager.onFeedBaby("Dolphin"));
        assertEquals(before, profile.getSkillXpLevelRaw(PrimarySkillType.HUSBANDRY));
    }

    // --- Sub-skill: Accelerated Growth ------------------------------------------------------------

    @Test
    void acceleratedGrowthDoesNothingWhileLocked() {
        setHusbandryLevel(0);
        assertFalse(manager.canAcceleratedGrowth());
        assertEquals(0.0, manager.getGrowthAcceleration());
        assertEquals(-24000, manager.applyGrowthAcceleration(-24000),
                "a locked sub-skill must leave the newborn exactly as vanilla made it");
        assertFalse(manager.rollDoubleFeed());
    }

    @Test
    void growthAccelerationGrowsWithLevelAndTopsOutAtTheShippedMaximum() {
        setHusbandryLevel(150);
        final double atUnlock = manager.getGrowthAcceleration();
        assertTrue(atUnlock > 0 && atUnlock < HusbandryManager.DEFAULT_MAX_GROWTH_ACCELERATION,
                "a fresh unlock is worth something but not the maximum, was " + atUnlock);

        setHusbandryLevel(1000);
        assertEquals(HusbandryManager.DEFAULT_MAX_GROWTH_ACCELERATION,
                manager.getGrowthAcceleration(), 1e-9);

        // Asserted off both ends: a formula that ignored the level would read identically at one.
        setHusbandryLevel(500);
        final double halfway = manager.getGrowthAcceleration();
        assertTrue(halfway > atUnlock
                        && halfway < HusbandryManager.DEFAULT_MAX_GROWTH_ACCELERATION,
                "half-levelled acceleration must sit strictly between the ends, was " + halfway);
    }

    @Test
    void growthAccelerationIsHardClampedWhateverTheConfigSays() {
        final AdvancedConfig absurd = mock(AdvancedConfig.class);
        lenient().when(absurd.getMaxGrowthAcceleration()).thenReturn(5.0);
        lenient().when(absurd.getMaxBonusLevel(SubSkillType.HUSBANDRY_ACCELERATED_GROWTH))
                .thenReturn(100);
        McMMOMod.setAdvancedConfig(absurd);

        setHusbandryLevel(1000);
        assertEquals(HusbandryManager.HARD_MAX_GROWTH_ACCELERATION,
                manager.getGrowthAcceleration(), 1e-9);
    }

    @Test
    void anAcceleratedNewbornIsAlwaysStillABaby() {
        // THE test in this file. Breeding ages run negative and count up toward zero, so an
        // acceleration of 1.0 does not mean "grows up instantly" -- it means the newborn's age lands
        // exactly on zero, which the raise hook reads as the baby->adult crossing. The raise verb
        // would then pay in the same tick as the breed verb, for every animal, forever. Driven with
        // a config well past the hard clamp so BOTH guards have to hold.
        final AdvancedConfig absurd = mock(AdvancedConfig.class);
        lenient().when(absurd.getMaxGrowthAcceleration()).thenReturn(1000.0);
        lenient().when(absurd.getMaxBonusLevel(SubSkillType.HUSBANDRY_ACCELERATED_GROWTH))
                .thenReturn(1);
        McMMOMod.setAdvancedConfig(absurd);

        setHusbandryLevel(1000);
        for (int age : new int[] {-24000, -1200, -10, -2, -1}) {
            final int accelerated = manager.applyGrowthAcceleration(age);
            assertTrue(accelerated < 0,
                    "a newborn at age " + age + " must still be a baby, was " + accelerated);
            assertTrue(accelerated >= age,
                    "acceleration must shorten childhood, never lengthen it, was " + accelerated);
        }
    }

    @Test
    void acceleratedGrowthLeavesAdultsAlone() {
        setHusbandryLevel(1000);
        // A positive breeding age is an adult's post-breeding cooldown. Shortening it here would
        // quietly hand Accelerated Growth a second, undesigned job: faster re-breeding.
        assertEquals(6000, manager.applyGrowthAcceleration(6000));
        assertEquals(0, manager.applyGrowthAcceleration(0));
    }

    @Test
    void doubleFeedNeverProcsWhileLockedEvenAtACertainChance() {
        advancedWithDoubleFeedChance(100.0);
        setHusbandryLevel(0);
        assertFalse(manager.rollDoubleFeed(), "a locked sub-skill must not proc at any chance");
        assertEquals(120, manager.applyFeedBonus(120));
    }

    @Test
    void applyFeedBonusDoublesExactlyOnASuccessfulRoll() {
        setHusbandryLevel(1000);

        advancedWithDoubleFeedChance(100.0);
        assertTrue(manager.rollDoubleFeed());
        assertEquals(240, manager.applyFeedBonus(120), "a certain roll doubles the growth");

        advancedWithDoubleFeedChance(0.0);
        assertFalse(manager.rollDoubleFeed());
        assertEquals(120, manager.applyFeedBonus(120), "a failed roll leaves vanilla's value alone");
    }

    @Test
    void applyFeedBonusLeavesNonPositiveGrowthAlone() {
        // Doubling zero is zero, but doubling a negative would make the animal younger -- and the
        // one-argument growUp callers are free to pass whatever they like.
        advancedWithDoubleFeedChance(100.0);
        setHusbandryLevel(1000);
        assertEquals(0, manager.applyFeedBonus(0));
        assertEquals(-30, manager.applyFeedBonus(-30));
    }

    @Test
    void theShippedAcceleratedGrowthDefaultsMatchTheBundledConfig() {
        final AdvancedConfig shipped = McMMOMod.getAdvancedConfig();
        assertEquals(HusbandryManager.DEFAULT_MAX_GROWTH_ACCELERATION,
                shipped.getMaxGrowthAcceleration());
        assertEquals(25.0,
                shipped.getMaximumProbability(SubSkillType.HUSBANDRY_ACCELERATED_GROWTH),
                "advanced.yml Skills.Husbandry.AcceleratedGrowth.ChanceMax");
    }

    // --- The flat verbs ---------------------------------------------------------------------------

    @Test
    void theFlatVerbsReadTheShippedValues() {
        assertEquals(HusbandryManager.DEFAULT_FEED_BABY_XP, manager.getFeedBabyXp());
        assertEquals(HusbandryManager.DEFAULT_SHEAR_XP, manager.getShearXp());
        assertEquals(HusbandryManager.DEFAULT_HIVE_XP, manager.getHiveXp());
        assertEquals(HusbandryManager.DEFAULT_MILK_XP, manager.getMilkXp());
        assertEquals(HusbandryManager.DEFAULT_BRUSH_XP, manager.getBrushXp());
    }

    @Test
    void eachFlatVerbReadsItsOwnConfigKey() {
        // Five verbs share one private helper, so a copy-paste slip would point two of them at the
        // same key and nothing above would notice — every shipped value would still be returned by
        // *some* getter. Distinct answers pin the wiring.
        final ExperienceConfig distinct = mock(ExperienceConfig.class);
        when(distinct.getHusbandryFeedBabyXp()).thenReturn(11);
        when(distinct.getHusbandryShearXp()).thenReturn(22);
        when(distinct.getHusbandryHiveXp()).thenReturn(33);
        when(distinct.getHusbandryMilkXp()).thenReturn(44);
        when(distinct.getHusbandryBrushXp()).thenReturn(55);

        final HusbandryManager distinctManager = managerWithConfig(distinct);
        assertEquals(11F, distinctManager.getFeedBabyXp());
        assertEquals(22F, distinctManager.getShearXp());
        assertEquals(33F, distinctManager.getHiveXp());
        assertEquals(44F, distinctManager.getMilkXp());
        assertEquals(55F, distinctManager.getBrushXp());
    }

    @Test
    void theFlatVerbsFallBackToTheirShippedDefaultsWithNoConfigBound() {
        McMMOMod.setExperienceConfig(null);
        assertEquals(HusbandryManager.DEFAULT_FEED_BABY_XP, manager.getFeedBabyXp());
        assertEquals(HusbandryManager.DEFAULT_SHEAR_XP, manager.getShearXp());
        assertEquals(HusbandryManager.DEFAULT_HIVE_XP, manager.getHiveXp());
        assertEquals(HusbandryManager.DEFAULT_MILK_XP, manager.getMilkXp());
        assertEquals(HusbandryManager.DEFAULT_BRUSH_XP, manager.getBrushXp());
        assertEquals(HusbandryManager.DEFAULT_RAISE_MULTIPLIER, manager.getRaiseMultiplier());
    }

    @Test
    void negativeFlatValuesAreClamped() {
        final ExperienceConfig broken = mock(ExperienceConfig.class);
        when(broken.getHusbandryShearXp()).thenReturn(-1);
        when(broken.getHusbandryMilkXp()).thenReturn(-9999);

        final HusbandryManager brokenManager = managerWithConfig(broken);
        assertEquals(0F, brokenManager.getShearXp());
        assertEquals(0F, brokenManager.getMilkXp());
    }

    // --- The shipped defaults agree with the shipped YAML ------------------------------------------

    @Test
    void theJavaDefaultsMatchTheBundledConfig() {
        // The constants are the no-config fallback AND the default argument every ExperienceConfig
        // getter passes, so a drift between them and experience.yml would show up only for players
        // whose config predates the key — the exact "a changed default never reaches an existing
        // on-disk config" trap this port has already hit once.
        final ExperienceConfig shipped = McMMOMod.getExperienceConfig();
        assertEquals(HusbandryManager.DEFAULT_FEED_BABY_XP, shipped.getHusbandryFeedBabyXp());
        assertEquals(HusbandryManager.DEFAULT_SHEAR_XP, shipped.getHusbandryShearXp());
        assertEquals(HusbandryManager.DEFAULT_HIVE_XP, shipped.getHusbandryHiveXp());
        assertEquals(HusbandryManager.DEFAULT_MILK_XP, shipped.getHusbandryMilkXp());
        assertEquals(HusbandryManager.DEFAULT_BRUSH_XP, shipped.getHusbandryBrushXp());
        assertEquals(HusbandryManager.DEFAULT_RAISE_MULTIPLIER,
                shipped.getHusbandryRaiseMultiplier());
        assertEquals(HusbandryManager.DEFAULT_HARVEST_COOLDOWN_SECONDS,
                shipped.getHusbandryHarvestCooldownSeconds());
    }

    // --- Stage 4: the harvest cooldown and Beekeeper ------------------------------------------------

    @Test
    void theHarvestCooldownIsTheShippedFiveMinutes() {
        // The one number standing between "milk a cow" and the fastest XP in the mod. Vanilla puts no
        // cooldown on either milking or brushing -- and the brush is the one that looks limited and
        // is not, since brush/armadillo.json carries no conditions and brushScute never touches the
        // scute-shed timer.
        assertEquals(300, manager.getHarvestCooldownSeconds());
    }

    @Test
    void aMissingConfigStillYieldsTheShippedCooldownRatherThanZero() {
        // ⚠️ The failure mode this pins is silent and total: a 0 here does not "use a default", it
        // DISABLES the gate, which is the difference between a bounded verb and an infinite one.
        McMMOMod.setExperienceConfig(null);
        assertEquals(HusbandryManager.DEFAULT_HARVEST_COOLDOWN_SECONDS,
                manager.getHarvestCooldownSeconds());
    }

    @Test
    void aConfiguredZeroCooldownIsHonouredAsAnEscapeHatch() {
        final ExperienceConfig noCooldown = mock(ExperienceConfig.class);
        when(noCooldown.getHusbandryHarvestCooldownSeconds()).thenReturn(0);
        assertEquals(0, managerWithConfig(noCooldown).getHarvestCooldownSeconds(),
                "0 must reach the listener intact so the gate can be turned off for diagnosis");
    }

    @Test
    void beekeeperIsLockedUntilItsRankIsReached() {
        setHusbandryLevel(0);
        assertFalse(manager.canBeekeeper(), "Beekeeper must not be free at level 0");
        assertFalse(manager.countsAsShelteredHiveHarvest());
        assertFalse(manager.rollBonusHoney());

        // skillranks.yml gates it at 100 RetroMode / 10 Standard -- past the breed-family pair and
        // Bountiful Harvest, which are all free at 1, because Beekeeper deletes a logistical puzzle
        // rather than scaling a number.
        setHusbandryLevel(1000);
        assertTrue(manager.canBeekeeper(), "Beekeeper must be unlocked by max level");
        assertTrue(manager.countsAsShelteredHiveHarvest(),
                "the anger suppression is binary on the unlock, with no roll of its own");
    }

    @Test
    void theBonusHoneyRollIsAChanceAndTheAngerSuppressionIsNot() {
        // The two halves of one sub-skill deliberately behave differently, and conflating them would
        // be a real regression: a 30%-of-the-time campfire is worse than no campfire at all, because
        // you would still have to build one for the other 70%.
        setHusbandryLevel(1000);

        advancedWithBonusHoneyChance(0.0);
        assertFalse(manager.rollBonusHoney(), "a 0 ceiling must never proc");
        assertTrue(manager.countsAsShelteredHiveHarvest(),
                "the binary half must not follow the yield roll's ceiling");

        advancedWithBonusHoneyChance(100.0);
        assertTrue(manager.rollBonusHoney(), "a 100 ceiling must always proc");
    }

    @Test
    void theShippedBonusHoneyCeilingMatchesTheJavaDefault() {
        assertEquals(HusbandryManager.DEFAULT_BONUS_HONEY_CHANCE,
                McMMOMod.getAdvancedConfig()
                        .getMaximumProbability(SubSkillType.HUSBANDRY_BEEKEEPER));
    }

    @Test
    void theThreeStageFourVerbsPayTheirShippedRates() {
        assertEquals(500F, manager.getHiveXp());
        assertEquals(200F, manager.getMilkXp());
        assertEquals(300F, manager.getBrushXp());
    }

    @Test
    void theHarvestVerbsAwardExactlyWhatTheyArePriced() {
        setHusbandryLevel(1);
        assertEquals(500F, manager.onHiveHarvest());
        assertEquals(200F, manager.onMilk());
        assertEquals(300F, manager.onBrush());
    }

    @Test
    void aVerbPricedAtZeroAwardsNothingRatherThanFiringAnEmptyGain() {
        final ExperienceConfig free = mock(ExperienceConfig.class);
        when(free.getHusbandryHiveXp()).thenReturn(0);
        when(free.getHusbandryMilkXp()).thenReturn(0);
        when(free.getHusbandryBrushXp()).thenReturn(0);

        final HusbandryManager freeManager = managerWithConfig(free);
        assertEquals(0F, freeManager.onHiveHarvest());
        assertEquals(0F, freeManager.onMilk());
        assertEquals(0F, freeManager.onBrush());
    }

    /** The same pinned-RNG trick for Beekeeper's bonus-yield roll. */
    private AdvancedConfig advancedWithBonusHoneyChance(double ceiling) {
        final AdvancedConfig advanced = mock(AdvancedConfig.class);
        lenient().when(advanced.getMaximumProbability(SubSkillType.HUSBANDRY_BEEKEEPER))
                .thenReturn(ceiling);
        lenient().when(advanced.getMaxBonusLevel(SubSkillType.HUSBANDRY_BEEKEEPER)).thenReturn(0);
        McMMOMod.setAdvancedConfig(advanced);
        return advanced;
    }

    // --- Stage 5: Selective Breeding ---------------------------------------------------------------

    @Test
    void selectiveBreedingIsLockedUntilItsRankAndThenBiasesUpward() {
        setHusbandryLevel(0);
        assertFalse(manager.canSelectiveBreeding());
        assertEquals(0.0, manager.getStatBias());

        setHusbandryLevel(1000);
        assertTrue(manager.canSelectiveBreeding());
        assertEquals(HusbandryManager.DEFAULT_MAX_STAT_BIAS, manager.getStatBias(),
                "at max level the bias must reach the shipped ceiling");
    }

    @Test
    void theBiasMovesARolledStatPartwayTowardTheSpeciesMaximum() {
        setHusbandryLevel(1000); // bias 0.25
        // Rolled 20 in a [10, 30] range: a quarter of the remaining 10 is 2.5.
        assertEquals(22.5, manager.applyStatBias(20.0, 10.0, 30.0), 1.0E-9);
    }

    @Test
    void theBiasCanNeverWorsenAFoalAndNeverExceedsTheSpeciesMaximum() {
        // Both properties matter because this same code path runs for EVERY horse bred in the world,
        // including by players who have not unlocked the sub-skill.
        setHusbandryLevel(1000);
        assertEquals(30.0, manager.applyStatBias(30.0, 10.0, 30.0), 1.0E-9,
                "a stat already at the maximum must stay there, not overshoot");
        assertTrue(manager.applyStatBias(10.0, 10.0, 30.0) > 10.0,
                "the worst possible roll must still be improved");

        setHusbandryLevel(0);
        assertEquals(17.3, manager.applyStatBias(17.3, 10.0, 30.0), 1.0E-9,
                "with the sub-skill locked the bias must be exactly the identity");
    }

    @Test
    void aDegenerateAttributeRangeIsLeftAlone() {
        setHusbandryLevel(1000);
        assertEquals(5.0, manager.applyStatBias(5.0, 5.0, 5.0), 1.0E-9);
    }

    @Test
    void theStatBiasIsHardClampedEvenIfTheConfigAsksForMore() {
        // ⚠️ Not tidiness. At 1.0 every foal would land exactly on the species maximum from the first
        // breeding, which deletes horse breeding as an activity instead of rewarding it. And because
        // the effect compounds down the generations, a too-large value reaches further than it reads.
        setHusbandryLevel(1000);
        final AdvancedConfig greedy = mock(AdvancedConfig.class);
        lenient().when(greedy.getMaxSelectiveBreedingBias()).thenReturn(5.0);
        lenient().when(greedy.getMaxBonusLevel(SubSkillType.HUSBANDRY_SELECTIVE_BREEDING))
                .thenReturn(0);
        McMMOMod.setAdvancedConfig(greedy);

        assertEquals(HusbandryManager.HARD_MAX_STAT_BIAS, manager.getStatBias());
        assertTrue(manager.applyStatBias(20.0, 10.0, 30.0) <= 30.0);
    }

    // --- Stage 5: Brood ----------------------------------------------------------------------------

    @Test
    void broodIsLockedUntilItsRank() {
        setHusbandryLevel(0);
        assertFalse(manager.canBrood());
        assertFalse(manager.rollEggHatch());
        assertFalse(manager.rollMultipleChicks());
        assertEquals(0.0, manager.getMultiChickChance());
    }

    @Test
    void theHatchRollAndTheClutchRollAreIndependent() {
        // Two effects on one sub-skill, so only one can key off the SubSkillType in ProbabilityUtil.
        // The other is scaled by hand -- the split Accelerated Growth and Bountiful Harvest also make.
        setHusbandryLevel(1000);

        final AdvancedConfig advanced = mock(AdvancedConfig.class);
        lenient().when(advanced.getMaximumProbability(SubSkillType.HUSBANDRY_BROOD)).thenReturn(100.0);
        lenient().when(advanced.getMaxBonusLevel(SubSkillType.HUSBANDRY_BROOD)).thenReturn(0);
        lenient().when(advanced.getBroodMultiChickChance()).thenReturn(0.0);
        McMMOMod.setAdvancedConfig(advanced);

        assertTrue(manager.rollEggHatch(), "a 100 hatch ceiling must always proc");
        assertFalse(manager.rollMultipleChicks(),
                "a 0 clutch chance must never proc, whatever the hatch ceiling says");
    }

    @Test
    void theShippedBroodDefaultsMatchTheJavaConstants() {
        final AdvancedConfig shipped = McMMOMod.getAdvancedConfig();
        assertEquals(HusbandryManager.DEFAULT_MULTI_CHICK_CHANCE,
                shipped.getBroodMultiChickChance());
        assertEquals(HusbandryManager.DEFAULT_MAX_STAT_BIAS,
                shipped.getMaxSelectiveBreedingBias());
    }

    // --- Stage 5: Hidden Bounty --------------------------------------------------------------------

    @Test
    void hiddenBountyIsLockedUntilItsRank() {
        setHusbandryLevel(0);
        assertFalse(manager.canHiddenBounty());
        assertFalse(manager.rollHiddenBounty());
    }

    @Test
    void aFailedMainRollNeverLooksAtTheTreasureTable() {
        // The two gates are ordered on purpose: the sub-skill roll is the cheap one, and a failure must
        // short-circuit before any per-treasure chance is evaluated.
        final java.util.List<HusbandryTreasure> table = java.util.List.of(
                treasure("string", 100.0, 0));
        assertTrue(manager.selectHiddenBounty(table, false, chance -> true).isEmpty());
    }

    @Test
    void theFirstAffordableTreasureThatRollsWins() {
        setHusbandryLevel(1000);
        final HusbandryTreasure rare = treasure("name_tag", 4.0, 500);
        final HusbandryTreasure common = treasure("string", 15.0, 0);

        // Only the common one's chance rolls, so the rare one is skipped even though it comes first.
        assertSame(common, manager.selectHiddenBounty(java.util.List.of(rare, common), true,
                chance -> chance >= 15.0).orElseThrow());
        // Both roll: file order decides, which is why treasures.yml warns to put the rarest first.
        assertSame(rare, manager.selectHiddenBounty(java.util.List.of(rare, common), true,
                chance -> true).orElseThrow());
    }

    @Test
    void aTreasureAboveThePlayersLevelIsSkippedEntirely() {
        setHusbandryLevel(100);
        final HusbandryTreasure tooGood = treasure("name_tag", 100.0, 500);
        final HusbandryTreasure reachable = treasure("string", 100.0, 0);

        assertSame(reachable, manager.selectHiddenBounty(java.util.List.of(tooGood, reachable), true,
                chance -> true).orElseThrow());
    }

    @Test
    void anEmptyOrNullTableIsSafe() {
        assertTrue(manager.selectHiddenBounty(java.util.List.of(), true, chance -> true).isEmpty());
        assertTrue(manager.selectHiddenBounty(null, true, chance -> true).isEmpty());
    }

    @Test
    void aHiddenBountyFindPaysItsOwnSmallXpAndZeroIsNotAnAward() {
        setHusbandryLevel(1);
        assertEquals(50F, manager.onHiddenBountyFound(50));
        assertEquals(0F, manager.onHiddenBountyFound(0));
        assertEquals(0F, manager.onHiddenBountyFound(-10), "a mistyped negative must not pay");
    }

    private static HusbandryTreasure treasure(String material, double dropChance, int dropLevel) {
        return new HusbandryTreasure(new ItemSpec(material, 1, null, java.util.List.of()), 10,
                dropChance, dropLevel);
    }

    // --- Stage 6: Herdsman's Call ------------------------------------------------------------------

    @Test
    void herdsmansCallIsLockedUntilItsRank() {
        setHusbandryLevel(0);
        assertFalse(manager.canHerdsmansCall());

        setHusbandryLevel(1000);
        assertTrue(manager.canHerdsmansCall());
    }

    @Test
    void theHerdRadiusIsZeroUnlessTheCallIsSounding() {
        // ⚠️ Not cosmetic. This value gates a per-tick entity sweep that runs for every online player
        // forever, so "0 while idle" is what keeps the ability free when nobody is using it. A radius
        // that read its configured value unconditionally would scan and discard 20 times a second.
        setHusbandryLevel(1000);
        assertFalse(manager.isHerdsmansCallActive());
        assertEquals(0.0, manager.getHerdRadius());

        // getMaxHerdRadius answers the other question -- "how far can this reach" -- for /mcstats.
        assertEquals(HusbandryManager.DEFAULT_HERD_RADIUS, manager.getMaxHerdRadius());
    }

    @Test
    void theHerdRadiusIsHardClampedEvenIfTheConfigAsksForMore() {
        final AdvancedConfig greedy = mock(AdvancedConfig.class);
        lenient().when(greedy.getHerdsmansCallRadius()).thenReturn(400.0);
        McMMOMod.setAdvancedConfig(greedy);

        assertEquals(HusbandryManager.HARD_MAX_HERD_RADIUS, manager.getMaxHerdRadius(),
                "a mistyped radius must not turn a per-tick sweep into an eight-chunk box scan");
    }

    @Test
    void theCallDurationHasAFloorOfAtLeastOneTick() {
        assertEquals(HusbandryManager.DEFAULT_HERDSMANS_CALL_DURATION_TICKS,
                manager.getHerdsmansCallDurationTicks());

        final AdvancedConfig zero = mock(AdvancedConfig.class);
        lenient().when(zero.getHerdsmansCallDurationTicks()).thenReturn(0);
        McMMOMod.setAdvancedConfig(zero);
        assertEquals(1, manager.getHerdsmansCallDurationTicks(),
                "a 0 must not make the ability fire and end in the same tick");
    }

    @Test
    void theCallGuaranteesTheHarvestBonusWithoutNeedingBountifulHarvest() {
        // ⚠️ The double-yield half rides rollBonusHarvestDrop because all four harvest verbs already
        // route their bonus through it -- so the super reaches all four for free and cannot be wired
        // into three of them by accident. It deliberately does NOT require Bountiful Harvest's rank:
        // gating one sub-skill's effect behind another's is a hidden dependency nobody can read off a
        // stats screen.
        setHusbandryLevel(1000);
        final AdvancedConfig noBonus = mock(AdvancedConfig.class);
        lenient().when(noBonus.getMaximumProbability(SubSkillType.HUSBANDRY_BOUNTIFUL_HARVEST))
                .thenReturn(0.0);
        lenient().when(noBonus.getMaxBonusLevel(SubSkillType.HUSBANDRY_BOUNTIFUL_HARVEST))
                .thenReturn(0);
        // ⚠️ Stub the radius too. An unstubbed Mockito double answers 0.0, which here would read as
        // "the sweep is off" and quietly pass the bonus assertion while proving nothing about the
        // radius -- the "stub the shipped default, not Mockito's zero" trap.
        lenient().when(noBonus.getHerdsmansCallRadius())
                .thenReturn(HusbandryManager.DEFAULT_HERD_RADIUS);
        McMMOMod.setAdvancedConfig(noBonus);

        assertFalse(manager.rollBonusHarvestDrop(), "a 0 ceiling must never proc on its own");

        mmoPlayer.setAbilityMode(SuperAbilityType.HERDSMANS_CALL, true);
        try {
            assertTrue(manager.rollBonusHarvestDrop(),
                    "while the call sounds every harvest must double, whatever the roll says");
            assertTrue(manager.isHerdsmansCallActive());
            assertEquals(HusbandryManager.DEFAULT_HERD_RADIUS, manager.getHerdRadius(),
                    "and the sweep must switch on");
        } finally {
            mmoPlayer.setAbilityMode(SuperAbilityType.HERDSMANS_CALL, false);
        }
    }

    @Test
    void husbandryOwnsExactlyOneSuperAbilityAndItIsTheRightOne() {
        // Pins the SkillTools switch arm and the subSkillTypeDefinition wiring together. Miss either
        // and the ability compiles, boots and then resolves its cooldown against the wrong skill.
        final SkillTools tools = new SkillTools();
        assertEquals(SubSkillType.HUSBANDRY_HERDSMANS_CALL,
                SuperAbilityType.HERDSMANS_CALL.getSubSkillTypeDefinition());
        assertEquals(SuperAbilityType.HERDSMANS_CALL,
                tools.getSuperAbility(PrimarySkillType.HUSBANDRY));
        assertEquals(PrimarySkillType.HUSBANDRY,
                tools.getPrimarySkillBySuperAbility(SuperAbilityType.HERDSMANS_CALL));
    }
}
