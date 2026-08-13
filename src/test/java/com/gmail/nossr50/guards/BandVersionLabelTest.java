package com.gmail.nossr50.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;

/**
 * <b>The band-label guard</b> (multi-version TODO §10.2c): the Minecraft versions stamped into the
 * jar's <em>name</em> must be the versions the <em>loader</em> will actually accept.
 *
 * <p><b>Why this guard exists.</b> One jar serves a whole band (ruling R-a), so the same fact is
 * written down twice, in two files, in two different languages:
 *
 * <ul>
 *   <li>{@code supported_minecraft_versions} in {@code gradle.properties} — a plain list, and the
 *       thing {@code build.gradle} turns into the {@code +mc1.21.6-1.21.8} suffix on the filename.
 *       This is what a <b>player reads</b> when choosing a download.</li>
 *   <li>{@code depends.minecraft} in {@code fabric.mod.json} — a version <em>predicate</em>, and the
 *       only one of the two that is <b>enforced</b>. This is what actually refuses to boot.</li>
 * </ul>
 *
 * <p>Nothing but this test connects them. Let them drift and the failure is silent in the direction
 * that matters most: the filename advertises a version the loader then rejects, so the download is
 * simply broken for whoever trusted the name. That is a support ticket, not a stack trace.
 *
 * <p><b>Why the predicate is evaluated, not pattern-matched.</b> The range is parsed with Fabric's own
 * {@link VersionPredicate}, the same engine the loader runs at startup. A regex over {@code ">=1.21.6
 * <1.21.9"} would be re-deriving semver comparison — and would answer confidently and wrongly the
 * first time a band uses a form the regex did not anticipate ({@code ~1.21.11} is already in use on
 * one branch and means something a naive reader gets wrong).
 *
 * <p><b>Why the build's own version is imported rather than recomputed.</b> {@code build.gradle}
 * hands this test the version string it really produced, via the {@code mcmmo.build.version} system
 * property. Recomputing the label here instead would build a second implementation of the same rule,
 * and a second implementation keeps passing when the first one breaks — the exact shape of a vacuous
 * guard. {@link #theBuildWiringIsPresent()} fails if that hand-off is ever removed, so the
 * cross-check cannot be silently disconnected.
 *
 * <p><b>What this guard deliberately does NOT assert.</b> It does not require the predicate's upper
 * bound to stop exactly at the highest declared version. {@code ~1.21.11} accepts {@code 1.21.12}
 * and beyond, on purpose: that is the idiom for "this line, from here up", and tightening it would be
 * a change to what the loader enforces, which is out of scope for a naming change. What <em>is</em>
 * asserted is that the predicate is not open-ended — see
 * {@link #theRangeIsNotUnboundedAboveTheBand()}, which fails a bare {@code >=1.21.6} while passing
 * {@code ~1.21.11}.
 *
 * <p>⚠️ <b>This class must not live in {@code com.gmail.nossr50.fabric.mixin}.</b> That package is the
 * one declared by {@code mcmmo.mixins.json}, and the suite runs under {@code fabric-loader-junit}'s
 * Knot classloader, so the Mixin transformer claims every class in it — including a test. The result
 * is a load failure before any assertion runs. Same reason {@link MixinAllowCoverageTest} lives here.
 */
class BandVersionLabelTest {

    /** Relative to the project dir, which Gradle sets as the test working directory. */
    private static final Path GRADLE_PROPERTIES = Path.of("gradle.properties");

    private static final Path FABRIC_MOD_JSON =
            Path.of("src", "main", "resources", "fabric.mod.json");

    /** {@code supported_minecraft_versions=1.21.6,1.21.7,1.21.8} — ignores comment lines. */
    private static final Pattern BAND_VERSIONS =
            Pattern.compile("^\\s*supported_minecraft_versions\\s*=\\s*(\\S.*?)\\s*$", Pattern.MULTILINE);

    private static final Pattern MOD_VERSION =
            Pattern.compile("^\\s*mod_version\\s*=\\s*(\\S.*?)\\s*$", Pattern.MULTILINE);

    /** The {@code "minecraft": "<predicate>"} entry inside {@code depends}. */
    private static final Pattern MINECRAFT_DEPEND =
            Pattern.compile("\"minecraft\"\\s*:\\s*\"([^\"]+)\"");

    private static final Pattern SEMVER = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

    // -----------------------------------------------------------------------------------------
    // The load-bearing direction: the filename must never advertise a version the loader refuses.
    // -----------------------------------------------------------------------------------------

    @Test
    void everyDeclaredBandVersionSatisfiesTheLoaderPredicate() throws Exception {
        final List<String> declared = declaredBandVersions();
        final String predicateSource = minecraftDependPredicate();
        final VersionPredicate predicate = VersionPredicate.parse(predicateSource);

        final List<String> rejected = new ArrayList<>();
        for (String version : declared) {
            if (!predicate.test(Version.parse(version))) {
                rejected.add(version);
            }
        }

        assertTrue(
                rejected.isEmpty(),
                "gradle.properties advertises Minecraft " + rejected + " in the jar name, but "
                        + FABRIC_MOD_JSON + "'s depends.minecraft (\"" + predicateSource + "\") REFUSES "
                        + "them. The download would be broken for exactly the players who trusted the "
                        + "filename. Fix whichever of the two is wrong — they are one fact.");
    }

    /**
     * A range label claims everything between its endpoints. {@code +mc1.21.6-1.21.8} tells a
     * {@code 1.21.7} player the file is for them, so a declared list that skips {@code 1.21.7} makes
     * the label a lie even though both endpoints check out.
     */
    @Test
    void theDeclaredListIsAscendingAndHasNoGaps() {
        final List<String> declared = declaredBandVersions();
        assertFalse(declared.isEmpty(), "supported_minecraft_versions must not be empty");

        for (String version : declared) {
            assertTrue(
                    SEMVER.matcher(version).matches(),
                    "supported_minecraft_versions entry \"" + version + "\" is not a bare "
                            + "major.minor.patch version");
        }

        for (int i = 1; i < declared.size(); i++) {
            final String previous = declared.get(i - 1);
            final String current = declared.get(i);
            assertEquals(
                    nextPatch(previous),
                    current,
                    "supported_minecraft_versions must be ascending with no gaps: " + previous
                            + " is followed by " + current + ", but the jar is labelled as the range "
                            + declared.getFirst() + "-" + declared.getLast() + ", which claims every "
                            + "version in between.");
        }
    }

    /**
     * The lower bound must be tight. If the jar really runs on the version below the declared
     * minimum, the label hides it and those players never find the file — the mirror image of
     * {@link #everyDeclaredBandVersionSatisfiesTheLoaderPredicate()}, and invisible without this.
     */
    @Test
    void theRangeDoesNotReachBelowTheDeclaredMinimum() throws Exception {
        final List<String> declared = declaredBandVersions();
        final String predicateSource = minecraftDependPredicate();
        final VersionPredicate predicate = VersionPredicate.parse(predicateSource);

        final String below = previousPatch(declared.getFirst());
        if (below == null) {
            return; // patch 0: there is no previous patch on this minor line to probe.
        }

        assertFalse(
                predicate.test(Version.parse(below)),
                "depends.minecraft (\"" + predicateSource + "\") also accepts Minecraft " + below
                        + ", which is BELOW the declared band minimum " + declared.getFirst()
                        + ". The jar runs there but its name never says so, so those players cannot "
                        + "tell this build is for them.");
    }

    /**
     * A predicate with no upper bound claims every future Minecraft version, including ones that will
     * break it. This permits the {@code ~1.21.11} idiom (bounded at the next minor) and fails a bare
     * {@code >=1.21.6}.
     */
    @Test
    void theRangeIsNotUnboundedAboveTheBand() throws Exception {
        final List<String> declared = declaredBandVersions();
        final String predicateSource = minecraftDependPredicate();
        final VersionPredicate predicate = VersionPredicate.parse(predicateSource);

        final String nextMinorZero = nextMinor(declared.getLast());
        assertFalse(
                predicate.test(Version.parse(nextMinorZero)),
                "depends.minecraft (\"" + predicateSource + "\") accepts " + nextMinorZero
                        + ", so it is effectively unbounded above this band. A Minecraft version that "
                        + "does not exist yet cannot have been tested; the loader would let it boot "
                        + "anyway.");
    }

    // -----------------------------------------------------------------------------------------
    // The cross-check against what build.gradle really computed.
    // -----------------------------------------------------------------------------------------

    @Test
    void theBuildWiringIsPresent() {
        assertNotNull(
                System.getProperty("mcmmo.build.version"),
                "build.gradle no longer passes -Dmcmmo.build.version to the test JVM, so every "
                        + "cross-check in this class silently stopped testing build.gradle. Restore the "
                        + "systemProperty in the `test { }` block.");
        assertNotNull(
                System.getProperty("mcmmo.build.bandVersions"),
                "build.gradle no longer passes -Dmcmmo.build.bandVersions to the test JVM. Restore the "
                        + "systemProperty in the `test { }` block.");
    }

    /**
     * build.gradle must have parsed the same list this test reads off disk. Catches a stale
     * configuration cache and a mis-split property (a trailing comma yielding a phantom entry).
     */
    @Test
    void theBuildParsedTheSameBandList() {
        final String fromBuild = System.getProperty("mcmmo.build.bandVersions");
        assertNotNull(fromBuild, "see theBuildWiringIsPresent()");

        assertEquals(
                String.join(",", declaredBandVersions()),
                fromBuild,
                "build.gradle parsed a different band list than gradle.properties declares.");
    }

    /**
     * The exact string that becomes the jar filename and the mod's reported version. Two failures
     * live here: a label built from the wrong end of the list, and a version string the loader cannot
     * parse — which would be discovered at boot, per band, by a player.
     */
    @Test
    void theComputedVersionCarriesTheBandLabelAndIsLoaderParseable() throws Exception {
        final String fromBuild = System.getProperty("mcmmo.build.version");
        assertNotNull(fromBuild, "see theBuildWiringIsPresent()");

        final List<String> declared = declaredBandVersions();
        final String expectedLabel = expectedBandLabel(declared);
        final String expected = modVersion() + "+" + expectedLabel;

        assertEquals(
                expected,
                fromBuild,
                "the version build.gradle computed does not carry this band's label. The jar would be "
                        + "named after the wrong Minecraft versions.");

        // ⚠️ SEMANTIC, not merely "parsed". Measured against fabric-loader 0.19.3 on 2026-08-13:
        // Version.parse("not-a-version") does NOT throw — it returns a StringVersion, whose
        // getFriendlyString() echoes the input back verbatim. So asserting "it parsed" or "the label
        // survived the round-trip" passes for outright garbage; the check has to be that the result is
        // a SemanticVersion, which is what makes the version orderable at all. Build metadata after
        // '+' is exactly the part a hand-built version string gets wrong, and getting it wrong
        // downgrades the mod to a non-comparable version instead of failing.
        final Version parsed = Version.parse(fromBuild);
        assertInstanceOf(
                SemanticVersion.class,
                parsed,
                "Fabric could not read \"" + fromBuild + "\" as a semantic version; it degraded to "
                        + parsed.getClass().getSimpleName() + ", which no longer compares against "
                        + "other versions. Nothing at boot would report this.");
        assertTrue(
                parsed.getFriendlyString().contains(expectedLabel),
                "Fabric parsed \"" + fromBuild + "\" as \"" + parsed.getFriendlyString()
                        + "\", dropping the band label.");
    }

    // -----------------------------------------------------------------------------------------
    // Converse checks. A guard that has never failed is not known to work.
    // -----------------------------------------------------------------------------------------

    @Test
    void theLabelRuleDistinguishesASingleVersionFromARange() {
        assertEquals("mc1.21.5", expectedBandLabel(List.of("1.21.5")));
        assertEquals("mc1.21.6-1.21.8", expectedBandLabel(List.of("1.21.6", "1.21.7", "1.21.8")));
        assertEquals("mc1.21.9-1.21.10", expectedBandLabel(List.of("1.21.9", "1.21.10")));

        // The endpoints, not the count: a two-version band is still a range.
        assertFalse(
                expectedBandLabel(List.of("1.21.9", "1.21.10")).equals("mc1.21.9"),
                "a multi-version band must not be labelled with only its lower endpoint");
    }

    /**
     * The detector must fire on inputs it is supposed to reject. Without this, every assertion above
     * could be passing because the comparison is inert rather than because the repo is correct.
     */
    @Test
    void theDetectorFiresOnADriftedListAndOnAGap() throws Exception {
        final VersionPredicate band = VersionPredicate.parse(">=1.21.6 <1.21.9");

        // Claims a version the predicate refuses -> the filename-lies case.
        assertFalse(band.test(Version.parse("1.21.5")), "1.21.5 is outside >=1.21.6 <1.21.9");
        assertFalse(band.test(Version.parse("1.21.9")), "1.21.9 is outside >=1.21.6 <1.21.9");
        assertTrue(band.test(Version.parse("1.21.7")), "1.21.7 is inside >=1.21.6 <1.21.9");

        // The unbounded-above case that theRangeIsNotUnboundedAboveTheBand() must catch.
        assertTrue(
                VersionPredicate.parse(">=1.21.6").test(Version.parse("1.22.0")),
                "a bare >=1.21.6 does accept 1.22.0 — this is the condition that test rejects");
        assertFalse(
                VersionPredicate.parse("~1.21.11").test(Version.parse("1.22.0")),
                "~1.21.11 is bounded at the next minor — this is why the tilde idiom is permitted");

        // The gap case: nextPatch is what theDeclaredListIsAscendingAndHasNoGaps() compares against.
        assertEquals("1.21.7", nextPatch("1.21.6"));
        assertFalse("1.21.8".equals(nextPatch("1.21.6")), "a skipped 1.21.7 must not read as adjacent");

        // And a malformed version must be rejected rather than silently treated as zero.
        assertThrows(IllegalArgumentException.class, () -> nextPatch("1.21"));

        // ⚠️ Pins the trap that made the assertion above non-vacuous. Fabric does NOT reject a
        // nonsense version — it hands back a StringVersion that echoes the input, so a
        // "did it parse / did the label survive" check cannot tell garbage from a real version. If a
        // future loader starts throwing here instead, this fails and the sibling assertion can relax.
        final Version degraded = Version.parse("not-a-version");
        assertFalse(
                degraded instanceof SemanticVersion,
                "fabric-loader used to degrade an unparseable version to StringVersion rather than "
                        + "throwing; that is why the band-label check asserts SemanticVersion");
        assertEquals(
                "not-a-version",
                degraded.getFriendlyString(),
                "StringVersion echoes its input — the reason a contains() check alone is vacuous");
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    /** Mirrors the rule in build.gradle: one version is a point, more than one is an endpoint range. */
    private static String expectedBandLabel(List<String> declared) {
        return declared.size() == 1
                ? "mc" + declared.getFirst()
                : "mc" + declared.getFirst() + "-" + declared.getLast();
    }

    private static List<String> declaredBandVersions() {
        final Matcher matcher = BAND_VERSIONS.matcher(readStripped(GRADLE_PROPERTIES));
        assertTrue(
                matcher.find(),
                "gradle.properties declares no supported_minecraft_versions. build.gradle needs it to "
                        + "name the jar after the Minecraft versions this band's single jar runs on.");

        final List<String> versions = new ArrayList<>();
        for (String piece : matcher.group(1).split(",")) {
            final String trimmed = piece.trim();
            if (!trimmed.isEmpty()) {
                versions.add(trimmed);
            }
        }
        return versions;
    }

    private static String modVersion() {
        final Matcher matcher = MOD_VERSION.matcher(readStripped(GRADLE_PROPERTIES));
        assertTrue(matcher.find(), "gradle.properties declares no mod_version");
        return matcher.group(1);
    }

    private static String minecraftDependPredicate() {
        final Matcher matcher = MINECRAFT_DEPEND.matcher(read(FABRIC_MOD_JSON));
        assertTrue(matcher.find(), "fabric.mod.json declares no depends.minecraft");
        return matcher.group(1);
    }

    private static String nextPatch(String version) {
        final int[] parts = split(version);
        return parts[0] + "." + parts[1] + "." + (parts[2] + 1);
    }

    /** {@code null} when the patch is already 0 — there is no previous patch to probe. */
    private static String previousPatch(String version) {
        final int[] parts = split(version);
        return parts[2] == 0 ? null : parts[0] + "." + parts[1] + "." + (parts[2] - 1);
    }

    private static String nextMinor(String version) {
        final int[] parts = split(version);
        return parts[0] + "." + (parts[1] + 1) + ".0";
    }

    private static int[] split(String version) {
        final Matcher matcher = SEMVER.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "not a bare major.minor.patch version: \"" + version + "\"");
        }
        return new int[] {
            Integer.parseInt(matcher.group(1)),
            Integer.parseInt(matcher.group(2)),
            Integer.parseInt(matcher.group(3))
        };
    }

    /**
     * Comment lines are removed before matching. {@code gradle.properties} documents this band's
     * versions in prose directly above the property, so a naive match can bind to a commented-out or
     * illustrative value instead of the live one — the same trap {@link MixinAllowCoverageTest}
     * documents for javadoc.
     */
    private static String readStripped(Path file) {
        final StringBuilder stripped = new StringBuilder();
        for (String line : read(file).split("\\R")) {
            if (!line.stripLeading().startsWith("#")) {
                stripped.append(line).append('\n');
            }
        }
        return stripped.toString();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file.toAbsolutePath(), e);
        }
    }
}
