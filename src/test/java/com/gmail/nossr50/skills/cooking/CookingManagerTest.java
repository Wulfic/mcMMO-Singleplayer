package com.gmail.nossr50.skills.cooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.skills.SkillTools;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stage 1 of Cooking: the skill is <b>registered</b> and nothing fires it. There is no XP hook, no
 * proc and no effect until stages 2–4, so what this class pins is exactly the registration surface —
 * and that surface is where this port has been bitten repeatedly.
 *
 * <p>Every assertion here would pass silently as a compile if it were not asserted:
 * <ul>
 *   <li>a missing {@code initManager} case leaves the typed getter returning {@code null}, so every
 *       future call site NPEs at runtime while compiling perfectly;</li>
 *   <li>a sub-skill's parent is resolved from its enum-name prefix, so a {@code COOKING_*} constant
 *       that landed on the wrong parent would gate on the wrong level with no error anywhere;</li>
 *   <li>an absent or mis-indented {@code skillranks.yml} block leaves every rank at 0 forever behind
 *       a fully-built skill, which is the shape GitHub #7 shipped in.</li>
 * </ul>
 *
 * <p>It runs against the <b>real bundled config files</b> rather than mocks, because at this stage
 * the YAML <em>is</em> the feature: mocked configs would prove the getters compile while a
 * misplaced {@code Cooking:} block shipped a skill that unlocks nothing.
 */
class CookingManagerTest {

    private static final UUID UID = UUID.fromString("00000000-0000-0000-0000-00000000c00c");

    /** The shipped skillranks.yml RetroMode ladders, restated so a retune comes through this test. */
    private static final int[] POWER_COOK_RETRO = {100, 250, 450, 700, 1000};
    private static final int[] MASTER_CHEF_RETRO = {50, 200, 400, 650, 900};
    private static final int[] KITCHEN_EFFICIENCY_RETRO = {250, 500, 850};

    private PlayerProfile profile;
    private McMMOPlayer mmoPlayer;
    private CookingManager manager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));

        final PlatformPlayer player = mock(PlatformPlayer.class);
        lenient().when(player.getName()).thenReturn("Cook");
        lenient().when(player.getUniqueId()).thenReturn(UID);
        lenient().when(player.isCreative()).thenReturn(false);

        profile = new PlayerProfile("Cook", UID, 0);
        mmoPlayer = new McMMOPlayer(player, profile);
        manager = mmoPlayer.getCookingManager();
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setAdvancedConfig(null);
        McMMOMod.setRankConfig(null);
    }

    // --- Registration ---------------------------------------------------------------------------

    @Test
    void theSkillIsWiredEndToEndOnARealPlayer() {
        // Pins the initManager case and the typed getter together. Without the case the getter
        // returns null and every future Cooking call site NPEs while compiling perfectly.
        assertNotNull(manager, "McMMOPlayer must build a CookingManager for COOKING");
        assertSame(manager, mmoPlayer.getCookingManager(), "the manager is cached, not rebuilt");
    }

    @Test
    void theManagerReadsTheCookingLevelAndNotAnotherSkills() {
        // A SkillManager built with the wrong PrimarySkillType compiles and reports a neighbouring
        // skill's level, which is invisible until a gate starts unlocking at the wrong time.
        profile.modifySkill(PrimarySkillType.COOKING, 400);
        profile.modifySkill(PrimarySkillType.SMELTING, 900);
        assertEquals(400, manager.getSkillLevel());
    }

    @Test
    void cookingIsAMiscSkillAndNotAChild() {
        // Processing, not gathering: nothing is taken out of the world on any of its seams.
        assertTrue(new SkillTools().getMiscSkills().contains(PrimarySkillType.COOKING),
                "Cooking belongs with Smelting/Repair/Salvage, the other processing skills");
        // A child skill earns no XP of its own and splits any award into its parents, which would
        // discard every cook the skill is ever paid for.
        assertFalse(SkillTools.isChildSkill(PrimarySkillType.COOKING));
    }

    @Test
    void everyCookingSubSkillResolvesToCookingAndNotToACollidingPrefix() {
        // The parent comes from the enum name up to the first '_', matched against the WHOLE prefix.
        // SubSkillType warns in-file that a sub-skill must not share a name with a PrimarySkillType;
        // COOKING_SMELTING and COOKING_ALCHEMY would do exactly that, and this is what would catch a
        // future one landing on the wrong skill.
        final SkillTools skillTools = new SkillTools();
        for (SubSkillType subSkill : SubSkillType.values()) {
            if (!subSkill.name().startsWith("COOKING_")) {
                continue;
            }
            assertEquals(PrimarySkillType.COOKING,
                    skillTools.getPrimarySkillBySubSkill(subSkill),
                    () -> subSkill + " must parent onto COOKING");
        }
    }

    @Test
    void theRosterIsExactlyTheThreeRuledSubSkills() {
        // Quality (Gourmet Meal / Precision Cooking / Meal Memory), Cook's Diet, Flavor Burst,
        // Butchery and Holy Cook were all cut with reasons recorded on the enum. A fourth constant
        // appearing here means one of them came back without the ruling being revisited.
        final long cookingSubSkills = java.util.Arrays.stream(SubSkillType.values())
                .filter(s -> s.name().startsWith("COOKING_"))
                .count();
        assertEquals(3, cookingSubSkills);
    }

    // --- skillranks.yml -------------------------------------------------------------------------

    @Test
    void theShippedRankLaddersUnlockAtTheDocumentedRetroModeLevels() {
        assertLadder(SubSkillType.COOKING_POWER_COOK, POWER_COOK_RETRO);
        assertLadder(SubSkillType.COOKING_MASTER_CHEF, MASTER_CHEF_RETRO);
        assertLadder(SubSkillType.COOKING_KITCHEN_EFFICIENCY, KITCHEN_EFFICIENCY_RETRO);
    }

    @Test
    void aPlayerClimbsThroughEveryRankOfEverySubSkill() {
        // The other direction, and the one that matters: a ladder can be present in the YAML and
        // still never be reached if the sub-skill is wired to the wrong parent level. Walk a real
        // profile up each ladder and assert the rank actually advances.
        assertClimbs(SubSkillType.COOKING_POWER_COOK, POWER_COOK_RETRO);
        assertClimbs(SubSkillType.COOKING_MASTER_CHEF, MASTER_CHEF_RETRO);
        assertClimbs(SubSkillType.COOKING_KITCHEN_EFFICIENCY, KITCHEN_EFFICIENCY_RETRO);
    }

    @Test
    void anUnrankedCookHasRankZeroInEverySubSkill() {
        // The reference point. Rank 0 is the landmine this port has hit four times: a rank-indexed
        // lookup that assumes at least rank 1 reads index -1. Every Cooking mechanic must therefore
        // be written to no-op at 0, and this is what says 0 is genuinely reachable.
        profile.modifySkill(PrimarySkillType.COOKING, 0);
        assertEquals(0, RankUtils.getRank(mmoPlayer, SubSkillType.COOKING_POWER_COOK));
        assertEquals(0, RankUtils.getRank(mmoPlayer, SubSkillType.COOKING_MASTER_CHEF));
        assertEquals(0, RankUtils.getRank(mmoPlayer, SubSkillType.COOKING_KITCHEN_EFFICIENCY));
    }

    private void assertLadder(SubSkillType subSkill, int[] retroModeLevels) {
        assertEquals(retroModeLevels.length, subSkill.getNumRanks(),
                () -> subSkill + "'s declared rank count must match its shipped ladder");
        for (int rank = 1; rank <= retroModeLevels.length; rank++) {
            final int expected = retroModeLevels[rank - 1];
            final int actual = RankUtils.getRankUnlockLevel(subSkill, rank);
            assertEquals(expected, actual,
                    subSkill + " rank " + rank + " must unlock at RetroMode level " + expected);
        }
    }

    private void assertClimbs(SubSkillType subSkill, int[] retroModeLevels) {
        for (int rank = 1; rank <= retroModeLevels.length; rank++) {
            profile.modifySkill(PrimarySkillType.COOKING, retroModeLevels[rank - 1]);
            assertEquals(rank, RankUtils.getRank(mmoPlayer, subSkill),
                    subSkill + " must read rank " + rank + " at level " + retroModeLevels[rank - 1]);
        }
    }
}
