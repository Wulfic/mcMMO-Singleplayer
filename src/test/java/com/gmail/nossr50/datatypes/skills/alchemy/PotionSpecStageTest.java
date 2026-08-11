package com.gmail.nossr50.datatypes.skills.alchemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the Alchemy brew-stage arithmetic and potion matching on {@link PotionSpec} directly.
 *
 * <p>Deliberately <b>Minecraft-free</b>: {@link PotionStage} used to read a
 * {@code PotionContentsComponent} and could therefore only be exercised through {@code PotionConfig}
 * under the {@code fabric-loader-junit} registry harness. After Phase 2 slice 5 the whole stage table
 * is decidable from four fields, so every combination can be asserted here for free — including the
 * ones the shipped {@code potions.yml} does not happen to contain, which the config-driven test could
 * never reach.
 *
 * <p>{@code PotionConfigTest} keeps its registry-backed assertions on the real shipped tree; this is
 * the exhaustive companion, not a replacement.
 */
class PotionSpecStageTest {

    private static final String WATER = "minecraft:water";
    private static final String SWIFTNESS = "minecraft:swiftness";
    private static final String STRONG_SWIFTNESS = "minecraft:strong_swiftness";
    private static final String LONG_SWIFTNESS = "minecraft:long_swiftness";

    private static PotionSpec spec(String baseId, boolean baseHasEffects, PotionForm form,
            EffectSpec... effects) {
        return new PotionSpec(baseId, baseHasEffects, List.of(effects), form);
    }

    // --- The stage table -------------------------------------------------------------------------

    @Test
    void aPlainWaterBottleIsStageOne() {
        assertEquals(PotionStage.ONE, PotionStage.getPotionStage(
                spec(WATER, false, PotionForm.NORMAL)));
    }

    @Test
    void aBaseEffectIsTheFirstBrewStep() {
        assertEquals(PotionStage.TWO, PotionStage.getPotionStage(
                spec(SWIFTNESS, true, PotionForm.NORMAL)));
    }

    @Test
    void aCustomEffectAlsoCountsAsTheFirstBrewStep() {
        // A potion whose base carries nothing but which has a custom effect bolted on is still a
        // one-step brew -- the two sources of "has an effect" are interchangeable here.
        assertEquals(PotionStage.TWO, PotionStage.getPotionStage(
                spec(WATER, false, PotionForm.NORMAL,
                        new EffectSpec("minecraft:absorption", 0, 200))));
    }

    @Test
    void aStrongBaseAddsTheAmplifierStep() {
        assertEquals(PotionStage.THREE, PotionStage.getPotionStage(
                spec(STRONG_SWIFTNESS, true, PotionForm.NORMAL)));
    }

    @Test
    void anAmplifiedCustomEffectAddsTheAmplifierStep() {
        assertEquals(PotionStage.THREE, PotionStage.getPotionStage(
                spec(WATER, false, PotionForm.NORMAL,
                        new EffectSpec("minecraft:absorption", 1, 200))));
        // Amplifier 0 is level I -- not an amplifier step.
        assertEquals(PotionStage.TWO, PotionStage.getPotionStage(
                spec(WATER, false, PotionForm.NORMAL,
                        new EffectSpec("minecraft:absorption", 0, 200))));
    }

    @Test
    void aLongBaseAddsTheDurationStep() {
        assertEquals(PotionStage.THREE, PotionStage.getPotionStage(
                spec(LONG_SWIFTNESS, true, PotionForm.NORMAL)));
    }

    @Test
    void splashAndLingeringBothAddTheDispersionStep() {
        assertEquals(PotionStage.THREE, PotionStage.getPotionStage(
                spec(SWIFTNESS, true, PotionForm.SPLASH)));
        assertEquals(PotionStage.THREE, PotionStage.getPotionStage(
                spec(SWIFTNESS, true, PotionForm.LINGERING)));
    }

    @Test
    void everyStepTogetherReachesTheTopStage() {
        // effect + strong_ amplifier + splash = 4. A strong_ base is never also long_, so five is
        // only reachable through the same-stage swap rule below -- which is exactly why that rule
        // exists.
        assertEquals(PotionStage.FOUR, PotionStage.getPotionStage(
                spec(STRONG_SWIFTNESS, true, PotionForm.SPLASH)));
    }

    @Test
    void aStageAboveFiveClampsToFive() {
        // long_ base + an amplified custom effect + lingering = 5 steps.
        assertEquals(PotionStage.FIVE, PotionStage.getPotionStage(
                spec(LONG_SWIFTNESS, true, PotionForm.LINGERING,
                        new EffectSpec("minecraft:absorption", 2, 200))));
    }

    @Test
    void anAbsentSpecIsStageOne() {
        // A stack with no potion contents at all -- the floor, not a crash.
        assertEquals(PotionStage.ONE, PotionStage.getPotionStage((PotionSpec) null));
    }

    // --- Prefix reading on the spec ---------------------------------------------------------------

    @Test
    void variantPredicatesReadThePathNotTheNamespace() {
        final PotionSpec strong = spec(STRONG_SWIFTNESS, true, PotionForm.NORMAL);
        assertTrue(strong.isStrongBase());
        assertFalse(strong.isLongBase());
        assertFalse(strong.isWaterBase());
        assertEquals("strong_swiftness", strong.basePotionPath());

        assertTrue(spec(WATER, false, PotionForm.NORMAL).isWaterBase());
    }

    @Test
    void anAbsentBasePotionHasNoPath() {
        final PotionSpec none = spec(null, false, PotionForm.NORMAL);
        assertNull(none.basePotionPath());
        assertFalse(none.isWaterBase());
        assertFalse(none.isStrongBase());
        assertFalse(none.isLongBase());
    }

    // --- Content matching -------------------------------------------------------------------------

    @Test
    void matchingIsOrderIndependentAcrossCustomEffects() {
        final EffectSpec a = new EffectSpec("minecraft:speed", 1, 100);
        final EffectSpec b = new EffectSpec("minecraft:absorption", 0, 200);
        assertTrue(spec(WATER, false, PotionForm.NORMAL, a, b)
                .matchesContents(spec(WATER, false, PotionForm.NORMAL, b, a)));
    }

    @Test
    void matchingRejectsADifferentAmplifierOrDuration() {
        final PotionSpec mine = spec(WATER, false, PotionForm.NORMAL,
                new EffectSpec("minecraft:speed", 1, 100));
        assertFalse(mine.matchesContents(spec(WATER, false, PotionForm.NORMAL,
                new EffectSpec("minecraft:speed", 2, 100))));
        assertFalse(mine.matchesContents(spec(WATER, false, PotionForm.NORMAL,
                new EffectSpec("minecraft:speed", 1, 999))));
        assertFalse(mine.matchesContents(spec(WATER, false, PotionForm.NORMAL)));
    }

    @Test
    void matchingComparesTheNamespacedBaseIdNotTheBarePath() {
        // The regression this guards: comparing bare paths would make another mod's "swiftness" the
        // same potion as vanilla's, which the RegistryEntry comparison this replaced never would.
        assertFalse(spec(SWIFTNESS, true, PotionForm.NORMAL)
                .matchesContents(spec("mymod:swiftness", true, PotionForm.NORMAL)));
    }

    @Test
    void matchingIgnoresTheFormBecauseTheItemIdentityCarriesIt() {
        // isSimilarPotion compares the potion item separately (potion vs splash_potion), so the
        // contents comparison must not double-count the form.
        assertTrue(spec(SWIFTNESS, true, PotionForm.NORMAL)
                .matchesContents(spec(SWIFTNESS, true, PotionForm.SPLASH)));
    }

    @Test
    void bothMissingBasePotionsMatch() {
        assertTrue(spec(null, false, PotionForm.NORMAL)
                .matchesContents(spec(null, false, PotionForm.NORMAL)));
        assertFalse(spec(null, false, PotionForm.NORMAL)
                .matchesContents(spec(WATER, false, PotionForm.NORMAL)));
    }
}
