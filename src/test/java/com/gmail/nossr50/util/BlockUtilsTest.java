package com.gmail.nossr50.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import java.nio.file.Path;
import net.minecraft.block.Blocks;
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
