package com.gmail.nossr50.util;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Minecraft-free half of mcMMO's potion naming: translating the legacy Bukkit
 * {@code PotionType}/{@code PotionEffectType} names the shipped {@code potions.yml} still uses into
 * modern registry paths, and reading the {@code strong_} / {@code long_} / {@code water} prefixes
 * back off one.
 *
 * <p>In modern Minecraft "extended" and "upgraded" are not flags but distinct registry entries with
 * {@code long_} / {@code strong_} id prefixes (e.g. {@code minecraft:long_swiftness},
 * {@code minecraft:strong_leaping}), so resolving a config potion is a prefix-then-fall-back name
 * search — {@link #variantPaths} produces exactly the candidate list, in order, and the registry
 * lookup that consumes it lives in {@code platform/Potions}.
 *
 * <p>Split out of the old {@code util/PotionUtil} in Phase 2 slice 5: every method here is string
 * work with no Minecraft type in sight, which is why it stays in {@code util/}.
 */
public final class PotionNames {

    /** Legacy Bukkit {@code PotionType} names that were renamed in modern Minecraft. */
    private static final Map<String, String> LEGACY_POTION_TYPES = new HashMap<>();
    /** Legacy Bukkit {@code PotionEffectType} names that were renamed in modern Minecraft. */
    private static final Map<String, String> LEGACY_EFFECT_TYPES = new HashMap<>();

    private static final String STRONG_PREFIX = "strong_";
    private static final String LONG_PREFIX = "long_";
    private static final String WATER = "water";

    static {
        // Uncraftable no longer exists; Mundane is the modern no-op stand-in (legacy PotionUtil).
        LEGACY_POTION_TYPES.put("uncraftable", "mundane");
        LEGACY_POTION_TYPES.put("jump", "leaping");
        LEGACY_POTION_TYPES.put("speed", "swiftness");
        LEGACY_POTION_TYPES.put("instant_heal", "healing");
        LEGACY_POTION_TYPES.put("instant_damage", "harming");
        LEGACY_POTION_TYPES.put("regen", "regeneration");

        // Bukkit PotionEffectType.getByName aliases that differ from the registry id.
        LEGACY_EFFECT_TYPES.put("confusion", "nausea");
        LEGACY_EFFECT_TYPES.put("damage_resistance", "resistance");
        LEGACY_EFFECT_TYPES.put("fast_digging", "haste");
        LEGACY_EFFECT_TYPES.put("slow_digging", "mining_fatigue");
    }

    private PotionNames() {
    }

    /**
     * The registry paths to try, in order, for a config potion type. The first is the
     * variant-prefixed path when the config marks the potion Upgraded/Extended; the second (present
     * only when a prefix was applied) is the unprefixed base, because not every potion has a
     * {@code strong_}/{@code long_} variant — the fall-back the legacy {@code resolveVariant} did.
     *
     * @param partialName the {@code PotionType} string from the config (may be a legacy Bukkit name)
     * @param upgraded    whether the config marks this potion Upgraded (amplified, {@code strong_})
     * @param extended    whether the config marks this potion Extended ({@code long_})
     * @return one or two candidate paths, most specific first; empty if the name is blank
     */
    public static @NotNull List<String> variantPaths(@Nullable String partialName, boolean upgraded,
            boolean extended) {
        if (partialName == null || partialName.isEmpty()) {
            return List.of();
        }
        final String base = convertLegacyPotionName(partialName);
        if (upgraded) {
            return List.of(STRONG_PREFIX + base, base);
        }
        if (extended) {
            return List.of(LONG_PREFIX + base, base);
        }
        return List.of(base);
    }

    /** A legacy Bukkit potion-type name mapped to its modern registry path, lower-cased. */
    public static @NotNull String convertLegacyPotionName(@NotNull String name) {
        final String lower = name.toLowerCase(Locale.ENGLISH);
        return LEGACY_POTION_TYPES.getOrDefault(lower, lower);
    }

    /** A legacy Bukkit effect name mapped to its modern registry path, lower-cased. */
    public static @NotNull String convertLegacyEffectName(@NotNull String name) {
        final String lower = name.toLowerCase(Locale.ENGLISH);
        return LEGACY_EFFECT_TYPES.getOrDefault(lower, lower);
    }

    /** Whether the potion path is an amplified ({@code strong_}) variant. */
    public static boolean isStrong(@Nullable String potionPath) {
        return potionPath != null && potionPath.startsWith(STRONG_PREFIX);
    }

    /** Whether the potion path is an extended ({@code long_}) variant. */
    public static boolean isLong(@Nullable String potionPath) {
        return potionPath != null && potionPath.startsWith(LONG_PREFIX);
    }

    /** Whether the potion path is the plain water potion (Alchemy's stage-1 base). */
    public static boolean isWater(@Nullable String potionPath) {
        return WATER.equals(potionPath);
    }
}
