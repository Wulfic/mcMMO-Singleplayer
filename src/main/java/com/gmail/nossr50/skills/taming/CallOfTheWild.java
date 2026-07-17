package com.gmail.nossr50.skills.taming;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.datatypes.skills.subskills.taming.CallOfTheWildType;
import com.gmail.nossr50.datatypes.skills.subskills.taming.TamingSummon;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * The MC-free Call-of-the-Wild lookup tables, replacing legacy {@code TamingManager}'s two static
 * caches ({@code summoningItems} and {@code cotwSummonDataProperties}). It maps a held item's registry
 * path to the summon it triggers, and each {@link CallOfTheWildType} to its configured
 * {@link TamingSummon} properties.
 *
 * <p>Built once from {@link GeneralConfig} via {@link #fromConfig(GeneralConfig)} — the config's Bukkit
 * material names ({@code BONE}, {@code COD}, {@code APPLE}) are lower-cased to registry paths
 * ({@code bone}, {@code cod}, {@code apple}) so the MC-typed handler can match them against a held
 * stack's id without this class touching a server type. Kept separate from the per-player
 * {@code TamingManager} because the tables are global and config-derived, so they are constructed once
 * and unit-tested in isolation.
 */
public final class CallOfTheWild {

    private final @NotNull Map<CallOfTheWildType, TamingSummon> properties;
    private final @NotNull Map<String, TamingSummon> byItemId;

    public CallOfTheWild(@NotNull Map<CallOfTheWildType, TamingSummon> properties) {
        this.properties = new EnumMap<>(properties);
        this.byItemId = new HashMap<>();
        for (TamingSummon summon : properties.values()) {
            byItemId.put(normalizeItemId(summon.getItemId()), summon);
        }
    }

    /**
     * Build the tables from config. Reads {@code Item_Material} / {@code Item_Amount} /
     * {@code Summon_Amount} / {@code Summon_Length} / {@code Per_Player_Limit} for each summon type,
     * keyed by that type's {@link CallOfTheWildType#getConfigEntityTypeEntry() config section}.
     */
    public static @NotNull CallOfTheWild fromConfig(@NotNull GeneralConfig config) {
        final Map<CallOfTheWildType, TamingSummon> properties = new EnumMap<>(CallOfTheWildType.class);
        for (CallOfTheWildType type : CallOfTheWildType.values()) {
            final String entry = type.getConfigEntityTypeEntry();
            final String itemId = normalizeItemId(config.getTamingCOTWMaterialName(entry));
            properties.put(type, new TamingSummon(type, itemId,
                    config.getTamingCOTWCost(entry),
                    config.getTamingCOTWAmount(entry),
                    config.getTamingCOTWLength(entry),
                    config.getTamingCOTWMaxAmount(entry)));
        }
        return new CallOfTheWild(properties);
    }

    /** The summon a held item triggers, or empty if the item is not a COTW item. */
    public @NotNull Optional<TamingSummon> summonForItem(@NotNull String itemId) {
        return Optional.ofNullable(byItemId.get(normalizeItemId(itemId)));
    }

    /** Whether the given item id is any COTW summoning item (legacy {@code isCOTWItem}). */
    public boolean isCOTWItem(@NotNull String itemId) {
        return byItemId.containsKey(normalizeItemId(itemId));
    }

    /** The configured properties for a summon type. */
    public @NotNull TamingSummon getSummon(@NotNull CallOfTheWildType type) {
        return properties.get(type);
    }

    private static @NotNull String normalizeItemId(String raw) {
        // Defensive against a stripped config key (getString → null): such a summon type simply never
        // matches a held item, rather than NPE-ing the whole config load.
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT);
    }
}
