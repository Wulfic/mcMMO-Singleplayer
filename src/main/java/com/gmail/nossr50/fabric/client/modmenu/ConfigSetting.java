package com.gmail.nossr50.fabric.client.modmenu;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single editable mcMMO config option, described in a Minecraft-free way so the curated option
 * catalogue ({@link McMMOSettings}) and its read/write plumbing ({@link ConfigSession}) can be
 * unit-tested without a game bootstrap. The Cloth Config UI ({@code ClothConfigScreenBuilder})
 * turns each of these into a widget.
 *
 * @param category the tab this option is grouped under in the config screen
 * @param file     the config file that owns the key, e.g. {@code "config.yml"}
 * @param path     the dotted key address within that file, e.g. {@code "Abilities.Enabled"}
 * @param kind     the value type (drives which widget/getter is used)
 * @param def      the default value; its runtime type must match {@code kind}
 *                 ({@link Boolean}/{@link Integer}/{@link Double})
 * @param label    the human-readable option name shown in the UI
 * @param tooltip  an optional hover description ({@code null} for none)
 * @param min      the inclusive lower bound for numeric kinds ({@code null} = unbounded)
 * @param max      the inclusive upper bound for numeric kinds ({@code null} = unbounded)
 * @param mirrors  further keys in the same file written whenever {@code path} is written, for a
 *                 single control that fronts more than one key; empty for the ordinary case.
 *                 Reads still come from {@code path} alone, so it is the one that decides what the
 *                 widget shows.
 */
public record ConfigSetting(@NotNull String category, @NotNull String file, @NotNull String path,
        @NotNull Kind kind, @NotNull Object def, @NotNull String label, @Nullable String tooltip,
        @Nullable Double min, @Nullable Double max, @NotNull List<String> mirrors) {

    /** The supported value types. */
    public enum Kind { BOOLEAN, INT, DOUBLE }

    public ConfigSetting {
        // Fail fast if a catalogue entry's declared kind and default value disagree — a mistake
        // that would otherwise surface only as a mis-typed widget or a silent wrong write.
        final boolean typeOk = switch (kind) {
            case BOOLEAN -> def instanceof Boolean;
            case INT -> def instanceof Integer;
            case DOUBLE -> def instanceof Double;
        };
        if (!typeOk) {
            throw new IllegalArgumentException(
                    "Default value " + def + " (" + def.getClass().getSimpleName()
                            + ") does not match kind " + kind + " for " + file + ":" + path);
        }
        mirrors = List.copyOf(mirrors);
        if (mirrors.contains(path)) {
            throw new IllegalArgumentException(
                    "Mirror list for " + file + ":" + path + " repeats the primary key");
        }
    }

    static @NotNull ConfigSetting bool(@NotNull String category, @NotNull String file,
            @NotNull String path, boolean def, @NotNull String label, @Nullable String tooltip) {
        return new ConfigSetting(category, file, path, Kind.BOOLEAN, def, label, tooltip, null, null,
                List.of());
    }

    /**
     * A boolean row that writes {@code path} and every key in {@code mirrors} together.
     *
     * <p>Added for GitHub #17.8: the Effects tab carried two separate switches, "Super Ability
     * Firework (on)" and "(off)", which read as two features rather than one. They are one feature
     * to a player. The keys stay separate in {@code config.yml} -- a hand-editor keeps the finer
     * grain -- but the screen offers a single control.
     */
    static @NotNull ConfigSetting boolMirrored(@NotNull String category, @NotNull String file,
            @NotNull String path, boolean def, @NotNull String label, @Nullable String tooltip,
            @NotNull List<String> mirrors) {
        return new ConfigSetting(category, file, path, Kind.BOOLEAN, def, label, tooltip, null, null,
                mirrors);
    }

    static @NotNull ConfigSetting integer(@NotNull String category, @NotNull String file,
            @NotNull String path, int def, int min, int max, @NotNull String label,
            @Nullable String tooltip) {
        return new ConfigSetting(category, file, path, Kind.INT, def, label, tooltip,
                (double) min, (double) max, List.of());
    }

    static @NotNull ConfigSetting decimal(@NotNull String category, @NotNull String file,
            @NotNull String path, double def, double min, double max, @NotNull String label,
            @Nullable String tooltip) {
        return new ConfigSetting(category, file, path, Kind.DOUBLE, def, label, tooltip, min, max,
                List.of());
    }

    public boolean defBoolean() {
        return (Boolean) def;
    }

    public int defInt() {
        return (Integer) def;
    }

    public double defDouble() {
        return (Double) def;
    }
}
