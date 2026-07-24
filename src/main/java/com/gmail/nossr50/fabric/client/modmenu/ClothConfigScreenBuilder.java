package com.gmail.nossr50.fabric.client.modmenu;

import com.gmail.nossr50.fabric.McMMOMod;
import java.io.IOException;
import java.nio.file.Path;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the editable mcMMO options screen with the Cloth Config API. Every reference to Cloth lives
 * in this class, and it is only touched behind a {@code FabricLoader.isModLoaded("cloth-config")}
 * guard in {@link McMMOModMenuIntegration}, so the mod links cleanly when Cloth is absent.
 *
 * <p>The screen edits the on-disk {@code .yml} files directly via a {@link ConfigSession}; changes
 * are flushed on save and take effect on the next world load (the reload contract mcMMO already
 * documents). The whole option catalogue comes from {@link McMMOSettings}.
 */
public final class ClothConfigScreenBuilder {

    private ClothConfigScreenBuilder() {
    }

    public static @NotNull Screen build(Screen parent, @NotNull Path configDir) {
        final ConfigSession session = new ConfigSession(configDir);

        final ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("mcMMO Configuration"));

        final ConfigEntryBuilder entries = builder.entryBuilder();

        for (String category : McMMOSettings.categories()) {
            final ConfigCategory tab = builder.getOrCreateCategory(Text.literal(category));
            for (ConfigSetting setting : McMMOSettings.byCategory(category)) {
                tab.addEntry(buildEntry(entries, session, setting));
            }
        }

        builder.setSavingRunnable(() -> {
            try {
                final int written = session.saveAll();
                if (written > 0) {
                    McMMOMod.LOGGER.info("mcMMO config screen saved changes to {} file(s); "
                            + "they apply on the next world load.", written);
                }
            } catch (IOException e) {
                McMMOMod.LOGGER.error("Failed to save mcMMO config edits from the config screen.", e);
            }
        });

        return builder.build();
    }

    private static @NotNull AbstractConfigListEntry<?> buildEntry(
            @NotNull ConfigEntryBuilder entries, @NotNull ConfigSession session,
            @NotNull ConfigSetting setting) {
        final Text label = Text.literal(setting.label());
        return switch (setting.kind()) {
            case BOOLEAN -> {
                var b = entries.startBooleanToggle(label, session.readBoolean(setting))
                        .setDefaultValue(setting.defBoolean())
                        .setSaveConsumer(value -> session.write(setting, value));
                if (setting.tooltip() != null) {
                    b = b.setTooltip(Text.literal(setting.tooltip()));
                }
                yield b.build();
            }
            case INT -> {
                var b = entries.startIntField(label, session.readInt(setting))
                        .setDefaultValue(setting.defInt())
                        .setSaveConsumer(value -> session.write(setting, value));
                if (setting.min() != null) {
                    b = b.setMin(setting.min().intValue());
                }
                if (setting.max() != null) {
                    b = b.setMax(setting.max().intValue());
                }
                if (setting.tooltip() != null) {
                    b = b.setTooltip(Text.literal(setting.tooltip()));
                }
                yield b.build();
            }
            case DOUBLE -> {
                var b = entries.startDoubleField(label, session.readDouble(setting))
                        .setDefaultValue(setting.defDouble())
                        .setSaveConsumer(value -> session.write(setting, value));
                if (setting.min() != null) {
                    b = b.setMin(setting.min());
                }
                if (setting.max() != null) {
                    b = b.setMax(setting.max());
                }
                if (setting.tooltip() != null) {
                    b = b.setTooltip(Text.literal(setting.tooltip()));
                }
                yield b.build();
            }
        };
    }
}
