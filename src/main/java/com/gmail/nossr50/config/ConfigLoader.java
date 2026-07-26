package com.gmail.nossr50.config;

import com.gmail.nossr50.util.skills.SkillRenames;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
        warnOnRenamedSections();
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

    /**
     * Warn about tuning stranded under a section name that has since been renamed.
     *
     * <p>{@link #copyMissingDefaults()} back-fills only <em>absent</em> keys, so after a skill is
     * renamed a user's existing config file ends up carrying both spellings: the freshly written
     * defaults under the new name (which is what the code reads) and their own hand-tuned values
     * still sitting under the old one, silently ignored. That is a genuinely nasty failure — the
     * file looks edited, the game ignores it, and nothing says why.
     *
     * <p>Warn rather than rewrite, deliberately. This file belongs to the user; a migrator that
     * moves values between sections has to guess which of two conflicting values wins and can
     * corrupt hand-authored comments and layout on a file mcMMO did not author. A log line naming
     * the exact old and new paths costs nothing and cannot destroy anything.
     */
    private void warnOnRenamedSections() {
        for (Map.Entry<String, String> rename : SkillRenames.legacyConfigSections().entrySet()) {
            final String legacy = rename.getKey();
            for (String key : config.getKeys(true)) {
                if (!isUnderSection(key, legacy)) {
                    continue;
                }
                LOGGER.warn("{} still contains a '{}' section, which was renamed to '{}'. Any "
                                + "values you set under '{}' are being ignored — move them to '{}'.",
                        fileName, legacy, rename.getValue(), legacy, rename.getValue());
                break; // One warning per file per rename; the whole section moves together.
            }
        }
    }

    /**
     * Whether {@code key} is the dotted path of, or nested inside, a section literally named
     * {@code section}. Matches on whole path segments so a key such as {@code Skills.Agility.Dodge}
     * is never mistaken for one under a section whose name it merely contains.
     */
    private static boolean isUnderSection(@NotNull String key, @NotNull String section) {
        for (String segment : key.split("\\.")) {
            if (segment.equals(section)) {
                return true;
            }
        }
        return false;
    }

    /** The on-disk location of this config. */
    public @NotNull Path getFile() {
        return dataFolder.resolve(fileName);
    }

    /** Reads this config's typed values into the subclass's fields. */
    protected abstract void loadKeys();
}
