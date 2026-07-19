package com.gmail.nossr50.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.BlockUtils.AgeableState;
import java.nio.file.Path;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
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
    }

    @AfterEach
    void clearConfig() {
        McMMOMod.setExperienceConfig(null);
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
