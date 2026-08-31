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
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * TODO 8.4 — the per-band guard on {@code scripts/mc-ids.txt}.
 *
 * <p>That manifest lists every vanilla item, block and entity-type registry id for every Minecraft
 * version in the declared scope, and {@code scripts/config-id-audit.py} resolves ~1,013 shipped
 * config ids against it. ⚠️ <b>This sentence used to carry a version count and it rotted twice</b> — it
 * said 12 before §52 and 14 before §56.3, and nothing reads a javadoc, so neither was noticed until
 * something else went looking. The count is gone deliberately; {@link
 * #theManifestCoversEveryVersionThisBandShipsTo} asserts the invariant that number was standing in
 * for. It
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

    /**
     * {@code supported_minecraft_versions=26.1,26.1.1,26.1.2} — the whole band, not the pinned
     * build. Ignores comment lines, same as its siblings in {@link BandVersionLabelTest}.
     */
    private static final Pattern SUPPORTED_VERSIONS = Pattern.compile(
            "^\\s*supported_minecraft_versions\\s*=\\s*(\\S.*?)\\s*$", Pattern.MULTILINE);

    private static final Pattern MINECRAFT_VERSION =
            Pattern.compile("^\\s*minecraft_version\\s*=\\s*(\\S+)\\s*$", Pattern.MULTILINE);

    private static final String BLOCK = "block";
    private static final String ITEM = "item";
    private static final String ENTITY = "entity";

    /**
     * Floor for the anti-vacuity check. The oldest supported version, {@code 1.21}, carries 1060
     * blocks and 1333 items; anything remotely near this number means a registry that failed to
     * bootstrap or a manifest section that failed to parse, and every assertion below would then
     * pass by comparing two empty sets.
     */
    private static final int PLAUSIBLE_MINIMUM_IDS = 900;

    /**
     * The same floor for entity types (§52), which are an order of magnitude fewer — {@code 1.21}
     * has 130 and {@code 26.2} has 158.
     *
     * <p>⚠️ It is a separate constant rather than a reuse of {@link #PLAUSIBLE_MINIMUM_IDS} because
     * reusing that one would make the entity assertion fail permanently, and the tempting repair is
     * to lower the shared floor — which would silently weaken the item and block checks from "900"
     * to "100" and leave a registry that bootstrapped 150 items reading as healthy.
     */
    private static final int PLAUSIBLE_MINIMUM_ENTITY_IDS = 100;

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    // -----------------------------------------------------------------------------------------
    // The load-bearing assertions: the manifest section for THIS band equals the live registry.
    // -----------------------------------------------------------------------------------------

    @Test
    void theManifestListsExactlyTheItemsThisMinecraftVersionHas() throws IOException {
        assertRegistryMatchesManifest(ITEM, vanillaIds(Registries.ITEM.getIds()),
                PLAUSIBLE_MINIMUM_IDS);
    }

    @Test
    void theManifestListsExactlyTheBlocksThisMinecraftVersionHas() throws IOException {
        assertRegistryMatchesManifest(BLOCK, vanillaIds(Registries.BLOCK.getIds()),
                PLAUSIBLE_MINIMUM_IDS);
    }

    /**
     * §52. Entity types joined the manifest so the entity-keyed config tables could enter gate 4,
     * and they get the same third-authority treatment as items and blocks: whatever the generator
     * wrote and whatever the jar assets say, this is the registry the mod will actually call.
     *
     * <p>⚠️ Entities have <b>no jar-asset counterpart</b>, so {@code extract-mc-ids.py}'s
     * cross-validation deliberately skips them. That removes one of the three authorities for this
     * kind alone — which makes this assertion the only independent check the entity sections get,
     * rather than a third opinion on an already-agreed answer.
     */
    @Test
    void theManifestListsExactlyTheEntityTypesThisMinecraftVersionHas() throws IOException {
        assertRegistryMatchesManifest(ENTITY, vanillaIds(Registries.ENTITY_TYPE.getIds()),
                PLAUSIBLE_MINIMUM_ENTITY_IDS);
    }

    /**
     * Every Minecraft version this band actually ships to must have a section in the manifest.
     *
     * <p><b>This closes a hole that was open on a shipped band.</b>
     * {@code config-id-audit.py}'s {@code supported_versions()} derives its comparison set -- the
     * denominator behind every DEAD-EVERYWHERE verdict -- from {@code scripts/mc-ids.txt} itself.
     * So a version missing from the manifest is not refused and not reported: it is never asked
     * about, and the audit prints a confident answer about a smaller world than the mod ships to.
     * {@code mc/26.1.2} declares {@code supported_minecraft_versions=26.1,26.1.1,26.1.2} while the
     * manifest carried only the last of the three, so gate 4 had never once run against two
     * versions that band puts in players' hands.
     *
     * <p><b>The two facts lived in different files and nothing compared them.</b>
     * {@code supported_minecraft_versions} is read by {@link BandDocsMatchRealityTest},
     * {@link BandVersionLabelTest} and {@code scripts/gradle-key-identity-audit.py}; the manifest's
     * version list is read by {@code config-id-audit.py}. Each source was internally consistent and
     * each was individually green. This assertion is the join between them.
     *
     * <p>⚠️ Deliberately a <b>subset</b> check, never equality. The manifest is a fact about
     * Minecraft, byte-identical on every branch, and legitimately carries versions this band does
     * not ship. Demanding equality would make every branch unshippable at once.
     *
     * <p>⚠️ It cannot see the inverse -- a version in {@code supported_minecraft_versions} that
     * Minecraft never released, or a band shipping to a version it cannot actually run. That is
     * {@link BandVersionLabelTest}'s ordering-and-shape check, and it is a different question.
     */
    @Test
    void theManifestCoversEveryVersionThisBandShipsTo() throws IOException {
        final Set<String> sections = parseManifest(readManifest()).keySet();

        // Guard the guard: a parse that yields nothing makes the containment check below vacuous.
        assertFalse(sections.isEmpty(),
                MANIFEST + " parsed to zero version sections -- the containment check below would"
                        + " pass by reading nothing. Check the '## <version>' section headers.");

        final List<String> declared = declaredSupportedVersions();
        assertFalse(declared.isEmpty(),
                GRADLE_PROPERTIES + " declares no supported_minecraft_versions, so this guard has"
                        + " nothing to hold the manifest to.");

        final List<String> missing = new ArrayList<>();
        for (String version : declared) {
            if (!sections.contains(version)) {
                missing.add(version);
            }
        }

        assertTrue(missing.isEmpty(), () ->
                "this band declares supported_minecraft_versions=" + String.join(",", declared)
                        + " but " + MANIFEST + " has no section for " + missing + " (it has: "
                        + new TreeSet<>(sections) + ").\nThis is not a bookkeeping gap:"
                        + " config-id-audit.py takes its supported-version set FROM the manifest,"
                        + " so those versions are not refused -- they are silently dropped from the"
                        + " comparison, and gate 4 reports a confident verdict about versions this"
                        + " band never audited. Regenerate with `python scripts/extract-mc-ids.py"
                        + " --mc " + missing.get(0) + " --write` (Loom's cached server jar is"
                        + " enough, no download) and CHERRY-PICK the result to every band -- the"
                        + " manifest is a fact about Minecraft, not about this branch.");
    }

    private void assertRegistryMatchesManifest(String kind, Set<String> live, int floor)
            throws IOException {
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
                live.size() >= floor,
                "only " + live.size() + " vanilla " + kind + " ids in the live registry; expected "
                        + "at least " + floor + ". That is a bootstrap failure, "
                        + "not a fact about Minecraft " + version + ".");
        assertTrue(
                manifest.size() >= floor,
                "only " + manifest.size() + " " + kind + " ids in " + MANIFEST + " for " + version
                        + "; expected at least " + floor + ". The section is "
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
        // The entity section is here so the round-trip covers all three kinds: a parser that
        // rejects `### entity` fails the whole suite rather than only the band-specific assertion,
        // which is how §52's addition was caught in the first place.
        final String good = "## 1.21\n### block 2\nstone\ndirt\n### entity 1\nzombie\n"
                + "### item 1\nstick\n";
        assertEquals(Set.of("stone", "dirt"), parseManifest(good).get("1.21").get(BLOCK));
        assertEquals(Set.of("zombie"), parseManifest(good).get("1.21").get(ENTITY));

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

    /** Every version this band ships to, in declaration order. */
    private static List<String> declaredSupportedVersions() throws IOException {
        final Matcher m = SUPPORTED_VERSIONS.matcher(
                Files.readString(GRADLE_PROPERTIES, StandardCharsets.UTF_8));
        assertTrue(m.find(), "no supported_minecraft_versions in " + GRADLE_PROPERTIES);
        final List<String> out = new ArrayList<>();
        for (String part : m.group(1).split(",")) {
            final String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
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
                if (parts.length != 2
                        || !(BLOCK.equals(parts[0]) || ITEM.equals(parts[0])
                                || ENTITY.equals(parts[0]))) {
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
