package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.cooking.CookingManager;
import com.gmail.nossr50.skills.smelting.SmeltingManager;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cooking's Stage 2 <b>trigger layer</b> — the half {@code CookingManagerTest} cannot reach.
 *
 * <p>That test pins the pricing, the batch arithmetic and the hourly cap as arithmetic. What is
 * entirely unproven without this file is the wiring, and wiring is where this port has been bitten
 * over and over: a skill can be registered, priced, configured, unit-tested and green while
 * <b>nothing on any seam ever calls it</b>. Specifically, what is asserted here is:
 *
 * <ul>
 *   <li>that a finished furnace cook actually reaches {@link CookingManager#onCook} at all;</li>
 *   <li>that the <b>input</b> is the key it is charged on, not the result — a hook reading the
 *       output slot would charge {@code Cooked_Beef}, find no price, and pay zero forever;</li>
 *   <li>⚠️ that <b>ore and food are mutually exclusive</b> on the shared furnace. Both skills read
 *       the same input slot on the same block, and nothing in the config format stops an item being
 *       listed under both — so the branch order is a ruling, and this is where it is written down
 *       in executable form;</li>
 *   <li>that a crafted batch reaches {@link CookingManager#onCraft} carrying the <b>whole count</b>,
 *       which is the 8× cookie trap;</li>
 *   <li>that the client-side copy of a screen handler pays nobody, which would otherwise double
 *       every craft in singleplayer.</li>
 * </ul>
 */
class CookingListenerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
    }

    /** The furnace under test. Any position; it is only ever a key. */
    private static final BlockPos FURNACE_POS = new BlockPos(12, 64, -30);

    /** The campfire under test, at a deliberately different position from the furnace. */
    private static final BlockPos CAMPFIRE_POS = new BlockPos(-5, 70, 18);

    /** An arbitrary but non-zero world time, so a bug that passes 0 everywhere is visible. */
    private static final long WORLD_TICK = 4242L;

    /** A cook that paid and did not trip the hourly cap. */
    private static final CookingManager.CookAward PAID =
            new CookingManager.CookAward(100F, 1, false);

    /** One coal, and what a rank-3 bonus turns it into. Distinct values, so a no-op is visible. */
    private static final int VANILLA_BURN = 1600;
    private static final int BOOSTED_BURN = 6400;

    private UUID uuid;
    private ServerWorld world;
    private McMMOPlayer mmoPlayer;
    private CookingManager cooking;
    private SmeltingManager smelting;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        // A real config, not a mock: SmeltingManager#isSmeltable and CookingManager#isCookable are
        // both static reads of the shipped YAML, and they are the branch under test. Stubbing them
        // out would leave this asserting that the mock returns what the mock was told to return.
        McMMOMod.setExperienceConfig(new ExperienceConfig(dataFolder));
        // Stage 3 added a second real-config branch: the Bonus_Drops.Smelting / Bonus_Drops.Cooking
        // membership test that decides which skill owns a finished cook's result. Same reasoning as
        // the ExperienceConfig above — stubbing it would leave this asserting that a mock returns
        // what the mock was told to return.
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));

        uuid = UUID.randomUUID();
        world = mock(ServerWorld.class);
        lenient().when(world.getTime()).thenReturn(WORLD_TICK);

        cooking = mock(CookingManager.class);
        smelting = mock(SmeltingManager.class);
        // ⚠️ Not optional. onCook/onCraft return a record, and an unstubbed mock hands back null,
        // which the listener dereferences on the very next line to check the cap. Every test here
        // would die on an NPE rather than on what it was actually asserting.
        lenient().when(cooking.onCook(any(), anyLong())).thenReturn(PAID);
        lenient().when(cooking.onCraft(any(), anyInt(), anyLong())).thenReturn(PAID);
        // Same trap in its primitive form: an unstubbed int-returning mock hands back 0, so a fuel
        // hook that IS wired would look exactly like one that is not — it would just put the furnace
        // out. Both managers are stubbed so the assertions read a value neither default produces.
        lenient().when(cooking.boostFuelTime(anyInt())).thenReturn(BOOSTED_BURN);
        lenient().when(smelting.boostFuelTime(anyInt())).thenReturn(BOOSTED_BURN);

        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(uuid);
        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getCookingManager()).thenReturn(cooking);
        lenient().when(mmoPlayer.getSmeltingManager()).thenReturn(smelting);
        UserManager.track(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        UserManager.cleanupPlayer(mmoPlayer);
        // The owner map is a process-wide static keyed on a position, so one test's claimed furnace
        // would otherwise decide the next test's owner.
        SmeltingListener.clearOwners();
        CookingListener.clearOwners();
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setGeneralConfig(null);
    }

    // --- The furnace seam -------------------------------------------------------------------------

    @Test
    void cookingAFoodPaysCookingAndChargesTheInput() {
        claimFurnace();

        SmeltingListener.onFurnaceSmelt(world, FURNACE_POS, new ItemStack(Items.BEEF));

        // "Beef", not "Cooked_Beef": the seam injects before vanilla's craftRecipe, so the input is
        // what it can see -- and the input is what the config is keyed on.
        verify(cooking).onCook("Beef", WORLD_TICK);
        verify(smelting, never()).awardSmeltingXP(any());
    }

    @Test
    void smeltingAnOrePaysSmeltingAndNeverAlsoCooking() {
        // ⚠️ The boundary, in the direction that would be a live double-payout. Both skills read the
        // same input slot on the same block; Smelting wins and Cooking is the else, which is the
        // same order the fuel-efficiency gate has enforced since the Smelting port.
        claimFurnace();

        SmeltingListener.onFurnaceSmelt(world, FURNACE_POS, new ItemStack(Items.IRON_ORE));

        verify(smelting).awardSmeltingXP("Iron_Ore");
        verify(cooking, never()).onCook(any(), anyLong());
    }

    @Test
    void kelpIsCookedEvenThoughKelpIsNotItselfAFood() {
        // ⚠️ The trap that would drop dried kelp from the skill entirely: this section is keyed on
        // the INPUT, and kelp has no food component at all. A table written by filtering "which
        // foods can be cooked" never contains it, and the miss is completely silent.
        claimFurnace();

        SmeltingListener.onFurnaceSmelt(world, FURNACE_POS, new ItemStack(Items.KELP));

        verify(cooking).onCook("Kelp", WORLD_TICK);
    }

    @Test
    void aFurnaceNobodyHasTouchedPaysNobody() {
        // No claim: the owner map is empty, so this is the "someone else's furnace" path.
        SmeltingListener.onFurnaceSmelt(world, FURNACE_POS, new ItemStack(Items.BEEF));

        verify(cooking, never()).onCook(any(), anyLong());
        verify(smelting, never()).awardSmeltingXP(any());
    }

    @Test
    void anEmptyInputSlotPaysNobody() {
        claimFurnace();

        SmeltingListener.onFurnaceSmelt(world, FURNACE_POS, ItemStack.EMPTY);

        verify(cooking, never()).onCook(any(), anyLong());
    }

    // --- The crafting seam ------------------------------------------------------------------------

    @Test
    void aCraftedBatchCarriesItsWholeCountToTheManager() {
        // ⚠️ The 8× cookie trap, at the wiring level rather than the arithmetic level: the slot is
        // handed one result stack and the batch size lives in a separate field. A hook that passed
        // the stack's own count -- or a literal 1 -- would pay an eighth, and compile perfectly.
        CookingListener.onCraftedItemTaken(serverPlayer(), new ItemStack(Items.COOKIE), 8);

        verify(cooking).onCraft("Cookie", 8, WORLD_TICK);
    }

    @Test
    void aCraftedResultIsChargedOnTheResultAndNotOnAnIngredient() {
        CookingListener.onCraftedItemTaken(serverPlayer(), new ItemStack(Items.BREAD), 1);

        verify(cooking).onCraft("Bread", 1, WORLD_TICK);
    }

    @Test
    void theClientSideCopyOfTheScreenHandlerPaysNobody() {
        // ⚠️ In singleplayer both logical sides run a screen handler, so without the server-side
        // guard every craft would be paid twice -- and the doubling would look like a tuning
        // problem, not a bug. The repair-anvil pass learned this the hard way.
        final PlayerEntity clientPlayer = mock(PlayerEntity.class);
        lenient().when(clientPlayer.getUuid()).thenReturn(uuid);

        CookingListener.onCraftedItemTaken(clientPlayer, new ItemStack(Items.COOKIE), 8);

        verify(cooking, never()).onCraft(any(), anyInt(), anyLong());
    }

    @Test
    void takingNothingOutOfTheResultSlotPaysNobody() {
        CookingListener.onCraftedItemTaken(serverPlayer(), new ItemStack(Items.COOKIE), 0);
        CookingListener.onCraftedItemTaken(serverPlayer(), ItemStack.EMPTY, 8);

        verify(cooking, never()).onCraft(any(), anyInt(), anyLong());
    }

    @Test
    void anUntrackedCrafterPaysNobody() {
        UserManager.cleanupPlayer(mmoPlayer);

        CookingListener.onCraftedItemTaken(serverPlayer(), new ItemStack(Items.COOKIE), 8);

        verify(cooking, never()).onCraft(any(), anyInt(), anyLong());
    }

    // --- The cap notification ---------------------------------------------------------------------

    @Test
    void aRefusedCookTellsThePlayerRatherThanFailingSilently() {
        // A rate cap that pays nothing and says nothing is indistinguishable from a broken skill --
        // the lesson two of the ten GitHub issues turned on. The manager decides *when* to announce
        // (once a window); all the listener owes is actually delivering it.
        claimFurnace();
        when(cooking.onCook(eq("Beef"), anyLong()))
                .thenReturn(new CookingManager.CookAward(0F, 0, true));

        SmeltingListener.onFurnaceSmelt(world, FURNACE_POS, new ItemStack(Items.BEEF));

        verify(mmoPlayer).getPlayer(); // the notification path resolved a player to talk to
    }

    // --- Stage 3: Kitchen Efficiency on the shared furnace ---------------------------------------

    @Test
    void cookingAFoodStretchesTheOwnersFuelThroughCookingAndNotSmelting() {
        claimFurnace();

        assertEquals(BOOSTED_BURN, SmeltingListener.boostFuelTime(
                VANILLA_BURN, FURNACE_POS, new ItemStack(Items.BEEF)));

        verify(cooking).boostFuelTime(VANILLA_BURN);
        verify(smelting, never()).boostFuelTime(anyInt());
    }

    @Test
    void smeltingAnOreStretchesFuelThroughSmeltingAndNotCooking() {
        claimFurnace();

        SmeltingListener.boostFuelTime(VANILLA_BURN, FURNACE_POS, new ItemStack(Items.IRON_ORE));

        verify(smelting).boostFuelTime(VANILLA_BURN);
        verify(cooking, never()).boostFuelTime(anyInt());
    }

    @Test
    void aNonFoodNonOreInputGetsNOBODYSFuelBonus() {
        // ⚠️⚠️ The bug a literal reading of "Kitchen Efficiency is the else of the smeltable gate"
        // would ship. Non-smeltable does NOT mean food: every input below is a perfectly ordinary
        // furnace recipe and none of them is Cooking's business. Written as a negation instead of an
        // explicit isCookable() test, a glass farm would burn on a chef's fuel bonus, and nothing
        // anywhere would say so.
        claimFurnace();

        for (ItemStack input : new ItemStack[] {new ItemStack(Items.SAND),
                new ItemStack(Items.COBBLESTONE), new ItemStack(Items.OAK_LOG),
                new ItemStack(Items.CLAY_BALL), new ItemStack(Items.CACTUS),
                new ItemStack(Items.WET_SPONGE)}) {
            assertEquals(VANILLA_BURN,
                    SmeltingListener.boostFuelTime(VANILLA_BURN, FURNACE_POS, input),
                    input.getItem() + " is neither ore nor food and must burn at vanilla speed");
        }

        verify(cooking, never()).boostFuelTime(anyInt());
        verify(smelting, never()).boostFuelTime(anyInt());
    }

    @Test
    void chorusFruitPaysNoXpAndEarnsNoFuelBonusEither() {
        // Priced at an explicit 0, and both facts fall out of the same config read on purpose: a
        // chorus farm is fully automatable, and subsidising something the skill refuses to pay for
        // is a half-gate.
        claimFurnace();

        assertEquals(VANILLA_BURN, SmeltingListener.boostFuelTime(
                VANILLA_BURN, FURNACE_POS, new ItemStack(Items.CHORUS_FRUIT)));
        verify(cooking, never()).boostFuelTime(anyInt());
    }

    @Test
    void anUnclaimedFurnaceBurnsFuelAtVanillaSpeedForBothSkills() {
        assertEquals(VANILLA_BURN, SmeltingListener.boostFuelTime(
                VANILLA_BURN, FURNACE_POS, new ItemStack(Items.BEEF)));
        assertEquals(VANILLA_BURN, SmeltingListener.boostFuelTime(
                VANILLA_BURN, FURNACE_POS, new ItemStack(Items.IRON_ORE)));
    }

    // --- Stage 3: Master Chef on the shared furnace ----------------------------------------------

    @Test
    void aFinishedCookOffersTheOwnerASecondHelping() {
        claimFurnace();
        when(cooking.canSecondHelping("Cooked_Beef")).thenReturn(true);
        final ItemStack output = new ItemStack(Items.COOKED_BEEF, 1);

        SmeltingListener.onSmeltComplete(FURNACE_POS, output);

        assertEquals(2, output.getCount(), "the bonus is added to the furnace's live output stack");
        verify(smelting, never()).canSecondSmelt(any());
    }

    @Test
    void anOreResultRollsSmeltingAndIsNeverOfferedToCooking() {
        // ⚠️ The dispatch is on table membership, and it decides BEFORE anything is rolled. If the
        // listener rolled Smelting first and fell through to Cooking on a miss, an item listed in
        // both Bonus_Drops sections would get two chances at one bonus — and a miss looks identical
        // to "not eligible", so the extra roll would never show up in any log.
        claimFurnace();
        when(smelting.canSecondSmelt("Iron_Ingot")).thenReturn(false);

        SmeltingListener.onSmeltComplete(FURNACE_POS, new ItemStack(Items.IRON_INGOT, 1));

        verify(smelting).canSecondSmelt("Iron_Ingot");
        verify(cooking, never()).canSecondHelping(any());
    }

    @Test
    void aResultInNeitherBonusTableAsksNobodyAndCostsNoOwnerLookup() {
        claimFurnace();

        SmeltingListener.onSmeltComplete(FURNACE_POS, new ItemStack(Items.GLASS, 1));

        verify(cooking, never()).canSecondHelping(any());
        verify(smelting, never()).canSecondSmelt(any());
    }

    @Test
    void aFullOutputStackNeverOverflowsEvenWithMasterChefUp() {
        claimFurnace();
        lenient().when(cooking.canSecondHelping(any())).thenReturn(true);
        final ItemStack full = new ItemStack(Items.COOKED_BEEF, 64);

        SmeltingListener.onSmeltComplete(FURNACE_POS, full);

        assertEquals(64, full.getCount(), "the room check is re-used from Smelting, not re-derived");
        verify(cooking, never()).canSecondHelping(any());
    }

    @Test
    void threeConsecutiveCooksLeaveONEMergEABLEStackAndNotThree() {
        // ⚠️⚠️ The furnace-does-not-jam test (D-CK1). The cut quality tier would have stamped a
        // component onto each cooked item, and ItemStack.areItemsAndComponentsEqual compares the
        // WHOLE component map with no exclusion list — so canAcceptRecipeOutput returns false on the
        // second cook and the smoker STOPS until a human empties it, while a stack of 64 splits into
        // unmergeable piles. Nothing in Stage 3 adds a component, and this is what says so out loud
        // for whoever next thinks a "small tag" on the result is harmless.
        claimFurnace();
        when(cooking.canSecondHelping("Cooked_Beef")).thenReturn(true);
        final ItemStack output = new ItemStack(Items.COOKED_BEEF, 1);

        for (int cook = 0; cook < 3; cook++) {
            SmeltingListener.onSmeltComplete(FURNACE_POS, output);
            output.increment(1); // vanilla's own craftRecipe merge, which our seam sits just after.
        }

        assertEquals(7, output.getCount(), "3 cooks + 3 second helpings, all in one slot");
        assertTrue(ItemStack.areItemsAndComponentsEqual(output, new ItemStack(Items.COOKED_BEEF)),
                "a Master Chef helping must stay merge-compatible with a plain cooked steak");
    }

    // --- Stage 5: the campfire seam ---------------------------------------------------------------

    @Test
    void aFinishedCampfireCookPaysCookingAndChargesTheRawInput() {
        claimCampfire();

        CookingListener.onCampfireCook(world, CAMPFIRE_POS,
                new ItemStack(Items.BEEF), new ItemStack(Items.COOKED_BEEF));

        // Same table and same key space as the furnace: Experience_Values.Cooking.Cook prices raw
        // inputs. A campfire hook keyed on the result would find no price and pay zero forever.
        verify(cooking).onCook("Beef", WORLD_TICK);
        verify(cooking, never()).onCook(eq("Cooked_Beef"), anyLong());
    }

    @Test
    void theCampfireChargesTheINPUTWhileMasterChefReadsTheRESULT() {
        // ⚠️⚠️ The reason this test exists. The campfire is the one seam where both key spaces are
        // live in a single method — the raw input for XP, the cooked result for Bonus_Drops — and the
        // mixin picks both off the same stack frame. Swap them and everything still compiles, still
        // boots, still binds: "Cooked_Beef" is simply unpriced under .Cook and "Beef" is simply not in
        // Bonus_Drops.Cooking, so the entire feature pays nothing and drops nothing, silently. This is
        // the assertion that turns that swap into a red test.
        claimCampfire();
        when(cooking.canSecondHelping("Cooked_Beef")).thenReturn(true);
        final ItemStack result = new ItemStack(Items.COOKED_BEEF, 1);

        final ItemStack scattered =
                CookingListener.onCampfireCook(world, CAMPFIRE_POS, new ItemStack(Items.BEEF), result);

        verify(cooking).onCook("Beef", WORLD_TICK);
        verify(cooking).canSecondHelping("Cooked_Beef");
        verify(cooking, never()).canSecondHelping("Beef");
        assertSame(result, scattered, "the stack handed back is the one vanilla scatters");
        assertEquals(2, scattered.getCount(), "Master Chef's second helping lands on the floor");
    }

    @Test
    void aCampfireCookWithoutMasterChefScattersExactlyWhatVanillaWouldHave() {
        claimCampfire();
        when(cooking.canSecondHelping(any())).thenReturn(false);

        final ItemStack scattered = CookingListener.onCampfireCook(world, CAMPFIRE_POS,
                new ItemStack(Items.BEEF), new ItemStack(Items.COOKED_BEEF, 1));

        assertEquals(1, scattered.getCount());
    }

    @Test
    void masterChefStillFiresOnACookTheHourlyCapRefused() {
        // The documented ruling, in executable form: the rate cap is a gate on XP and on nothing
        // else. The furnace path behaves identically (onSmeltComplete never consults the window), and
        // one config key quietly switching off two unrelated mechanics is how a "known limit" becomes
        // a bug report.
        claimCampfire();
        when(cooking.onCook(eq("Beef"), anyLong()))
                .thenReturn(new CookingManager.CookAward(0F, 0, true));
        when(cooking.canSecondHelping("Cooked_Beef")).thenReturn(true);

        final ItemStack scattered = CookingListener.onCampfireCook(world, CAMPFIRE_POS,
                new ItemStack(Items.BEEF), new ItemStack(Items.COOKED_BEEF, 1));

        assertEquals(2, scattered.getCount());
        verify(mmoPlayer).getPlayer(); // and the player was still told the cap bit
    }

    @Test
    void aCampfireNobodyHasTouchedPaysNobody() {
        // No claim. A campfire is only reachable by a player right-click (addItem has exactly one
        // caller and the block entity is not an Inventory), so in practice an unowned campfire that
        // finishes a cook means the owner logged out — it must behave exactly like vanilla.
        final ItemStack scattered = CookingListener.onCampfireCook(world, CAMPFIRE_POS,
                new ItemStack(Items.BEEF), new ItemStack(Items.COOKED_BEEF, 1));

        verify(cooking, never()).onCook(any(), anyLong());
        verify(cooking, never()).canSecondHelping(any());
        assertEquals(1, scattered.getCount());
    }

    @Test
    void aRawItemSpatBackBecauseNoRecipeMatchedIsNotACook() {
        // ⚠️ litServerTick resolves the result as getFirstMatch(...).map(craft).orElse(rawStack), and
        // craft() returns a fresh copy — so the result is the *same object* as the input only when no
        // campfire recipe matched and the raw item is being thrown back out. A data-pack reload that
        // drops a recipe mid-cook is the way there, and it must pay nothing: nothing was cooked.
        claimCampfire();
        final ItemStack raw = new ItemStack(Items.BEEF);

        final ItemStack scattered = CookingListener.onCampfireCook(world, CAMPFIRE_POS, raw, raw);

        verify(cooking, never()).onCook(any(), anyLong());
        verify(cooking, never()).canSecondHelping(any());
        assertSame(raw, scattered);
        assertEquals(1, raw.getCount(), "and it is certainly not doubled on the way out");
    }

    @Test
    void anEmptyCampfireStackOnEitherSidePaysNobody() {
        claimCampfire();

        CookingListener.onCampfireCook(world, CAMPFIRE_POS,
                ItemStack.EMPTY, new ItemStack(Items.COOKED_BEEF));
        CookingListener.onCampfireCook(world, CAMPFIRE_POS,
                new ItemStack(Items.BEEF), ItemStack.EMPTY);

        verify(cooking, never()).onCook(any(), anyLong());
        verify(cooking, never()).canSecondHelping(any());
    }

    @Test
    void theCampfireAndFurnaceOwnerMapsAreDisjointAndNeitherHookClaimsTheOthersBlock() {
        // ⚠️ Two UseBlockCallback registrations now run on every right-click in the game, and each
        // has to ignore the other's block. Smelting's gate is `instanceof AbstractFurnaceBlockEntity`
        // and a campfire is not one (it extends BlockEntity directly — that is the whole reason this
        // stage needed a new mixin), so if either gate were widened to BlockEntity the two skills
        // would start claiming each other's blocks with no symptom until a cook finished.
        //
        // ⚠️ Both fixtures below fire BOTH hooks, which is the only reason this test has any teeth:
        // an earlier version claimed the furnace through Smelting's hook alone, so Cooking's gate was
        // never asked about a furnace at all and a mutation widening it to `instanceof BlockEntity`
        // passed clean. A gate you never point the wrong block at is not under test.
        claimCampfire();
        claimFurnace();

        // Cooking's hook saw the furnace click. If it had claimed it, this cook would pay.
        CookingListener.onCampfireCook(world, FURNACE_POS,
                new ItemStack(Items.BEEF), new ItemStack(Items.COOKED_BEEF));
        verify(cooking, never()).onCook(eq("Beef"), anyLong());

        // Smelting's hook saw the campfire click. If it had claimed it, the furnace path would find
        // an owner at a position no furnace occupies.
        SmeltingListener.onFurnaceSmelt(world, CAMPFIRE_POS, new ItemStack(Items.KELP));
        verify(cooking, never()).onCook(eq("Kelp"), anyLong());
    }

    @Test
    void kelpCooksOnACampfireExactlyAsItDoesInASmoker() {
        // The same not-a-food input the furnace test pins, on the other block: this section is keyed
        // on the input, and kelp carries no food component at all.
        claimCampfire();

        CookingListener.onCampfireCook(world, CAMPFIRE_POS,
                new ItemStack(Items.KELP), new ItemStack(Items.DRIED_KELP));

        verify(cooking).onCook("Kelp", WORLD_TICK);
    }

    // --- fixture ----------------------------------------------------------------------------------

    /**
     * Right-click the furnace, which is the only thing that makes it "yours" for XP purposes.
     *
     * <p>⚠️ <b>Both</b> listeners' hooks are fired, not just Smelting's. Two
     * {@link net.fabricmc.fabric.api.event.player.UseBlockCallback} registrations are live in the
     * running game and each one sees every click, so a fixture that told only one of them about a
     * click is testing a game that does not exist — and it hides exactly the failure where one
     * skill's gate is widened and starts claiming the other's blocks. A mutation that broadened
     * Cooking's {@code instanceof} to any {@code BlockEntity} survived until this line was added.
     */
    private void claimFurnace() {
        lenient().when(world.getBlockEntity(FURNACE_POS))
                .thenReturn(mock(FurnaceBlockEntity.class));

        final BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(FURNACE_POS), Direction.UP, FURNACE_POS, false);
        final ServerPlayerEntity player = serverPlayer();

        assertEquals(net.minecraft.util.ActionResult.PASS,
                SmeltingListener.onUseBlock(player, world, Hand.MAIN_HAND, hit),
                "claiming a furnace must never swallow the click that opens it");
        CookingListener.onUseBlock(player, world, Hand.MAIN_HAND, hit);
    }

    /**
     * Right-click the campfire — which, unlike the furnace, is also the <em>only</em> way food ever
     * gets into one. Both listeners' hooks are fired, because both are registered on
     * {@link net.fabricmc.fabric.api.event.player.UseBlockCallback} and both really do see every
     * click in the game.
     */
    private void claimCampfire() {
        lenient().when(world.getBlockEntity(CAMPFIRE_POS))
                .thenReturn(mock(CampfireBlockEntity.class));

        final BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(CAMPFIRE_POS), Direction.UP, CAMPFIRE_POS, false);
        final ServerPlayerEntity player = serverPlayer();

        assertEquals(net.minecraft.util.ActionResult.PASS,
                CookingListener.onUseBlock(player, world, Hand.MAIN_HAND, hit),
                "claiming a campfire must never swallow the click that puts the food on it");
        SmeltingListener.onUseBlock(player, world, Hand.MAIN_HAND, hit);
    }

    private ServerPlayerEntity serverPlayer() {
        final ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        lenient().when(player.getUuid()).thenReturn(uuid);
        lenient().when(player.getEntityWorld()).thenReturn(world);
        return player;
    }
}
