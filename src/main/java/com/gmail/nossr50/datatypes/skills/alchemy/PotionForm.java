package com.gmail.nossr50.datatypes.skills.alchemy;

/**
 * The dispersion form of a potion — which of the three vanilla potion items carries it.
 *
 * <p>A Minecraft-free mirror of the {@code POTION} / {@code SPLASH_POTION} / {@code LINGERING_POTION}
 * item identity, so {@link PotionStage}'s dispersion step can be decided without an item type. The
 * vanilla mapping lives in exactly one place — {@code platform/Potions#formOf} — and is written as
 * exact {@code isOf(Items.…)} identity checks, never an id-path comparison, so it cannot silently
 * broaden across namespaces. {@link #NORMAL} is the fall-through: anything that is not one of the two
 * thrown potion items disperses on drinking, which is what the pre-seal
 * {@code isSplash()}/{@code isLingering()} pair meant.
 */
public enum PotionForm {
    /** A drinkable potion ({@code minecraft:potion}). */
    NORMAL,
    /** A thrown, instantly-applied potion ({@code minecraft:splash_potion}). */
    SPLASH,
    /** A thrown potion that leaves an area-effect cloud ({@code minecraft:lingering_potion}). */
    LINGERING
}
