package com.gmail.nossr50.platform;

import com.gmail.nossr50.fabric.McMMOMod;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter that replaces {@code org.bukkit.Material} lookups with vanilla registry lookups.
 *
 * <p>mcMMO used the {@code Material} enum (90 distinct constants, ~292 {@code getType()} call
 * sites) as its universal item/block identity. Bukkit's {@code Material} names are UPPER_SNAKE
 * and, in modern Minecraft, map 1:1 to vanilla registry paths (e.g. {@code DIAMOND_PICKAXE} ->
 * {@code minecraft:diamond_pickaxe}). This resolver turns a Bukkit-style name (or a namespaced
 * id string, as {@code Material.matchMaterial} accepted) into an {@link Item} or {@link Block}.
 *
 * <p>CONVERSION_TODO.md Phase 2: "Map org.bukkit.Material enum -> net.minecraft Item/Block
 * registries (registry lookups, not enum switches)." Ported code should hold {@link Item}/
 * {@link Block}/{@link Identifier} instead of the enum, resolving once at load rather than
 * per-call where possible.
 *
 * <p>Registries are only populated after Minecraft's bootstrap, so these methods must be called
 * at/after server start, never during static init of a config that loads at mod-load time.
 */
public final class Materials {

    private Materials() {}

    /**
     * Pre-1.13 Bukkit {@code Material} names that still ship in mcMMO's own YAML, mapped to their
     * modern registry paths.
     *
     * <p><b>A deliberate fix, not a transcription (CONVERSION_TODO §F, upstream defect #16.)</b>
     * Bukkit renamed these in the 1.13 "flattening"; a name that is no longer a {@code Material}
     * constant resolves to nothing upstream either, so the shipped entry silently does nothing —
     * the same shape as the stale {@code Shake} section names in {@code fishing_treasures.yml}
     * (defect #10). Aliasing here makes the shipped config mean what it says.
     *
     * <p>Kept deliberately minimal: only names the bundled configs actually contain go in, so this
     * never becomes a speculative port of Bukkit's whole legacy-material table. Today that is one
     * entry — {@code potions.yml}'s {@code WATER_LILY} ingredient, which boot logged as unresolvable.
     */
    private static final Map<String, String> LEGACY_NAME_ALIASES = Map.of(
            "water_lily", "lily_pad");

    /**
     * Normalize a Bukkit {@code Material} name (or an already-namespaced id) to an
     * {@link Identifier}. Unqualified names resolve to the {@code minecraft} namespace, and the
     * handful of pre-1.13 names still present in the shipped configs are aliased to their modern
     * registry paths first (see {@link #LEGACY_NAME_ALIASES}).
     *
     * @return the identifier, or {@code null} if the string is not a valid identifier
     */
    public static @Nullable Identifier idOf(@NotNull String name) {
        final String trimmed = name.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return null;
        }
        // Alias only unqualified names: an explicitly namespaced id is the caller being specific,
        // and another mod's "foo:water_lily" is not ours to rewrite.
        if (trimmed.indexOf(':') >= 0) {
            return Identifier.tryParse(trimmed);
        }
        return Identifier.ofVanilla(LEGACY_NAME_ALIASES.getOrDefault(trimmed, trimmed));
    }

    /** Resolve an item by Bukkit-style name / namespaced id, empty if unknown. */
    public static @NotNull Optional<Item> item(@NotNull String name) {
        final Identifier id = idOf(name);
        if (id == null || !Registries.ITEM.containsId(id)) {
            McMMOMod.LOGGER.warn("No vanilla item for material name '{}'", name);
            return Optional.empty();
        }
        return Optional.of(Registries.ITEM.get(id));
    }

    /** Resolve a block by Bukkit-style name / namespaced id, empty if unknown. */
    public static @NotNull Optional<Block> block(@NotNull String name) {
        final Identifier id = idOf(name);
        if (id == null || !Registries.BLOCK.containsId(id)) {
            McMMOMod.LOGGER.warn("No vanilla block for material name '{}'", name);
            return Optional.empty();
        }
        return Optional.of(Registries.BLOCK.get(id));
    }

    /** Whether a vanilla item exists for the given name. Does not log on miss. */
    public static boolean isItem(@NotNull String name) {
        final Identifier id = idOf(name);
        return id != null && Registries.ITEM.containsId(id);
    }

    /** Whether a vanilla block exists for the given name. Does not log on miss. */
    public static boolean isBlock(@NotNull String name) {
        final Identifier id = idOf(name);
        return id != null && Registries.BLOCK.containsId(id);
    }
}
