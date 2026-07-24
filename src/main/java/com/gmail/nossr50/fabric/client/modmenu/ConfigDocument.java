package com.gmail.nossr50.fabric.client.modmenu;

import com.gmail.nossr50.config.YamlConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single mcMMO {@code .yml} config file, opened for in-place editing by the ModMenu config screen.
 *
 * <p>Deliberately independent of the live {@link com.gmail.nossr50.config.ConfigLoader} singletons:
 * those are only wired while a world/server is running (see {@code ConfigBootstrap}), whereas the
 * config screen is normally opened from the title screen with no world loaded. So this reads and
 * writes the on-disk YAML directly. Edits take effect on the next world load, which is exactly the
 * reload contract {@code ConfigBootstrap.unload()} already documents.
 *
 * <p>On {@link #load} the on-disk file is used if present; otherwise the bundled default shipped in
 * the jar (classpath root, same source {@code ConfigLoader} uses) is loaded so the screen still
 * shows real defaults before a world has ever been created. Because the bundled default is a
 * complete document, a subsequent {@link #save} writes a complete file rather than a stub.
 *
 * <p>Minecraft-free, so it is fully unit-testable against a temp directory.
 */
public final class ConfigDocument {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/ConfigGui");

    private final @NotNull Path file;
    private final @NotNull String fileName;
    private final @NotNull YamlConfiguration yaml;
    private boolean dirty;

    private ConfigDocument(@NotNull Path file, @NotNull String fileName,
            @NotNull YamlConfiguration yaml) {
        this.file = file;
        this.fileName = fileName;
        this.yaml = yaml;
    }

    /**
     * Opens {@code fileName} under {@code dataFolder}, falling back to the bundled default when the
     * on-disk copy does not exist yet. Never throws: on any read error it opens an empty document
     * and logs, so the screen can still render.
     */
    public static @NotNull ConfigDocument load(@NotNull Path dataFolder, @NotNull String fileName) {
        final Path file = dataFolder.resolve(fileName);
        YamlConfiguration yaml = null;
        try {
            if (Files.exists(file)) {
                yaml = YamlConfiguration.loadConfiguration(file);
            } else {
                final InputStream bundled =
                        ConfigDocument.class.getResourceAsStream("/" + fileName);
                if (bundled != null) {
                    yaml = YamlConfiguration.loadConfiguration(bundled);
                } else {
                    LOGGER.warn("No on-disk or bundled copy of {} found; opening empty.", fileName);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read config {} for the ModMenu editor.", fileName, e);
        }
        return new ConfigDocument(file, fileName, yaml != null ? yaml : YamlConfiguration.empty());
    }

    public boolean getBoolean(@NotNull String path, boolean def) {
        return yaml.getBoolean(path, def);
    }

    public int getInt(@NotNull String path, int def) {
        return yaml.getInt(path, def);
    }

    public double getDouble(@NotNull String path, double def) {
        return yaml.getDouble(path, def);
    }

    /** Sets {@code path} and marks the document dirty when the value actually changed. */
    public void set(@NotNull String path, @NotNull Object value) {
        final Object current = yaml.get(path);
        if (!value.equals(current)) {
            yaml.set(path, value);
            dirty = true;
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Writes the whole document to disk if any value changed; a no-op otherwise. */
    public void save() throws IOException {
        if (!dirty) {
            return;
        }
        yaml.save(file);
        dirty = false;
        LOGGER.info("Saved mcMMO config edits to {}", file);
    }

    @NotNull String fileName() {
        return fileName;
    }
}
