package com.gmail.nossr50.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.BlockUtils.AgeableState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Exercises the MC-typed {@link BlockUtils} wrappers end-to-end against real vanilla
 * {@link net.minecraft.block.Block}s: this proves the id-path extraction
 * ({@code Registries.BLOCK.getId(block).getPath()}) lines up with the keys the two MC-free backing
 * layers are tested on — the {@link MaterialMapStore} whitelists ({@link MaterialMapStoreTest}) and
 * the {@link ExperienceConfig} block-XP tables (real bundled {@code experience.yml}) — so the layers
 * actually connect. Runs under the {@code fabric-loader-junit} harness (see {@link McTestRegistries}).
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

    // --- MaterialMapStore-backed (registry-path key, config-independent) -----

    @Test
    void activationGatesReadTheBlacklists() {
        // Plain stone is neither ability- nor tool-activation blacklisted.
        assertTrue(BlockUtils.canActivateAbilities(Blocks.STONE));
        assertTrue(BlockUtils.canActivateTools(Blocks.STONE));
        // A BlockState overload resolves to the same answer as its Block.
        assertTrue(BlockUtils.canActivateAbilities(Blocks.STONE.getDefaultState()));
    }

    @Test
    void classifiesOre() {
        assertTrue(BlockUtils.isOre(Blocks.IRON_ORE));
        assertFalse(BlockUtils.isOre(Blocks.STONE));
    }

    @Test
    void classifiesTreeParts() {
        assertTrue(BlockUtils.isNonWoodPartOfTree(Blocks.OAK_LEAVES));
        assertFalse(BlockUtils.isNonWoodPartOfTree(Blocks.OAK_LOG));
        // A log is part of a tree via the Woodcutting-XP half; leaves via the non-wood half.
        assertTrue(BlockUtils.isPartOfTree(Blocks.OAK_LOG));
        assertTrue(BlockUtils.isPartOfTree(Blocks.OAK_LEAVES));
        assertFalse(BlockUtils.isPartOfTree(Blocks.STONE));
    }

    @Test
    void herbalismConversionsAndActivation() {
        assertTrue(BlockUtils.canMakeMossy(Blocks.COBBLESTONE));
        assertTrue(BlockUtils.canMakeMossy(Blocks.STONE_BRICKS));
        assertFalse(BlockUtils.canMakeMossy(Blocks.STONE));

        assertTrue(BlockUtils.canMakeShroomy(Blocks.DIRT));
        assertTrue(BlockUtils.canMakeShroomy(Blocks.GRASS_BLOCK));
        assertFalse(BlockUtils.canMakeShroomy(Blocks.STONE));

        assertTrue(BlockUtils.canActivateHerbalism(Blocks.DIRT));
        assertTrue(BlockUtils.affectedByBlockCracker(Blocks.STONE_BRICKS));
        assertFalse(BlockUtils.affectedByBlockCracker(Blocks.STONE));
    }

    @Test
    void classifiesHylianTreasureGroupsFromHardcodedMembers() {
        // Only the MaterialMapStore-backed branches are exercised here — the nine flowers and the
        // three non-tag bush blocks — because each returns before any tag check.
        assertEquals("Flowers", BlockUtils.getHylianTreasureGroup(Blocks.POPPY.getDefaultState()));
        assertEquals("Bushes", BlockUtils.getHylianTreasureGroup(Blocks.FERN.getDefaultState()));
        assertEquals("Bushes",
                BlockUtils.getHylianTreasureGroup(Blocks.SHORT_GRASS.getDefaultState()));
        assertEquals("Bushes", BlockUtils.getHylianTreasureGroup(Blocks.DEAD_BUSH.getDefaultState()));
        // NOTE: any block that is NOT a hardcoded flower/bush member falls through to a block-tag check
        // (SAPLINGS→"Bushes", FLOWER_POTS→"Pots", else null). The Bootstrap.initialize() harness does
        // not bind datapack tags — BlockState#isIn(TagKey) *throws* IllegalStateException there — so
        // those branches (and the null return) can't be asserted here; they are verified in-game (§G).
        // In a live world session tags are bound (as the DamageTypeTags checks elsewhere rely on).
    }

    // --- ExperienceConfig-backed (config string key, needs experience.yml) ---

    @Test
    void superAbilityAffectedChecksReadTheXpTables() {
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

    // --- The placed-block gate (ExploitFix.PlacedBlocks, GitHub #9) ----------

    /** A world that answers only the one question the tracker asks it: which world am I? */
    private static World overworld() {
        final World world = mock(World.class);
        when(world.getRegistryKey()).thenReturn(World.OVERWORLD);
        return world;
    }

    /** An {@link ExperienceConfig} whose {@code ExploitFix.PlacedBlocks} is switched off. */
    private static ExperienceConfig configWithTrackingOff(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("experience.yml"), "ExploitFix:\n    PlacedBlocks: false\n",
                StandardCharsets.UTF_8);
        return new ExperienceConfig(dir);
    }

    @Test
    void placedBlocksAreIneligibleWhileTheGateIsOn() {
        final World world = overworld();
        final BlockPos pos = new BlockPos(10, 64, -20);

        assertFalse(BlockUtils.isRewardIneligible(world, pos), "a never-placed block is eligible");
        BlockUtils.markPlaced(world, pos);
        assertTrue(BlockUtils.isRewardIneligible(world, pos), "a hand-placed block must not reward");
        BlockUtils.markNatural(world, pos);
        assertFalse(BlockUtils.isRewardIneligible(world, pos), "breaking it makes the spot natural");
    }

    @Test
    void switchingTheGateOffStopsBothReadingAndWritingFlags(@TempDir Path dir) throws IOException {
        final World world = overworld();
        final BlockPos pos = new BlockPos(3, 70, 4);
        McMMOMod.setExperienceConfig(configWithTrackingOff(dir));

        BlockUtils.markPlaced(world, pos);
        assertFalse(BlockUtils.isRewardIneligible(world, pos),
                "with the gate off a hand-placed block pays out again (the pre-K9 behaviour)");
        // The write must be refused too, not merely the read: otherwise the flags still accumulate
        // in memory and in placed_blocks.dat, and re-enabling the gate resurrects them.
        assertEquals(0, McMMOMod.getPlacedBlockTracker().size(),
                "no flag should have been recorded at all while the gate is off");
    }

    @Test
    void flagsAlreadyOnDiskStopBitingTheMomentTheGateIsOff(@TempDir Path dir) throws IOException {
        // The case the write-side gate alone cannot cover, and the one a player actually hits:
        // they played with the gate ON, so placed_blocks.dat is full of flags, and *then* they
        // switch it off. Those flags are restored into the tracker at world load by PlacedBlockStore
        // without ever going through markPlaced -- so the read side has to be gated too, or turning
        // the setting off does nothing for every block they had already placed.
        final World world = overworld();
        final BlockPos pos = new BlockPos(64, 11, 64);
        BlockUtils.markPlaced(world, pos); // written while the gate was still on
        assertTrue(BlockUtils.isRewardIneligible(world, pos));

        McMMOMod.setExperienceConfig(configWithTrackingOff(dir));

        assertFalse(BlockUtils.isRewardIneligible(world, pos),
                "a flag recorded before the gate was switched off must stop applying");
    }

    // --- The lava-generator gate (ExploitFix.LavaStoneAndCobbleFarming) ------

    @Test
    void lavaGeneratedBlocksThatPayMiningXpAreFlagged() {
        final World world = overworld();
        // Basalt is the one that matters: 40 Mining XP a block from a blue-ice generator that runs
        // itself, and the K9 tracker can never see it because nobody placed it.
        final BlockPos basaltPos = new BlockPos(0, 30, 0);
        BlockUtils.markLavaFormed(world, basaltPos, Blocks.BASALT);
        assertTrue(BlockUtils.isRewardIneligible(world, basaltPos));

        // Stone comes from the other seam (LavaFluid#flow) but through the same decision.
        final BlockPos stonePos = new BlockPos(0, 31, 0);
        BlockUtils.markLavaFormed(world, stonePos, Blocks.STONE);
        assertTrue(BlockUtils.isRewardIneligible(world, stonePos));
    }

    @Test
    void obsidianIsExemptFromTheLavaGate() {
        // Making obsidian consumes the lava source, so it cannot repeat without another bucket --
        // it is a trade, not a generator. Legacy exempts it by name and so do we.
        final World world = overworld();
        final BlockPos pos = new BlockPos(1, 30, 0);
        BlockUtils.markLavaFormed(world, pos, Blocks.OBSIDIAN);
        assertFalse(BlockUtils.isRewardIneligible(world, pos));
    }

    @Test
    void aFormedBlockWorthNoMiningXpIsNotFlagged() {
        // Plain cobblestone has no entry in the shipped Mining table, so the classic cobble
        // generator pays nothing regardless -- flagging it would grow the tracker for no reason.
        assertFalse(McMMOMod.getExperienceConfig()
                        .doesBlockGiveSkillXP(PrimarySkillType.MINING, "Cobblestone"),
                "test premise: shipped experience.yml gives plain Cobblestone no Mining XP");
        final World world = overworld();
        final BlockPos pos = new BlockPos(2, 30, 0);
        BlockUtils.markLavaFormed(world, pos, Blocks.COBBLESTONE);
        assertFalse(BlockUtils.isRewardIneligible(world, pos));
    }

    @Test
    void switchingOffTheLavaGateStopsFlaggingGeneratedBlocks(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("experience.yml"),
                "ExploitFix:\n    LavaStoneAndCobbleFarming: false\n", StandardCharsets.UTF_8);
        McMMOMod.setExperienceConfig(new ExperienceConfig(dir));

        final World world = overworld();
        final BlockPos pos = new BlockPos(3, 30, 0);
        BlockUtils.markLavaFormed(world, pos, Blocks.BASALT);
        assertFalse(BlockUtils.isRewardIneligible(world, pos));
    }

    // --- The piston gate (ExploitFix.PistonCheating) -------------------------

    @Test
    void aPushedBlockCarriesItsPlacedFlagWithIt() {
        final World world = overworld();
        final BlockPos from = new BlockPos(0, 64, 0);
        final BlockPos to = from.offset(Direction.EAST);
        BlockUtils.markPlaced(world, from);

        BlockUtils.movePlacedFlags(world, List.of(from), List.of(), Direction.EAST);

        assertFalse(BlockUtils.isRewardIneligible(world, from), "the old spot is empty now");
        assertTrue(BlockUtils.isRewardIneligible(world, to),
                "place -> push -> mine must not launder a hand-placed block into a rewarding one");
    }

    @Test
    void pushingANaturalBlockDoesNotMakeItWorthless() {
        // Legacy marks every destination unnatural, because its tracker over-marks anyway. Doing
        // that here would invent a false positive: a natural stone wall nudged sideways by a piston
        // would stop paying forever. A piston moves blocks, it does not create them.
        final World world = overworld();
        final BlockPos from = new BlockPos(5, 64, 5);

        BlockUtils.movePlacedFlags(world, List.of(from), List.of(), Direction.UP);

        assertFalse(BlockUtils.isRewardIneligible(world, from.offset(Direction.UP)));
        assertEquals(0, McMMOMod.getPlacedBlockTracker().size());
    }

    @Test
    void everyBlockOfAPushedColumnKeepsItsFlag() {
        // The case the three-pass implementation exists for: in a column, one block's destination is
        // the next block's source. Clearing sources as you go would wipe a flag that had just been
        // written, and the middle of every pushed column would quietly become farmable again.
        final World world = overworld();
        final BlockPos a = new BlockPos(0, 64, 0);
        final BlockPos b = new BlockPos(0, 64, 1);
        final BlockPos c = new BlockPos(0, 64, 2);
        BlockUtils.markPlaced(world, a);
        BlockUtils.markPlaced(world, b);
        BlockUtils.markPlaced(world, c);

        BlockUtils.movePlacedFlags(world, List.of(a, b, c), List.of(), Direction.SOUTH);

        assertTrue(BlockUtils.isRewardIneligible(world, b), "a moved onto b's old spot");
        assertTrue(BlockUtils.isRewardIneligible(world, c), "b moved onto c's old spot");
        assertTrue(BlockUtils.isRewardIneligible(world, c.offset(Direction.SOUTH)), "c moved on");
        assertFalse(BlockUtils.isRewardIneligible(world, a), "only a's old spot is vacated");
        assertEquals(3, McMMOMod.getPlacedBlockTracker().size(), "three blocks, three flags");
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

    @Test
    void switchingOffThePistonGateLeavesFlagsBehind(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("experience.yml"),
                "ExploitFix:\n    PistonCheating: false\n", StandardCharsets.UTF_8);
        final World world = overworld();
        final BlockPos from = new BlockPos(2, 64, 2);
        BlockUtils.markPlaced(world, from);
        McMMOMod.setExperienceConfig(new ExperienceConfig(dir));

        BlockUtils.movePlacedFlags(world, List.of(from), List.of(), Direction.NORTH);

        assertTrue(BlockUtils.isRewardIneligible(world, from), "the flag stays where it was");
        assertFalse(BlockUtils.isRewardIneligible(world, from.offset(Direction.NORTH)));
    }

    @Test
    void theGateFailsClosedBeforeAnyConfigIsLoaded() {
        // A gate whose config has not arrived yet must behave as ON. Failing open would pay full
        // gathering rewards for hand-placed blocks during world load, which is the exploit itself.
        McMMOMod.setExperienceConfig(null);
        final World world = overworld();
        final BlockPos pos = new BlockPos(-8, 12, 9);

        BlockUtils.markPlaced(world, pos);
        assertTrue(BlockUtils.isRewardIneligible(world, pos));
    }

    @Test
    void xpBackedChecksAreNullSafeWithoutConfig() {
        // Without a loaded ExperienceConfig the XP-driven checks collapse to false (no crash), while
        // the intended-pickaxe half of Super Breaker still answers from the MaterialMapStore.
        McMMOMod.setExperienceConfig(null);
        assertTrue(BlockUtils.affectedBySuperBreaker(Blocks.STONE)); // pickaxe-set half, no config.
        assertFalse(BlockUtils.affectedByGigaDrillBreaker(Blocks.DIRT));
        assertFalse(BlockUtils.affectedByGreenTerra(Blocks.WHEAT));
        assertFalse(BlockUtils.hasWoodcuttingXP(Blocks.OAK_LOG));
    }
}
