package com.gmail.nossr50.fabric.client.modmenu;

import com.gmail.nossr50.fabric.McMMOMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Fallback config screen shown when ModMenu is installed but Cloth Config is not. It cannot offer
 * the editable options UI (that needs Cloth), so it explains how to unlock it and offers a shortcut
 * to the folder holding the editable {@code .yml} files. Pure vanilla client classes — no Cloth
 * reference — so it stays loadable whether or not Cloth is present.
 */
public final class McMMOInfoScreen extends Screen {

    private final @Nullable Screen parent;
    private final Path configDir;

    public McMMOInfoScreen(@Nullable Screen parent, Path configDir) {
        super(Component.literal("mcMMO"));
        this.parent = parent;
        this.configDir = configDir;
    }

    @Override
    protected void init() {
        final int centerX = this.width / 2;
        final int buttonWidth = 220;
        int y = this.height / 2;

        this.addDrawableChild(Button.builder(
                Component.literal("Open Config Folder"), button -> openConfigFolder())
                .dimensions(centerX - buttonWidth / 2, y, buttonWidth, 20)
                .build());

        y += 24;
        this.addDrawableChild(Button.builder(
                Component.translatable("gui.done"), button -> close())
                .dimensions(centerX - buttonWidth / 2, y, buttonWidth, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        final int centerX = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, Component.literal("mcMMO"),
                centerX, this.height / 2 - 60, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Component.literal("Install the Cloth Config API mod to edit mcMMO settings in-game.")
                        .formatted(ChatFormatting.GRAY),
                centerX, this.height / 2 - 40, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Component.literal("Otherwise, edit the .yml files directly. Changes apply on world load.")
                        .formatted(ChatFormatting.GRAY),
                centerX, this.height / 2 - 28, 0xAAAAAA);
    }

    private void openConfigFolder() {
        try {
            Files.createDirectories(configDir);
            Util.getOperatingSystem().open(configDir.toUri());
        } catch (IOException | RuntimeException e) {
            McMMOMod.LOGGER.warn("Could not open the mcMMO config folder {}", configDir, e);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
