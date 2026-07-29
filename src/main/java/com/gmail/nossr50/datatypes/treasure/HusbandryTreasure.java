package com.gmail.nossr50.datatypes.treasure;

/**
 * A rare find turned up by a Husbandry harvest — {@code Hidden Bounty}.
 *
 * <p>Its own type rather than a reuse of {@link HylianTreasure} for the same reason that one is not a
 * reuse of {@link ExcavationTreasure}: the maps in {@code TreasureConfig} are keyed by different
 * things (a block type, a plant group, a harvest verb) and sharing a type would make it possible to
 * hand a Herbalism treasure to a shear by accident and have it compile.
 */
public class HusbandryTreasure extends Treasure {
    public HusbandryTreasure(ItemSpec drop, int xp, double dropChance, int dropLevel) {
        super(drop, xp, dropChance, dropLevel);
    }
}
