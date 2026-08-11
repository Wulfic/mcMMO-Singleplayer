package com.gmail.nossr50.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.McTestRegistries;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * <b>Proves every mixin actually applies</b> — the runtime half of the risk-R4 work
 * (multi-version TODO §5.4), and the per-band check that {@code plans/BAND_TABLE.md} says it cannot
 * provide.
 *
 * <p>{@link MixinAllowCoverageTest} asserts each injector <em>declares</em> {@code allow};
 * {@code scripts/mixin-allow-audit.py} computes what the number <em>should</em> be from bytecode.
 * Neither runs Mixin. This does: it loads every {@code @Mixin} target class through the test JVM's
 * Knot classloader, which is what triggers Mixin to apply the mixins to it. An injector that matches
 * more sites than its {@code allow}, a selector that no longer resolves, a dropped {@code @Slice} —
 * all of them throw during application, and a failure to transform surfaces here as a load error.
 *
 * <p><b>Why loading, rather than booting.</b> {@code scripts/boot-check.sh} exercises a real server,
 * but a headless flat-world boot only ever loads a fraction of these classes: nothing summons a
 * sheep, a bogged or an armadillo, so {@code ShearableInteractMixin}'s four targets and
 * {@code ArmadilloBrushMixin}'s never transform and their {@code allow} values are never tested.
 * Sampling like that is precisely how a version-specific mixin bug reaches a player instead of CI.
 * Forcing the load covers all of them, deterministically, in the build.
 *
 * <p><b>Why it matters per band</b> (ruling R-a): {@code BAND_TABLE.md} records that its javap probe
 * can only see whether a callee still <em>exists</em> on its owner class — not whether the injected
 * method still <em>calls</em> it. "A vanilla refactor that keeps {@code ItemStack#decrement} but
 * stops calling it from the method we inject into reads green here and fails at runtime." This test
 * closes that hole, because application is the thing being measured.
 *
 * <p><b>Anti-vacuity.</b> A test that loads classes and asserts nothing threw would pass just as
 * happily if Mixin were not running at all — the single most likely way for this to become
 * decoration. {@link #theMixinsWereActuallyApplied()} therefore proves the transformer ran, by
 * finding mcMMO's own {@code mcmmo$}-prefixed members on the loaded vanilla classes. If those are
 * absent, the "no failures" result above means nothing and this test says so.
 *
 * <p><b>Proven by mutation, and here is exactly what it looks like.</b> Lowering
 * {@code LivingEntityDamageMixin}'s measured {@code allow = 4} to {@code 1} reddens the build — but
 * as an {@code initializationError} on this class, <em>not</em> through the assertion message above:
 * {@code LivingEntity} is loaded by the shared {@code Bootstrap.initialize()} in {@code @BeforeAll},
 * so it has already failed to transform before any test body runs. The report reads
 * <i>"Mixin transformation of net.minecraft.entity.LivingEntity failed"</i>, which names the target
 * but not the injector; the {@code InvalidInjectionException} detail goes to the Mixin log.
 * {@code scripts/mixin-allow-audit.py} turns that class name into the offending injector in one step.
 *
 * <p>So the two halves have different diagnostics and both are covered: targets that the registry
 * bootstrap already pulls in fail early and coarsely, while the ones nothing else touches —
 * {@code SheepEntity}, {@code BoggedEntity}, {@code ArmadilloEntity}, {@code PowderSnowBlock} and
 * most of the rest — are loaded <em>only</em> here and are reported with the full message. Those are
 * also exactly the ones a headless boot never reaches.
 */
class MixinApplicationTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    /** {@code @Mixin(Foo.class)} / {@code @Mixin({A.class, B.class})} / a fully-qualified form. */
    private static final Pattern MIXIN_ANNOTATION = Pattern.compile("@Mixin\\s*\\(");

    private static final Pattern CLASS_LITERAL = Pattern.compile("([\\w.$]+)\\s*\\.class");

    private static final Pattern IMPORT = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?([\\w.$]+)\\s*;");

    @BeforeAll
    static void bootstrap() {
        // Some target classes' static initialisers touch the registries.
        McTestRegistries.bootstrap();
    }

    // --- The property ----------------------------------------------------------------------------

    @Test
    void everyMixinTargetLoadsAndTransformsCleanly() {
        final Map<String, Throwable> failures = new TreeMap<>();
        for (Map.Entry<String, String> target : targetsByMixin().entrySet()) {
            try {
                Class.forName(target.getKey(), true, MixinApplicationTest.class.getClassLoader());
            } catch (Throwable t) { // NOSONAR - Mixin failures arrive as Errors, not Exceptions
                failures.put(target.getKey() + "  (from " + target.getValue() + ")", t);
            }
        }
        assertTrue(failures.isEmpty(), () -> {
            final StringBuilder sb = new StringBuilder(
                    "Mixin application failed for these target classes. An 'InvalidInjectionException"
                            + ": ... allow' means an injector bound to MORE sites than declared -- "
                            + "re-measure with scripts/mixin-allow-audit.py rather than raising the "
                            + "number to make it pass:\n");
            failures.forEach((name, t) -> sb.append("  ").append(name).append('\n')
                    .append("      ").append(rootCause(t)).append('\n'));
            return sb.toString();
        });
    }

    // --- Anti-vacuity: prove Mixin really ran -----------------------------------------------------

    @Test
    void theMixinsWereActuallyApplied() {
        // mcMMO's handlers are all named mcmmo$something and are added to the TARGET class by the
        // transformer. If none of them are present after loading, Mixin did not apply and the
        // "everything loaded fine" result above is worthless.
        final List<String> transformed = new ArrayList<>();
        for (String fqcn : targetsByMixin().keySet()) {
            final Class<?> loaded;
            try {
                loaded = Class.forName(fqcn, true, MixinApplicationTest.class.getClassLoader());
            } catch (Throwable t) { // NOSONAR - covered by the test above; not this test's job
                continue;
            }
            for (Method m : loaded.getDeclaredMethods()) {
                if (m.getName().contains("mcmmo$")) {
                    transformed.add(fqcn);
                    break;
                }
            }
        }
        assertTrue(transformed.size() >= 20,
                () -> "Only " + transformed.size() + " target classes carry an mcmmo$ member after "
                        + "loading. Mixin is not applying in this test JVM, so "
                        + "everyMixinTargetLoadsAndTransformsCleanly proves nothing. Check that the "
                        + "suite still runs under the fabric-loader-junit launcher (build.gradle).");
    }

    @Test
    void theScanFoundTheRealMixinPopulation() {
        // 37, not 42: seven target classes are shared by more than one mixin file (four
        // LivingEntity*Mixin files alone target LivingEntity, and BrewingStandBrewTimeAccessor
        // shares BrewingStandBlockEntity with its mixin). That is the same 37 that Phase 1's
        // independently-written scripts/extract-mc-surface.py records as `MIXINCLASS 37` in
        // scripts/mc-surface.txt -- two parsers written months apart agreeing on the population is
        // the cross-check that makes this floor meaningful rather than a number chosen to pass.
        final Map<String, String> targets = targetsByMixin();
        assertTrue(targets.size() >= 37,
                () -> "expected the full mixin target population (37 distinct classes); parsed only "
                        + targets.size()
                        + ". A parser that matches nothing passes the load test trivially.");
        assertTrue(targets.keySet().stream().allMatch(n -> n.startsWith("net.minecraft.")),
                () -> "every @Mixin target should be a Minecraft type; got "
                        + targets.keySet().stream()
                                .filter(n -> !n.startsWith("net.minecraft."))
                                .toList());
    }

    // --- Plumbing ---------------------------------------------------------------------------------

    /** Target class -> the mixin file that declares it. Multi-target mixins contribute each target. */
    private static Map<String, String> targetsByMixin() {
        final Map<String, String> out = new LinkedHashMap<>();
        for (Path file : mixinSources()) {
            final String code = strip(read(file));
            final Set<String> imports = new LinkedHashSet<>();
            final Matcher im = IMPORT.matcher(code);
            while (im.find()) {
                imports.add(im.group(1));
            }
            final Matcher am = MIXIN_ANNOTATION.matcher(code);
            if (!am.find()) {
                continue;
            }
            final int open = am.end() - 1;
            final int close = matchingParen(code, open);
            if (close < 0) {
                continue;
            }
            final Matcher lit = CLASS_LITERAL.matcher(code.substring(open, close + 1));
            while (lit.find()) {
                final String resolved = resolve(lit.group(1), imports);
                if (resolved != null) {
                    out.putIfAbsent(resolved, file.getFileName().toString());
                }
            }
        }
        return out;
    }

    /**
     * Resolve a {@code @Mixin} target to a binary name.
     *
     * <p>Two shapes both occur in this package and a parser that handles only the first quietly
     * skips the others — which would shrink the tested population without failing anything:
     * {@code @Mixin(BeehiveBlock.class)} needs the file's imports, while
     * {@code @Mixin(net.minecraft.block.BeehiveBlock.class)} is already qualified. Nested types must
     * also come out as {@code Outer$Inner}, not {@code Outer.Inner}, or {@code Class.forName} fails.
     */
    private static String resolve(String token, Set<String> imports) {
        if (token.startsWith("net.minecraft.")) {
            return token;
        }
        final String head = token.contains(".") ? token.substring(0, token.indexOf('.')) : token;
        final String rest = token.contains(".") ? token.substring(token.indexOf('.') + 1) : "";
        for (String imp : imports) {
            if (imp.endsWith("." + head)) {
                return rest.isEmpty() ? imp : imp + "$" + rest.replace('.', '$');
            }
        }
        return null;
    }

    private static int matchingParen(String code, int open) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            final char c = code.charAt(i);
            if (c == '"') {
                i++;
                while (i < code.length() && code.charAt(i) != '"') {
                    i += code.charAt(i) == '\\' ? 2 : 1;
                }
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static String rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getClass().getName() + ": " + cur.getMessage();
    }

    private static List<Path> mixinSources() {
        try (Stream<Path> walk = Files.walk(MAIN_SOURCES)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> read(p).contains("@Mixin"))
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

    /** Blank comments and literals, preserving newlines. See MixinAllowCoverageTest#strip. */
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
                out.append(c);
                i++;
                while (i < n && source.charAt(i) != c) {
                    i += source.charAt(i) == '\\' ? 2 : 1;
                }
                out.append(c);
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
