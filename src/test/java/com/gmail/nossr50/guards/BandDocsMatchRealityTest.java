package com.gmail.nossr50.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * <b>Proves this band's own documentation does not deny that this band exists</b> — risk R9's
 * recorded instance, which no propagation check can reach.
 *
 * <h2>The defect this exists for</h2>
 * When {@code mc/1.21.4} was cut, built, gated and released, {@code README.md} and six wiki pages
 * went on saying <i>"Minecraft 1.21.4 and older are not supported"</i> for a whole session. That
 * band's own players were told their jar did not exist, on the page they would read first.
 *
 * <h2>⚠️ Why `drift-audit.py` structurally cannot catch this, and neither can a cross-branch diff</h2>
 * The obvious reading is "a docs fix was forgotten on a band", and it is <b>wrong</b>. The docs were
 * <b>byte-identical on all five branches</b> — and identically wrong. Every drift audit in that
 * window read clean and was right to; so would {@code git diff master <band> -- README.md wiki/}.
 *
 * <p>🔑 <b>Cross-branch equality is not correctness.</b> Propagation checks answer <i>"do the
 * branches agree?"</i>; this one answers <i>"is what they agree on true here?"</i> — a different
 * question needing a different instrument, which is why R9 was split rather than widened.
 *
 * <h2>Why the check has to be per-band, and why it passes on master</h2>
 * One wiki serves every band, so <i>"X is not supported"</i> reads as <i>"your jar does not exist"</i>
 * to whichever band is below the stated floor — and to nobody else. That is exactly what happened:
 * the sentence was true from {@code master}, where it still is, and false on the band that had just
 * shipped. So this test is <b>expected to pass on every branch today and to fail on the next band
 * cut below the documented floor, before that band ever ships.</b> A green run here is not evidence
 * the sentence is right in general; it is evidence it is right <em>from this branch</em>, which is
 * the only claim a reader of this branch's docs can act on.
 *
 * <p>⚠️ <b>Next cut this fires on:</b> the first {@code 1.20.x} band (TODO §22, ruling R-v). The
 * floor sentence must move in the same commit, in both files. It reads {@code 1.20.6} today, moved
 * there when {@code mc/1.21.1} was cut; 8.2 and 8.3 have both shipped.
 *
 * <p>⚠️ <b>This pointer is a dated note, and it rots.</b> It has already been stale once — it named
 * {@code mc/1.21.3} through two completed band cuts, because nothing here reads a javadoc. Treat it
 * as a hint about where to look, never as the current state; {@code gradle.properties} and the two
 * documents are the facts, and the assertions below read those.
 */
class BandDocsMatchRealityTest {

    private static final Path GRADLE_PROPERTIES = Path.of("gradle.properties");
    private static final Path README = Path.of("README.md");
    private static final Path INSTALLATION = Path.of("wiki", "Installation.md");

    /** {@code supported_minecraft_versions=1.21.6,1.21.7,1.21.8}, ignoring comment lines. */
    private static final Pattern SUPPORTED = Pattern.compile(
            "^\\s*supported_minecraft_versions\\s*=\\s*(\\S.*?)\\s*$", Pattern.MULTILINE);

    /**
     * The support-floor sentence. Matches both bolding styles in use — {@code README.md} writes
     * {@code Minecraft **1.21.3 and older are not supported**} and {@code wiki/Installation.md}
     * writes {@code **Minecraft 1.21.3 and older are not supported**} — because pinning the
     * asterisks would make this guard fail on a reformat rather than on a lie.
     */
    private static final Pattern FLOOR =
            Pattern.compile("(\\d+(?:\\.\\d+)+)\\s+and older are not supported");

    // --- 1. This band is in the matrix at all -------------------------------------------------

    @Test
    void everyVersionThisBandShipsAppearsInTheReadme() {
        final List<String> versions = supportedVersions();
        final String readme = read(README);

        for (String version : versions) {
            assertTrue(readme.contains(version), () -> "gradle.properties says this band ships "
                    + "Minecraft " + version + ", but README.md never mentions that version. A "
                    + "player on " + version + " has no way to learn which jar is theirs. Declared "
                    + "band: " + versions);
        }
    }

    // --- 2. The recorded instance: the docs must not deny this band -----------------------------

    @Test
    void theUnsupportedFloorSitsBelowEveryVersionThisBandShips() {
        final List<String> versions = supportedVersions();

        for (Path doc : List.of(README, INSTALLATION)) {
            final List<String> floors = floorsIn(read(doc));

            // Anti-vacuity: if the sentence is reworded, this guard silently stops guarding. A
            // doc with no floor claim at all is a finding, not a pass.
            assertFalse(floors.isEmpty(), () -> doc + " no longer contains an \"<version> and older "
                    + "are not supported\" sentence. Either the support floor stopped being "
                    + "documented -- which is its own defect -- or it was reworded and this guard "
                    + "now checks nothing. Re-point the pattern; do not delete the test.");

            for (String floor : floors) {
                for (String shipped : versions) {
                    assertTrue(compare(floor, shipped) < 0, () -> doc + " says Minecraft " + floor
                            + " and older are not supported, but this branch SHIPS " + shipped
                            + " (gradle.properties: " + versions + ").\n"
                            + "One wiki serves every band, so this sentence tells this band's own "
                            + "players their jar does not exist. That is R9's recorded instance "
                            + "verbatim: mc/1.21.4 shipped and released while six pages still "
                            + "denied it, and every drift audit read clean throughout because the "
                            + "docs were byte-identical on all five branches and identically "
                            + "wrong.\n"
                            + "Move the floor sentence in BOTH README.md and wiki/Installation.md.");
                }
            }
        }
    }

    @Test
    void theTwoDocsStateTheSameFloor() {
        final List<String> readmeFloors = floorsIn(read(README));
        final List<String> wikiFloors = floorsIn(read(INSTALLATION));

        assertFalse(readmeFloors.isEmpty(), () -> "no floor sentence in " + README);
        assertFalse(wikiFloors.isEmpty(), () -> "no floor sentence in " + INSTALLATION);

        assertEquals(readmeFloors.get(0), wikiFloors.get(0),
                () -> README + " says the floor is " + readmeFloors.get(0) + " but " + INSTALLATION
                        + " says " + wikiFloors.get(0) + ". They are read by the same person about "
                        + "the same question, and the caveat-expiry rule exists because the page "
                        + "carrying a stale claim is almost never the page a fix touched.");
    }

    // --- 3. The detector must be able to fail ---------------------------------------------------

    /**
     * Drives the same comparison over synthetic documents. Without this the guard above is a
     * tautology on every branch where the docs happen to be right — which is every branch today,
     * and is precisely how a guard ships without ever having failed. Seventh sighting in this
     * project; this one has been made to fail on purpose.
     */
    @Test
    void theDetectorFiresOnADocThatDeniesThisBand() {
        final List<String> versions = supportedVersions();
        final String newest = versions.get(versions.size() - 1);
        final String oldest = versions.get(0);

        // A doc claiming everything up to and including our newest version is unsupported.
        final List<String> denies = floorsIn("Minecraft **" + newest + " and older are not "
                + "supported**, and neither is the `26.x` line yet.");
        assertEquals(List.of(newest), denies, () -> "the floor pattern did not parse a sentence it "
                + "must parse; the real check above is reading nothing.");
        assertFalse(compare(denies.get(0), oldest) < 0,
                () -> "a doc denying " + newest + " was judged acceptable for a band shipping "
                        + oldest + "-" + newest + ". The comparison is inverted or stuck, so "
                        + "theUnsupportedFloorSitsBelowEveryVersionThisBandShips proves nothing.");

        // ...and the converse of the converse: a genuinely lower floor must still be accepted, or
        // this guard would fail every branch forever and get deleted rather than heeded.
        assertTrue(compare("1.20.6", oldest) < 0,
                () -> "a floor of 1.20.6 was rejected for a band shipping " + oldest
                        + "; the comparison rejects valid documentation.");
    }

    @Test
    void theVersionComparisonHandlesMultiDigitPatches() {
        // 1.21.4 vs 1.21.11 is the case a string compare gets wrong, and it is not hypothetical --
        // it is the exact pair in this project's band table.
        assertTrue(compare("1.21.4", "1.21.11") < 0, "1.21.4 must sort BELOW 1.21.11");
        assertTrue(compare("1.21.11", "1.21.4") > 0, "1.21.11 must sort ABOVE 1.21.4");
        assertEquals(0, compare("1.21.5", "1.21.5"), "equal versions must compare equal");
        assertTrue(compare("1.21", "1.21.1") < 0, "a shorter version is the earlier one");
    }

    // --- Plumbing ---------------------------------------------------------------------------------

    /** This branch's declared band, ascending. */
    private static List<String> supportedVersions() {
        final Matcher m = SUPPORTED.matcher(stripComments(read(GRADLE_PROPERTIES)));
        assertTrue(m.find(), () -> GRADLE_PROPERTIES + " declares no supported_minecraft_versions, "
                + "so this guard cannot know what this band ships.");
        final List<String> versions = new ArrayList<>();
        for (String raw : m.group(1).split(",")) {
            final String v = raw.trim();
            if (!v.isEmpty()) {
                versions.add(v);
            }
        }
        assertFalse(versions.isEmpty(), "supported_minecraft_versions must not be empty");
        return versions;
    }

    private static List<String> floorsIn(String document) {
        final List<String> found = new ArrayList<>();
        final Matcher m = FLOOR.matcher(document);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    /** Numeric dotted-version compare. {@code 1.21.4 < 1.21.11}, which a string compare gets wrong. */
    private static int compare(String a, String b) {
        final int[] left = parts(a);
        final int[] right = parts(b);
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            final int l = i < left.length ? left[i] : 0;
            final int r = i < right.length ? right[i] : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static int[] parts(String version) {
        return Arrays.stream(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
    }

    /** Drops {@code #} comment lines so a commented-out property cannot be read as live. */
    private static String stripComments(String properties) {
        final StringBuilder out = new StringBuilder(properties.length());
        for (String line : properties.split("\n", -1)) {
            out.append(line.stripLeading().startsWith("#") ? "" : line).append('\n');
        }
        return out.toString();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file.toAbsolutePath()
                    + " -- this guard reads the shipped docs, so their absence is a finding", e);
        }
    }
}
