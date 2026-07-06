package com.gmail.nossr50.util.text;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Converts mcMMO's legacy section-code ({@code §}) formatted strings into vanilla
 * {@link net.minecraft.text.Text}.
 *
 * <p>The original Bukkit plugin produced {@code String}s carrying legacy formatting codes
 * ({@code §a}, {@code §l}, and the six-nibble hex form {@code §x§R§R§G§G§B§B}); every
 * user-facing message flowed through Kyori Adventure's {@code LegacyComponentSerializer}.
 * Fabric has no Adventure, so this rebuilds the same styled output as a vanilla {@code Text}
 * tree. It expects section-sign codes: {@code &} and {@code [[COLOR]]} tokens are normalised
 * to {@code §} upstream by {@link com.gmail.nossr50.locale.LocaleLoader#addColors(String)}.
 *
 * <p>Semantics match legacy Minecraft: a colour (or {@code §r} reset) code clears any active
 * decorations, while a decoration code ({@code §l} etc.) accumulates onto the current style.
 */
public final class TextUtils {

    /** The section sign ({@code U+00A7}) that prefixes Minecraft legacy formatting codes. */
    public static final char SECTION_SIGN = '§';

    private TextUtils() {
    }

    /**
     * Parses a legacy section-code formatted string into a vanilla {@link Text}.
     *
     * @param legacy the section-code string (may be {@code null} or empty)
     * @return a {@link MutableText} tree reproducing the string's colours and decorations
     */
    public static @NotNull MutableText toText(@Nullable String legacy) {
        final MutableText result = Text.empty();
        if (legacy == null || legacy.isEmpty()) {
            return result;
        }

        final StringBuilder buffer = new StringBuilder();
        Style style = Style.EMPTY;
        final int len = legacy.length();
        int i = 0;

        while (i < len) {
            final char c = legacy.charAt(i);

            if (c == SECTION_SIGN && i + 1 < len) {
                final char code = Character.toLowerCase(legacy.charAt(i + 1));

                // §x introduces a six-nibble hex colour: §x§R§R§G§G§B§B (14 chars total).
                if (code == 'x') {
                    final Integer rgb = readHexColor(legacy, i, len);
                    if (rgb != null) {
                        flush(result, buffer, style);
                        style = Style.EMPTY.withColor(TextColor.fromRgb(rgb));
                        i += 14;
                        continue;
                    }
                    // Malformed hex run: swallow the "§x" and carry on (legacy behaviour).
                    i += 2;
                    continue;
                }

                final Formatting formatting = Formatting.byCode(code);
                if (formatting != null) {
                    flush(result, buffer, style);
                    style = applyCode(style, formatting);
                }
                // Whether or not the code was recognised, legacy strips "§<code>".
                i += 2;
                continue;
            }

            buffer.append(c);
            i++;
        }

        flush(result, buffer, style);
        return result;
    }

    private static Style applyCode(@NotNull Style style, @NotNull Formatting formatting) {
        if (formatting == Formatting.RESET) {
            return Style.EMPTY;
        }
        if (formatting.isColor()) {
            // A colour code resets active decorations, matching legacy section-code behaviour.
            return Style.EMPTY.withColor(formatting);
        }
        return switch (formatting) {
            case BOLD -> style.withBold(true);
            case ITALIC -> style.withItalic(true);
            case UNDERLINE -> style.withUnderline(true);
            case STRIKETHROUGH -> style.withStrikethrough(true);
            case OBFUSCATED -> style.withObfuscated(true);
            default -> style;
        };
    }

    /**
     * Reads the six hex nibbles of a {@code §x§R§R§G§G§B§B} run starting at {@code sectionIndex}
     * (the index of the leading {@code §x}). Returns the packed RGB int, or {@code null} if the
     * run is truncated or malformed.
     */
    private static @Nullable Integer readHexColor(@NotNull String s, int sectionIndex, int len) {
        // Need §x plus six "§<nibble>" pairs: the last nibble sits at sectionIndex + 13.
        if (sectionIndex + 13 >= len) {
            return null;
        }
        final StringBuilder hex = new StringBuilder(6);
        for (int k = 0; k < 6; k++) {
            final int signPos = sectionIndex + 2 + k * 2;
            final char nibble = s.charAt(signPos + 1);
            if (s.charAt(signPos) != SECTION_SIGN || Character.digit(nibble, 16) < 0) {
                return null;
            }
            hex.append(nibble);
        }
        try {
            return Integer.parseInt(hex.toString(), 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void flush(@NotNull MutableText result, @NotNull StringBuilder buffer,
            @NotNull Style style) {
        if (buffer.length() > 0) {
            result.append(Text.literal(buffer.toString()).setStyle(style));
            buffer.setLength(0);
        }
    }
}
