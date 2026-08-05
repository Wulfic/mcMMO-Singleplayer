package com.gmail.nossr50.skills.cooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

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
import com.gmail.nossr50.skills.smelting.SmeltingManager;
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

    /**
     * The shipped experience.yml prices, restated so a retune has to come through this test rather
     * than through a config edit nobody reviews.
     */
    private static final int BEEF_COOK_XP = 100;
    private static final int KELP_COOK_XP = 60;
    private static final int BREAD_CRAFT_XP = 80;
    private static final int COOKIE_CRAFT_XP = 10;

    /** The shipped {@code ExploitFix.Cooking.Max_Cooks_Per_Hour}. */
    private static final int MAX_COOKS_PER_HOUR = CookingManager.DEFAULT_MAX_COOKS_PER_HOUR;

    /** Vanilla's batch sizes, read off the shipped recipe JSONs. These are the 8x/9x/4x traps. */
    private static final int COOKIE_BATCH = 8;
    private static final int DRIED_KELP_BATCH = 9;
    private static final int HONEY_BOTTLE_BATCH = 4;

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

    // --- Stage 2: cook XP ------------------------------------------------------------------------

    @Test
    void theFurnacePathIsKeyedOnTheInputAndTheCraftingPathOnTheResult() {
        // The single most confusable thing in this skill: three hooks, three different keys. The
        // furnace seam injects BEFORE vanilla's craftRecipe (which is what decrements the input), so
        // it can only read the input; the crafting seam only ever sees the result. A getter pointed
        // at the wrong section still compiles and still returns a number -- it just returns 0.
        assertEquals(BEEF_COOK_XP, manager.getCookXp("Beef"));
        assertEquals(BREAD_CRAFT_XP, manager.getCraftXp("Bread"));

        // And the converse, which is what actually pins the two sections apart: neither key exists
        // in the other's space. A single flattened section would make both of these non-zero.
        assertEquals(0, manager.getCraftXp("Beef"), "a raw input is not a crafting result");
        assertEquals(0, manager.getCookXp("Bread"), "bread is crafted, never smelted");
    }

    @Test
    void aCookPaysTheInputsPriceOnce() {
        final CookingManager.CookAward award = manager.onCook("Beef", 0L);

        assertEquals(BEEF_COOK_XP, award.xp());
        assertEquals(1, award.creditedItems(), "a cook produces exactly one item");
        assertFalse(award.capReached());
        assertTrue(profile.getSkillXpLevelRaw(PrimarySkillType.COOKING) > 0,
                "the award must actually reach the profile, not just be returned");
    }

    @Test
    void anUnpricedItemPaysNothingAndCostsNoCapBudget() {
        final float before = profile.getSkillXpLevelRaw(PrimarySkillType.COOKING);

        assertEquals(0F, manager.onCook("Iron_Ore", 0L).xp(), "ore is Smelting's, never Cooking's");
        assertEquals(0F, manager.onCook("Not_A_Real_Item", 0L).xp());
        assertEquals(0F, manager.onCraft("Not_A_Real_Item", 64, 0L).xp());
        assertEquals(before, profile.getSkillXpLevelRaw(PrimarySkillType.COOKING));

        // The important half: an unpriced item must not quietly eat the hourly budget, or a stack of
        // crafted planks would starve the cap before a single steak was cooked.
        assertEquals(MAX_COOKS_PER_HOUR, manager.onCraft("Cookie", MAX_COOKS_PER_HOUR, 0L)
                .creditedItems(), "unpriced items must leave the whole budget intact");
    }

    @Test
    void chorusFruitIsPricedZeroExplicitlyRatherThanBeingAbsent() {
        // Chorus fruit IS one of vanilla's nine furnace food inputs, and it is fully automatable.
        // It is in the shipped table at 0 on purpose: an absent key and a zeroed key behave the
        // same, but only one of them says a decision was made.
        assertEquals(0, manager.getCookXp("Chorus_Fruit"));
    }

    // --- Stage 2: the batch count ----------------------------------------------------------------

    @Test
    void aBatchCraftPaysPerItemAndNotPerCraft() {
        // ⚠️ CraftingResultSlot#onCrafted(ItemStack) fires ONCE PER TAKE and the slot's `amount`
        // field holds the whole batch. Pricing per event instead of per item pays for one cookie
        // when eight were made -- and a shift-clicked stack pays 1/64th. This is the test that says
        // which way round it is.
        final CookingManager.CookAward award = manager.onCraft("Cookie", COOKIE_BATCH, 0L);

        assertEquals(COOKIE_CRAFT_XP * COOKIE_BATCH, award.xp());
        assertEquals(COOKIE_BATCH, award.creditedItems());
    }

    @Test
    void aSingleResultCraftPaysExactlyItsPrice() {
        // The reference point for the test above: with a batch of 1 the per-item and per-event
        // readings agree, which is exactly why a test that only crafts one loaf proves nothing.
        assertEquals(BREAD_CRAFT_XP, manager.onCraft("Bread", 1, 0L).xp());
    }

    @Test
    void takingNothingPaysNothing() {
        assertEquals(0F, manager.onCraft("Cookie", 0, 0L).xp());
        assertEquals(0F, manager.onCraft("Cookie", -3, 0L).xp(), "a negative batch is not a refund");
    }

    // --- Stage 2: D-CK8a, the free infinite XP loops ---------------------------------------------

    @Test
    void driedKelpPaysOnTheFurnacePathAndNothingOnTheCraftingPath() {
        // ⚠️⚠️ 9 dried kelp craft into a dried kelp block and the block crafts straight back into 9
        // dried kelp, consuming NOTHING. Priced per item, that is infinite XP at click speed with no
        // ingredient, no fuel and no farm -- strictly worse than the eight-smoker array the hourly
        // cap was written against, because this one is bounded only by how fast you can click.
        //
        // Both directions, because either one alone is half a test: pricing the loop at 0 is only
        // correct if smoking real kelp still pays.
        assertEquals(KELP_COOK_XP, manager.onCook("Kelp", 0L).xp(),
                "smoking kelp is a real cook and must still pay");
        assertEquals(0F, manager.onCraft("Dried_Kelp", DRIED_KELP_BATCH, 0L).xp(),
                "crafting dried kelp out of its own storage block must pay nothing");
    }

    @Test
    void honeyBottlePaysNothingOnTheCraftingPath() {
        // The same round trip through a honey block, x4, with the bottles returned.
        assertEquals(0F, manager.onCraft("Honey_Bottle", HONEY_BOTTLE_BATCH, 0L).xp());
    }

    @Test
    void theGoldAndSuspiciousStewFoodsAreZeroedRatherThanForgotten() {
        // The four made foods deliberately left out of the skill. Each is an explicit 0 in the
        // shipped config, so that "Cooking pays nothing for this" is a recorded decision rather than
        // an item somebody forgot to add.
        assertEquals(0F, manager.onCraft("Golden_Apple", 1, 0L).xp());
        assertEquals(0F, manager.onCraft("Golden_Carrot", 1, 0L).xp());
        assertEquals(0F, manager.onCraft("Suspicious_Stew", 1, 0L).xp());
    }

    // --- Stage 2: the rolling cook cap -----------------------------------------------------------

    @Test
    void theShippedCapIsTheDocumentedRateOverOneRollingHour() {
        assertEquals(MAX_COOKS_PER_HOUR, manager.getMaxCooksPerHour());
        assertEquals(3600 * 20, manager.getCookRateWindowTicks(), "one hour of world ticks");
        assertTrue(manager.isCookRateCapped());
    }

    @Test
    void twoThousandCooksInOneHourCreditExactlyTwelveHundred() {
        // The exploit-cap test the plan demands, in the shape the furnace actually produces them:
        // one item at a time, all inside a single window.
        // ⚠️ Cooldowns in this codebase count WORLD TICKS, not wall-clock -- every call here shares
        // one tick on purpose, which is also the worst case (an eight-smoker array bursting).
        int credited = 0;
        for (int cook = 0; cook < 2000; cook++) {
            credited += manager.onCook("Beef", 500L).creditedItems();
        }

        assertEquals(MAX_COOKS_PER_HOUR, credited);
    }

    @Test
    void theCapCountsItemsSoOneShiftClickCannotBuyTheWholeHour() {
        // ⚠️ The 64x hole. If the cap counted crafting EVENTS, one take of 64 cookies would spend a
        // single unit of a 1,200 budget while paying 64 items' worth of XP -- and the skill's only
        // anti-farm gate would be worth 1/64th of its stated value.
        final CookingManager.CookAward first = manager.onCraft("Cookie", MAX_COOKS_PER_HOUR, 0L);
        assertEquals(MAX_COOKS_PER_HOUR, first.creditedItems(), "one batch can spend the whole hour");

        final CookingManager.CookAward second = manager.onCraft("Cookie", 8, 0L);
        assertEquals(0, second.creditedItems(), "the budget is items, and it is now gone");
        assertEquals(0F, second.xp());
    }

    @Test
    void aBatchStraddlingTheCapIsCreditedInPartRatherThanRefusedWhole() {
        // Refusing the whole batch would make the cap's bite depend on batch size: a 9-item craft
        // would forfeit 9 units of budget it was entitled to, while nine 1-item crafts would not.
        manager.onCraft("Cookie", MAX_COOKS_PER_HOUR - 3, 0L);

        final CookingManager.CookAward straddle = manager.onCraft("Cookie", 10, 0L);

        assertEquals(3, straddle.creditedItems());
        assertEquals(COOKIE_CRAFT_XP * 3, straddle.xp());
        assertTrue(straddle.capReached(), "a trimmed batch is the cap biting, and must say so");
    }

    @Test
    void theCapIsAnnouncedOnceAWindowAndNotOncePerCook() {
        // Spend the window's budget EXACTLY. A batch trimmed on the way in would announce here and
        // the loop below would then measure nothing -- which is how this test first failed.
        assertEquals(MAX_COOKS_PER_HOUR,
                manager.onCraft("Cookie", MAX_COOKS_PER_HOUR, 0L).creditedItems());

        long announcements = 0;
        for (int cook = 0; cook < 50; cook++) {
            if (manager.onCook("Beef", 0L).capReached()) {
                announcements++;
            }
        }

        assertEquals(1, announcements,
                "an eight-smoker array would otherwise print one line per finished cook");
    }

    @Test
    void theBudgetRefreshesOnceTheWindowHasElapsed() {
        manager.onCraft("Cookie", MAX_COOKS_PER_HOUR, 0L);
        assertEquals(0, manager.onCook("Beef", 0L).creditedItems());

        // One tick short of the window is still the same window.
        assertEquals(0, manager.onCook("Beef", manager.getCookRateWindowTicks() - 1)
                .creditedItems());

        assertEquals(1, manager.onCook("Beef", manager.getCookRateWindowTicks()).creditedItems(),
                "a fresh hour must pay again");
    }

    @Test
    void aWorldClockThatMovesBackwardsResetsTheWindowRatherThanLockingTheSkillOut() {
        // /time set, or a restore from backup. Refusing to reset would silently stop paying Cooking
        // XP for as long as the clock stayed behind, with nothing to distinguish it from a bug.
        manager.onCraft("Cookie", MAX_COOKS_PER_HOUR, 5000L);
        assertEquals(0, manager.onCook("Beef", 5000L).creditedItems());

        assertEquals(1, manager.onCook("Beef", 10L).creditedItems());
    }

    @Test
    void aCapOfZeroDisablesTheGateEntirely() {
        final ExperienceConfig uncapped = spy(McMMOMod.getExperienceConfig());
        doReturn(0).when(uncapped).getCookingMaxCooksPerHour();
        McMMOMod.setExperienceConfig(uncapped);

        assertFalse(manager.isCookRateCapped());
        int credited = 0;
        for (int cook = 0; cook < MAX_COOKS_PER_HOUR + 100; cook++) {
            credited += manager.onCook("Beef", 0L).creditedItems();
        }

        assertEquals(MAX_COOKS_PER_HOUR + 100, credited, "0 means no cap, not a cap of nothing");
    }

    // --- Stage 2: the Smelting boundary ----------------------------------------------------------

    @Test
    void noItemIsBothSmeltableAndCookable() {
        // The furnace listener checks Smelting first and Cooking is the else, so an item listed in
        // both sections would pay Smelting only. That ordering is a ruling, not an accident -- but
        // the shipped configs must not rely on it, because the ambiguity is invisible in the YAML.
        for (String ore : new String[] {"Iron_Ore", "Gold_Ore", "Ancient_Debris", "Raw_Copper",
                "Cobbled_Deepslate"}) {
            assertEquals(0, manager.getCookXp(ore), ore + " is Smelting's input, not Cooking's");
        }
        for (String food : new String[] {"Beef", "Porkchop", "Chicken", "Mutton", "Rabbit", "Cod",
                "Salmon", "Potato", "Kelp"}) {
            assertFalse(SmeltingManager.isSmeltable(food),
                    food + " must not also be smeltable, or one cook would pay two skills");
            assertTrue(CookingManager.isCookable(food), food + " must be a priced Cooking input");
        }
    }

    @Test
    void isCookableAgreesWithThePriceItReads() {
        assertTrue(CookingManager.isCookable("Beef"));
        assertFalse(CookingManager.isCookable("Chorus_Fruit"), "priced 0 is not cookable");
        assertFalse(CookingManager.isCookable("Not_A_Real_Item"));

        // Fails closed with no config wired: a furnace with no opinion available pays nobody rather
        // than paying twice.
        McMMOMod.setExperienceConfig(null);
        assertFalse(CookingManager.isCookable("Beef"));
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
