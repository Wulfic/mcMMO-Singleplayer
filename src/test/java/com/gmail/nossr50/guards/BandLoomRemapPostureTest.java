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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * <b>Proves this branch asks Loom for the remap behaviour its own Minecraft version requires</b> —
 * TODO §64.1, closing the carried box at *"`build.gradle:2`'s bare {@code fabric-loom} id"*.
 *
 * <h2>What the carried row asked, and the answer</h2>
 * The row recorded {@code master}'s id as *"the explicit non-remap id"* and the bands' bare id as
 * <b>inferred, not measured</b>. Measured from Loom 1.17.13's bytecode: it registers <b>five</b>
 * plugin ids in {@code META-INF/gradle-plugins/}, of which two are in use here —
 * {@code fabric-loom} to {@code LoomGradlePlugin}, and {@code net.fabricmc.fabric-loom} to
 * {@code LoomNoRemapGradlePlugin}.
 *
 * <p>⚠️ <b>Reading only those wrapper classes gives a convincing WRONG answer.</b> Both
 * {@code LoomNoRemapGradlePlugin} and {@code LoomRemapGradlePlugin} do nothing but
 * {@code plugins.apply("fabric-loom")} — the no-remap one first throwing
 * {@code IllegalStateException("net.fabricmc.fabric-loom must be applied before fabric-loom")} if
 * the bare id got there first. On that evidence the ids look interchangeable. They are not: the
 * branch lives in {@code LoomGradleExtensionImpl}'s constructor, which asks <em>which id was
 * applied</em> and, for the qualified one, sets {@code disableObfuscation=true} and
 * {@code finalizeValue()}s it — forced and unoverridable — which forces {@code dontRemap}. That is
 * why {@code ./gradlew remapJar} fails with <i>"Task 'remapJar' not found"</i> on {@code master}
 * and works on every band branch.
 *
 * <h2>🔑 Why this guard keys on {@code minecraft_version} and not on self-consistency</h2>
 * The first version of this guard checked only that {@code build.gradle} did not contradict
 * <em>itself</em>. <b>Measurement killed that design, and the way it died is worth keeping.</b>
 *
 * <p>Swapping <em>only</em> line 2 does not need a guard at all — Gradle already refuses it, loudly,
 * at configuration time, in both directions (measured in a scratch clone, 2026-09-10):
 *
 * <pre>
 *   master  -&gt; bare id       : "Configuration 'mappings' has no dependencies"
 *   band    -&gt; qualified id  : "Could not find method mappings() for arguments
 *                               [net.fabricmc:yarn:1.21.11+build.6:v2]"
 * </pre>
 *
 * <p>The dangerous edit is the <b>coordinated</b> one: converting a band to {@code master}'s whole
 * posture — qualified id <em>and</em> no {@code mappings} <em>and</em> plain {@code implementation}.
 * A self-consistency check calls that <b>coherent</b>, because it is; it is simply coherent about
 * the wrong Minecraft.
 *
 * <p>⚠️ <b>Measured, and the result is weaker than "it builds fine" — say so rather than overclaim.</b>
 * The converted band also fails today, but <b>incidentally and late</b>:
 *
 * <pre>
 *   band -&gt; full master posture : "Failed to process jar when running jar processor:
 *                                  fabric-loom:access-widener - Expected official namespace for
 *                                  access widener entry, found: intermediary in mod: cloth-config"
 * </pre>
 *
 * <p>🔑 That protection is <b>borrowed from an optional third-party dependency</b>. It fires only
 * because {@code cloth-config} happens to carry an access widener and happens to be on
 * {@code localRuntime}; drop or replace that dependency and the conversion has nothing left to trip
 * over. The error also names {@code cloth-config}, which is not the defect — three sessions could
 * reasonably chase the wrong dependency. <b>So the value here is not "nothing else catches it" —
 * it is that this names the cause immediately, and it keeps holding when the incidental catch goes
 * away.</b>
 *
 * <p>🔑 So the invariant is anchored to a fact the converting edit would have no reason to touch:
 * <b>{@code minecraft_version}</b>. Minecraft ships unobfuscated from {@code 26.1}; below that it
 * needs yarn and therefore remapping. A band keeps its own {@code minecraft_version} through any
 * build-script tidy-up — ruling R-a requires it, since no two branches may resolve to the same
 * value — which is exactly what makes it independent evidence here.
 *
 * <p><b>Internal consistency is not correctness</b>, the same shape as R-y's
 * <i>"cross-branch equality is not correctness"</i>. Both checks are kept: the version anchor for
 * correctness, the self-consistency rules to catch a half-finished edit early.
 *
 * <h2>Why per-branch, and not a cross-branch diff</h2>
 * A cross-branch check answers <i>"do the branches agree?"</i>, and here the correct answer is
 * <b>no</b> — {@code build.gradle} is deliberately outside gate 10's identity set because it
 * <em>must</em> differ, and gate 11 compares {@code gradle.properties} keys only. So this line is a
 * required per-band difference that no existing gate inspects. This test asks the per-branch
 * question instead, needs no remote and no second checkout, and is correct on all nine branches
 * without knowing which one it is running on.
 *
 * <h2>⚠️ The parsing trap, which produced a wrong reading once already</h2>
 * {@code master}'s dependency block carries the comment <i>"Nothing needs remapping either, so
 * these are plain {@code implementation}, not {@code modImplementation}"</i> — so a scan for the
 * token {@code modImplementation} finds one on the branch that must not have one, and the words
 * <i>"NO `mappings` line"</i> look like a mappings declaration. Comments are stripped first and
 * every pattern is anchored at a line start; {@link #comments_are_stripped_before_configurations_are_read()}
 * asserts it rather than trusting it.
 */
class BandLoomRemapPostureTest {

    private static final Path BUILD_GRADLE = Path.of("build.gradle");
    private static final Path GRADLE_PROPERTIES = Path.of("gradle.properties");

    /** Applying this id forces {@code disableObfuscation=true} and finalizes it. */
    static final String NO_REMAP_ID = "net.fabricmc.fabric-loom";

    /** The plain plugin. Obfuscation state stays computed, so remapping happens. */
    static final String REMAP_ID = "fabric-loom";

    /**
     * The first Minecraft release that ships unobfuscated, so no mappings and no remap are needed.
     *
     * <p>⚠️ A <b>property of Minecraft</b>, not of this build — safe to state, unlike a claim about
     * what version this branch targets, which rots the moment a band is cut.
     * {@code 26.1 > 1.21.11} sorts correctly under semver, so no special-casing is needed.
     */
    private static final int UNOBFUSCATED_FROM_MAJOR = 26;
    private static final int UNOBFUSCATED_FROM_MINOR = 1;

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("^\\s*//.*$", Pattern.MULTILINE);

    private static final Pattern LOOM_PLUGIN = Pattern.compile(
            "^\\s*id\\s+['\"]((?:net\\.fabricmc\\.)?fabric-loom(?:-[a-z]+)?)['\"]", Pattern.MULTILINE);

    /** A real {@code mappings "net.fabricmc:yarn:..."} dependency, never the word in a comment. */
    private static final Pattern MAPPINGS = Pattern.compile(
            "^\\s*mappings\\s+[\"']", Pattern.MULTILINE);

    private static final Pattern LOADER = Pattern.compile(
            "^\\s*(\\w+)\\s+[\"']net\\.fabricmc:fabric-loader:", Pattern.MULTILINE);

    private static final Pattern FABRIC_API = Pattern.compile(
            "^\\s*(\\w+)\\s+[\"']net\\.fabricmc\\.fabric-api:fabric-api:", Pattern.MULTILINE);

    private static final Pattern MINECRAFT_VERSION = Pattern.compile(
            "^\\s*minecraft_version\\s*=\\s*(\\S+)\\s*$", Pattern.MULTILINE);

    /** What one {@code build.gradle} declares about remapping. */
    record Posture(String loomPluginId, boolean mappingsDeclared, String loaderConfiguration,
                   String fabricApiConfiguration) {}

    // -------------------------------------------------------------------------------------------
    // The pure half. Kept off the filesystem so the cases below can feed it a branch that exists
    // nowhere -- a guard only ever observed saying YES has not been shown able to say NO.
    // -------------------------------------------------------------------------------------------

    static Posture parse(String buildScript) {
        final String code = LINE_COMMENT.matcher(
                BLOCK_COMMENT.matcher(buildScript).replaceAll("")).replaceAll("");
        return new Posture(
                firstGroup(LOOM_PLUGIN, code),
                MAPPINGS.matcher(code).find(),
                firstGroup(LOADER, code),
                firstGroup(FABRIC_API, code));
    }

    private static String firstGroup(Pattern pattern, String text) {
        final Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** {@code true} when this Minecraft ships unobfuscated and therefore must not be remapped. */
    static boolean shipsUnobfuscated(String minecraftVersion) {
        final String[] parts = minecraftVersion.split("[.+-]");
        final int major = Integer.parseInt(parts[0]);
        final int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return major > UNOBFUSCATED_FROM_MAJOR
                || (major == UNOBFUSCATED_FROM_MAJOR && minor >= UNOBFUSCATED_FROM_MINOR);
    }

    /**
     * Every way this branch's remap posture is wrong, in words a reader can act on. Empty is good.
     *
     * <p>The load-bearing rule is the first one: <b>the id must match what this branch's Minecraft
     * needs</b>. The rest catch a half-finished edit before it becomes a coherent wrong one.
     */
    static List<String> violations(Posture posture, String minecraftVersion) {
        final List<String> problems = new ArrayList<>();
        final String id = posture.loomPluginId();

        if (id == null) {
            problems.add("build.gradle applies no Loom plugin at all -- expected one of '"
                    + REMAP_ID + "' or '" + NO_REMAP_ID + "'");
            return problems;
        }
        if (!id.equals(REMAP_ID) && !id.equals(NO_REMAP_ID)) {
            problems.add("unrecognised Loom plugin id '" + id + "'. Only '" + REMAP_ID + "' and '"
                    + NO_REMAP_ID + "' have a measured remap posture; -remap, -companion and "
                    + "-repositories are different plugins and this guard cannot vouch for them");
            return problems;
        }

        final boolean remapDisabled = id.equals(NO_REMAP_ID);
        final boolean unobfuscated = shipsUnobfuscated(minecraftVersion);

        // The anchor. Everything below only catches an edit caught halfway.
        if (remapDisabled != unobfuscated) {
            problems.add(unobfuscated
                    ? "minecraft_version=" + minecraftVersion + " ships UNOBFUSCATED, so remapping "
                      + "must be off -- but build.gradle applies '" + id + "', which leaves it on"
                    : "minecraft_version=" + minecraftVersion + " is OBFUSCATED and needs yarn, so "
                      + "remapping must be ON -- but build.gradle applies '" + NO_REMAP_ID + "', "
                      + "which forces disableObfuscation=true and finalizes it. This branch would "
                      + "ship an UNREMAPPED, yarn-named jar that cannot run. Nothing else reports "
                      + "this: build.gradle is outside gate 10's identity set and gate 11 reads "
                      + "gradle.properties keys only");
        }

        final String expectedConfiguration = remapDisabled ? "implementation" : "modImplementation";
        if (remapDisabled && posture.mappingsDeclared()) {
            problems.add("'" + NO_REMAP_ID + "' forces disableObfuscation=true, so nothing is "
                    + "remapped -- but a `mappings` dependency is declared. Gradle refuses this "
                    + "outright: \"Could not find method mappings()\"");
        }
        if (!remapDisabled && !posture.mappingsDeclared()) {
            problems.add("'" + REMAP_ID + "' leaves remapping ON, but no `mappings` dependency is "
                    + "declared. Gradle refuses this outright: \"Configuration 'mappings' has no "
                    + "dependencies\"");
        }
        for (String each : new String[] {"fabric-loader", "fabric-api"}) {
            final String actual = each.equals("fabric-loader")
                    ? posture.loaderConfiguration() : posture.fabricApiConfiguration();
            if (actual == null) {
                problems.add(each + " is not declared at all");
            } else if (!actual.equals(expectedConfiguration)) {
                problems.add(each + " is on `" + actual + "` but the '" + id + "' posture requires `"
                        + expectedConfiguration + "`"
                        + (remapDisabled
                            ? " -- with remapping off, a `mod` configuration asks for a remap that "
                              + "will never run"
                            : " -- with remapping on, a plain configuration ships the dependency "
                              + "un-remapped"));
            }
        }
        return problems;
    }

    // -------------------------------------------------------------------------------------------
    // The real branch.
    // -------------------------------------------------------------------------------------------

    @Test
    void this_branch_asks_loom_for_the_remap_behaviour_its_minecraft_needs() {
        final String minecraftVersion = thisBranchMinecraftVersion();
        final Posture posture = parse(read(BUILD_GRADLE));
        assertEquals(List.of(), violations(posture, minecraftVersion),
                "build.gradle's remap posture does not match minecraft_version=" + minecraftVersion
                        + ". Posture read: " + posture);
    }

    /**
     * 🔑 <b>The non-vacuity proof for the assertion above, run against the REAL files on whatever
     * branch this is.</b>
     *
     * <p>The check above can only be trusted if its verdict actually depends on this branch's
     * {@code minecraft_version}. So take the real posture and ask it about the <em>other</em> era:
     * it must be rejected. A {@code build.gradle} that passes for both eras would mean the anchor
     * decides nothing and the green above is worth nothing — the exact shape of the ~17 vacuous
     * guards this repo has already caught, and the reason a one-sided guard pair proves only that
     * it can say NO (§61.6).
     *
     * <p>This is deliberately preferred over mutating a tracked file: the coordinated conversion
     * cannot be demonstrated end-to-end through Gradle at all, because configuration fails before
     * the test task runs.
     */
    @Test
    void the_real_posture_is_rejected_for_the_other_era() {
        final String thisVersion = thisBranchMinecraftVersion();
        final String otherEra = shipsUnobfuscated(thisVersion) ? "1.21.11" : "26.2";
        final Posture real = parse(read(BUILD_GRADLE));
        assertFalse(violations(real, otherEra).isEmpty(),
                "this branch's real build.gradle is accepted for BOTH the obfuscated and the "
                        + "unobfuscated era, so minecraft_version decides nothing and the passing "
                        + "assertion above is vacuous. Posture: " + real);
    }

    /** The version anchor has to actually parse on this branch, or the check above is vacuous. */
    @Test
    void this_branch_declares_a_parseable_minecraft_version() {
        final String version = thisBranchMinecraftVersion();
        assertTrue(version.matches("\\d+\\.\\d+.*"), "unparseable minecraft_version=" + version);
    }

    private static String thisBranchMinecraftVersion() {
        final Matcher matcher = MINECRAFT_VERSION.matcher(read(GRADLE_PROPERTIES));
        assertTrue(matcher.find(), "gradle.properties declares no minecraft_version");
        return matcher.group(1);
    }

    // -------------------------------------------------------------------------------------------
    // The two real postures, and the version each belongs to.
    // -------------------------------------------------------------------------------------------

    private static final String MASTER_SHAPED = """
            plugins {
                id 'net.fabricmc.fabric-loom' version '1.17.13'
            }
            dependencies {
                minecraft "com.mojang:minecraft:${project.minecraft_version}"
                // NO `mappings` line. From 26.1 Minecraft ships unobfuscated.
                // Nothing needs remapping either, so these are plain `implementation`,
                // not `modImplementation`.
                implementation "net.fabricmc:fabric-loader:${project.loader_version}"
                implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
            }
            """;

    private static final String BAND_SHAPED = """
            plugins {
                id 'fabric-loom' version '1.17.13'
            }
            dependencies {
                minecraft "com.mojang:minecraft:${project.minecraft_version}"
                mappings "net.fabricmc:yarn:${project.yarn_mappings}:v2"
                modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
                modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
            }
            """;

    /**
     * ⚠️ <b>Ties the fixtures to reality.</b> Every rejection case below is built from
     * {@link #MASTER_SHAPED} / {@link #BAND_SHAPED}, so if those drift away from the real build
     * scripts the cases stop covering anything real while still passing. The real branch must parse
     * to exactly one of them.
     */
    @Test
    void this_branch_matches_one_of_the_two_declared_postures() {
        final Posture real = parse(read(BUILD_GRADLE));
        assertTrue(real.equals(parse(MASTER_SHAPED)) || real.equals(parse(BAND_SHAPED)),
                "this branch's build.gradle parses to a posture matching neither fixture, so the "
                        + "rejection cases in this class no longer describe a real branch. Real: "
                        + real + " | master fixture: " + parse(MASTER_SHAPED)
                        + " | band fixture: " + parse(BAND_SHAPED));
    }

    @Test
    void the_master_posture_is_correct_for_an_unobfuscated_version() {
        assertEquals(List.of(), violations(parse(MASTER_SHAPED), "26.2"));
    }

    @Test
    void the_band_posture_is_correct_for_an_obfuscated_version() {
        assertEquals(List.of(), violations(parse(BAND_SHAPED), "1.21.11"));
    }

    @Test
    void comments_are_stripped_before_configurations_are_read() {
        final Posture posture = parse(MASTER_SHAPED);
        assertFalse(posture.mappingsDeclared(),
                "the words 'NO `mappings` line' in a comment were read as a mappings dependency");
        assertEquals("implementation", posture.loaderConfiguration(),
                "the word `modImplementation` in a comment was read as the real configuration");
    }

    @Test
    void the_unobfuscated_boundary_sits_between_the_two_lines() {
        assertFalse(shipsUnobfuscated("1.21.11"));
        assertFalse(shipsUnobfuscated("1.20.6"));
        assertTrue(shipsUnobfuscated("26.1"));
        assertTrue(shipsUnobfuscated("26.1.2"));
        assertTrue(shipsUnobfuscated("26.2"));
    }

    // -------------------------------------------------------------------------------------------
    // Proof the guard can say NO. The first case is the one that matters: it is the only one Gradle
    // does NOT already refuse, and it is the reason this guard keys on minecraft_version.
    // -------------------------------------------------------------------------------------------

    /**
     * 🔴 <b>The silent catastrophe.</b> A band converted wholesale to {@code master}'s posture is
     * internally coherent, configures cleanly and builds — and ships an unremapped, yarn-named jar
     * against an obfuscated Minecraft. The earlier self-consistency-only design passed this.
     */
    @Test
    void a_band_fully_converted_to_the_master_posture_is_rejected() {
        final List<String> problems = violations(parse(MASTER_SHAPED), "1.21.11");
        assertFalse(problems.isEmpty(),
                "a 1.21.11 band carrying master's entire build posture is internally coherent and "
                        + "would ship an unremapped jar -- this is the case Gradle does NOT catch, "
                        + "and the guard reported nothing");
        assertTrue(problems.stream().anyMatch(p -> p.contains("UNREMAPPED")),
                "expected the unremapped-jar consequence to be spelled out; got " + problems);
    }

    /** The mirror: {@code master} on the band posture. Also coherent, also wrong. */
    @Test
    void master_fully_converted_to_the_band_posture_is_rejected() {
        final List<String> problems = violations(parse(BAND_SHAPED), "26.2");
        assertFalse(problems.isEmpty(),
                "26.2 carrying the yarn band posture was accepted");
        assertTrue(problems.stream().anyMatch(p -> p.contains("UNOBFUSCATED")), problems.toString());
    }

    @Test
    void a_band_with_only_line_two_swapped_is_rejected() {
        final List<String> problems = violations(
                parse(BAND_SHAPED.replace("id 'fabric-loom'", "id '" + NO_REMAP_ID + "'")), "1.21.11");
        assertFalse(problems.isEmpty(), "a half-finished conversion was accepted");
    }

    @Test
    void a_band_that_lost_its_mappings_line_is_rejected() {
        final List<String> problems = violations(
                parse(BAND_SHAPED.replaceAll("(?m)^\\s*mappings .*$\\R", "")), "1.21.11");
        assertFalse(problems.isEmpty(), "a band with remapping on and no mappings was accepted");
    }

    @Test
    void a_sibling_loom_plugin_id_is_refused_rather_than_guessed() {
        final List<String> problems = violations(
                parse(BAND_SHAPED.replace("id 'fabric-loom'", "id 'net.fabricmc.fabric-loom-remap'")),
                "1.21.11");
        assertFalse(problems.isEmpty(),
                "an unrecognised Loom id must be refused, not assumed to behave like a known one");
        assertTrue(problems.stream().anyMatch(p -> p.contains("unrecognised")), problems.toString());
    }

    @Test
    void a_build_script_with_no_loom_plugin_is_rejected() {
        assertFalse(violations(parse("plugins {\n    id 'java'\n}\n"), "26.2").isEmpty(),
                "a build script applying no Loom plugin was accepted");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path.toAbsolutePath()
                    + " -- this guard reads the checkout, so it must run from the project root", e);
        }
    }
}
