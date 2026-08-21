package com.gmail.nossr50.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.McTestRegistries;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * TODO 8.4 — the per-band guard on {@code scripts/mc-ids.txt}.
 *
 * <p>That manifest lists every vanilla item and block registry id for all 12 supported Minecraft
 * versions, and {@code scripts/config-id-audit.py} resolves ~689 shipped config ids against it. It
 * replaced a scan of {@code assets/minecraft/items/} inside the merged jar, which does not exist
 * below {@code 1.21.4} and therefore left bands {@code 1.21.3} and {@code 1.21.1} unable to run one
 * of the seven ship gates at all.
 *
 * <p><b>🔑 Why a Java test, when the generator already cross-checks.</b> There are three independent
 * authorities on what ids a Minecraft version has, and this class is the only place the third one
 * speaks:
 * <ol>
 *   <li>the data generator's {@code reports/registries.json} — what wrote the manifest;</li>
 *   <li>{@code assets/minecraft/items/} + {@code blockstates/} in the merged jar — what
 *       {@code extract-mc-ids.py} cross-checks it against, and which only exists from
 *       {@code 1.21.4};</li>
 *   <li><b>the live registry inside this build</b> — asserted here.</li>
 * </ol>
 * The first two are both produced by tooling reading a jar. This one is the registry the mod will
 * actually call at runtime, and it is the only check that runs on a band branch <em>without anyone
 * remembering to</em> — it is inside {@code ./gradlew build}, which is ship-gate #1. Every other
 * leg of this gate is a person running a script, and this repo has already had a three-legged risk
 * (R8) collapse to zero legs because two of them lived in one working tree.
 *
 * <p><b>⚠️⚠️ The manifest is a fact about MINECRAFT, not about this branch.</b> It is byte-identical
 * on every band and must be cherry-picked, never regenerated per band. That is the exact inverse of
 * {@code scripts/mc-surface.txt}, which describes this branch's own source and must be regenerated —
 * do not carry the rule you learned there over to here. What differs per band is only <em>which
 * section of it this test reads</em>, chosen by {@code gradle.properties}' {@code minecraft_version}.
 *
 * <p><b>⚠️ This class must not live in {@code com.gmail.nossr50.fabric.mixin}</b> — that package is
 * claimed by the Mixin transformer under Knot and a test there fails to <em>load</em> rather than to
 * assert. Same reason {@link MixinAllowCoverageTest} and {@link BandVersionLabelTest} live here.
 */
class ConfigIdManifestTest {

    /** Relative to the project dir, which Gradle sets as the test working directory. */
    private static final Path MANIFEST = Path.of("scripts", "mc-ids.txt");

    private static final Path GRADLE_PROPERTIES = Path.of("gradle.properties");

    private static final Pattern MINECRAFT_VERSION =
            Pattern.compile("^\\s*minecraft_version\\s*=\\s*(\\S+)\\s*$", Pattern.MULTILINE);

    private static final String BLOCK = "block";
    private static final String ITEM = "item";

    /**
     * Floor for the anti-vacuity check. The oldest supported version, {@code 1.21}, carries 1060
     * blocks and 1333 items; anything remotely near this number means a registry that failed to
     * bootstrap or a manifest section that failed to parse, and every assertion below would then
     * pass by comparing two empty sets.
     */
    private static final int PLAUSIBLE_MINIMUM_IDS = 900;

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    // -----------------------------------------------------------------------------------------
    // The load-bearing assertions: the manifest section for THIS band equals the live registry.
    // -----------------------------------------------------------------------------------------

    @Test
    void theManifestListsExactlyTheItemsThisMinecraftVersionHas() throws IOException {
        assertRegistryMatchesManifest(ITEM, vanillaIds(BuiltInRegistries.ITEM.getIds()));
    }

    @Test
    void theManifestListsExactlyTheBlocksThisMinecraftVersionHas() throws IOException {
        assertRegistryMatchesManifest(BLOCK, vanillaIds(BuiltInRegistries.BLOCK.getIds()));
    }

    private void assertRegistryMatchesManifest(String kind, Set<String> live) throws IOException {
        final String version = pinnedMinecraftVersion();
        final Map<String, Map<String, Set<String>>> parsed = parseManifest(readManifest());

        assertTrue(
                parsed.containsKey(version),
                MANIFEST + " has no section for this band's Minecraft version (" + version
                        + "); it lists " + parsed.keySet() + ". A band cannot run ship gate #4 "
                        + "without its own section — regenerate with `python "
                        + "scripts/extract-mc-ids.py --mc " + version + " --write`.");

        final Set<String> manifest = parsed.get(version).get(kind);

        // Anti-vacuity FIRST. Two empty sets are equal, and a failed bootstrap or a mis-parsed
        // section is exactly how they both end up empty at once.
        assertTrue(
                McTestRegistries.itemRegistryIsPopulated(),
                "the item registry did not populate — the bootstrap failed, so nothing below this "
                        + "line means anything, including a pass");
        assertTrue(
                live.size() >= PLAUSIBLE_MINIMUM_IDS,
                "only " + live.size() + " vanilla " + kind + " ids in the live registry; expected "
                        + "at least " + PLAUSIBLE_MINIMUM_IDS + ". That is a bootstrap failure, "
                        + "not a fact about Minecraft " + version + ".");
        assertTrue(
                manifest.size() >= PLAUSIBLE_MINIMUM_IDS,
                "only " + manifest.size() + " " + kind + " ids in " + MANIFEST + " for " + version
                        + "; expected at least " + PLAUSIBLE_MINIMUM_IDS + ". The section is "
                        + "truncated or the format changed.");

        final Difference diff = difference(manifest, live);
        assertFalse(
                diff.exists(),
                MANIFEST + " disagrees with the live registry about Minecraft " + version + " "
                        + kind + "s.\n"
                        + "  in the manifest but NOT in this build: " + diff.onlyInManifest() + "\n"
                        + "  in this build but NOT in the manifest: " + diff.onlyInLive() + "\n"
                        + "The manifest drives config-id-audit.py, so a mismatch means that gate is "
                        + "answering about a different Minecraft than the one this jar ships "
                        + "against. Regenerate it (`python scripts/extract-mc-ids.py --mc " + version
                        + "`) and read the diff before trusting either side.");
    }

    // -----------------------------------------------------------------------------------------
    // The converse. A guard that has never failed is not known to work — this repo has shipped six
    // vacuous ones, so the detector is a pure function and it is fed input that MUST trip it.
    // -----------------------------------------------------------------------------------------

    @Test
    void theDetectorFiresInBothDirections() {
        final Set<String> base = new TreeSet<>(Set.of("stone", "dirt", "stick"));

        assertFalse(difference(base, base).exists(), "identical sets must not report a difference");

        final Set<String> missingOne = new TreeSet<>(Set.of("stone", "dirt"));
        assertTrue(
                difference(base, missingOne).exists(),
                "an id present in the manifest and absent from the registry must be reported — "
                        + "that is a config row the audit would call live when it is dead");
        assertTrue(
                difference(missingOne, base).exists(),
                "an id present in the registry and absent from the manifest must be reported — "
                        + "that is a config row the audit would call dead when it is live");

        assertEquals(Set.of("stick"), difference(base, missingOne).onlyInManifest());
        assertEquals(Set.of("stick"), difference(missingOne, base).onlyInLive());
    }

    /**
     * The manifest parser must reject a section whose declared count does not match its contents.
     *
     * <p>A truncated manifest — a bad merge, a partial write, a half-finished cherry-pick — is
     * otherwise indistinguishable from a Minecraft version that genuinely lost ids, and the audit
     * would report it as expected band drift. The Python side carries the same check for the same
     * reason; both are proven to fire rather than assumed to.
     */
    @Test
    void aTruncatedSectionIsRejectedRatherThanReadAsDrift() {
        final String good = "## 1.21\n### block 2\nstone\ndirt\n### item 1\nstick\n";
        assertEquals(Set.of("stone", "dirt"), parseManifest(good).get("1.21").get(BLOCK));

        final String truncated = "## 1.21\n### block 2\nstone\n### item 1\nstick\n";
        final IllegalStateException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> parseManifest(truncated),
                "a section declaring 2 ids and holding 1 must be rejected, not silently accepted "
                        + "as a version that lost an id");
        assertTrue(
                thrown.getMessage().contains("declared 2"),
                "the rejection must name the counts so the reader can tell truncation from drift; "
                        + "got: " + thrown.getMessage());
    }

    // -----------------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------------

    /** Set difference in both directions, as one value, so neither direction can be forgotten. */
    private record Difference(Set<String> onlyInManifest, Set<String> onlyInLive) {
        boolean exists() {
            return !onlyInManifest.isEmpty() || !onlyInLive.isEmpty();
        }
    }

    private static Difference difference(Set<String> manifest, Set<String> live) {
        final Set<String> onlyManifest = new TreeSet<>(manifest);
        onlyManifest.removeAll(live);
        final Set<String> onlyLive = new TreeSet<>(live);
        onlyLive.removeAll(manifest);
        return new Difference(onlyManifest, onlyLive);
    }

    /**
     * Vanilla id paths only.
     *
     * <p>The namespace filter is not decoration: the manifest is generated from vanilla's own
     * registry dump, so anything another mod or the test harness injected would read as a manifest
     * that is missing entries — a failure blaming the wrong file.
     */
    private static Set<String> vanillaIds(Iterable<Identifier> ids) {
        final Set<String> out = new TreeSet<>();
        for (Identifier id : ids) {
            if ("minecraft".equals(id.getNamespace())) {
                out.add(id.getPath());
            }
        }
        return out;
    }

    private static String readManifest() throws IOException {
        assertTrue(
                Files.isRegularFile(MANIFEST),
                MANIFEST + " is missing. It is committed and cherry-picked to every band; if this "
                        + "branch lacks it, config-id-audit.py cannot run here at all.");
        return Files.readString(MANIFEST, StandardCharsets.UTF_8);
    }

    private static String pinnedMinecraftVersion() throws IOException {
        final Matcher m = MINECRAFT_VERSION.matcher(
                Files.readString(GRADLE_PROPERTIES, StandardCharsets.UTF_8));
        assertTrue(m.find(), "no minecraft_version in " + GRADLE_PROPERTIES);
        return m.group(1);
    }

    /**
     * Mirrors {@code extract-mc-ids.py}'s {@code parse_manifest}, including its count check.
     *
     * <p>Format: {@code ## <version>}, then {@code ### <kind> <count>}, then one registry path per
     * line. Lines starting with a single {@code #} are comments.
     */
    private static Map<String, Map<String, Set<String>>> parseManifest(String text) {
        final Map<String, Map<String, Set<String>>> out = new LinkedHashMap<>();
        final List<String> pending = new ArrayList<>();
        String version = null;
        String kind = null;
        int declared = 0;

        for (String raw : text.split("\n", -1)) {
            final String line = raw.strip();
            if (line.isEmpty() || (line.startsWith("#") && !line.startsWith("##"))) {
                continue;
            }
            if (line.startsWith("### ")) {
                flush(out, version, kind, declared, pending);
                final String[] parts = line.substring(4).trim().split("\\s+");
                if (parts.length != 2 || !(BLOCK.equals(parts[0]) || ITEM.equals(parts[0]))) {
                    throw new IllegalStateException("bad kind header: " + line);
                }
                kind = parts[0];
                declared = Integer.parseInt(parts[1]);
                continue;
            }
            if (line.startsWith("## ")) {
                flush(out, version, kind, declared, pending);
                version = line.substring(3).trim();
                kind = null;
                declared = 0;
                out.computeIfAbsent(version, v -> new LinkedHashMap<>());
                continue;
            }
            if (version == null || kind == null) {
                throw new IllegalStateException("id outside any version/kind section: " + line);
            }
            pending.add(line);
        }
        flush(out, version, kind, declared, pending);
        return out;
    }

    private static void flush(Map<String, Map<String, Set<String>>> out, String version,
            String kind, int declared, List<String> pending) {
        if (version == null || kind == null) {
            pending.clear();
            return;
        }
        final Set<String> ids = new TreeSet<>(pending);
        pending.clear();
        if (ids.size() != declared) {
            throw new IllegalStateException(
                    version + "/" + kind + ": header declared " + declared + " ids, section held "
                            + ids.size() + ". The manifest is truncated or was hand-edited — "
                            + "refusing to use it.");
        }
        out.get(version).put(kind, ids);
    }
}
