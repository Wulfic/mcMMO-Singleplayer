package com.gmail.nossr50.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * <b>The per-band toolchain guard</b> (ruling R-aa, multi-version TODO &sect;41.1): the JDK level
 * this band compiles against is declared <em>once</em>, in {@code gradle.properties}, and every
 * consumer reads it.
 *
 * <p><b>Why this guard exists.</b> {@code 26.x} requires Java 25 — Mojang's own version manifest
 * says {@code javaVersion.majorVersion: 25} for {@code 26.1} through {@code 26.2} — while every
 * {@code 1.21.x} band requires 21. That number used to be hardcoded in <b>five</b> places: four in
 * {@code build.gradle} ({@code sourceCompatibility}, {@code targetCompatibility}, the toolchain and
 * {@code options.release}) and a fifth in {@code .github/workflows/release.yml}, which was stuck at
 * {@code '21'} on <em>all eight branches including the one that needs 25</em>.
 *
 * <p>{@code release.yml} cannot simply be corrected per branch: it must stay <b>byte-identical</b>
 * on every branch under <b>P19-1</b>, and no single hardcoded number satisfies both sides of the
 * boundary. So the workflow refers to the key and the <em>value</em> is per-band, exactly like
 * {@code minecraft_version}.
 *
 * <p><b>&#9888;&#9888; The assertion that carries the weight is
 * {@link #theCompiledBytecodeTargetsTheDeclaredLevel()}.</b> Checking that the build handed us a
 * number matching {@code gradle.properties} proves only that two strings agree — a
 * {@code build.gradle} that had kept its literal {@code 25} and also passed a literal {@code 25} to
 * the test would pass it. Reading the class-file major version out of this very test's own
 * {@code .class} bytes proves {@code javac} actually ran with {@code --release <n>}, which is the
 * fact the players' launcher enforces.
 *
 * <p>The failure mode being prevented is silent in every direction that matters: a band compiled
 * against the wrong release <em>builds</em> clean, <em>tests</em> clean, and dies at the player's
 * launcher with {@code UnsupportedClassVersionError}. No gate in this repository would see it.
 *
 * <p>&#9888; <b>This class must not live in {@code com.gmail.nossr50.fabric.mixin}</b> — same
 * reason as {@link BandVersionLabelTest}: the Mixin transformer claims every class in the package
 * {@code mcmmo.mixins.json} declares, including a test.
 */
class BandToolchainLevelTest {

    /** Relative to the project dir, which Gradle sets as the test working directory. */
    private static final Path GRADLE_PROPERTIES = Path.of("gradle.properties");

    private static final Path RELEASE_WORKFLOW =
            Path.of(".github", "workflows", "release.yml");

    /** {@code java_version=25} — ignores comment lines. */
    private static final Pattern JAVA_VERSION =
            Pattern.compile("^\\s*java_version\\s*=\\s*(\\S.*?)\\s*$", Pattern.MULTILINE);

    /**
     * A hardcoded JDK level in the workflow — {@code java-version: '21'} or {@code java-version: 21}.
     *
     * <p>Deliberately does NOT match {@code java-version: ${{ ... }}}, which is the form R-aa
     * requires: {@code $} is excluded from the value.
     */
    private static final Pattern HARDCODED_JAVA_VERSION =
            Pattern.compile("^\\s*java-version\\s*:\\s*['\"]?(\\d+)['\"]?\\s*$", Pattern.MULTILINE);

    /**
     * The step must write the value it read into {@code $GITHUB_OUTPUT}, or the output is empty.
     *
     * <p>&#9888; This exists because its absence was <em>measured</em> vacuous: deleting the export
     * left the suite green, since the two obvious assertions still matched other text in the file.
     */
    private static final Pattern JDK_OUTPUT_EXPORT =
            Pattern.compile("java_version=\\$\\w+\"?\\s*>>\\s*\"?\\$GITHUB_OUTPUT");

    /** The class-file major version for Java N is N + 44 (Java 8 is 52, 21 is 65, 25 is 69). */
    private static final int CLASS_FILE_MAJOR_OFFSET = 44;

    // -----------------------------------------------------------------------------------------
    // The load-bearing direction: what the compiler ACTUALLY did, not what a file says it did.
    // -----------------------------------------------------------------------------------------

    /**
     * The bytecode this suite is running from must target exactly the declared level.
     *
     * <p>This is the assertion that cannot be satisfied by two files agreeing with each other. If
     * {@code build.gradle} ignored {@code java_version} and kept a literal, the class files would
     * carry the literal's major version and this fails.
     */
    @Test
    void theCompiledBytecodeTargetsTheDeclaredLevel() throws Exception {
        final int declared = declaredJavaVersion();
        final int major = classFileMajorVersion();
        assertEquals(declared + CLASS_FILE_MAJOR_OFFSET, major,
                () -> "gradle.properties declares java_version=" + declared + ", so javac should "
                        + "have compiled with --release " + declared + " and emitted class-file "
                        + "major version " + (declared + CLASS_FILE_MAJOR_OFFSET) + ". This class "
                        + "is actually major version " + major + " (Java "
                        + (major - CLASS_FILE_MAJOR_OFFSET) + "). build.gradle is not reading the "
                        + "key -- see ruling R-aa. A band compiled against the wrong release builds "
                        + "clean, tests clean, and fails at the player's launcher with "
                        + "UnsupportedClassVersionError.");
    }

    /**
     * The build resolved the same level the file declares.
     *
     * <p>Weaker than the bytecode check above and kept because it names the <em>wiring</em>
     * specifically: it fails when {@code build.gradle} stops handing the value over, which is the
     * first thing a well-meaning simplification deletes.
     */
    @Test
    void theBuildResolvedTheDeclaredLevel() {
        final String resolved = System.getProperty("mcmmo.build.javaVersion");
        assertNotNull(resolved, "build.gradle no longer hands the test suite "
                + "mcmmo.build.javaVersion. Without it this guard cannot tell what the build "
                + "actually resolved and would be checking gradle.properties against itself.");
        assertEquals(declaredJavaVersion(), Integer.parseInt(resolved.trim()),
                () -> "the build resolved Java " + resolved + " but gradle.properties declares "
                        + declaredJavaVersion() + ".");
    }

    // -----------------------------------------------------------------------------------------
    // The half P19-1 needs: release.yml states no band's number, on any branch.
    // -----------------------------------------------------------------------------------------

    @Test
    void theReleaseWorkflowPinsNoJdkLevelOfItsOwn() {
        final String yaml = read(RELEASE_WORKFLOW);
        final Matcher m = HARDCODED_JAVA_VERSION.matcher(yaml);
        assertFalse(m.find(), () -> RELEASE_WORKFLOW + " hardcodes java-version: "
                + m.group(1) + ". That number is PER-BAND -- 26.x needs 25, the 1.21.x bands need "
                + "21 -- and this file must stay byte-identical on every branch under P19-1, so no "
                + "literal can be correct everywhere. It was pinned '21' on all eight branches "
                + "including the one needing 25, and nothing said so. Read java_version out of "
                + "gradle.properties instead; see ruling R-aa.");
    }

    @Test
    void theReleaseWorkflowReadsTheLevelFromGradleProperties() {
        final String yaml = read(RELEASE_WORKFLOW);
        assertTrue(yaml.contains("java_version=") && yaml.contains("gradle.properties"),
                () -> RELEASE_WORKFLOW + " no longer reads java_version out of gradle.properties. "
                        + "Without that step setup-java has no per-band level to install and the "
                        + "workflow either pins a number (which P19-1 forbids) or installs the "
                        + "runner's default (which nothing declares).");
        assertTrue(yaml.contains("steps.jdk.outputs.java_version"),
                () -> RELEASE_WORKFLOW + " reads java_version but no longer feeds it to setup-java "
                        + "via steps.jdk.outputs.java_version. Reading a value and not using it is "
                        + "the shape of every vacuous guard in this repository.");
        // ⚠️ The step must EXPORT the value, not merely read it. Measured 2026-08-26: without this
        // assertion, deleting the $GITHUB_OUTPUT write left the whole suite green -- the two checks
        // above still matched, because `grep -E '^java_version='` contains the string "java_version="
        // and the setup-java line still referenced an output that no longer existed. The workflow
        // would have installed nothing and failed only in CI, on a real release run.
        assertTrue(JDK_OUTPUT_EXPORT.matcher(yaml).find(),
                () -> RELEASE_WORKFLOW + " reads java_version and references "
                        + "steps.jdk.outputs.java_version, but never WRITES java_version to "
                        + "$GITHUB_OUTPUT. A step output that is never exported is empty, and "
                        + "setup-java would be handed an empty java-version on every release run.");
    }

    /**
     * The reading step must come BEFORE {@code setup-java}, or it cannot inform it.
     *
     * <p>Ordering assertions look pedantic until they fire. {@code BandVersionLabelTest} carries the
     * same shape for the stale-{@code mod_version} refusal, and it exists because moving that step
     * below the tag push silently disarmed it.
     */
    @Test
    void theLevelIsReadBeforeTheJdkIsInstalled() {
        final String yaml = read(RELEASE_WORKFLOW);
        final int read = yaml.indexOf("Read the JDK level this band builds with");
        final int setup = yaml.indexOf("actions/setup-java@");
        assertTrue(read >= 0, () -> RELEASE_WORKFLOW + " no longer has the \"Read the JDK level "
                + "this band builds with\" step.");
        assertTrue(setup >= 0, () -> RELEASE_WORKFLOW + " no longer sets up a JDK at all.");
        assertTrue(read < setup, () -> RELEASE_WORKFLOW + " reads the JDK level AFTER installing a "
                + "JDK. setup-java has to know the level before it installs anything, so in that "
                + "order the workflow silently builds with whatever the runner defaults to.");
    }

    // -----------------------------------------------------------------------------------------
    // The declared value itself.
    // -----------------------------------------------------------------------------------------

    @Test
    void theDeclaredLevelIsAPlainMajorVersion() {
        final String raw = rawJavaVersion();
        assertTrue(raw.matches("\\d+"), () -> "gradle.properties has java_version='" + raw
                + "'. Write the major version alone, e.g. java_version=21 -- both build.gradle and "
                + "release.yml parse it as a plain integer.");
        final int level = Integer.parseInt(raw);
        assertTrue(level >= 21, () -> "gradle.properties declares java_version=" + level
                + ". No supported band builds below Java 21 (the 1.21.x line's requirement), so "
                + "this is a typo or a copy from somewhere older.");
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private int declaredJavaVersion() {
        return Integer.parseInt(rawJavaVersion());
    }

    private String rawJavaVersion() {
        final Matcher m = JAVA_VERSION.matcher(read(GRADLE_PROPERTIES));
        assertTrue(m.find(), () -> GRADLE_PROPERTIES + " declares no java_version. It is a PER-BAND "
                + "fact (26.x needs 25, the 1.21.x bands need 21) that build.gradle and "
                + "release.yml both read from there -- see ruling R-aa. build.gradle refuses to "
                + "configure without it, so a green run here with the key missing is impossible; "
                + "if you are reading this, the key was removed and something re-added a literal.");
        return m.group(1).trim();
    }

    /** Bytes 6-7 of any class file are its major version, big-endian. */
    private int classFileMajorVersion() throws IOException {
        final String resource = "/" + getClass().getName().replace('.', '/') + ".class";
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in, () -> "cannot read this test's own class file at " + resource
                    + ", so the compiled bytecode level cannot be checked.");
            final byte[] head = in.readNBytes(8);
            assertEquals(8, head.length, "class file is truncated");
            assertEquals((byte) 0xCA, head[0], "not a class file (bad magic)");
            return ((head[6] & 0xFF) << 8) | (head[7] & 0xFF);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath(), e);
        }
    }
}
