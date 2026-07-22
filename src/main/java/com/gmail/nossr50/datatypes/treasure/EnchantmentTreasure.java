package com.gmail.nossr50.datatypes.treasure;

import org.jetbrains.annotations.NotNull;

/**
 * One entry of the Fishing <b>Magic Hunter</b> enchantment table: an enchantment and the level Magic
 * Hunter grants it at. Consumed by {@code FishingManager#selectMagicHunterEnchants} and written onto a
 * caught treasure by {@code fabric.listeners.FishingListener}.
 *
 * <p><b>Port note:</b> the legacy field was a live {@code org.bukkit.enchantments.Enchantment},
 * resolved at config-load time through {@code EnchantmentUtils.getByName}. In 1.21 enchantments live in
 * a <b>dynamic</b> (datapack-driven) registry that does not exist when configs load, so this port keeps
 * the enchantment's <b>vanilla registry path</b> ({@code "bane_of_arthropods"}) and resolves it at drop
 * time — the same resolve-at-use shape {@link com.gmail.nossr50.config.treasure.FishingTreasureConfig}
 * uses for Shake's entity paths and {@link com.gmail.nossr50.config.treasure.TreasureConfig} uses for
 * Hylian groups, and it keeps this datatype MC-free.
 *
 * <p>Unlike its siblings this is not a {@link Treasure}: an enchantment is not an item drop, so it
 * carries no {@link ItemSpec}, XP, drop chance or drop level (Magic Hunter's odds come from the
 * {@code Enchantment_Drop_Rates} tier curve, not from the entry).
 *
 * @param enchantmentId the enchantment's vanilla registry path, lower-case (e.g. {@code "sharpness"})
 * @param level the level to apply, unclamped by vanilla's maximum (legacy applied these with
 *     {@code addUnsafeEnchantments})
 */
public record EnchantmentTreasure(@NotNull String enchantmentId, int level) {
}
