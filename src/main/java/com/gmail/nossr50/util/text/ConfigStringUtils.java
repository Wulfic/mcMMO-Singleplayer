package com.gmail.nossr50.util.text;

import static com.gmail.nossr50.util.text.StringUtils.getCapitalized;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Produces the config-friendly key form mcMMO uses to address per-material / per-entity settings
 * inside its YAML configs (e.g. {@code Experience_Values.Mining.Diamond_Ore}).
 *
 * <p><b>Port note (singleplayer):</b> the legacy class took Bukkit {@code Material}/{@code
 * EntityType} enums (whose {@code name()} was UPPER_SNAKE, e.g. {@code DIAMOND_ORE}). In the Fabric
 * port we key on vanilla registry path strings instead (lower_snake, e.g. {@code diamond_ore}). The
 * formatter is identical either way — split on {@code _}/space, capitalize each word, re-join with
 * {@code _} — so a registry path {@code "diamond_ore"} yields the same {@code "Diamond_Ore"} key the
 * old {@code Material.DIAMOND_ORE} did. The party-feature overload is dropped (party cut). Results
 * are cached.
 */
public final class ConfigStringUtils {

    private ConfigStringUtils() {}

    public static final String UNDERSCORE = "_";
    public static final String SPACE = " ";

    private static final Map<String, String> configStrings = new ConcurrentHashMap<>();

    /**
     * Config key for a material, addressed by its vanilla registry path (e.g. {@code "diamond_ore"}
     * -> {@code "Diamond_Ore"}). A fully-qualified {@code minecraft:diamond_ore} has its namespace
     * stripped first so the key matches the legacy unqualified form.
     */
    public static String getMaterialConfigString(@NotNull String materialRegistryPath) {
        return configStrings.computeIfAbsent(stripNamespace(materialRegistryPath),
                ConfigStringUtils::createConfigFriendlyString);
    }

    /**
     * Config key for an entity type, addressed by its vanilla registry path (e.g. {@code "wolf"} ->
     * {@code "Wolf"}, {@code "zombie_villager"} -> {@code "Zombie_Villager"}).
     */
    public static String getConfigEntityTypeString(@NotNull String entityRegistryPath) {
        return configStrings.computeIfAbsent(stripNamespace(entityRegistryPath),
                ConfigStringUtils::createConfigFriendlyString);
    }

    private static String stripNamespace(@NotNull String registryId) {
        final int colon = registryId.indexOf(':');
        return colon >= 0 ? registryId.substring(colon + 1) : registryId;
    }

    private static String createConfigFriendlyString(String baseString) {
        return CONFIG_FRIENDLY_STRING_FORMATTER.apply(baseString);
    }

    private static final Function<String, String> CONFIG_FRIENDLY_STRING_FORMATTER = baseString -> {
        if (baseString.contains(UNDERSCORE) && !baseString.contains(SPACE)) {
            return asConfigFormat(baseString.split(UNDERSCORE));
        } else {
            if (baseString.contains(SPACE)) {
                return asConfigFormat(baseString.split(SPACE));
            } else {
                return getCapitalized(baseString);
            }
        }
    };

    private static @NotNull String asConfigFormat(String[] substrings) {
        final StringBuilder configString = new StringBuilder();

        for (int i = 0; i < substrings.length; i++) {
            configString.append(getCapitalized(substrings[i]));
            if (i < substrings.length - 1) {
                configString.append(UNDERSCORE);
            }
        }

        return configString.toString();
    }
}
