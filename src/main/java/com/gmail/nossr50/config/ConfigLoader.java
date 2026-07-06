package com.gmail.nossr50.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for mcMMO's file-backed configs, replacing the Bukkit {@code BukkitConfig} base.
 *
 * <p>Behaviour is preserved from the plugin: on construction it loads the bundled default copy
 * (from the jar, i.e. the classpath root) and the on-disk user copy, writing the defaults out if
 * the user file is missing, then back-filling any keys the user file is missing so configs stay
 * forward-compatible when new options are added. Subclasses read their typed values by calling
 * {@link #loadKeys()} themselves (matching the legacy contract, which does not call it in the
 * constructor).
 *
 * <p>The data folder is injected rather than pulled from a global, so the whole load/merge flow is
 * unit-testable against a temp directory with no Fabric/Minecraft bootstrap.
 */
public abstract class ConfigLoader {

    protected static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/Config");

    protected final @NotNull String fileName;
    protected final @NotNull Path dataFolder;
    protected @NotNull YamlConfiguration defaultConfig;
    protected @NotNull YamlConfiguration config;

    protected ConfigLoader(@NotNull String fileName, @NotNull Path dataFolder) {
        this.fileName = fileName;
        this.dataFolder = dataFolder;
        this.defaultConfig = loadDefaults();
        this.config = initConfig();
        copyMissingDefaults();
    }

    /** The bundled default config shipped inside the jar at the classpath root. */
    private @NotNull YamlConfiguration loadDefaults() {
        final InputStream in = ConfigLoader.class.getResourceAsStream("/" + fileName);
        if (in == null) {
            LOGGER.error("Missing bundled default config resource: {}", fileName);
            return YamlConfiguration.empty();
        }
        try {
            return YamlConfiguration.loadConfiguration(in);
        } catch (IOException e) {
            LOGGER.error("Failed to read bundled default config: {}", fileName, e);
            return YamlConfiguration.empty();
        }
    }

    /** Loads the user config from disk, first writing out the defaults if it does not exist. */
    private @NotNull YamlConfiguration initConfig() {
        final Path configFile = getFile();
        try {
            if (!Files.exists(configFile)) {
                LOGGER.info("Config {} not found, writing defaults to disk.", fileName);
                defaultConfig.save(configFile);
            }
            return YamlConfiguration.loadConfiguration(configFile);
        } catch (IOException e) {
            LOGGER.error("Failed to load config file: {}", fileName, e);
            return YamlConfiguration.empty();
        }
    }

    /** Back-fills any leaf keys present in the defaults but missing from the user config. */
    private void copyMissingDefaults() {
        boolean updated = false;
        for (String key : defaultConfig.getKeys(true)) {
            final Object defaultValue = defaultConfig.get(key);
            // Skip section nodes: writing a missing leaf recreates its parent sections anyway,
            // and this avoids aliasing the defaults' nested maps into the live config.
            if (defaultValue instanceof java.util.Map<?, ?>) {
                continue;
            }
            if (!config.contains(key)) {
                config.set(key, defaultValue);
                updated = true;
            }
        }
        if (updated) {
            try {
                config.save(getFile());
            } catch (IOException e) {
                LOGGER.error("Failed to save merged defaults into config: {}", fileName, e);
            }
        }
    }

    /** The on-disk location of this config. */
    public @NotNull Path getFile() {
        return dataFolder.resolve(fileName);
    }

    /** Reads this config's typed values into the subclass's fields. */
    protected abstract void loadKeys();
}
