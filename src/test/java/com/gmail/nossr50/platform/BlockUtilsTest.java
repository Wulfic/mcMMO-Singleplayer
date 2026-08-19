package com.gmail.nossr50.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.BlockUtils.AgeableState;
import com.gmail.nossr50.util.BlockRules;
import com.gmail.nossr50.util.McTestRegistries;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the MC-typed {@link BlockUtils} bridge against real vanilla
 * {@link net.minecraft.block.Block}s. Its one job since the Phase 2 extraction is to prove the
 * <b>key extraction connects</b>:
 *
 * <ul>
 *   <li>{@code Registries.BLOCK.getId(block).getPath()} produces exactly the strings
 *       {@link BlockRules} (and behind it {@link com.gmail.nossr50.util.MaterialMapStore} and the
 *       {@code experience.yml} tables) are keyed on — asserted in {@code BlockRulesTest} against
 *       hand-written literals, so if the two ever disagree, one of these files goes red;</li>
 *   <li>a {@link World} + {@link BlockPos} packs into the tracker keys the rules layer expects;</li>
 *   <li>the three things that could not be extracted still behave — the two block-identity checks,
 *       crop maturity, and the Hylian tag laziness.</li>
 * </ul>
 *
 * <p>The decisions themselves are <b>not</b> re-asserted here; they moved to the MC-free
 * {@code BlockRulesTest}, which needs no {@code Bootstrap.initialize()}. Runs under the
 * {@code fabric-loader-junit} harness (see {@link McTestRegistries}).
 */
class BlockUtilsTest {

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    @BeforeEach
    void loadConfig(@TempDir Path dir) {
        McMMOMod.setExperienceConfig(new ExperienceConfig(dir));
        // The placed-block tracker is a JVM singleton, so flags leak between tests unless cleared.
        McMMOMod.getPlacedBlockTracker().clear();
    }

    @AfterEach
    void clearConfig() {
        McMMOMod.setExperienceConfig(null);
        McMMOMod.getPlacedBlockTracker().clear();
    }

    // --- The id-path bridge: a live Block reaches the right rule -------------

    @Test
    void theIdPathBridgeReachesTheMaterialMapStoreAnswers() {
        // Each pair below is the same question asked through a live Block and through the raw path
        // BlockRulesTest asserts on. They must agree, or the extraction silently disconnected the
        // two layers -- which is the one failure mode a relocation can introduce.
        assertEquals(BlockRules.isOre("iron_ore"), BlockUtils.isOre(Blocks.IRON_ORE));
        assertTrue(BlockUtils.isOre(Blocks.IRON_ORE));
        assertFalse(BlockUtils.isOre(Blocks.STONE));

        assertTrue(BlockUtils.canActivateAbilities(Blocks.STONE));
        assertTrue(BlockUtils.canActivateTools(Blocks.STONE));
        // A BlockState overload resolves to the same answer as its Block.
        assertTrue(BlockUtils.canActivateAbilities(Blocks.STONE.getDefaultState()));

        assertTrue(BlockUtils.isNonWoodPartOfTree(Blocks.OAK_LEAVES));
        assertFalse(BlockUtils.isNonWoodPartOfTree(Blocks.OAK_LOG));
        assertTrue(BlockUtils.isPartOfTree(Blocks.OAK_LOG));
        assertTrue(BlockUtils.isPartOfTree(Blocks.OAK_LEAVES));
        assertFalse(BlockUtils.isPartOfTree(Blocks.STONE));

        assertTrue(BlockUtils.canMakeMossy(Blocks.COBBLESTONE));
        assertTrue(BlockUtils.canMakeMossy(Blocks.STONE_BRICKS));
        assertFalse(BlockUtils.canMakeMossy(Blocks.STONE));
        assertTrue(BlockUtils.canMakeShroomy(Blocks.DIRT));
        assertTrue(BlockUtils.canMakeShroomy(Blocks.GRASS_BLOCK));
        assertTrue(BlockUtils.canActivateHerbalism(Blocks.DIRT));
        assertTrue(BlockUtils.affectedByBlockCracker(Blocks.STONE_BRICKS));
        assertFalse(BlockUtils.affectedByBlockCracker(Blocks.STONE));
    }

    @Test
    void theIdPathBridgeReachesTheXpTables() {
        // Super Breaker: stone is an intended-pickaxe block (config-independent half) AND Mining XP.
        assertTrue(BlockUtils.affectedBySuperBreaker(Blocks.STONE));
        // Giga Drill Breaker: dirt grants Excavation XP in the bundled experience.yml.
        assertTrue(BlockUtils.affectedByGigaDrillBreaker(Blocks.DIRT));
        // Green Terra: wheat grants Herbalism XP.
        assertTrue(BlockUtils.affectedByGreenTerra(Blocks.WHEAT));
        // Woodcutting XP: an oak log yes, plain stone no.
        assertTrue(BlockUtils.hasWoodcuttingXP(Blocks.OAK_LOG));
        assertFalse(BlockUtils.hasWoodcuttingXP(Blocks.STONE));
    }

    // --- Identity check #1: Berserk's snow LAYER arm -------------------------

    @Test
    void berserkInstaBreaksTheSnowLayerByIdentity() {
        // This arm cannot live in BlockRules: it is `isOf(Blocks.SNOW)`, an identity comparison, and
        // "snow".equals(idPath) would also match another namespace's block called snow.
        assertTrue(BlockUtils.affectedByBerserk(Blocks.SNOW.getDefaultState()), "the snow layer");
        assertTrue(BlockUtils.affectedByBerserk(Blocks.GLASS.getDefaultState()), "the glass arm");
        assertFalse(BlockUtils.affectedByBerserk(Blocks.STONE.getDefaultState()));
    }

    // --- Identity check #2: obsidian's lava-gate exemption -------------------

    @Test
    void obsidianIsExemptFromTheLavaGate() {
        // Making obsidian consumes the lava source, so it cannot repeat without another bucket --
        // it is a trade, not a generator. Legacy exempts it by name and so do we, by identity: the
        // exemption sits on this side of the boundary for the same reason the snow arm does.
        final World world = overworld();
        final BlockPos pos = new BlockPos(1, 30, 0);
        BlockUtils.markLavaFormed(world, pos, Blocks.OBSIDIAN);
        assertFalse(BlockUtils.isRewardIneligible(world, pos));

        // Converse: the same call for a block that is NOT exempt does flag, so the assertion above
        // is about obsidian rather than about the gate being off.
        final BlockPos basaltPos = new BlockPos(1, 31, 0);
        BlockUtils.markLavaFormed(world, basaltPos, Blocks.BASALT);
        assertTrue(BlockUtils.isRewardIneligible(world, basaltPos));
    }

    // --- The Hylian tag laziness, and proof the tags really are unbound ------

    @Test
    void classifiesHylianTreasureGroupsFromHardcodedMembers() {
        // Only the MaterialMapStore-backed branches are reachable here -- the nine flowers and the
        // three non-tag bush blocks -- because each returns before any tag check.
        assertEquals("Flowers", BlockUtils.getHylianTreasureGroup(Blocks.POPPY.getDefaultState()));
        assertEquals("Bushes", BlockUtils.getHylianTreasureGroup(Blocks.FERN.getDefaultState()));
        assertEquals("Bushes",
                BlockUtils.getHylianTreasureGroup(Blocks.SHORT_GRASS.getDefaultState()));
        assertEquals("Bushes", BlockUtils.getHylianTreasureGroup(Blocks.DEAD_BUSH.getDefaultState()));
    }

    /**
     * &#9888;&#9888; Makes the test above non-vacuous &mdash; and does it <b>without depending on how
     * the harness treats an unbound tag</b>.
     *
     * <p>This used to assert that {@code BlockState#isIn} blows up under
     * {@code Bootstrap.initialize()}, reasoning that "the suppliers are lazy" and "the tags happen to
     * be bound after all" produce the same green, so the precondition must be asserted rather than
     * assumed. <b>The reasoning is right; the proxy was not.</b> Whether an unbound tag throws or
     * quietly answers {@code false} is a Minecraft behaviour and it differs across supported
     * versions &mdash; on this band it does not throw, so the guard failed while nothing it guards
     * had changed.
     *
     * <p>&#128273; The property is now proven directly, at the layer that owns it.
     * {@link BlockRules#hylianTreasureGroup} takes the two tag checks as {@link BooleanSupplier}s, so
     * a supplier that <b>throws if it is ever called</b> turns "was a tag consulted?" into something
     * this test observes for itself &mdash; on every band, with no dependence on datapack state.
     *
     * <p>&#9888; What this does NOT cover: {@link BlockUtils#getHylianTreasureGroup} could still
     * pre-compute the two booleans and hand back {@code () -> alreadyComputed}, which compiles and is
     * eager. That was observable only on a band where an unbound tag throws, and it is not observable
     * here. The sibling test above still pins the wrapper's answers; this one pins the rule's
     * laziness.
     */
    @Test
    void theHardcodedMembersAreClassifiedWithoutConsultingATag() {
        final BooleanSupplier mustNotBeCalled = () -> {
            throw new AssertionError("a hardcoded Hylian member consulted a block tag — the "
                    + "MaterialMapStore branches must return before any tag check, or the "
                    + "classification silently depends on datapack state");
        };

        assertEquals("Flowers",
                BlockRules.hylianTreasureGroup("poppy", mustNotBeCalled, mustNotBeCalled));
        assertEquals("Bushes",
                BlockRules.hylianTreasureGroup("fern", mustNotBeCalled, mustNotBeCalled));
        assertEquals("Bushes",
                BlockRules.hylianTreasureGroup("short_grass", mustNotBeCalled, mustNotBeCalled));
        assertEquals("Bushes",
                BlockRules.hylianTreasureGroup("dead_bush", mustNotBeCalled, mustNotBeCalled));
    }

    // --- Crop maturity (age state property) ---------------------------------

    @Test
    void getAgeableStateReadsCropAgeAndMax() {
        // Wheat's age property maxes at 7; a freshly-planted crop is age 0.
        AgeableState freshWheat = BlockUtils.getAgeableState(Blocks.WHEAT.getDefaultState());
        assertNotNull(freshWheat);
        assertEquals(0, freshWheat.age());
        assertEquals(7, freshWheat.maxAge());

        AgeableState grownWheat =
                BlockUtils.getAgeableState(Blocks.WHEAT.getDefaultState().with(Properties.AGE_7, 7));
        assertNotNull(grownWheat);
        assertEquals(7, grownWheat.age());
        assertEquals(7, grownWheat.maxAge());

        // Sweet berry bush maxes at 3.
        AgeableState berries = BlockUtils.getAgeableState(
                Blocks.SWEET_BERRY_BUSH.getDefaultState().with(Properties.AGE_3, 2));
        assertNotNull(berries);
        assertEquals(2, berries.age());
        assertEquals(3, berries.maxAge());
    }

    @Test
    void getAgeableStateIsNullForBlocksWithoutAnAgeProperty() {
        // Stone has no state properties at all; a log has only an axis, not age.
        assertNull(BlockUtils.getAgeableState(Blocks.STONE.getDefaultState()));
        assertNull(BlockUtils.getAgeableState(Blocks.OAK_LOG.getDefaultState()));
    }

    @Test
    void withAgeSetsCropAgeClampsAndPreservesOtherProperties() {
        // Re-age wheat (age 0-7) to 3 — the Green Thumb replant path.
        AgeableState wheat3 = BlockUtils.getAgeableState(
                BlockUtils.withAge(Blocks.WHEAT.getDefaultState(), 3));
        assertNotNull(wheat3);
        assertEquals(3, wheat3.age());

        // An age above the crop's maximum clamps to it, so BlockState#with never throws (a high
        // Green Thumb stage against a short crop).
        AgeableState wheatOver = BlockUtils.getAgeableState(
                BlockUtils.withAge(Blocks.WHEAT.getDefaultState(), 99));
        assertNotNull(wheatOver);
        assertEquals(7, wheatOver.age());

        // Cocoa's age maxes at 2 and its facing must survive the re-age (the record-preserved
        // property the AFTER-seam replant relies on instead of a Directional rebuild).
        BlockState cocoa = Blocks.COCOA.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.SOUTH);
        BlockState cocoa1 = BlockUtils.withAge(cocoa, 1);
        AgeableState cocoaState = BlockUtils.getAgeableState(cocoa1);
        assertNotNull(cocoaState);
        assertEquals(1, cocoaState.age());
        assertEquals(Direction.SOUTH, cocoa1.get(Properties.HORIZONTAL_FACING));

        // A block with no age property is returned unchanged.
        assertEquals(Blocks.STONE.getDefaultState(),
                BlockUtils.withAge(Blocks.STONE.getDefaultState(), 3));
    }

    // --- The (World, BlockPos) -> tracker-key bridge -------------------------

    /** A world that answers only the one question the tracker asks it: which world am I? */
    private static World overworld() {
        final World world = mock(World.class);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        return world;
    }

    @Test
    void aWorldAndPositionPackIntoTheKeysTheRulesLayerUses() {
        final World world = overworld();
        final BlockPos pos = new BlockPos(10, 64, -20);

        assertFalse(BlockUtils.isRewardIneligible(world, pos), "a never-placed block is eligible");
        BlockUtils.markPlaced(world, pos);
        assertTrue(BlockUtils.isRewardIneligible(world, pos), "a hand-placed block must not reward");

        // The same flag, read through the MC-free layer with the keys this bridge should have
        // produced. If the packing ever drifts, this is the assertion that catches it.
        assertTrue(BlockRules.isRewardIneligible("minecraft:overworld", pos.asLong()),
                "the bridge must write the world's registry-key string and BlockPos#asLong()");

        BlockUtils.markNatural(world, pos);
        assertFalse(BlockUtils.isRewardIneligible(world, pos), "breaking it makes the spot natural");
    }

    @Test
    void theDirectionArithmeticThisBridgeOwnsSendsFlagsToTheRightNeighbour() {
        // BlockRules takes movedFrom/movedTo already packed, so offsetting by the push direction is
        // the one piece of this mechanic still on the MC side -- and therefore the one piece
        // BlockRulesTest cannot cover.
        final World world = overworld();
        final BlockPos from = new BlockPos(0, 64, 0);
        final BlockPos to = from.offset(Direction.EAST);
        BlockUtils.markPlaced(world, from);

        BlockUtils.movePlacedFlags(world, List.of(from), List.of(), Direction.EAST);

        assertFalse(BlockUtils.isRewardIneligible(world, from), "the old spot is empty now");
        assertTrue(BlockUtils.isRewardIneligible(world, to),
                "place -> push -> mine must not launder a hand-placed block into a rewarding one");
        // Every other neighbour must be untouched, or the offset is right by accident.
        for (Direction other : Direction.values()) {
            if (other != Direction.EAST) {
                assertFalse(BlockUtils.isRewardIneligible(world, from.offset(other)),
                        "the flag went " + other + " as well as EAST");
            }
        }
    }

    @Test
    void aBlockDestroyedByThePushLosesItsFlag() {
        final World world = overworld();
        final BlockPos broken = new BlockPos(9, 64, 9);
        BlockUtils.markPlaced(world, broken);

        BlockUtils.movePlacedFlags(world, List.of(), List.of(broken), Direction.WEST);

        assertFalse(BlockUtils.isRewardIneligible(world, broken));
        assertEquals(0, McMMOMod.getPlacedBlockTracker().size(), "a destroyed block frees its flag");
    }
}
