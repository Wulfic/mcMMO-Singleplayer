package com.gmail.nossr50.platform.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

/**
 * Coverage for the legacy-{@code §}-string to vanilla {@link Text} parser.
 * Runs against the Loom-provided Minecraft classes (Text/Style/Formatting are plain,
 * registry-free types, so no bootstrap is required).
 */
class TextUtilsTest {

    @Test
    void plainStringHasNoFormatting() {
        assertEquals("Hello", TextUtils.toText("Hello").getString());
    }

    @Test
    void colorCodeIsStrippedFromVisibleText() {
        assertEquals("Green", TextUtils.toText("§aGreen").getString());
    }

    @Test
    void colorCodeAppliesTheColor() {
        final Component text = TextUtils.toText("§aGreen");
        final List<Component> siblings = text.getSiblings();
        assertEquals(1, siblings.size());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GREEN),
                siblings.get(0).getStyle().getColor());
    }

    @Test
    void decorationCodeAccumulates() {
        final Component text = TextUtils.toText("§l§nBold");
        final Component run = text.getSiblings().get(0);
        assertTrue(run.getStyle().isBold());
        assertTrue(run.getStyle().isUnderlined());
    }

    @Test
    void colorResetsPriorDecoration() {
        // §l turns on bold; a following colour code must clear it (legacy behaviour).
        final Component text = TextUtils.toText("§lBold§aPlain");
        final List<Component> siblings = text.getSiblings();
        assertEquals(2, siblings.size());
        assertTrue(siblings.get(0).getStyle().isBold());
        assertTrue(!siblings.get(1).getStyle().isBold());
    }

    @Test
    void hexColorIsParsed() {
        final Component text = TextUtils.toText("§x§F§F§0§0§0§0Red");
        assertEquals("Red", text.getString());
        assertEquals(TextColor.fromRgb(0xFF0000),
                text.getSiblings().get(0).getStyle().getColor());
    }
}
