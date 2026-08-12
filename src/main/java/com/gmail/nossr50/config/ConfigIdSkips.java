package com.gmail.nossr50.config;

import com.gmail.nossr50.platform.Materials;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * Records the config rows a loader dropped because their Minecraft id does not exist on the version
 * being run, and reports them as one summary line per file.
 *
 * <p>TODO 5.5 / risk R5. ~4,200 lines of item-keyed YAML ship in the jar and the mod supports a band
 * of Minecraft versions, so a shipped row naming an item added after the running version is normal
 * and expected — {@code copper_sword} on 1.21.8, the seven spears on anything below 1.21.11. What is
 * <em>not</em> acceptable is dropping such a row silently, which is what this class exists to stop.
 * The cross-band counterpart is {@code scripts/config-id-audit.py}, which answers the same question
 * for a band that has not been cut yet.
 *
 * <p><b>⚠️⚠️ The empty-registry guard is the whole safety argument, and it is why callers go through
 * {@link #keepItem}/{@link #keepBlock} rather than calling {@link Materials#isItem} themselves.</b>
 * "This Minecraft version does not have that item" and "Minecraft's bootstrap has not run yet" are
 * the same observation from outside — both make the registry answer {@code false} for everything. A
 * loader that concluded absence from an unpopulated registry would prune <em>every</em> row on
 * <em>every</em> version, and the summary line would state it with total confidence. So when the
 * registry is not populated these methods keep the row and record nothing: an unresolvable id costs
 * a lookup that misses, whereas a wrongly-pruned one costs the player the content.
 *
 * <p>This is the same trap {@code SkillAvailability} was rebuilt to avoid, where a lazy probe's
 * answer depended on which Gradle fork the test class landed in. Deciding once, at a point where the
 * registry is known to be live, is the fix in both places — configs load from
 * {@code onServerStarting}, which is after bootstrap.
 *
 * <p>Kept Minecraft-free (Phase 2's platform seal): it reaches the registry only through
 * {@code platform/Materials}, exactly as {@code RepairConfig} and {@code SalvageConfig} already do.
 */
public final class ConfigIdSkips {

    private final String fileName;
    /** section -> ids dropped there, insertion-ordered so the summary reads in config order. */
    private final Map<String, List<String>> skipped = new LinkedHashMap<>();

    public ConfigIdSkips(@NotNull String fileName) {
        this.fileName = fileName;
    }

    /**
     * Whether a row naming {@code id} should be kept.
     *
     * @return {@code true} to keep the row — the item exists, or the registry cannot yet answer.
     *         {@code false} only when the registry is live and genuinely has no such item, in which
     *         case the drop is recorded for {@link #logSummary}.
     */
    public boolean keepItem(@NotNull String section, @NotNull String id) {
        if (!Materials.itemRegistryIsPopulated() || Materials.isItem(id)) {
            return true;
        }
        record(section, id);
        return false;
    }

    /** {@link #keepItem} for a block id — same empty-registry argument, block registry. */
    public boolean keepBlock(@NotNull String section, @NotNull String id) {
        if (!Materials.itemRegistryIsPopulated() || Materials.isBlock(id)) {
            return true;
        }
        record(section, id);
        return false;
    }

    /**
     * Record a drop the caller decided on its own (e.g. {@code RepairConfig}, which needs the
     * resolved registry path rather than a yes/no and so does its own lookup).
     */
    public void record(@NotNull String section, @NotNull String id) {
        // Deduplicated: one config entry can be reachable from several map keys (a treasure listing
        // more than one Drops_From group is a single object in several lists), so a prune walk asks
        // about the same id repeatedly. Reporting it N times would overstate the damage.
        final List<String> ids = skipped.computeIfAbsent(section, k -> new ArrayList<>());
        if (!ids.contains(id)) {
            ids.add(id);
        }
    }

    /** Total rows dropped across all sections. */
    public int count() {
        return skipped.values().stream().mapToInt(List::size).sum();
    }

    /** Dropped ids by section — the seam {@code ConfigItemIdResolutionTest} asserts against. */
    public @NotNull Map<String, List<String>> bySection() {
        return Collections.unmodifiableMap(skipped);
    }

    /**
     * Emit one line naming every dropped id, or nothing at all when none were dropped.
     *
     * <p>INFO, deliberately, and one line rather than one per id. WARN would read as
     * misconfiguration to anyone running an older supported version, where these drops are the
     * correct and expected outcome; DEBUG is what the Repair/Salvage loaders used before this and is
     * indistinguishable from silence in a normal boot log — TODO 5.5's own criterion is that silent
     * skips are not acceptable. The ids are named, not just counted, because "9 entries skipped"
     * cannot be checked against anything.
     */
    public void logSummary(@NotNull Logger logger) {
        if (skipped.isEmpty()) {
            return;
        }
        final String detail = skipped.entrySet().stream()
                .map(e -> e.getKey() + ": " + String.join(", ", e.getValue()))
                .collect(Collectors.joining("; "));
        logger.info("{}: skipped {} entr{} naming an item or block this Minecraft version does not"
                        + " have — {}", fileName, count(), count() == 1 ? "y" : "ies", detail);
    }
}
