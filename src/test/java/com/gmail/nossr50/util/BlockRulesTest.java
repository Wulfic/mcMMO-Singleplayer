package com.gmail.nossr50.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link BlockRules} — every mcMMO block decision, with no Minecraft type in sight.
 *
 * <p><b>There is deliberately no {@code McTestRegistries.bootstrap()} here, and that is the headline
 * result of the Phase 2 extraction.</b> These assertions used to live in {@code BlockUtilsTest} and
 * therefore paid {@code Bootstrap.initialize()} (~53s per fork, see {@code gradle-build-tuning}) to
 * assert things about strings and longs. Phase 4.4 has to run the MC-typed suite once per band; every
 * test that leaves that suite is a per-band cost removed. If a change to this file ever makes it need
 * the harness, that is a signal the boundary leaked, not a reason to add the bootstrap.
 *
 * <p>The complementary direction — that a live vanilla {@code Block}'s id path actually lines up with
 * the keys asserted here — is {@code platform.BlockUtilsTest}, which does need the harness. Neither
 * file is sufficient alone: this one proves the rules are right, that one proves the bridge connects.
 */
class BlockRulesTest {

    private static final String OVERWORLD = "minecraft:overworld";

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

    /** An {@link ExperienceConfig} built from a one-key {@code experience.yml} override. */
    private static ExperienceConfig configWith(Path dir, String yaml) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("experience.yml"), yaml, StandardCharsets.UTF_8);
        return new ExperienceConfig(dir);
    }

    // --- MaterialMapStore-backed (registry-path key, config-independent) -----

    @Test
    void activationGatesReadTheBlacklists() {
        assertTrue(BlockRules.canActivateAbilities("stone"));
        assertTrue(BlockRules.canActivateTools("stone"));
    }

    @Test
    void classifiesOre() {
        assertTrue(BlockRules.isOre("iron_ore"));
        assertFalse(BlockRules.isOre("stone"));
    }

    @Test
    void classifiesTreeParts() {
        assertTrue(BlockRules.isNonWoodPartOfTree("oak_leaves"));
        assertFalse(BlockRules.isNonWoodPartOfTree("oak_log"));
        // A log is part of a tree via the Woodcutting-XP half; leaves via the non-wood half.
        assertTrue(BlockRules.isPartOfTree("oak_log"));
        assertTrue(BlockRules.isPartOfTree("oak_leaves"));
        assertFalse(BlockRules.isPartOfTree("stone"));
    }

    @Test
    void herbalismConversionsAndActivation() {
        assertTrue(BlockRules.canMakeMossy("cobblestone"));
        assertTrue(BlockRules.canMakeMossy("stone_bricks"));
        assertFalse(BlockRules.canMakeMossy("stone"));

        assertTrue(BlockRules.canMakeShroomy("dirt"));
        assertTrue(BlockRules.canMakeShroomy("grass_block"));
        assertFalse(BlockRules.canMakeShroomy("stone"));

        assertTrue(BlockRules.canActivateHerbalism("dirt"));
        assertTrue(BlockRules.affectedByBlockCracker("stone_bricks"));
        assertFalse(BlockRules.affectedByBlockCracker("stone"));
    }

    // --- ExperienceConfig-backed (config string key, needs experience.yml) ---

    @Test
    void superAbilityAffectedChecksReadTheXpTables() {
        // Super Breaker: stone is an intended-pickaxe block (config-independent half) AND Mining XP.
        assertTrue(BlockRules.affectedBySuperBreaker("stone"));
        // Giga Drill Breaker: dirt grants Excavation XP in the bundled experience.yml.
        assertTrue(BlockRules.affectedByGigaDrillBreaker("dirt"));
        // Green Terra: wheat grants Herbalism XP.
        assertTrue(BlockRules.affectedByGreenTerra("wheat"));
        // Woodcutting XP: an oak log yes, plain stone no.
        assertTrue(BlockRules.hasWoodcuttingXP("oak_log"));
        assertFalse(BlockRules.hasWoodcuttingXP("stone"));
    }

    @Test
    void xpBackedChecksAreNullSafeWithoutConfig() {
        // Without a loaded ExperienceConfig the XP-driven checks collapse to false (no crash), while
        // the intended-pickaxe half of Super Breaker still answers from the MaterialMapStore.
        McMMOMod.setExperienceConfig(null);
        assertTrue(BlockRules.affectedBySuperBreaker("stone")); // pickaxe-set half, no config.
        assertFalse(BlockRules.affectedByGigaDrillBreaker("dirt"));
        assertFalse(BlockRules.affectedByGreenTerra("wheat"));
        assertFalse(BlockRules.hasWoodcuttingXP("oak_log"));
    }

    @Test
    void berserkInstaBreaksExcavationBlocksAndGlassButNotStone() {
        // Note: the snow-LAYER arm of Berserk is an identity check and lives on the MC-typed bridge,
        // so it is asserted in platform.BlockUtilsTest, not here. Hence the method's blunt name.
        assertTrue(BlockRules.affectedByBerserkExceptSnowLayer("dirt"), "Excavation XP block");
        assertTrue(BlockRules.affectedByBerserkExceptSnowLayer("glass"));
        assertFalse(BlockRules.affectedByBerserkExceptSnowLayer("stone"));
    }

    // --- Hylian Luck grouping, and the laziness that keeps it from throwing --

    @Test
    void classifiesHylianTreasureGroupsFromHardcodedMembers() {
        assertEquals("Flowers", BlockRules.hylianTreasureGroup("poppy", mustNotBeEvaluated(), mustNotBeEvaluated()));
        assertEquals("Bushes", BlockRules.hylianTreasureGroup("fern", mustNotBeEvaluated(), mustNotBeEvaluated()));
        assertEquals("Bushes", BlockRules.hylianTreasureGroup("short_grass", mustNotBeEvaluated(), mustNotBeEvaluated()));
        assertEquals("Bushes", BlockRules.hylianTreasureGroup("dead_bush", mustNotBeEvaluated(), mustNotBeEvaluated()));
    }

    @Test
    void fallsThroughToTheTagsForEverythingElse() {
        assertEquals("Bushes",
                BlockRules.hylianTreasureGroup("oak_sapling", () -> true, mustNotBeEvaluated()));
        assertEquals("Pots",
                BlockRules.hylianTreasureGroup("potted_poppy", () -> false, () -> true));
        assertNull(BlockRules.hylianTreasureGroup("stone", () -> false, () -> false));
    }

    /**
     * ⚠️⚠️ The converse check for the {@link BooleanSupplier} arguments, and the reason they exist.
     *
     * <p>{@code BlockState#isIn(TagKey)} throws while the datapack tags are unbound, so the hardcoded
     * flower/bush arms MUST return before either tag is consulted. A plain {@code boolean} parameter
     * would be evaluated eagerly by the caller and turn a working short-circuit into a crash. The
     * tests above would still pass under that bug — they only assert the returned group — so the
     * laziness needs its own assertion: count the invocations and require zero.
     */
    @Test
    void aHardcodedMemberNeverEvaluatesEitherTag() {
        final AtomicInteger saplingReads = new AtomicInteger();
        final AtomicInteger potReads = new AtomicInteger();

        assertEquals("Flowers", BlockRules.hylianTreasureGroup("poppy",
                () -> {
                    saplingReads.incrementAndGet();
                    return false;
                },
                () -> {
                    potReads.incrementAndGet();
                    return false;
                }));

        assertEquals(0, saplingReads.get(), "a hardcoded flower must not touch the saplings tag");
        assertEquals(0, potReads.get(), "a hardcoded flower must not touch the flower_pots tag");
    }

    @Test
    void aBushMemberNeverEvaluatesTheFlowerPotTag() {
        final AtomicInteger potReads = new AtomicInteger();

        assertEquals("Bushes", BlockRules.hylianTreasureGroup("fern", () -> false,
                () -> {
                    potReads.incrementAndGet();
                    return false;
                }));

        assertEquals(0, potReads.get(), "a hardcoded bush must not touch the flower_pots tag");
    }

    /** A supplier that fails the test if anything evaluates it. */
    private static BooleanSupplier mustNotBeEvaluated() {
        return () -> {
            throw new AssertionError("tag supplier must not be evaluated for a hardcoded member");
        };
    }

    // --- The placed-block gate (ExploitFix.PlacedBlocks, GitHub #9) ----------

    @Test
    void placedBlocksAreIneligibleWhileTheGateIsOn() {
        final long pos = 1_234L;

        assertFalse(BlockRules.isRewardIneligible(OVERWORLD, pos), "a never-placed block is eligible");
        BlockRules.markUnnatural(OVERWORLD, pos);
        assertTrue(BlockRules.isRewardIneligible(OVERWORLD, pos), "a hand-placed block must not reward");
        BlockRules.markNatural(OVERWORLD, pos);
        assertFalse(BlockRules.isRewardIneligible(OVERWORLD, pos), "breaking it makes the spot natural");
    }

    @Test
    void switchingTheGateOffStopsBothReadingAndWritingFlags(@TempDir Path dir) throws IOException {
        final long pos = 42L;
        McMMOMod.setExperienceConfig(
                configWith(dir, "ExploitFix:\n    PlacedBlocks: false\n"));

        BlockRules.markUnnatural(OVERWORLD, pos);
        assertFalse(BlockRules.isRewardIneligible(OVERWORLD, pos),
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
        // without ever going through markUnnatural -- so the read side has to be gated too, or turning
        // the setting off does nothing for every block they had already placed.
        final long pos = 7L;
        BlockRules.markUnnatural(OVERWORLD, pos); // written while the gate was still on
        assertTrue(BlockRules.isRewardIneligible(OVERWORLD, pos));

        McMMOMod.setExperienceConfig(
                configWith(dir, "ExploitFix:\n    PlacedBlocks: false\n"));

        assertFalse(BlockRules.isRewardIneligible(OVERWORLD, pos),
                "a flag recorded before the gate was switched off must stop applying");
    }

    @Test
    void theGateFailsClosedBeforeAnyConfigIsLoaded() {
        // A gate whose config has not arrived yet must behave as ON. Failing open would pay full
        // gathering rewards for hand-placed blocks during world load, which is the exploit itself.
        McMMOMod.setExperienceConfig(null);
        final long pos = -99L;

        assertTrue(BlockRules.isPlacedBlockTrackingEnabled());
        BlockRules.markUnnatural(OVERWORLD, pos);
        assertTrue(BlockRules.isRewardIneligible(OVERWORLD, pos));
    }

    @Test
    void flagsAreScopedPerWorld() {
        final long pos = 5L;
        BlockRules.markUnnatural(OVERWORLD, pos);
        assertTrue(BlockRules.isRewardIneligible(OVERWORLD, pos));
        assertFalse(BlockRules.isRewardIneligible("minecraft:the_nether", pos),
                "the same packed position in another dimension is a different block");
    }

    // --- The lava-generator gate (ExploitFix.LavaStoneAndCobbleFarming) ------

    @Test
    void lavaGeneratedBlocksThatPayMiningXpAreFlagged() {
        // Basalt is the one that matters: 40 Mining XP a block from a blue-ice generator that runs
        // itself, and the K9 tracker can never see it because nobody placed it.
        BlockRules.markLavaFormed(OVERWORLD, 100L, "basalt");
        assertTrue(BlockRules.isRewardIneligible(OVERWORLD, 100L));

        // Stone comes from the other seam (LavaFluid#flow) but through the same decision.
        BlockRules.markLavaFormed(OVERWORLD, 101L, "stone");
        assertTrue(BlockRules.isRewardIneligible(OVERWORLD, 101L));
    }

    @Test
    void aFormedBlockWorthNoMiningXpIsNotFlagged() {
        // Plain cobblestone has no entry in the shipped Mining table, so the classic cobble
        // generator pays nothing regardless -- flagging it would grow the tracker for no reason.
        assertFalse(McMMOMod.getExperienceConfig()
                        .doesBlockGiveSkillXP(PrimarySkillType.MINING, "Cobblestone"),
                "test premise: shipped experience.yml gives plain Cobblestone no Mining XP");
        BlockRules.markLavaFormed(OVERWORLD, 102L, "cobblestone");
        assertFalse(BlockRules.isRewardIneligible(OVERWORLD, 102L));
    }

    @Test
    void switchingOffTheLavaGateStopsFlaggingGeneratedBlocks(@TempDir Path dir) throws IOException {
        McMMOMod.setExperienceConfig(
                configWith(dir, "ExploitFix:\n    LavaStoneAndCobbleFarming: false\n"));

        BlockRules.markLavaFormed(OVERWORLD, 103L, "basalt");
        assertFalse(BlockRules.isRewardIneligible(OVERWORLD, 103L));
    }

    // --- The snow-golem gate (ExploitFix.SnowGolemExcavation) ----------------

    @Test
    void snowLaidByAGolemIsFlaggedWhileItPaysExcavationXp() {
        BlockRules.markSnowGolemFormed(OVERWORLD, 200L, "snow");
        assertTrue(BlockRules.isRewardIneligible(OVERWORLD, 200L));
    }

    @Test
    void switchingOffTheSnowGateStopsFlaggingTheTrail(@TempDir Path dir) throws IOException {
        McMMOMod.setExperienceConfig(
                configWith(dir, "ExploitFix:\n    SnowGolemExcavation: false\n"));

        BlockRules.markSnowGolemFormed(OVERWORLD, 201L, "snow");
        assertFalse(BlockRules.isRewardIneligible(OVERWORLD, 201L));
    }

    // --- The piston gate (ExploitFix.PistonCheating) -------------------------

    @Test
    void aPushedBlockCarriesItsPlacedFlagWithIt() {
        final long from = 10L;
        final long to = 11L;
        BlockRules.markUnnatural(OVERWORLD, from);

        BlockRules.movePlacedFlags(OVERWORLD, new long[] {from}, new long[] {to}, new long[0]);

        assertFalse(BlockRules.isRewardIneligible(OVERWORLD, from), "the old spot is empty now");
        assertTrue(BlockRules.isRewardIneligible(OVERWORLD, to),
                "place -> push -> mine must not launder a hand-placed block into a rewarding one");
    }

    @Test
    void pushingANaturalBlockDoesNotMakeItWorthless() {
        // Legacy marks every destination unnatural, because its tracker over-marks anyway. Doing
        // that here would invent a false positive: a natural stone wall nudged sideways by a piston
        // would stop paying forever. A piston moves blocks, it does not create them.
        BlockRules.movePlacedFlags(OVERWORLD, new long[] {20L}, new long[] {21L}, new long[0]);

        assertFalse(BlockRules.isRewardIneligible(OVERWORLD, 21L));
        assertEquals(0, McMMOMod.getPlacedBlockTracker().size());
    }

    @Test
    void everyBlockOfAPushedColumnKeepsItsFlag() {
        // The case the three-pass implementation exists for: in a column, one block's destination is
        // the next block's source. Clearing sources as you go would wipe a flag that had just been
        // written, and the middle of every pushed column would quietly become farmable again.
        final long a = 30L;
        final long b = 31L;
        final long c = 32L;
        final long past = 33L;
        BlockRules.markUnnatural(OVERWORLD, a);
        BlockRules.markUnnatural(OVERWORLD, b);
        BlockRules.markUnnatural(OVERWORLD, c);

        BlockRules.movePlacedFlags(OVERWORLD,
                new long[] {a, b, c}, new long[] {b, c, past}, new long[0]);

        assertTrue(BlockRules.isRewardIneligible(OVERWORLD, b), "a moved onto b's old spot");
        assertTrue(BlockRules.isRewardIneligible(OVERWORLD, c), "b moved onto c's old spot");
        assertTrue(BlockRules.isRewardIneligible(OVERWORLD, past), "c moved on");
        assertFalse(BlockRules.isRewardIneligible(OVERWORLD, a), "only a's old spot is vacated");
        assertEquals(3, McMMOMod.getPlacedBlockTracker().size(), "three blocks, three flags");
    }

    @Test
    void aBlockDestroyedByThePushLosesItsFlag() {
        final long broken = 40L;
        BlockRules.markUnnatural(OVERWORLD, broken);

        BlockRules.movePlacedFlags(OVERWORLD, new long[0], new long[0], new long[] {broken});

        assertFalse(BlockRules.isRewardIneligible(OVERWORLD, broken));
        assertEquals(0, McMMOMod.getPlacedBlockTracker().size(), "a destroyed block frees its flag");
    }

    @Test
    void switchingOffThePistonGateLeavesFlagsBehind(@TempDir Path dir) throws IOException {
        final long from = 50L;
        BlockRules.markUnnatural(OVERWORLD, from);
        McMMOMod.setExperienceConfig(
                configWith(dir, "ExploitFix:\n    PistonCheating: false\n"));

        BlockRules.movePlacedFlags(OVERWORLD, new long[] {from}, new long[] {51L}, new long[0]);

        assertTrue(BlockRules.isRewardIneligible(OVERWORLD, from), "the flag stays where it was");
        assertFalse(BlockRules.isRewardIneligible(OVERWORLD, 51L));
    }

    @Test
    void misalignedMoveArraysAreRejectedRatherThanSilentlyTruncated() {
        // The extraction handed the direction arithmetic to the caller, which introduced a way to get
        // it wrong that the BlockPos-typed signature could not express. Fail loudly: a quiet truncation
        // would drop the flags of whichever blocks fell off the end of the shorter array.
        assertThrows(IllegalArgumentException.class,
                () -> BlockRules.movePlacedFlags(OVERWORLD,
                        new long[] {1L, 2L}, new long[] {3L}, new long[0]));
    }
}
