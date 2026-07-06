package com.gmail.nossr50.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * A minimal, snakeyaml-backed re-implementation of the slice of Bukkit's
 * {@code YamlConfiguration}/{@code ConfigurationSection} API that mcMMO's config classes use.
 *
 * <p>The Fabric port keeps mcMMO's {@code .yml} config files and their dotted key addresses
 * (e.g. {@code "Skills.Mining.DoubleDrops.ChanceMax"}) essentially unchanged, so instead of
 * rewriting every config getter we reproduce the accessor surface those getters call. Paths are
 * dot-delimited; nested maps are sections.
 *
 * <p>This type doubles as both the root document and a section view: {@link #getConfigurationSection}
 * returns another {@code YamlConfiguration} sharing the same backing map with a base-path prefix,
 * so {@link #set} through a section writes back to the root document.
 *
 * <p>Deliberately Minecraft-free so it is fully unit-testable without a game bootstrap.
 */
public final class YamlConfiguration {

    /** The backing document, shared by all section views of the same config. */
    private final @NotNull Map<String, Object> root;
    /** Dotted prefix this view is rooted at; empty for the document root. */
    private final @NotNull String basePath;

    private YamlConfiguration(@NotNull Map<String, Object> root, @NotNull String basePath) {
        this.root = root;
        this.basePath = basePath;
    }

    /** Creates an empty configuration. */
    public static @NotNull YamlConfiguration empty() {
        return new YamlConfiguration(new LinkedHashMap<>(), "");
    }

    /** Loads a configuration from a YAML file, or an empty one if the file is missing/blank. */
    public static @NotNull YamlConfiguration loadConfiguration(@NotNull Path file)
            throws IOException {
        if (!Files.exists(file)) {
            return empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return load(reader);
        }
    }

    /** Loads a configuration from a stream (e.g. a bundled jar resource). Closes the stream. */
    public static @NotNull YamlConfiguration loadConfiguration(@NotNull InputStream in)
            throws IOException {
        try (InputStream stream = in) {
            return load(stream);
        }
    }

    private static @NotNull YamlConfiguration load(@NotNull Object source) {
        // A fresh Yaml per load: snakeyaml's Yaml is not thread-safe to share.
        final Object data = source instanceof Reader r
                ? new Yaml().load(r)
                : new Yaml().load((InputStream) source);
        return new YamlConfiguration(asMap(data), "");
    }

    @SuppressWarnings("unchecked")
    private static @NotNull Map<String, Object> asMap(@Nullable Object o) {
        if (o instanceof Map<?, ?> map) {
            // snakeyaml yields String keys for mappings; copy into a stable-ordered map.
            final Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    // ------------------------------------------------------------------ raw access

    private @NotNull String fullPath(@NotNull String path) {
        return basePath.isEmpty() ? path : basePath + "." + path;
    }

    /** Resolves an absolute (already prefixed) dotted path to its raw value, or {@code null}. */
    private @Nullable Object resolve(@NotNull String absolutePath) {
        if (absolutePath.isEmpty()) {
            return root;
        }
        Object current = root;
        for (String part : absolutePath.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    /** Returns the raw value at {@code path} relative to this view, or {@code null}. */
    public @Nullable Object get(@NotNull String path) {
        return resolve(fullPath(path));
    }

    // ------------------------------------------------------------------ typed getters

    public boolean getBoolean(@NotNull String path) {
        return getBoolean(path, false);
    }

    public boolean getBoolean(@NotNull String path, boolean def) {
        final Object o = get(path);
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return def;
    }

    public int getInt(@NotNull String path) {
        return getInt(path, 0);
    }

    public int getInt(@NotNull String path, int def) {
        final Object o = get(path);
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return def;
    }

    public long getLong(@NotNull String path) {
        return getLong(path, 0L);
    }

    public long getLong(@NotNull String path, long def) {
        final Object o = get(path);
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return def;
    }

    public double getDouble(@NotNull String path) {
        return getDouble(path, 0.0D);
    }

    public double getDouble(@NotNull String path, double def) {
        final Object o = get(path);
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return def;
    }

    public @Nullable String getString(@NotNull String path) {
        return getString(path, null);
    }

    public @Nullable String getString(@NotNull String path, @Nullable String def) {
        final Object o = get(path);
        return o != null ? String.valueOf(o) : def;
    }

    /** Returns the list at {@code path} with every element stringified; empty if absent/not a list. */
    public @NotNull List<String> getStringList(@NotNull String path) {
        final List<String> result = new ArrayList<>();
        if (get(path) instanceof List<?> list) {
            for (Object element : list) {
                result.add(String.valueOf(element));
            }
        }
        return result;
    }

    public @Nullable List<?> getList(@NotNull String path) {
        return get(path) instanceof List<?> list ? list : null;
    }

    // ------------------------------------------------------------------ structure

    /** True if the path exists as a key (even if its value is {@code null}). */
    public boolean contains(@NotNull String path) {
        final String full = fullPath(path);
        final int lastDot = full.lastIndexOf('.');
        final String parentPath = lastDot < 0 ? "" : full.substring(0, lastDot);
        final String leaf = lastDot < 0 ? full : full.substring(lastDot + 1);
        return resolve(parentPath) instanceof Map<?, ?> map && map.containsKey(leaf);
    }

    public boolean isConfigurationSection(@NotNull String path) {
        return get(path) instanceof Map<?, ?>;
    }

    /** Returns a section view rooted at {@code path}, or {@code null} if it is not a section. */
    public @Nullable YamlConfiguration getConfigurationSection(@NotNull String path) {
        return get(path) instanceof Map<?, ?> ? new YamlConfiguration(root, fullPath(path)) : null;
    }

    /**
     * Returns the keys of this section. When {@code deep} is false, only immediate child keys;
     * when true, all descendant keys as dot-joined paths. Order follows the file.
     */
    public @NotNull Set<String> getKeys(boolean deep) {
        final Set<String> keys = new LinkedHashSet<>();
        if (resolve(basePath) instanceof Map<?, ?> section) {
            collectKeys(section, "", deep, keys);
        }
        return keys;
    }

    private static void collectKeys(@NotNull Map<?, ?> section, @NotNull String prefix,
            boolean deep, @NotNull Set<String> out) {
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            final String key = prefix + entry.getKey();
            out.add(key);
            if (deep && entry.getValue() instanceof Map<?, ?> child) {
                collectKeys(child, key + ".", deep, out);
            }
        }
    }

    // ------------------------------------------------------------------ mutation

    /**
     * Sets (or, when {@code value} is {@code null}, removes) the value at {@code path},
     * creating intermediate sections as needed.
     */
    @SuppressWarnings("unchecked")
    public void set(@NotNull String path, @Nullable Object value) {
        final String[] parts = fullPath(path).split("\\.");
        Map<String, Object> node = root;
        for (int i = 0; i < parts.length - 1; i++) {
            final Object child = node.get(parts[i]);
            if (child instanceof Map<?, ?> childMap) {
                node = (Map<String, Object>) childMap;
            } else {
                final Map<String, Object> created = new LinkedHashMap<>();
                node.put(parts[i], created);
                node = created;
            }
        }
        final String leaf = parts[parts.length - 1];
        if (value == null) {
            node.remove(leaf);
        } else {
            node.put(leaf, value);
        }
    }

    /** Writes this configuration (the whole document for the root view) to disk as YAML. */
    public void save(@NotNull Path file) throws IOException {
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setPrettyFlow(true);
        final Object toDump = resolve(basePath);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            new Yaml(options).dump(toDump, writer);
        }
    }
}
