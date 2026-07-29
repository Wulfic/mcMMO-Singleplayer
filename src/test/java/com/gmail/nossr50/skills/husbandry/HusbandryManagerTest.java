package com.gmail.nossr50.skills.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
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
    private static final int SNIFFER_BREED_XP = 1500;

    private McMMOPlayer mmoPlayer;
    private HusbandryManager manager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        final PlatformPlayer player = mock(PlatformPlayer.class);
        lenient().when(player.getName()).thenReturn("Farmer");
        lenient().when(player.getUniqueId()).thenReturn(UID);
        lenient().when(player.isCreative()).thenReturn(false);

        mmoPlayer = new McMMOPlayer(player, new PlayerProfile("Farmer", UID, 0));
        manager = mmoPlayer.getHusbandryManager();
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setAdvancedConfig(null);
    }

    /** Rebinds a mocked config for the cases the shipped file cannot express. */
    private HusbandryManager managerWithConfig(ExperienceConfig config) {
        McMMOMod.setExperienceConfig(config);
        return manager;
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
    void aSkillWithNoSubSkillsYetResolvesToAnEmptySetRatherThanNull() {
        // Husbandry is the first skill in the mod to have ZERO sub-skills -- they arrive with their
        // stages, so at stage 0 there are none. The only thing between that and an NPE is that
        // buildPrimarySkillChildrenMap pre-seeds an empty set for every PrimarySkillType before
        // filling it, and SkillStatsRenderer feeds the result straight into `new ArrayList<>(...)`.
        // That pre-seed is invisible and easy to "simplify" away, so /mcstats husbandry crashing is
        // pinned here rather than discovered by typing the command.
        assertNotNull(new SkillTools().getSubSkills(PrimarySkillType.HUSBANDRY),
                "a skill with no sub-skills must map to an empty set, not to null");
        assertTrue(new SkillTools().getSubSkills(PrimarySkillType.HUSBANDRY).isEmpty(),
                "stage 0 ships no Husbandry sub-skills; they land with their stages");
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
    }
}
