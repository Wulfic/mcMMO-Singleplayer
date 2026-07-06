package com.gmail.nossr50.util.text;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for String operations, including formatting and caching deterministic results to
 * improve performance.
 *
 * <p><b>Port note (singleplayer):</b> the legacy class carried typed convenience overloads keyed on
 * Bukkit {@code Material}, {@code EntityType}, and mcMMO's {@code SuperAbilityType}. Those are gone
 * here — {@code Material}/{@code EntityType} no longer exist in the Fabric port (we key on vanilla
 * registry path strings instead) and {@code SuperAbilityType} is not ported yet. Callers pass the
 * raw {@link String} form (registry path or enum name) and get the same cached pretty/config output
 * the enum overloads used to produce. The typed overloads will be re-added retargeted to
 * {@code platform/} when Phase 10 consumers need them.
 */
public final class StringUtils {

    private StringUtils() {}

    private static final DecimalFormat shortDecimal = new DecimalFormat("##0.0",
            DecimalFormatSymbols.getInstance(Locale.US));

    // Cache the deterministic pretty-string results. Keyed by the raw source string (registry path
    // or enum name) rather than the old Bukkit enum types.
    private static final Map<String, String> formattedStrings = new ConcurrentHashMap<>();

    /**
     * Gets a capitalized version of the target string (first char upper, remainder lower).
     *
     * @param target String to capitalize
     * @return the capitalized string
     */
    public static String getCapitalized(String target) {
        if (target == null || target.isEmpty()) {
            return target;
        }
        return target.substring(0, 1).toUpperCase(Locale.ENGLISH) + target.substring(1)
                .toLowerCase(Locale.ENGLISH);
    }

    /**
     * Converts ticks to seconds, formatted to one decimal place.
     *
     * @param ticks Number of ticks
     * @return String representation of seconds
     */
    public static String ticksToSeconds(double ticks) {
        return shortDecimal.format(ticks / 20);
    }

    /**
     * Creates a string from an array skipping the first n elements.
     *
     * @param args The array to iterate over when forming the string
     * @param index The number of elements to skip over
     * @return The "trimmed" string
     */
    public static String buildStringAfterNthElement(@NotNull String @NotNull [] args, int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Index must be greater than or equal to 0");
        }

        final StringBuilder trimMessage = new StringBuilder();

        for (int i = index; i < args.length; i++) {
            if (i > index) {
                trimMessage.append(' ');
            }
            trimMessage.append(args[i]);
        }

        return trimMessage.toString();
    }

    /**
     * Gets a pretty (human-facing) string from a raw source string, splitting on underscores or
     * spaces and capitalizing each word. Results are cached.
     *
     * @param source raw string (e.g. a registry path {@code "diamond_ore"} or enum name
     *     {@code "TREE_FELLER"})
     * @return pretty string (e.g. {@code "Diamond Ore"} / {@code "Tree Feller"})
     */
    public static String getPrettyString(String source) {
        return formattedStrings.computeIfAbsent(source, StringUtils::createPrettyString);
    }

    private static String createPrettyString(String baseString) {
        return PRETTY_STRING_FUNC.apply(baseString);
    }

    private static final Function<String, String> PRETTY_STRING_FUNC = baseString -> {
        if (baseString.contains("_") && !baseString.contains(" ")) {
            return prettify(baseString.split("_"));
        } else {
            if (baseString.contains(" ")) {
                return prettify(baseString.split(" "));
            } else {
                return getCapitalized(baseString);
            }
        }
    };

    private static @NotNull String prettify(String[] substrings) {
        final StringBuilder prettyString = new StringBuilder();

        for (int i = 0; i < substrings.length; i++) {
            prettyString.append(getCapitalized(substrings[i]));
            if (i < substrings.length - 1) {
                prettyString.append(' ');
            }
        }

        return prettyString.toString();
    }

    /**
     * Determine if a string represents an Integer.
     *
     * @param string String to check
     * @return true if the string is an Integer, false otherwise
     */
    public static boolean isInt(String string) {
        try {
            Integer.parseInt(string);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /**
     * Determine if a string represents a Double.
     *
     * @param string String to check
     * @return true if the string is a Double, false otherwise
     */
    public static boolean isDouble(String string) {
        try {
            Double.parseDouble(string);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
