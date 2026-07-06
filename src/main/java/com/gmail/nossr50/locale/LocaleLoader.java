package com.gmail.nossr50.locale;

import com.gmail.nossr50.util.text.TextUtils;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads mcMMO's user-facing strings.
 *
 * <p><b>Singleplayer port:</b> the multi-language machinery of the Bukkit plugin is gone
 * (CONVERSION_TODO Phase 8). This is <b>English only</b>: a single {@code locale_en_US}
 * {@link ResourceBundle} is the one source of strings — no per-server locale selection, no
 * filesystem {@code locale_override.properties}, no Folia concurrency hooks, and no
 * {@code /mcreloadlocale} rewrite-on-disk behaviour.
 *
 * <p><b>Colour pipeline:</b> raw strings still carry legacy colour markup — simplified codes
 * ({@code &a}), mcMMO's own {@code [[COLOR]]} tokens, and {@code &#RRGGBB} hex. {@link #addColors}
 * normalises all of these to section-sign ({@code §}) codes. {@link #getString} returns that
 * {@code §} string (useful for logs/legacy call sites); {@link #getText} parses it into a vanilla
 * {@link Text} via {@link TextUtils} for anything shown to the player.
 */
public final class LocaleLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/Locale");
    private static final String BUNDLE_ROOT = "com.gmail.nossr50.locale.locale";

    /** Matches the {@code &#RRGGBB} hex colour form. */
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private static final Map<String, String> rawCache = new ConcurrentHashMap<>();
    private static ResourceBundle bundle;

    private LocaleLoader() {
    }

    public static @NotNull String getString(@NotNull String key) {
        return getString(key, (Object[]) null);
    }

    /**
     * Gets a formatted, colour-translated string from the locale bundle.
     *
     * @param key the locale key
     * @param messageArguments {@link MessageFormat} arguments, or {@code null} for none
     * @return the formatted string with legacy {@code §} colour codes applied
     */
    public static @NotNull String getString(@NotNull String key, Object... messageArguments) {
        if (bundle == null) {
            initialize();
        }
        final String rawMessage = rawCache.computeIfAbsent(key, LocaleLoader::getRawString);
        return formatString(rawMessage, messageArguments);
    }

    public static @NotNull Text getText(@NotNull String key) {
        return getText(key, (Object[]) null);
    }

    /**
     * Gets a locale string as a vanilla {@link Text}, ready to send to a player.
     *
     * @param key the locale key
     * @param messageArguments {@link MessageFormat} arguments, or {@code null} for none
     * @return the message as styled {@link Text}
     */
    public static @NotNull Text getText(@NotNull String key, Object... messageArguments) {
        return TextUtils.toText(getString(key, messageArguments));
    }

    /** Drops the cached, parsed strings; the bundle itself is immutable so it is kept. */
    public static void reload() {
        rawCache.clear();
        bundle = null;
    }

    private static void initialize() {
        // English only: always the en_US bundle, regardless of the JVM default locale.
        bundle = ResourceBundle.getBundle(BUNDLE_ROOT, Locale.US);
    }

    private static @NotNull String getRawString(@NotNull String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            if (!key.contains("Guides")) {
                LOGGER.warn("Could not find locale string: {}", key);
            }
            return '!' + key + '!';
        }
    }

    /**
     * Applies {@link MessageFormat} substitution (if arguments are given) then colour translation.
     */
    public static @NotNull String formatString(@NotNull String string, Object... messageArguments) {
        if (messageArguments != null) {
            final MessageFormat formatter = new MessageFormat("", Locale.US);
            formatter.applyPattern(string.replace("'", "''"));
            string = formatter.format(messageArguments);
        }
        return addColors(string);
    }

    /**
     * Normalises every colour form mcMMO understands — {@code &#RRGGBB} hex, {@code [[COLOR]]}
     * tokens, and simplified {@code &} codes — into section-sign ({@code §}) codes.
     */
    public static @NotNull String addColors(@NotNull String input) {
        input = translateHexColorCodes(input);

        // mcMMO's own [[COLOR]] tokens.
        input = input.replace("[[BLACK]]", "§0");
        input = input.replace("[[DARK_BLUE]]", "§1");
        input = input.replace("[[DARK_GREEN]]", "§2");
        input = input.replace("[[DARK_AQUA]]", "§3");
        input = input.replace("[[DARK_RED]]", "§4");
        input = input.replace("[[DARK_PURPLE]]", "§5");
        input = input.replace("[[GOLD]]", "§6");
        input = input.replace("[[GRAY]]", "§7");
        input = input.replace("[[DARK_GRAY]]", "§8");
        input = input.replace("[[BLUE]]", "§9");
        input = input.replace("[[GREEN]]", "§a");
        input = input.replace("[[AQUA]]", "§b");
        input = input.replace("[[RED]]", "§c");
        input = input.replace("[[LIGHT_PURPLE]]", "§d");
        input = input.replace("[[YELLOW]]", "§e");
        input = input.replace("[[WHITE]]", "§f");
        input = input.replace("[[BOLD]]", "§l");
        input = input.replace("[[UNDERLINE]]", "§n");
        input = input.replace("[[ITALIC]]", "§o");
        input = input.replace("[[STRIKE]]", "§m");
        input = input.replace("[[MAGIC]]", "§k");
        input = input.replace("[[RESET]]", "§r");

        // Simplified &-codes. Only translate the recognised set so stray ampersands survive.
        input = input.replaceAll("&([0-9a-fk-or])", "§$1");
        // Legacy quirk: mcMMO used "&?" for the obfuscated/magic code.
        input = input.replace("&?", "§k");

        return input;
    }

    /**
     * Translates {@code &#RRGGBB} hex codes into the {@code §x§R§R§G§G§B§B} section form that
     * {@link TextUtils} understands.
     */
    public static @NotNull String translateHexColorCodes(@NotNull String messageWithHex) {
        final Matcher matcher = HEX_PATTERN.matcher(messageWithHex);
        final StringBuilder buffer = new StringBuilder(messageWithHex.length() + 4 * 8);
        while (matcher.find()) {
            final String group = matcher.group(1);
            final String hexEquivalent = "§x"
                    + "§" + group.charAt(0) + "§" + group.charAt(1)
                    + "§" + group.charAt(2) + "§" + group.charAt(3)
                    + "§" + group.charAt(4) + "§" + group.charAt(5);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(hexEquivalent));
        }
        return matcher.appendTail(buffer).toString();
    }
}
