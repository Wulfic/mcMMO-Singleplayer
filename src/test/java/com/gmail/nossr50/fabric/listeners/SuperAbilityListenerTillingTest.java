package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.fabric.mixin.HoeTillingActionsAccessor;
import com.gmail.nossr50.util.McTestRegistries;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link SuperAbilityListener#isTillAction} — the GitHub #1 gate that stops a till from also
 * re-readying the hoe.
 *
 * <p><b>The bug.</b> Tilling is a right-click with a hoe, and so is readying the hoe for Green Terra:
 * the same gesture on the same tool, and the listener could not tell them apart. Farming a row
 * therefore re-readied the tool on every single till — a "you ready your hoe" message and sound every
 * few seconds — and left the hoe permanently armed, so the next left-click on a crop spent Green
 * Terra's 240-second cooldown by accident.
 *
 * <p><b>What is actually hard here.</b> Not detecting the till: vanilla's own
 * {@code HoeItem#TILLING_ACTIONS} answers that. The hard part is <i>not over-suppressing</i>. That
 * table is keyed on the five blocks a player spends the entire game standing on, and
 * {@code useOnBlock} is an instance method on {@code HoeItem}, so no entry's predicate bothers to
 * check the held item — by the time vanilla runs one, it cannot be anything but a hoe. Reproducing
 * the table lookup without reproducing that implicit item check reads every right-click on the
 * ground as a till, and the ready is how all six tool skills arm. Hence
 * {@link #groundIsStillAReadyingSurfaceForEveryNonHoeTool()}, which is the test that matters most
 * in this file: it fails against the obvious implementation, not against a typo.
 */
class SuperAbilityListenerTillingTest {

    private static final BlockPos POS = new BlockPos(10, 64, 10);
    private static final BlockPos ABOVE = POS.up();

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    // --- the fix ------------------------------------------------------------

    @Test
    void tillingGrassWithAHoeIsATillAndSoSuppressesTheReady() {
        assertTrue(tills(new ItemStack(Items.DIAMOND_HOE), Blocks.GRASS_BLOCK),
                "right-clicking grass with a hoe is the till the player asked for, so it must not "
                        + "also re-ready the hoe — that re-ready is GitHub #1");
    }

    @Test
    void everyBlockVanillaCanTillCountsAsATill() {
        // Whatever is in vanilla's table today, a hoe click on it tills — so none of them may ready.
        // Driven off the live table rather than a hand-written list, so a block Mojang adds is covered
        // the moment it appears instead of quietly re-opening the issue for that one block.
        for (Block tillable : HoeTillingActionsAccessor.getTillingActions().keySet()) {
            assertTrue(tills(new ItemStack(Items.DIAMOND_HOE), tillable),
                    "a hoe click on " + Registries.BLOCK.getId(tillable) + " tills, so it must not "
                            + "re-ready the hoe");
        }
    }

    // --- ⚠️ the over-suppression guard — the point of this file -------------

    @Test
    void groundIsStillAReadyingSurfaceForEveryNonHoeTool() {
        // ⚠️ THE regression this file exists for. The tilling table is grass_block, dirt, coarse_dirt,
        // dirt_path and rooted_dirt — the floor. Right-clicking the floor is how a player arms Super
        // Breaker before mining and Giga Drill Breaker before digging, and readying is gated on
        // `canActivateTools`, which only excludes blacklisted blocks, so the floor qualifies.
        //
        // A block-only implementation of isTillAction — table lookup plus the entry's predicate, with
        // no held-item check — returns true for every one of these, silently making five super
        // abilities unreadyable while aiming at the ground. It would pass every other test here.
        final Set<ItemStack> notHoes = Set.of(
                new ItemStack(Items.DIAMOND_PICKAXE),   // Super Breaker
                new ItemStack(Items.DIAMOND_SHOVEL),    // Giga Drill Breaker
                new ItemStack(Items.DIAMOND_AXE),       // Tree Feller / Skull Splitter
                new ItemStack(Items.DIAMOND_SWORD),     // Serrated Strikes
                ItemStack.EMPTY);                       // Berserk (ToolType.FISTS is a bare hand)

        for (ItemStack held : notHoes) {
            for (Block tillable : HoeTillingActionsAccessor.getTillingActions().keySet()) {
                assertFalse(tills(held, tillable),
                        held.getItem() + " on " + Registries.BLOCK.getId(tillable) + " is not a till "
                                + "— vanilla only consults the tilling table for a HoeItem. Calling "
                                + "it one suppresses the ready, and right-clicking the ground is how "
                                + "the pickaxe/shovel/axe/sword/fist super abilities arm.");
            }
        }
    }

    // --- the legit "ready hoe → strike → Green Terra" flow stays intact ------

    @Test
    void hoeOnACropStillReadies() {
        // Nothing farmable is in the tilling table, so a hoe click on a crop is unambiguously a
        // readying gesture. This is load-bearing: it is the flow a player uses to arm Green Terra, and
        // it is order-sensitive (the strike that activates Green Terra also converts the block it hit).
        assertFalse(tills(new ItemStack(Items.DIAMOND_HOE), Blocks.WHEAT));
        assertFalse(tills(new ItemStack(Items.DIAMOND_HOE), Blocks.CARROTS));
        assertFalse(tills(new ItemStack(Items.DIAMOND_HOE), Blocks.FARMLAND));
    }

    @Test
    void hoeOnGrassThatCannotBeTilledStillReadies() {
        // The predicate half. Vanilla's canTillFarmland refuses when the block is covered or the click
        // came from underneath, and a refused till is not a till — so those clicks keep readying.
        assertFalse(tills(new ItemStack(Items.DIAMOND_HOE), Blocks.GRASS_BLOCK, Direction.UP,
                        Blocks.STONE.getDefaultState()),
                "a covered grass block cannot be tilled, so the click is a ready");
        assertFalse(tills(new ItemStack(Items.DIAMOND_HOE), Blocks.GRASS_BLOCK, Direction.DOWN,
                        Blocks.AIR.getDefaultState()),
                "a grass block clicked from below cannot be tilled, so the click is a ready");
    }

    // --- we run the ENTRY's predicate, not a hardcoded canTillFarmland ------

    @Test
    void rootedDirtTillsFromUnderneathAndCovered() {
        // rooted_dirt is the entry whose predicate is a bare `return true` (bytecode: iconst_1;
        // ireturn) rather than canTillFarmland — it drops hanging roots and vanilla does not care
        // about the side or what is on top. Pinning it proves isTillAction runs each entry's OWN
        // predicate, which is what keeps the answer correct when Mojang edits the table. Substituting
        // the public HoeItem#canTillFarmland for the pair's predicate would leave this red.
        assertTrue(tills(new ItemStack(Items.DIAMOND_HOE), Blocks.ROOTED_DIRT, Direction.DOWN,
                        Blocks.STONE.getDefaultState()),
                "rooted dirt tills from any angle even when covered, so it must not re-ready the hoe");
    }

    // --- the assumptions the fix rests on -----------------------------------

    @Test
    void vanillaHoesAreHoeItemInstances() {
        // The whole gate is `instanceof HoeItem`. Items are increasingly data-driven (a hoe is built
        // through Item.Settings#hoe), so if a version bump ever turns the vanilla hoes into plain
        // Items carrying a component, isTillAction would answer "not a hoe" for every real hoe and
        // GitHub #1 would silently reopen with no other test noticing.
        for (net.minecraft.item.Item hoe : Set.of(Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE,
                Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE)) {
            assertTrue(hoe instanceof HoeItem,
                    hoe + " is no longer a HoeItem — isTillAction's held-item gate is now dead and "
                            + "every till re-readies the hoe again");
        }
    }

    @Test
    void tillingTableStillHoldsTheGroundBlocksTheOverSuppressionArgumentAssumes() {
        // Documents the table this MC version actually ships, because the shape of the fix is argued
        // from its contents: these are common ground blocks, which is why the held-item gate is
        // mandatory rather than belt-and-braces. If this list changes, re-read that argument — a
        // future table of rare blocks would not need the same care, and a wider one needs more.
        final Set<String> tillable = HoeTillingActionsAccessor.getTillingActions().keySet().stream()
                .map(block -> Registries.BLOCK.getId(block).getPath())
                .collect(Collectors.toSet());
        assertEquals(Set.of("grass_block", "dirt", "coarse_dirt", "dirt_path", "rooted_dirt"),
                tillable);
    }

    // --- helpers ------------------------------------------------------------

    /** A hoe-tillable click: clicked from above, nothing on top. */
    private static boolean tills(ItemStack held, Block target) {
        return tills(held, target, Direction.UP, Blocks.AIR.getDefaultState());
    }

    private static boolean tills(ItemStack held, Block target, Direction side, BlockState above) {
        final ServerWorld world = mock(ServerWorld.class);
        when(world.getBlockState(ABOVE)).thenReturn(above);

        final ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        when(player.getStackInHand(Hand.MAIN_HAND)).thenReturn(held);
        // ItemUsageContext's public constructor reads the world off the player, and vanilla's
        // predicates read it back off the context.
        //
        // ⚠️⚠️ WHICH ACCESSOR IT CALLS DIFFERS BY VERSION -- it has been both
        // getWorld() and getEntityWorld() (javap-verified on this band's merged jar: the 3-arg
        // constructor calls getWorld). Stubbing only one leaves the context holding a null world, and
        // the failure surfaces far downstream as "Cannot invoke World.getBlockState() because
        // ItemUsageContext.getWorld() is null" -- which reads like a bug in the code under test
        // rather than a gap in the harness. Both are stubbed so this harness does not care.
        lenient().when(player.getWorld()).thenReturn(world);
        lenient().when(player.getEntityWorld()).thenReturn(world);

        final BlockHitResult hit =
                new BlockHitResult(Vec3d.ofCenter(POS), side, POS, false);
        return SuperAbilityListener.isTillAction(player, Hand.MAIN_HAND, hit,
                target.getDefaultState());
    }
}
