package com.gmail.nossr50.config;

import com.gmail.nossr50.util.skills.SkillRenames;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
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
        this(fileName, dataFolder, ConfigRetunes.forFile(fileName),
                SkillRenames.movedConfigPaths(fileName));
    }

    /**
     * @param retunes the changed shipped defaults for this file. Injected rather than looked up so
     *        the migration mechanism can be exercised against a fixture resource — a test that can
     *        only drive it through a real config file is a test of one retune, not of the mechanism.
     */
    protected ConfigLoader(@NotNull String fileName, @NotNull Path dataFolder,
            @NotNull List<ConfigRetunes.Retune> retunes) {
        this(fileName, dataFolder, retunes, SkillRenames.movedConfigPaths(fileName));
    }

    /**
     * @param movedPaths the re-parented config paths for this file, injected for the same reason as
     *        {@code retunes}: the real table is scoped per file, so a fixture config would otherwise
     *        match nothing and the whole migrator would be untestable except through the three real
     *        files it currently happens to name.
     */
    protected ConfigLoader(@NotNull String fileName, @NotNull Path dataFolder,
            @NotNull List<ConfigRetunes.Retune> retunes,
            @NotNull List<SkillRenames.MovedPath> movedPaths) {
        this.fileName = fileName;
        this.dataFolder = dataFolder;
        this.defaultConfig = loadDefaults();
        // ⚠️ Order matters: a file this constructor is about to write from the defaults is already
        // current, and applyRetunedDefaults has to be told so before initConfig blurs the
        // distinction between "brand new" and "predates every retune".
        final boolean writtenFresh = !Files.exists(getFile());
        this.config = initConfig();
        applyRetunedDefaults(retunes, writtenFresh);
        // ⚠️ Before copyMissingDefaults, deliberately. Run after it, and every destination path
        // already holds a freshly written shipped default, so "is the new path untouched?" can no
        // longer be answered by asking whether it exists. Running first keeps the common case
        // unambiguous: the destination is absent, so the move cannot possibly clobber anything.
        migrateMovedPaths(movedPaths, writtenFresh);
        copyMissingDefaults();
        warnOnRenamedSections();
        warnOnRenamedPaths();
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
     * Carry changed shipped defaults onto an existing config file — see {@link ConfigRetunes} for
     * why this is needed at all and what it refuses to touch.
     *
     * <p>Three outcomes per retune, and only the first two write anything:
     * <ul>
     *   <li>on-disk value equals the <em>old</em> shipped default → the player never expressed an
     *       opinion, so it is updated and logged at INFO with the reason;</li>
     *   <li>on-disk value equals the <em>new</em> default already → silent no-op (a fresh file, or a
     *       file migrated on an earlier boot);</li>
     *   <li>anything else → the player tuned it deliberately. Left exactly as it is, logged once so
     *       that "the changelog says 50 but mine says 30" has a visible answer.</li>
     * </ul>
     *
     * <p>The version stamp is written whether or not anything changed, which is what makes each
     * retune fire once ever rather than every boot.
     *
     * @param retunes every changed default declared for this file
     * @param writtenFresh whether {@link #initConfig()} just created this file from the defaults, in
     *        which case there is nothing to migrate and the stamp goes straight to current
     */
    private void applyRetunedDefaults(@NotNull List<ConfigRetunes.Retune> retunes,
            boolean writtenFresh) {
        final int current = ConfigRetunes.highestVersion(retunes);
        if (current == 0) {
            return; // This file has never been retuned; do not stamp a version onto it for nothing.
        }

        final int applied = writtenFresh ? current : config.getInt(ConfigRetunes.VERSION_KEY, 0);
        if (applied < current) {
            for (ConfigRetunes.Retune retune : retunes) {
                if (retune.version() > applied) {
                    applyRetune(retune);
                }
            }
        }

        if (config.getInt(ConfigRetunes.VERSION_KEY, -1) == current) {
            return;
        }
        config.set(ConfigRetunes.VERSION_KEY, current);
        try {
            config.save(getFile());
        } catch (IOException e) {
            // Left unstamped, so the same retunes are reconsidered next boot. That is the safe
            // direction (they are value-guarded), but it must not happen silently.
            LOGGER.error("Failed to save config after retuning defaults: {}", fileName, e);
        }
    }

    /** One retune, already known to be newer than this file's stamp. */
    private void applyRetune(@NotNull ConfigRetunes.Retune retune) {
        final Object onDisk = config.get(retune.path());
        if (onDisk == null) {
            // Absent entirely: copyMissingDefaults writes the new default a moment from now, which
            // is the right outcome, so there is nothing to do here.
            return;
        }
        if (valuesMatch(onDisk, retune.newDefault())) {
            return;
        }
        if (!valuesMatch(onDisk, retune.oldDefault())) {
            LOGGER.info("{}: keeping your '{}' = {} (the shipped default changed {} → {}: {})",
                    fileName, retune.path(), onDisk, retune.oldDefault(), retune.newDefault(),
                    retune.reason());
            return;
        }
        config.set(retune.path(), retune.newDefault());
        LOGGER.info("{}: updated '{}' {} → {} ({}). It was still at the old shipped default; set it "
                        + "back by hand if you preferred it.",
                fileName, retune.path(), retune.oldDefault(), retune.newDefault(), retune.reason());
    }

    /**
     * Whether an on-disk value is the same as a declared default.
     *
     * <p>Numbers are compared by value, not by type: SnakeYAML reads {@code 25} as an {@code Integer}
     * and {@code 25.0} as a {@code Double}, so {@code Object#equals} would report a hand-typed
     * {@code 25} as "customised" and strand exactly the retune that needs to fire. Everything else
     * (booleans, strings) falls through to {@code equals}.
     */
    private static boolean valuesMatch(@NotNull Object onDisk, @NotNull Object declared) {
        if (onDisk instanceof Number a && declared instanceof Number b) {
            return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
        }
        return onDisk.equals(declared);
    }

    /**
     * Move a player's tuning from paths that have been re-parented to where the code now reads it,
     * then delete the dead originals.
     *
     * <p>{@link #copyMissingDefaults()} back-fills only <em>absent</em> keys, so after a sub-skill is
     * re-parented an existing config ends up carrying both spellings: freshly written defaults at
     * the new path (which is what the game reads) and the player's own values still sitting at the
     * old one, silently ignored. The file looks edited, the game ignores it, and nothing says why.
     *
     * <p>This <b>rewrites the user's file</b>, which the sibling {@link #warnOnRenamedPaths()}
     * deliberately refuses to do. The distinction is not inconsistency, it is the entry condition on
     * {@link SkillRenames#movedConfigPaths()}: every move in that table is a straight sub-tree
     * relocation within one file, leaf-for-leaf, so there is nothing to guess about where a value
     * belongs. Where a move is not that simple — a different file, a changed unit, an outright
     * retirement — it stays in the warn-only table and the player is asked to move it by hand.
     *
     * <p>Conflicts are still not guessed at. If the player has tuned <em>both</em> the old path and
     * the new one, the value already at the new path wins (it is the one the game has been reading)
     * and the discarded one is logged in full, so the decision is visible rather than silent.
     *
     * @param movedPaths the moves declared for this file, already filtered by
     *        {@link SkillRenames#movedConfigPaths(String)}
     * @param writtenFresh whether this file was just created from the shipped defaults, in which
     *        case it cannot contain a legacy path and the whole scan is skipped
     */
    private void migrateMovedPaths(@NotNull List<SkillRenames.MovedPath> movedPaths,
            boolean writtenFresh) {
        if (writtenFresh || movedPaths.isEmpty()) {
            return;
        }
        boolean moved = false;
        for (SkillRenames.MovedPath move : movedPaths) {
            moved |= migrateOnePath(move.legacyPath(), move.newPaths());
        }
        if (!moved) {
            return; // The overwhelmingly common case: nothing stranded, so the file is not touched.
        }
        try {
            config.save(getFile());
        } catch (IOException e) {
            // Left on disk unmigrated. The next boot retries (the scan is driven by what the file
            // actually contains, not by a version stamp), but a silent failure here would mean the
            // player's tuning stays ignored with no trace of why.
            LOGGER.error("Failed to save config after migrating moved paths: {}", fileName, e);
        }
    }

    /**
     * Relocate one legacy path's values, returning whether anything actually changed.
     *
     * <p>Split out and package-private so the decision is assertable without a
     * {@code LOGGER}-scraping test.
     *
     * <p>⚠️ <b>The delete happens once, after every destination is written.</b> A one-to-many move
     * exists because a dissolving skill can leave one number with several equally correct homes;
     * clearing the legacy path per destination would move it to the first and lose it for the rest.
     *
     * @param legacy the dotted path values may be stranded at
     * @param targets every path the code reads them from now; each receives the full sub-tree
     * @return {@code true} if the config was modified
     */
    boolean migrateOnePath(@NotNull String legacy, @NotNull List<String> targets) {
        if (!config.contains(legacy)) {
            return false;
        }

        // Snapshot first: the leaves are read out of the sub-tree before anything is written, so a
        // move can never read back a value this same loop has already written. Overlapping legacy
        // and target paths are not expected, but a migrator that corrupts data if they ever overlap
        // is a landmine rather than a constraint.
        final Map<String, Object> leaves = new LinkedHashMap<>();
        collectLeaves(legacy, leaves);

        for (String target : targets) {
            for (Map.Entry<String, Object> leaf : leaves.entrySet()) {
                final String targetKey = leaf.getKey().isEmpty()
                        ? target
                        : target + "." + leaf.getKey();
                final Object existing = config.get(targetKey);
                if (existing != null && !valuesMatch(existing, defaultConfig.get(targetKey))) {
                    LOGGER.warn("{}: '{}' moved to '{}', but you have tuned BOTH. Keeping {} (the "
                                    + "value the game has been using) and discarding {} from the old "
                                    + "path.",
                            fileName, legacy, target, existing, leaf.getValue());
                    continue;
                }
                config.set(targetKey, leaf.getValue());
            }
        }

        config.set(legacy, null);
        LOGGER.info("{}: moved your '{}' settings to {} and removed the old keys, which the game "
                        + "no longer reads.",
                fileName, legacy, targets);
        return true;
    }

    /**
     * Collect every leaf value at or under {@code path}, keyed by its suffix relative to
     * {@code path} (the empty string when {@code path} is itself a leaf).
     *
     * <p>Leaves only: a section node carries no value of its own, and copying one across would alias
     * the legacy sub-tree's live map into the new location so that the subsequent delete of the old
     * path takes the migrated values with it.
     */
    private void collectLeaves(@NotNull String path, @NotNull Map<String, Object> out) {
        final Object value = config.get(path);
        if (!(value instanceof Map<?, ?>)) {
            out.put("", value);
            return;
        }
        final YamlConfiguration section = config.getConfigurationSection(path);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(true)) {
            final Object leaf = section.get(key);
            if (!(leaf instanceof Map<?, ?>)) {
                out.put(key, leaf);
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
     * Warn about tuning stranded at a dotted path that has since moved somewhere else.
     *
     * <p>Same failure and the same warn-don't-rewrite policy as {@link #warnOnRenamedSections()},
     * one level finer: this fires when a <em>sub-skill</em> is re-parented, which moves its config
     * sub-tree while both the old and the new parent section legitimately continue to exist. The
     * section-level check cannot express that without warning about every unmoved sibling.
     *
     * <p>Silent unless the file actually still carries the old path, which a freshly generated
     * config never does — a warning that fires for everyone is a warning nobody reads.
     */
    private void warnOnRenamedPaths() {
        for (Map.Entry<String, String> moved : strandedLegacyPaths().entrySet()) {
            LOGGER.warn("{} still contains '{}', which moved to '{}'. Any values you set under "
                            + "'{}' are being ignored — move them to '{}'.",
                    fileName, moved.getKey(), moved.getValue(), moved.getKey(), moved.getValue());
        }
    }

    /**
     * The subset of {@link SkillRenames#legacyConfigPaths()} this config file still carries values
     * for: legacy path → where it moved to.
     *
     * <p>Split out of {@link #warnOnRenamedPaths()} purely so the detection is assertable — a bare
     * {@code LOGGER.warn} is not. Package-private for the test.
     *
     * @return an insertion-ordered map, empty (the common case) when nothing is stranded
     */
    @NotNull Map<String, String> strandedLegacyPaths() {
        final Map<String, String> stranded = new LinkedHashMap<>();
        for (Map.Entry<String, String> moved : SkillRenames.legacyConfigPaths().entrySet()) {
            final String legacy = moved.getKey();
            for (String key : config.getKeys(true)) {
                // Whole-path match or a descendant of it — never a prefix match on a partial
                // segment, so "Skills.Agility.Roll" cannot claim a hypothetical
                // "Skills.Agility.RollTwo".
                if (key.equals(legacy) || key.startsWith(legacy + ".")) {
                    stranded.put(legacy, moved.getValue());
                    break; // The whole sub-tree moves together; one entry per move.
                }
            }
        }
        return stranded;
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
