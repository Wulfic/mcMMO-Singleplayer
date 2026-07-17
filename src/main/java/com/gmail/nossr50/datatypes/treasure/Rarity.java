package com.gmail.nossr50.datatypes.treasure;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loot-rarity tier for Fishing treasures (and, once ported, Shake). Ordered most-rare first, matching
 * the order {@code FishingTreasureConfig} and the drop-rate tables iterate.
 *
 * <p><b>Port note:</b> byte-identical to legacy except the {@code Records}→{@code MYTHIC} alias
 * warning now goes through this class's SLF4J logger instead of {@code mcMMO.p.getLogger()} (the
 * plugin singleton is gone in the singleplayer port).
 */
public enum Rarity {
    MYTHIC,
    LEGENDARY,
    EPIC,
    RARE,
    UNCOMMON,
    COMMON;

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/Rarity");

    /**
     * Resolve a rarity by name, tolerant of legacy configs: the pre-rename {@code Records} maps to
     * {@link #MYTHIC}, and any unrecognised value falls back to {@link #COMMON} (as upstream does).
     */
    public static @NotNull Rarity getRarity(@NotNull String string) {
        if (string.equalsIgnoreCase("Records")) {
            LOGGER.error("A fishing treasure has Records set as its rarity, but Records was renamed to"
                    + " Mythic. Please update your treasures to read MYTHIC instead of RECORDS, or"
                    + " delete the config file to regenerate a new one.");
            return Rarity.MYTHIC; // Copy-pasted old configs read Records as Mythic.
        }
        try {
            return valueOf(string);
        } catch (IllegalArgumentException ex) {
            return COMMON;
        }
    }
}
