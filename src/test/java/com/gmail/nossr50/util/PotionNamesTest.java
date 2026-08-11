package com.gmail.nossr50.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link PotionNames} — the string half of potion resolution that Phase 2 slice 5 split out of
 * the old {@code util/PotionUtil}.
 *
 * <p>Deliberately <b>Minecraft-free</b>: no {@code McTestRegistries.bootstrap()}, so these assertions
 * cost nothing of the ~53s {@code Bootstrap.initialize()} that every registry-backed fork pays, and
 * they run once per build rather than once per Minecraft band (multi-version TODO Phase 4.4).
 */
class PotionNamesTest {

    // --- Legacy Bukkit name translation ----------------------------------------------------------

    @Test
    void translatesLegacyPotionNames() {
        // The shipped potions.yml still uses the pre-1.13 Bukkit spellings.
        assertEquals("swiftness", PotionNames.convertLegacyPotionName("SPEED"));
        assertEquals("leaping", PotionNames.convertLegacyPotionName("JUMP"));
        assertEquals("healing", PotionNames.convertLegacyPotionName("INSTANT_HEAL"));
        assertEquals("harming", PotionNames.convertLegacyPotionName("INSTANT_DAMAGE"));
        assertEquals("regeneration", PotionNames.convertLegacyPotionName("REGEN"));
        // Uncraftable no longer exists; Mundane is the modern no-op stand-in.
        assertEquals("mundane", PotionNames.convertLegacyPotionName("UNCRAFTABLE"));
    }

    @Test
    void leavesAModernPotionNameAloneApartFromCase() {
        assertEquals("poison", PotionNames.convertLegacyPotionName("POISON"));
        assertEquals("water", PotionNames.convertLegacyPotionName("water"));
    }

    @Test
    void translatesLegacyEffectNames() {
        assertEquals("mining_fatigue", PotionNames.convertLegacyEffectName("SLOW_DIGGING"));
        assertEquals("haste", PotionNames.convertLegacyEffectName("FAST_DIGGING"));
        assertEquals("nausea", PotionNames.convertLegacyEffectName("CONFUSION"));
        assertEquals("resistance", PotionNames.convertLegacyEffectName("DAMAGE_RESISTANCE"));
        assertEquals("absorption", PotionNames.convertLegacyEffectName("ABSORPTION"));
    }

    // --- Variant candidate paths -----------------------------------------------------------------

    @Test
    void plainPotionHasASingleCandidate() {
        assertEquals(List.of("swiftness"), PotionNames.variantPaths("SPEED", false, false));
    }

    @Test
    void upgradedPrefersStrongThenFallsBackToTheBase() {
        // Not every potion has a strong_ variant, so the unprefixed base must stay in the list --
        // that fall-back is what legacy resolveVariant did, and dropping it would turn a missing
        // variant into a config potion that fails to load at all.
        assertEquals(List.of("strong_swiftness", "swiftness"),
                PotionNames.variantPaths("SPEED", true, false));
    }

    @Test
    void extendedPrefersLongThenFallsBackToTheBase() {
        assertEquals(List.of("long_leaping", "leaping"),
                PotionNames.variantPaths("JUMP", false, true));
    }

    @Test
    void upgradedWinsOverExtendedWhenBothAreSet() {
        // PotionConfig already clears Upgraded when both are set; this pins the ordering here too so
        // the two cannot disagree about which prefix a doubly-flagged potion gets.
        assertEquals(List.of("strong_poison", "poison"),
                PotionNames.variantPaths("POISON", true, true));
    }

    @Test
    void aBlankNameHasNoCandidates() {
        assertTrue(PotionNames.variantPaths(null, false, false).isEmpty());
        assertTrue(PotionNames.variantPaths("", true, false).isEmpty());
    }

    // --- Prefix predicates -----------------------------------------------------------------------

    @Test
    void readsTheVariantPrefixesBackOffAPath() {
        assertTrue(PotionNames.isStrong("strong_healing"));
        assertFalse(PotionNames.isStrong("healing"));
        assertFalse(PotionNames.isStrong("long_healing"));

        assertTrue(PotionNames.isLong("long_swiftness"));
        assertFalse(PotionNames.isLong("swiftness"));
        assertFalse(PotionNames.isLong("strong_swiftness"));

        assertTrue(PotionNames.isWater("water"));
        // "water_breathing" starts with "water" but is a different potion entirely -- the water
        // check must be exact equality, never a prefix.
        assertFalse(PotionNames.isWater("water_breathing"));
    }

    @Test
    void thePredicatesTolerateAnAbsentBasePotion() {
        // A stack with no base potion yields a null path; the stage calculation asks all three.
        assertFalse(PotionNames.isStrong(null));
        assertFalse(PotionNames.isLong(null));
        assertFalse(PotionNames.isWater(null));
    }
}
