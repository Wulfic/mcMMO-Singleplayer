package com.gmail.nossr50.datatypes.treasure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link FishingTreasureBook}'s whitelist/blacklist precedence — the MC-free half of the
 * enchanted-book fishing reward (legacy {@code FishingTreasureBook#isEnchantAllowed} plus the
 * load-time name resolution that fed it). No Minecraft: the caller supplies the registry's paths.
 */
class FishingTreasureBookTest {

    /** Stand-in for a world's enchantment registry. */
    private static final Set<String> REGISTRY = Set.of("efficiency", "silk_touch", "fortune",
            "unbreaking", "vanishing_curse");

    private static FishingTreasureBook book(List<String> blacklist, List<String> whitelist) {
        return new FishingTreasureBook(new ItemSpec("enchanted_book", 1), 400, blacklist, whitelist);
    }

    @Test
    void withNeitherListEveryEnchantmentIsAllowed() {
        // The shipped configuration: both lists ship commented out.
        assertEquals(REGISTRY,
                book(List.of(), List.of()).resolveAllowedEnchantmentIds(REGISTRY));
    }

    @Test
    void aWhitelistExcludesEverythingElse() {
        assertEquals(Set.of("fortune", "silk_touch"),
                book(List.of(), List.of("Fortune", "Silk_Touch"))
                        .resolveAllowedEnchantmentIds(REGISTRY));
    }

    @Test
    void aWhitelistBeatsABlacklist() {
        // Legacy's precedence: a non-empty whitelist returns before the blacklist is consulted, so a
        // name in both lists is allowed.
        assertEquals(Set.of("fortune"),
                book(List.of("fortune"), List.of("fortune")).resolveAllowedEnchantmentIds(REGISTRY));
    }

    @Test
    void aBlacklistRemovesOnlyItsOwnEntries() {
        final Set<String> allowed = book(List.of("Vanishing_Curse"), List.of())
                .resolveAllowedEnchantmentIds(REGISTRY);

        assertFalse(allowed.contains("vanishing_curse"));
        assertEquals(REGISTRY.size() - 1, allowed.size());
    }

    @Test
    void configNamesAreMatchedCaseInsensitively() {
        // The shipped example uses title case ("Fortune"); the registry path is lower-case.
        assertEquals(Set.of("fortune"),
                book(List.of(), List.of("  FoRtUnE  ")).resolveAllowedEnchantmentIds(REGISTRY));
    }

    @Test
    void anAllTypoWhitelistDegradesToAllowingEverything() {
        // Upstream drops unmatched names at config load, so a whitelist of nothing but typos becomes
        // an EMPTY whitelist and the book allows everything rather than nothing. This port cannot
        // resolve at load (dynamic registry), so it intersects with the live paths first to keep that
        // behaviour — the alternative would be a book that silently never enchants.
        assertEquals(REGISTRY,
                book(List.of(), List.of("Fortunee", "Not_An_Enchantment"))
                        .resolveAllowedEnchantmentIds(REGISTRY));
    }

    @Test
    void aPartlyTypoedWhitelistKeepsOnlyItsKnownNames() {
        assertEquals(Set.of("fortune"),
                book(List.of(), List.of("Fortune", "Not_An_Enchantment"))
                        .resolveAllowedEnchantmentIds(REGISTRY));
    }

    @Test
    void blacklistingTheWholeRegistryAllowsNothing() {
        // The one case that reaches the listener's empty-pool guard (upstream throws here).
        assertTrue(book(List.copyOf(REGISTRY), List.of())
                .resolveAllowedEnchantmentIds(REGISTRY).isEmpty());
    }

    @Test
    void theBookCarriesItsTreasureXpAndASingleBook() {
        final FishingTreasureBook book = book(List.of(), List.of());

        assertEquals(400, book.getXp());
        assertEquals("enchanted_book", book.getDrop().getMaterialId());
        assertEquals(1, book.getDrop().getAmount());
    }
}
