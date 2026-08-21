package com.gmail.nossr50.platform;

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
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * <b>The Phase 2 build guard</b> (multi-version TODO §2.3): no Minecraft or Fabric type may be named
 * outside {@code fabric/} and {@code platform/}.
 *
 * <p>Phase 2 sealed 26 leak sites to zero. This is what keeps them at zero. Under the branch-per-band
 * strategy (ruling R-a) every file that names a Minecraft type is a file that can <em>diverge per
 * band</em>; the 175 Minecraft-free sources, the 357 resources and the Minecraft-free tests stay
 * byte-identical on every band only for as long as this holds. One re-added import in a skill manager
 * turns a version-agnostic file into a per-branch merge conflict, silently, until a back-port fails.
 *
 * <p><b>Zero exceptions, hard fail</b> (ruling P2-c). There is no allowlist and no escape hatch, on
 * purpose: <i>an allowlist is where sealed boundaries go to rot.</i> If a new file genuinely needs a
 * Minecraft type, it belongs in {@code platform/} (or {@code fabric/}), or the logic that needs it
 * belongs outside — that decision is the point of the boundary, and it must not be settleable by
 * appending a line here.
 *
 * <p><b>Scope: {@code src/main/java} only</b> (ruling P2-e), matching the TODO's own acceptance
 * criterion. Test-side Minecraft imports stay legal — {@code McTestRegistries} and
 * {@code McRegistryBootstrapProbeTest} are registry-bootstrap harnesses that <em>cannot</em> be
 * Minecraft-free, so policing {@code src/test/java} would immediately require the exemption list
 * P2-c forbids.
 *
 * <p><b>Two forms are policed</b>, because the import is only the obvious one:
 * <ol>
 *   <li>{@code import net.minecraft.…} / {@code import net.fabricmc.…} — the acceptance criterion;</li>
 *   <li>a fully-qualified {@code net.minecraft.Foo} written inline, which needs no import at all and
 *       would otherwise be a silent way back in. There are none today, so closing it cost nothing.</li>
 * </ol>
 *
 * <p><b>Why this guard is not vacuous.</b> Two prior guards in this project passed while proving
 * nothing — one was driven by the very table it validated, another read the wrong source and got the
 * right number. So this file carries its own converse checks:
 * {@link #theDetectorFlagsAViolationAndClearsCleanSource()} runs the detector over fabricated sources
 * and asserts it fires on a violation <em>and</em> stays quiet on a clean file;
 * {@link #theScanReachesTheRealSources()} asserts the walk found a plausible number of files and that
 * both boundary packages are non-empty, so a mis-resolved path cannot pass as "no violations".
 */
class PlatformBoundaryGuardTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    /** The two packages allowed to name a Minecraft type. Matched on the path, not the package line. */
    private static final List<String> BOUNDARY_SEGMENTS = List.of("/fabric/", "/platform/");

    /** {@code import [static] net.minecraft…;} — the acceptance criterion's form. */
    private static final Pattern MC_IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?net\\.(?:minecraft|fabricmc)\\.");

    /** A fully-qualified reference written inline, needing no import. */
    private static final Pattern MC_QUALIFIED = Pattern.compile(
            "\\bnet\\.(?:minecraft|fabricmc)\\.");

    // --- The property ----------------------------------------------------------------------------

    @Test
    void noMinecraftImportsOutsideTheBoundary() {
        final List<String> violations = new ArrayList<>();
        for (Path file : nonBoundarySources()) {
            if (MC_IMPORT.matcher(strip(read(file))).find()) {
                violations.add(file.toString());
            }
        }
        assertTrue(violations.isEmpty(),
                () -> "These files import a Minecraft/Fabric type outside fabric/ + platform/. Move "
                        + "the Minecraft-typed part behind an adapter in platform/ — do not add an "
                        + "exemption here (ruling P2-c):\n  " + String.join("\n  ", violations));
    }

    @Test
    void noFullyQualifiedMinecraftReferencesOutsideTheBoundary() {
        final List<String> violations = new ArrayList<>();
        for (Path file : nonBoundarySources()) {
            final String code = withoutImports(strip(read(file)));
            if (MC_QUALIFIED.matcher(code).find()) {
                violations.add(file.toString());
            }
        }
        assertTrue(violations.isEmpty(),
                () -> "These files name a Minecraft/Fabric type by its fully-qualified name outside "
                        + "fabric/ + platform/, which skips the import the other check looks for:\n  "
                        + String.join("\n  ", violations));
    }

    // --- Converse checks: prove the guard can fail ------------------------------------------------

    @Test
    void theDetectorFlagsAViolationAndClearsCleanSource() {
        // A guard that has never failed is not known to work. Rather than relying on someone
        // remembering to hand-mutate a source file, the detector is run here over fabricated ones.
        final String violating = """
                package com.gmail.nossr50.skills.mining;
                import java.util.List;
                import net.minecraft.world.item.ItemStack;
                class Example { }
                """;
        final String staticViolating = """
                package com.gmail.nossr50.skills.mining;
                import static net.minecraft.world.item.Items.DIRT;
                class Example { }
                """;
        final String qualifiedViolating = """
                package com.gmail.nossr50.skills.mining;
                class Example { net.minecraft.world.item.ItemStack held; }
                """;
        final String clean = """
                package com.gmail.nossr50.skills.mining;
                import com.gmail.nossr50.platform.PlatformItem;
                import java.util.List;
                /** Mentions net.minecraft.world.item.ItemStack only in prose. */
                class Example {
                    // and in a comment: import net.minecraft.world.item.Items;
                    String s = "net.minecraft.world.item.ItemStack";
                }
                """;

        assertTrue(MC_IMPORT.matcher(strip(violating)).find(), "a plain MC import must be caught");
        assertTrue(MC_IMPORT.matcher(strip(staticViolating)).find(),
                "a static MC import must be caught too — filing one as a non-import is exactly the "
                        + "mistake the Phase 1 symbol extractor made");
        assertTrue(MC_QUALIFIED.matcher(withoutImports(strip(qualifiedViolating))).find(),
                "an inline fully-qualified MC type must be caught");

        assertFalse(MC_IMPORT.matcher(strip(clean)).find(),
                "a javadoc/comment/string mention must NOT be reported — a guard that cries wolf on "
                        + "prose gets deleted, and this codebase quotes Minecraft names in javadoc "
                        + "constantly");
        assertFalse(MC_QUALIFIED.matcher(withoutImports(strip(clean))).find(),
                "…and neither must the qualified-reference check");
    }

    @Test
    void theScanReachesTheRealSources() {
        // If the working directory ever moves, the walk finds nothing and every assertion above
        // passes for the wrong reason. Pin that it saw a real tree.
        final List<Path> all = sources(p -> true);
        assertTrue(all.size() > 250,
                () -> "expected the whole main source tree; walked only " + all.size() + " files");

        final long boundary = all.size() - nonBoundarySources().size();
        assertTrue(boundary > 50,
                () -> "expected fabric/ + platform/ to hold the Minecraft-typed code; found only "
                        + boundary + " files there");
        assertTrue(nonBoundarySources().size() > 100,
                "expected a substantial Minecraft-free tree outside the boundary");
    }

    @Test
    void theBoundaryItselfStillNamesMinecraft() {
        // The converse of the property: if fabric/ + platform/ stopped containing Minecraft imports,
        // the detector is broken (or the sources are not where we think), not the mod Minecraft-free.
        final long withMcImports = sources(PlatformBoundaryGuardTest::isBoundary).stream()
                .filter(file -> MC_IMPORT.matcher(strip(read(file))).find())
                .count();
        assertTrue(withMcImports > 50,
                () -> "only " + withMcImports + " boundary files import Minecraft — the detector is "
                        + "not seeing imports it should");
    }

    @Test
    void theDeletedPotionUtilStayedDeleted() {
        // Slice 5 split util/PotionUtil into util/PotionNames (strings) + platform/Potions
        // (registries). Re-creating the merged form is the specific regression that would put a
        // registry lookup back in util/.
        assertFalse(Files.exists(MAIN_SOURCES.resolve(
                        Path.of("com", "gmail", "nossr50", "util", "PotionUtil.java"))),
                "util/PotionUtil was split in Phase 2 slice 5 — its Minecraft half lives in "
                        + "platform/Potions");
        assertTrue(Files.exists(MAIN_SOURCES.resolve(
                        Path.of("com", "gmail", "nossr50", "platform", "Potions.java"))),
                "platform/Potions is where the potion registry lookups live");
    }

    // --- Plumbing ---------------------------------------------------------------------------------

    private static boolean isBoundary(Path file) {
        final String path = file.toString().replace('\\', '/');
        return BOUNDARY_SEGMENTS.stream().anyMatch(path::contains);
    }

    private static List<Path> nonBoundarySources() {
        return sources(file -> !isBoundary(file));
    }

    private static List<Path> sources(java.util.function.Predicate<Path> filter) {
        try (Stream<Path> walk = Files.walk(MAIN_SOURCES)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(filter)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + MAIN_SOURCES.toAbsolutePath(), e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }

    /** Drop the import block, so the qualified-reference check does not just re-find the imports. */
    private static String withoutImports(String source) {
        final StringBuilder out = new StringBuilder(source.length());
        for (String line : source.split("\n", -1)) {
            out.append(line.stripLeading().startsWith("import ") ? "" : line).append('\n');
        }
        return out.toString();
    }

    /**
     * Blank out comments, string literals and char literals, so a Minecraft name quoted in javadoc —
     * which this codebase does constantly — is not reported as a violation.
     *
     * <p>A single character scan rather than a regex pass: stripping comments first corrupts a string
     * containing {@code "/*"}, and stripping strings first corrupts a comment containing a quote.
     * Newlines are preserved so reported positions still line up with the file.
     */
    private static String strip(String source) {
        final StringBuilder out = new StringBuilder(source.length());
        final int n = source.length();
        int i = 0;
        while (i < n) {
            final char c = source.charAt(i);
            final char next = i + 1 < n ? source.charAt(i + 1) : '\0';

            if (c == '/' && next == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                i += 2;
                while (i < n && !(source.charAt(i) == '*' && i + 1 < n && source.charAt(i + 1) == '/')) {
                    if (source.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i = Math.min(i + 2, n);
            } else if (c == '"' || c == '\'') {
                i++; // opening quote
                while (i < n && source.charAt(i) != c) {
                    // A backslash escapes the next character, including the closing quote itself
                    // ('\'' and "\"" both appear in this codebase).
                    i += source.charAt(i) == '\\' ? 2 : 1;
                }
                i++; // closing quote
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    @Test
    void theStripperSurvivesQuotesAndCommentsInsideEachOther() {
        // Both directions of the trap the character scan exists for.
        assertEquals("class A { String s = ; }\n",
                strip("class A { String s = \"/* not a comment */\"; }\n"));
        assertTrue(strip("/* a comment with a \" quote */ int x;").contains("int x;"));
        // An escaped quote inside a char literal must not end the literal early.
        assertTrue(strip("char q = '\\''; int after;").contains("int after;"));
    }
}
