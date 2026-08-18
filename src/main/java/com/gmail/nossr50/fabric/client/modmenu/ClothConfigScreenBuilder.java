package com.gmail.nossr50.fabric.client.modmenu;

import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.skills.SkillAvailability;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
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

    /**
     * A row's tooltip: the catalogue's own text, with the version-lock reason appended when the row
     * is locked. Returns {@code null} when there is nothing to show.
     *
     * <p>Appended rather than replacing, because the catalogue's sentence still tells the player what
     * the switch would do — the lock explains only why they cannot reach it here.
     */
    private static String tooltipFor(@NotNull ConfigSetting setting, boolean locked) {
        if (!locked) {
            return setting.tooltip();
        }
        final PrimarySkillType skill = McMMOSettings.masterSwitchSkill(setting);
        // masterSwitchSkill cannot be null here -- isLockedByVersion only answers true for a row it
        // resolved -- but a null-check beats an NPE inside a screen build if that ever changes.
        final String notice = skill == null ? "" : McMMOSettings.unsupportedNotice(skill);
        return setting.tooltip() == null ? notice : setting.tooltip() + " " + notice;
    }

    private static @NotNull AbstractConfigListEntry<?> buildEntry(
            @NotNull ConfigEntryBuilder entries, @NotNull ConfigSession session,
            @NotNull ConfigSetting setting) {
        final Text label = Text.literal(setting.label());
        return switch (setting.kind()) {
            case BOOLEAN -> {
                // Owner ruling S-3 (2026-08-18): a skill this Minecraft version cannot furnish gets
                // its row, read-only, with the reason on the tooltip. SkillAvailability force-
                // disables it whatever coreskills.yml says, so an editable toggle would be a control
                // that cannot do anything.
                //
                // ⚠️ The locked row is given a NO-OP save consumer, not merely a disabled widget.
                // Cloth calls every entry's save consumer on save, so the ordinary one would write
                // the value back into a file the player may have hand-edited — rewriting a value
                // nobody touched is worse than the missing control.
                //
                // ⚠️ Known limit, and it is by design: SkillAvailability.probe() runs from
                // onServerStarting, so a screen opened from the TITLE screen sees every skill as
                // supported and renders every row normally. The probe deliberately has one answer per
                // process (see its class docs — a lazy probe made the answer depend on test
                // scheduling), and /mcstats <skill> remains the authority on the reason.
                final boolean locked =
                        McMMOSettings.isLockedByVersion(setting, SkillAvailability::isSkillSupported);
                final Consumer<Boolean> onSave = session.booleanSaveConsumer(setting, locked);
                var b = entries.startBooleanToggle(label, session.readBoolean(setting))
                        .setDefaultValue(setting.defBoolean())
                        .setSaveConsumer(onSave);
                final String tooltip = tooltipFor(setting, locked);
                if (tooltip != null) {
                    b = b.setTooltip(Text.literal(tooltip));
                }
                final AbstractConfigListEntry<?> entry = b.build();
                if (locked) {
                    entry.setEditable(false);
                }
                yield entry;
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
