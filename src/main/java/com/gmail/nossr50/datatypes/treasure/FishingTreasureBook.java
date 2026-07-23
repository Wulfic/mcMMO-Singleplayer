package com.gmail.nossr50.datatypes.treasure;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * An <b>enchanted-book</b> Treasure-Hunter fishing reward: a book that arrives carrying one random
 * enchantment, drawn from the whole enchantment registry under this book's optional
 * {@code Enchantments_Whitelist} / {@code Enchantments_Blacklist}.
 *
 * <p><b>This is a different mechanism from Magic Hunter</b> and the two never both apply. Magic Hunter
 * enchants an ordinary caught treasure from the curated {@code Enchantments_Rarity} table (see
 * {@link EnchantmentTreasure}); a book ignores that table entirely, always gets exactly one
 * enchantment, and takes it from every enchantment the world knows about. Legacy encodes that by
 * branching on {@code treasure instanceof FishingTreasureBook} in {@code processFishing} and skipping
 * its {@code processMagicHunter} call; this port keeps the same branch in
 * {@code fabric.listeners.FishingListener}.
 *
 * <p><b>Port note — the legal-enchantment list is resolved at drop time, not here.</b> Legacy built a
 * flat {@code List<EnchantmentWrapper> legalEnchantments} in its constructor by walking
 * {@code Enchantment.values()} and expanding each allowed enchantment into one entry per level
 * ({@code 1..getMaxLevel()}). It cannot be built at config-load in 1.21, where the enchantment registry
 * is <b>dynamic</b> (datapack-driven) and does not exist yet — the same constraint that shaped
 * {@link EnchantmentTreasure}. So this type keeps only the parsed intent (two sets of registry-path
 * strings) and exposes {@link #resolveAllowedEnchantmentIds(Collection)}, which the listener calls with
 * the live registry's paths. The level expansion stays on the listener, since only Minecraft knows an
 * enchantment's maximum level.
 *
 * <p><b>That expansion is load-bearing for the odds, and is preserved:</b> because each enchantment
 * contributes one pool entry <i>per level</i> and the pick is uniform over the pool, an enchantment
 * with a high maximum level is proportionally likelier to be rolled — a fished book is five times more
 * likely to be some level of Efficiency than to be Silk Touch, and when it is Efficiency every level
 * from I to V is equally likely. That is upstream's behaviour, not an accident of this port.
 */
public class FishingTreasureBook extends FishingTreasure {

    private final @NotNull Set<String> blacklistedEnchantmentIds;
    private final @NotNull Set<String> whitelistedEnchantmentIds;

    /**
     * @param drop the book blueprint (always a single {@code enchanted_book}; see
     *     {@link com.gmail.nossr50.config.treasure.FishingTreasureConfig} for why the configured
     *     {@code Amount} and {@code Lore} are ignored for books)
     * @param xp the Treasure-Hunter bonus XP this book pays
     * @param blacklistedEnchantmentIds enchantment registry paths this book may <b>not</b> roll
     * @param whitelistedEnchantmentIds enchantment registry paths this book may roll, to the exclusion
     *     of all others; takes precedence over the blacklist when it resolves to anything
     */
    public FishingTreasureBook(@NotNull ItemSpec drop, int xp,
            @NotNull Collection<String> blacklistedEnchantmentIds,
            @NotNull Collection<String> whitelistedEnchantmentIds) {
        super(drop, xp);
        this.blacklistedEnchantmentIds = normalise(blacklistedEnchantmentIds);
        this.whitelistedEnchantmentIds = normalise(whitelistedEnchantmentIds);
    }

    /**
     * Config names are matched case-insensitively by legacy ({@code equalsIgnoreCase} against the
     * enchantment's key), and the shipped example uses title case ({@code Fortune}), so entries are
     * lower-cased into registry-path form here. Blank entries are dropped.
     */
    private static @NotNull Set<String> normalise(@NotNull Collection<String> enchantmentIds) {
        final Set<String> normalised = new LinkedHashSet<>();
        for (String enchantmentId : enchantmentIds) {
            if (enchantmentId == null || enchantmentId.isBlank()) {
                continue;
            }
            normalised.add(enchantmentId.trim().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalised);
    }

    /** The configured whitelist, as lower-case registry paths (empty when none is configured). */
    public @NotNull Set<String> getWhitelistedEnchantmentIds() {
        return whitelistedEnchantmentIds;
    }

    /** The configured blacklist, as lower-case registry paths (empty when none is configured). */
    public @NotNull Set<String> getBlacklistedEnchantmentIds() {
        return blacklistedEnchantmentIds;
    }

    /**
     * The enchantments this book may roll, given every enchantment the world's registry actually
     * holds. Ports legacy {@code FishingTreasureBook#isEnchantAllowed} plus the load-time filtering
     * that fed it, in one pass:
     *
     * <ol>
     *   <li>a whitelist that names at least one <i>known</i> enchantment wins outright — only those;
     *   <li>otherwise every known enchantment except the blacklisted ones;
     *   <li>a book with neither list allows everything (the shipped configuration — both lists ship
     *       commented out).
     * </ol>
     *
     * <p><b>The "known" filter reproduces a legacy behaviour that would otherwise be lost.</b> Legacy
     * resolved both lists against the registry at config load and silently dropped any name it could
     * not match, so a whitelist of nothing but typos collapsed to an <i>empty</i> whitelist and the
     * book degraded to allowing every enchantment rather than none. This port cannot resolve at load
     * (dynamic registry), so it applies the same drop-then-check here: intersecting with the live paths
     * first means an all-typo whitelist still falls through to the blacklist branch. Filtering the
     * blacklist would be a no-op — removing an unknown name removes nothing — so it is not done.
     *
     * @param registryEnchantmentIds every enchantment registry path in the world, e.g. {@code
     *     "efficiency"}
     * @return the allowed paths; empty only if the registry itself is empty, or a whitelist-free book
     *     blacklists literally everything
     */
    public @NotNull Set<String> resolveAllowedEnchantmentIds(
            @NotNull Collection<String> registryEnchantmentIds) {
        final Set<String> whitelisted = new LinkedHashSet<>(registryEnchantmentIds);
        whitelisted.retainAll(whitelistedEnchantmentIds);
        if (!whitelisted.isEmpty()) {
            return whitelisted;
        }

        final Set<String> allowed = new LinkedHashSet<>(registryEnchantmentIds);
        allowed.removeAll(blacklistedEnchantmentIds);
        return allowed;
    }

    @Override
    public String toString() {
        return "FishingTreasureBook{" + super.toString()
                + ", whitelist=" + whitelistedEnchantmentIds
                + ", blacklist=" + blacklistedEnchantmentIds + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!super.equals(o)) {
            return false; // Treasure#equals also enforces an exact class match.
        }
        final FishingTreasureBook other = (FishingTreasureBook) o;
        return whitelistedEnchantmentIds.equals(other.whitelistedEnchantmentIds)
                && blacklistedEnchantmentIds.equals(other.blacklistedEnchantmentIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), whitelistedEnchantmentIds, blacklistedEnchantmentIds);
    }
}
