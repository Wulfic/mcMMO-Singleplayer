package com.gmail.nossr50.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Potion helpers for the Alchemy config, retargeted from Bukkit's {@code PotionType}/
 * {@code PotionEffectType} onto the vanilla {@code Registries.POTION} / {@code Registries.STATUS_EFFECT}
 * static registries (both populated by {@code Bootstrap.initialize()}, so this stays unit-testable
 * under the {@code fabric-loader-junit} harness — unlike the dynamic enchantment registry).
 *
 * <p>In modern Minecraft "extended" and "upgraded" are not flags but distinct registry entries with
 * {@code long_} / {@code strong_} id prefixes (e.g. {@code minecraft:long_swiftness},
 * {@code minecraft:strong_leaping}). {@link #matchPotion} resolves the config's potion-type string —
 * translating the legacy Bukkit names mcMMO's shipped {@code potions.yml} still uses — into the
 * prefixed registry id, doing an <em>exact</em> id lookup (deterministic, and falling back to the
 * unprefixed base when a variant does not exist, matching legacy {@code resolveVariant}).
 */
public final class PotionUtil {

    /** Legacy Bukkit {@code PotionType} names that were renamed in modern Minecraft. */
    private static final Map<String, String> LEGACY_POTION_TYPES = new HashMap<>();
    /** Legacy Bukkit {@code PotionEffectType} names that were renamed in modern Minecraft. */
    private static final Map<String, String> LEGACY_EFFECT_TYPES = new HashMap<>();

    private static final String STRONG_PREFIX = "strong_";
    private static final String LONG_PREFIX = "long_";

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

    private PotionUtil() {
    }

    /**
     * Resolve a config potion-type string into its (possibly {@code long_}/{@code strong_}-prefixed)
     * registry entry.
     *
     * @param partialName the {@code PotionType} string from the config (may be a legacy Bukkit name)
     * @param upgraded    whether the config marks this potion Upgraded (amplified, {@code strong_})
     * @param extended    whether the config marks this potion Extended ({@code long_})
     * @return the potion registry entry, or {@code null} if it cannot be resolved
     */
    public static @Nullable RegistryEntry<Potion> matchPotion(@Nullable String partialName,
            boolean upgraded, boolean extended) {
        if (partialName == null || partialName.isEmpty()) {
            return null;
        }

        final String base = convertLegacyPotionName(partialName).toLowerCase(Locale.ENGLISH);
        String prefixed = base;
        if (upgraded) {
            prefixed = STRONG_PREFIX + base;
        } else if (extended) {
            prefixed = LONG_PREFIX + base;
        }

        RegistryEntry<Potion> entry = lookupPotion(prefixed);
        // Not every potion has a strong/long variant; fall back to the base like legacy resolveVariant.
        if (entry == null && !prefixed.equals(base)) {
            entry = lookupPotion(base);
        }
        return entry;
    }

    private static @Nullable RegistryEntry<Potion> lookupPotion(@NotNull String path) {
        final Identifier id = Identifier.ofVanilla(path);
        return Registries.POTION.getEntry(id).orElse(null);
    }

    /**
     * Resolve a config effect string into its status-effect registry entry, translating legacy
     * Bukkit effect names.
     *
     * @param effectName the effect token from the config (e.g. {@code "SLOW_DIGGING"})
     * @return the status-effect entry, or {@code null} if unknown
     */
    public static @Nullable RegistryEntry<StatusEffect> matchEffect(@Nullable String effectName) {
        if (effectName == null || effectName.isEmpty()) {
            return null;
        }
        final String modern = convertLegacyEffectName(effectName).toLowerCase(Locale.ENGLISH);
        return Registries.STATUS_EFFECT.getEntry(Identifier.ofVanilla(modern)).orElse(null);
    }

    private static @NotNull String convertLegacyPotionName(@NotNull String name) {
        return LEGACY_POTION_TYPES.getOrDefault(name.toLowerCase(Locale.ENGLISH), name);
    }

    private static @NotNull String convertLegacyEffectName(@NotNull String name) {
        return LEGACY_EFFECT_TYPES.getOrDefault(name.toLowerCase(Locale.ENGLISH), name);
    }

    /** The registry path of a potion entry (e.g. {@code "long_swiftness"}). */
    public static @NotNull String pathOf(@NotNull RegistryEntry<Potion> entry) {
        return Registries.POTION.getId(entry.value()).getPath();
    }

    /** Whether the potion is an amplified ({@code strong_}) variant. */
    public static boolean isStrong(@NotNull RegistryEntry<Potion> entry) {
        return pathOf(entry).startsWith(STRONG_PREFIX);
    }

    /** Whether the potion is an extended ({@code long_}) variant. */
    public static boolean isLong(@NotNull RegistryEntry<Potion> entry) {
        return pathOf(entry).startsWith(LONG_PREFIX);
    }

    /** Whether the potion is the plain water potion (Alchemy's stage-1 base). */
    public static boolean isWater(@NotNull RegistryEntry<Potion> entry) {
        return pathOf(entry).equals("water");
    }

    /** Whether the potion's base type carries any status effects of its own. */
    public static boolean hasBaseEffects(@NotNull RegistryEntry<Potion> entry) {
        return !entry.value().getEffects().isEmpty();
    }
}
