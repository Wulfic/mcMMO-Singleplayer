package com.gmail.nossr50.fabric.client.modmenu;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * One editing session over mcMMO's config files, backing a single opening of the ModMenu config
 * screen. Lazily opens a {@link ConfigDocument} per distinct file referenced by the settings being
 * edited, reads the current values into the widgets, collects edits, and writes back every dirty
 * document on save.
 *
 * <p>Minecraft-free, so the whole read → edit → save flow is unit-testable against a temp directory.
 */
public final class ConfigSession {

    private final @NotNull Path dataFolder;
    private final @NotNull Map<String, ConfigDocument> docs = new LinkedHashMap<>();

    public ConfigSession(@NotNull Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    private @NotNull ConfigDocument doc(@NotNull String fileName) {
        return docs.computeIfAbsent(fileName, name -> ConfigDocument.load(dataFolder, name));
    }

    /** The current on-disk value of {@code setting}, boxed to match its {@link ConfigSetting.Kind}. */
    public @NotNull Object read(@NotNull ConfigSetting setting) {
        final ConfigDocument document = doc(setting.file());
        return switch (setting.kind()) {
            case BOOLEAN -> document.getBoolean(setting.path(), setting.defBoolean());
            case INT -> document.getInt(setting.path(), setting.defInt());
            case DOUBLE -> document.getDouble(setting.path(), setting.defDouble());
        };
    }

    public boolean readBoolean(@NotNull ConfigSetting setting) {
        return doc(setting.file()).getBoolean(setting.path(), setting.defBoolean());
    }

    public int readInt(@NotNull ConfigSetting setting) {
        return doc(setting.file()).getInt(setting.path(), setting.defInt());
    }

    public double readDouble(@NotNull ConfigSetting setting) {
        return doc(setting.file()).getDouble(setting.path(), setting.defDouble());
    }

    /** Stages an edit to {@code setting}; only actually marks the file dirty if the value changed. */
    public void write(@NotNull ConfigSetting setting, @NotNull Object value) {
        doc(setting.file()).set(setting.path(), value);
    }

    /**
     * The save callback a boolean widget for {@code setting} should be given.
     *
     * <p>{@code locked} yields a callback that writes <b>nothing at all</b>, for a row the screen
     * renders read-only (a skill this Minecraft version cannot furnish — owner ruling S-3).
     *
     * <p>⚠️ A disabled <em>widget</em> is not enough on its own, and that is why this exists as a
     * decision rather than a line inside the screen builder: Cloth invokes every entry's save
     * consumer when the screen is saved, whether or not that entry was editable. The ordinary
     * consumer would therefore write the value back into a file the player may have hand-edited.
     * Rewriting a value nobody touched is worse than the control being missing, so a locked row's
     * callback is inert — and being here rather than in the Minecraft-facing builder is what makes
     * that assertable in a unit test.
     *
     * @param setting the row being rendered
     * @param locked  whether the screen is rendering this row read-only
     * @return a callback that stages the edit, or one that does nothing
     */
    public @NotNull Consumer<Boolean> booleanSaveConsumer(@NotNull ConfigSetting setting,
            boolean locked) {
        return locked ? value -> { } : value -> write(setting, value);
    }

    /** True if any staged edit changed a value that still needs flushing to disk. */
    public boolean hasPendingChanges() {
        return docs.values().stream().anyMatch(ConfigDocument::isDirty);
    }

    /**
     * Writes every dirty document to disk. Returns the number of files actually rewritten so the
     * caller can log/skip a no-op save.
     */
    public int saveAll() throws IOException {
        int written = 0;
        for (ConfigDocument document : docs.values()) {
            if (document.isDirty()) {
                document.save();
                written++;
            }
        }
        return written;
    }
}
