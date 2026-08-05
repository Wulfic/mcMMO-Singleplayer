package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    /** An arbitrary but non-zero world time, so a bug that passes 0 everywhere is visible. */
    private static final long WORLD_TICK = 4242L;

    /** A cook that paid and did not trip the hourly cap. */
    private static final CookingManager.CookAward PAID =
            new CookingManager.CookAward(100F, 1, false);

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
        McMMOMod.setExperienceConfig(null);
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

    // --- fixture ----------------------------------------------------------------------------------

    /** Right-click the furnace, which is the only thing that makes it "yours" for XP purposes. */
    private void claimFurnace() {
        lenient().when(world.getBlockEntity(FURNACE_POS))
                .thenReturn(mock(FurnaceBlockEntity.class));

        final BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(FURNACE_POS), Direction.UP, FURNACE_POS, false);

        assertEquals(net.minecraft.util.ActionResult.PASS,
                SmeltingListener.onUseBlock(serverPlayer(), world, Hand.MAIN_HAND, hit),
                "claiming a furnace must never swallow the click that opens it");
    }

    private ServerPlayerEntity serverPlayer() {
        final ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        lenient().when(player.getUuid()).thenReturn(uuid);
        lenient().when(player.getEntityWorld()).thenReturn(world);
        return player;
    }
}
