package com.gmail.nossr50.locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * MC-free coverage of the English-only {@link LocaleLoader} string + colour pipeline.
 * The {@code Text}-producing side ({@link LocaleLoader#getText}) is exercised via
 * {@code TextUtils} in its own test.
 */
class LocaleLoaderTest {

    @Test
    void resolvesKnownKeyFromBundle() {
        // "JSON.Rank=Rank" is a stable, colour-free entry in locale_en_US.properties.
        assertEquals("Rank", LocaleLoader.getString("JSON.Rank"));
    }

    @Test
    void missingKeyIsReturnedBangWrapped() {
        assertEquals("!This.Key.Does.Not.Exist!",
                LocaleLoader.getString("This.Key.Does.Not.Exist"));
    }

    @Test
    void ampersandCodesBecomeSectionCodes() {
        assertEquals("§aGreen §lBold", LocaleLoader.addColors("&aGreen &lBold"));
    }

    @Test
    void doubleBracketTokensBecomeSectionCodes() {
        assertEquals("§6Gold§r", LocaleLoader.addColors("[[GOLD]]Gold[[RESET]]"));
    }

    @Test
    void strayAmpersandSurvives() {
        // Only recognised codes are translated; "Tom & Jerry" must stay intact.
        assertEquals("Tom & Jerry", LocaleLoader.addColors("Tom & Jerry"));
    }

    @Test
    void hexCodesBecomeSectionHexForm() {
        assertEquals("§x§F§F§0§0§0§0Red", LocaleLoader.addColors("&#FF0000Red"));
    }

    @Test
    void messageFormatArgumentsAreSubstituted() {
        // Deliberately colour-free so we only assert the substitution behaviour.
        assertEquals("You gained 5 levels",
                LocaleLoader.formatString("You gained {0} levels", 5));
    }

    @Test
    void nullArgumentsSkipFormattingButStillColorize() {
        assertTrue(LocaleLoader.formatString("&aHi", (Object[]) null).equals("§aHi"));
    }
}
