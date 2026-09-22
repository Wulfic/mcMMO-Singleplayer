package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.util.McTestRegistries;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
 * <p><b>⚠️ The mechanism moved in Minecraft 26.3 and the DANGER GOT WORSE.</b> There used to be a
 * {@code HoeItem#TILLABLES} map, reachable only from {@code HoeItem}, so "is the held item a hoe"
 * was implied by the lookup itself. 26.3 deleted {@code HoeItem} outright and made transforms a
 * {@link DataComponents#BLOCK_TRANSFORMER} component — which <b>axes and shovels carry too</b>
 * (stripping a log, pathing grass). So an implementation that only asks "does this item transform
 * this block" now answers <i>true</i> for an axe on a log and a shovel on grass, suppressing the
 * ready for Woodcutting and Excavation. The held-item gate went from implied to load-bearing.
 * {@link #groundIsStillAReadyingSurfaceForEveryNonHoeTool()} is still the test that matters most in
 * this file, and {@link #anAxeStrippingALogIsNotATill()} is its new 26.3-shaped sibling.
 */
class SuperAbilityListenerTillingTest {

    private static final BlockPos POS = new BlockPos(10, 64, 10);
    private static final BlockPos ABOVE = POS.above();

    /**
     * The blocks a hoe tills on this Minecraft version.
     *
     * <p>⚠️ <b>Pinned rather than read back out of vanilla, and that is a deliberate change.</b> The
     * pre-26.3 edition iterated {@code HoeItem#TILLABLES} directly, so a block Mojang added was
     * covered silently. The component API cannot be enumerated the same way — a transform's match is
     * a {@code BlockPredicate} inside a {@code BlockStateProvider}, answerable only by probing a
     * position — so this list is the declaration, and
     * {@link #noBlockOutsideTheDeclaredSetHasQuietlyBecomeTillable()} is the detector that fails when
     * vanilla disagrees with it. A stated list plus a detector beats silent absorption: the old shape
     * would have changed behaviour with nothing to read.
     */
    private static Set<Block> tillable() {
        // ⚠️ A METHOD, not a static final field. Every Blocks.* read forces BuiltInRegistries'
        // static init, and a static field would run it at CLASS-INIT time -- before @BeforeAll
        // has called McTestRegistries.bootstrap(). That fails the whole class with
        // "Not bootstrapped" and an initializationError, so not one assertion in this file
        // would ever run. Measured, not theorised: it is exactly how the first draft failed.
        return Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.DIRT_PATH,
                Blocks.ROOTED_DIRT);
    }

    @BeforeAll
    static void bootstrap() {
        // ⚠️ bootstrapWithTags, not bootstrap: isTillAction reads ItemTags.HOES and the
        // transform's own predicate is a MatchingBlockTagPredicate. This class therefore runs in the
        // tagBoundTest Gradle task, in its own JVM -- see build.gradle.
        McTestRegistries.bootstrapWithTags();
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
        for (Block tillable : tillable()) {
            assertTrue(tills(new ItemStack(Items.DIAMOND_HOE), tillable),
                    "a hoe click on " + BuiltInRegistries.BLOCK.getKey(tillable)
                            + " tills, so it must not re-ready the hoe");
        }
    }

    // --- ⚠️ the over-suppression guard — the point of this file -------------

    @Test
    void groundIsStillAReadyingSurfaceForEveryNonHoeTool() {
        // ⚠️ THE regression this file exists for. The tillable set is the floor: grass_block, dirt,
        // coarse_dirt, dirt_path, rooted_dirt. Right-clicking the floor is how a player arms Super
        // Breaker before mining and Giga Drill Breaker before digging, and readying is gated on
        // `canActivateTools`, which only excludes blacklisted blocks, so the floor qualifies.
        //
        // An implementation without the held-item gate returns true for every pair below, silently
        // making five super abilities unreadyable while aiming at the ground. It would pass every
        // other test in this file.
        final Set<ItemStack> notHoes = Set.of(
                new ItemStack(Items.DIAMOND_PICKAXE),   // Super Breaker
                new ItemStack(Items.DIAMOND_SHOVEL),    // Giga Drill Breaker
                new ItemStack(Items.DIAMOND_AXE),       // Tree Feller / Skull Splitter
                new ItemStack(Items.DIAMOND_SWORD),     // Serrated Strikes
                ItemStack.EMPTY);                       // Berserk (ToolType.FISTS is a bare hand)

        for (ItemStack held : notHoes) {
            for (Block tillable : tillable()) {
                assertFalse(tills(held, tillable),
                        held.getItem() + " on " + BuiltInRegistries.BLOCK.getKey(tillable)
                                + " is not a till. Calling it one suppresses the ready, and "
                                + "right-clicking the ground is how the pickaxe/shovel/axe/sword/fist "
                                + "super abilities arm.");
            }
        }
    }

    @Test
    void aShovelOnGrassIsNotATillEvenThoughItTransformsIt() {
        // ⚠️ 26.3-specific, and it is NOT a duplicate of the loop above. A shovel genuinely carries a
        // BLOCK_TRANSFORMER that matches grass_block — it makes a dirt path. So this pair is one an
        // implementation can get wrong for a REASON rather than by omission: the component lookup
        // succeeds and returns non-null. Only the ItemTags.HOES gate rejects it.
        assertTrue(hasTransformFor(new ItemStack(Items.DIAMOND_SHOVEL), Blocks.GRASS_BLOCK),
                "premise check: a shovel is expected to transform grass_block, otherwise this test "
                        + "proves nothing about the held-item gate");
        assertFalse(tills(new ItemStack(Items.DIAMOND_SHOVEL), Blocks.GRASS_BLOCK),
                "a shovel pathing grass is not a till — calling it one makes Giga Drill Breaker "
                        + "unreadyable on the single most common block a digger stands on");
    }

    @Test
    void anAxeStrippingALogIsNotATill() {
        // The same trap on the Woodcutting side: an axe transforms a log (stripping), so a
        // component-only implementation suppresses Tree Feller's ready on the exact block a
        // woodcutter aims at.
        assertTrue(hasTransformFor(new ItemStack(Items.DIAMOND_AXE), Blocks.OAK_LOG),
                "premise check: an axe is expected to transform oak_log (stripping), otherwise this "
                        + "test proves nothing about the held-item gate");
        assertFalse(tills(new ItemStack(Items.DIAMOND_AXE), Blocks.OAK_LOG),
                "an axe stripping a log is not a till — calling it one makes Tree Feller unreadyable");
    }

    // --- the legit "ready hoe → strike → Green Terra" flow stays intact ------

    @Test
    void hoeOnACropStillReadies() {
        // Nothing farmable is tillable, so a hoe click on a crop is unambiguously a readying gesture.
        // Load-bearing: it is the flow a player uses to arm Green Terra, and it is order-sensitive
        // (the strike that activates Green Terra also converts the block it hit).
        assertFalse(tills(new ItemStack(Items.DIAMOND_HOE), Blocks.WHEAT));
        assertFalse(tills(new ItemStack(Items.DIAMOND_HOE), Blocks.CARROTS));
        assertFalse(tills(new ItemStack(Items.DIAMOND_HOE), Blocks.FARMLAND));
    }

    @Test
    void hoeOnGrassThatCannotBeTilledStillReadies() {
        // The predicate half. Vanilla refuses when the block is covered, and a refused till is not a
        // till — so those clicks keep readying.
        assertFalse(tills(new ItemStack(Items.DIAMOND_HOE), Blocks.GRASS_BLOCK, Direction.UP,
                        Blocks.STONE.defaultBlockState()),
                "a covered grass block cannot be tilled, so the click is a ready");
    }

    @Test
    void aHoeClickFromUnderneathIsNotATill() {
        // The disallowedFaces half of vanilla's own loop, which isTillAction reproduces. Tilling from
        // below is refused, so that click still readies.
        assertFalse(tills(new ItemStack(Items.DIAMOND_HOE), Blocks.GRASS_BLOCK, Direction.DOWN,
                        Blocks.AIR.defaultBlockState()),
                "a grass block clicked from below cannot be tilled, so the click is a ready");
    }

    // --- we run vanilla's OWN provider, not a hardcoded rule -----------------

    @Test
    void rootedDirtTillsEvenWhenCovered() {
        // rooted_dirt drops hanging roots and vanilla does not care what is on top of it, unlike the
        // grass/dirt entries. Pinning it proves isTillAction runs each transform's OWN provider rather
        // than one hardcoded "is it uncovered" rule — which is what keeps the answer correct when
        // Mojang edits the data.
        assertTrue(tills(new ItemStack(Items.DIAMOND_HOE), Blocks.ROOTED_DIRT, Direction.UP,
                        Blocks.STONE.defaultBlockState()),
                "rooted dirt tills even when covered, so it must not re-ready the hoe");
    }

    // --- the assumptions the fix rests on -----------------------------------

    @Test
    void everyVanillaHoeIsInTheHoesTag() {
        // The gate is now `typeHolder().is(ItemTags.HOES)`, because 26.3 deleted HoeItem. If a version
        // bump ever drops a hoe out of that tag, isTillAction answers "not a hoe" for a real hoe and
        // GitHub #1 silently reopens with nothing else noticing.
        // ⚠️ COPPER_HOE is in this list because 26.3 added copper tools. A hoe missing from the
        // tag is a hoe whose tills silently re-ready again, so the list tracks the version.
        for (Item hoe : Set.of(Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE,
                Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE, Items.COPPER_HOE)) {
            assertTrue(new ItemStack(hoe).typeHolder().is(ItemTags.HOES),
                    hoe + " is not in ItemTags.HOES — isTillAction's held-item gate is now dead and "
                            + "every till re-readies the hoe again");
        }
    }

    @Test
    void aHoeActuallyCarriesTheBlockTransformerComponent() {
        // The other half of the gate, and the one that fails LOUDLY rather than silently if the
        // component is renamed or stops being attached: the whole lookup returns null and every till
        // would ready again. Asserted directly so the cause is named rather than inferred from a
        // behaviour test going red.
        assertNotNull(new ItemStack(Items.DIAMOND_HOE).get(DataComponents.BLOCK_TRANSFORMER),
                "a diamond hoe carries no BLOCK_TRANSFORMER component — isTillAction can no longer "
                        + "see any till, so GitHub #1 reopens for every block");
    }

    @Test
    void noBlockOutsideTheDeclaredSetHasQuietlyBecomeTillable() {
        // ⚠️ The detector that replaces the old "iterate vanilla's live table" behaviour. tillable() is
        // a declaration; this proves vanilla still agrees with it, in BOTH directions, across a sweep
        // of plausible neighbours. Without it, a block Mojang makes tillable would be treated as a
        // ready by mcMMO and nothing would say so.
        final Set<Block> candidates = new LinkedHashSet<>(tillable());
        candidates.addAll(Set.of(Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MUD, Blocks.MUDDY_MANGROVE_ROOTS,
                Blocks.FARMLAND, Blocks.STONE, Blocks.SAND, Blocks.GRAVEL, Blocks.CLAY,
                Blocks.MOSS_BLOCK, Blocks.SNOW_BLOCK, Blocks.OAK_LOG));

        final Set<Block> actuallyTillable = candidates.stream()
                .filter(block -> tills(new ItemStack(Items.DIAMOND_HOE), block))
                .collect(Collectors.toSet());

        assertEquals(tillable(), actuallyTillable,
                "vanilla's tillable set no longer matches this file's declaration. Update tillable() "
                        + "AND re-read the over-suppression argument above — it is argued from these "
                        + "being common ground blocks, so a wider set needs more care, not less.");
    }

    @Test
    void theDeclaredSetIsStillTheCommonGroundTheOverSuppressionArgumentAssumes() {
        // Documents the set by name, because the shape of the fix is argued from its contents.
        assertEquals(Set.of("grass_block", "dirt", "coarse_dirt", "dirt_path", "rooted_dirt"),
                tillable().stream()
                        .map(block -> BuiltInRegistries.BLOCK.getKey(block).getPath())
                        .collect(Collectors.toSet()));
    }

    // --- helpers ------------------------------------------------------------

    /** A hoe-tillable click: clicked from above, nothing on top. */
    private static boolean tills(ItemStack held, Block target) {
        return tills(held, target, Direction.UP, Blocks.AIR.defaultBlockState());
    }

    private static boolean tills(ItemStack held, Block target, Direction side, BlockState above) {
        // ⚠️ The level is built BEFORE the when(...) that consumes it. levelWith() stubs, and
        // nesting one stubbing inside another's argument throws UnfinishedStubbingException --
        // which reports at this line and blames the wrong call. Measured, not theorised.
        final ServerLevel level = levelWith(target, above);
        final ServerPlayer player = mock(ServerPlayer.class);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(held);
        when(player.level()).thenReturn(level);

        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(POS), side, POS, false);
        return SuperAbilityListener.isTillAction(player, InteractionHand.MAIN_HAND, hit);
    }

    /**
     * Whether {@code held} transforms {@code target} at all, ignoring the held-item gate.
     *
     * <p>This is the premise check for the shovel and axe cases: it asserts those tools really do
     * match the block, so that {@code assertFalse(tills(...))} is evidence about the GATE rather than
     * a pair that never matched in the first place. Without it, both tests would pass against an
     * implementation that is broken in a different way.
     */
    private static boolean hasTransformFor(ItemStack held, Block target) {
        final var transformer = held.get(DataComponents.BLOCK_TRANSFORMER);
        if (transformer == null) {
            return false;
        }
        final ServerLevel level = levelWith(target, Blocks.AIR.defaultBlockState());
        return transformer.value().transforms().stream()
                .filter(transform -> !transform.disallowedFaces().contains(Direction.UP))
                .anyMatch(transform -> transform.blockStateProvider().value()
                        .getOptionalState(level, level.getRandom(), POS) != null);
    }

    /** A level that reports {@code target} at the clicked position and {@code above} on top of it. */
    private static ServerLevel levelWith(Block target, BlockState above) {
        final ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(POS)).thenReturn(target.defaultBlockState());
        when(level.getBlockState(ABOVE)).thenReturn(above);
        when(level.getRandom()).thenReturn(net.minecraft.util.RandomSource.create());
        return level;
    }
}
