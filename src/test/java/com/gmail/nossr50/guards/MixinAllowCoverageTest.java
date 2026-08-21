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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * <b>The risk-R4 guard</b> (multi-version TODO §5.4): every mixin injector must declare {@code allow}.
 *
 * <p><b>Why {@code require} is not enough.</b> {@code require} (and Fabric's {@code defaultRequire =
 * 1}) is a <em>minimum</em>: it fires when an injector binds to too <em>few</em> sites. Nothing in the
 * default configuration fires when one binds to too <em>many</em>. That is the dangerous direction,
 * because it is how a mixin silently starts doing its work in places it was never meant to:
 *
 * <ul>
 *   <li>An unresolvable {@code @Slice} is <b>silently dropped</b> and the injector then binds
 *       everywhere in the target method. {@code require = 1} is satisfied; the mod misbehaves.</li>
 *   <li>An upstream refactor that adds a second call to the anchored method turns one hook into two
 *       — a doubled XP award, a doubled durability save — with no error at any point.</li>
 * </ul>
 *
 * <p><b>Why this matters more under branch-per-band</b> (ruling R-a): each of those is one silent bug
 * <em>per band</em>, discovered by a player rather than by CI. This guard is cheap insurance bought
 * once on {@code master} so that every band branch inherits it.
 *
 * <p><b>Scope: coverage, not correctness.</b> This test asserts only that a value is
 * <em>declared</em>. Whether the number is <em>right</em> is a bytecode question, and it is answered
 * by {@code scripts/mixin-allow-audit.py}, which disassembles each {@code @Mixin} target out of the
 * Loom-cached jar and counts the instructions each {@code @At} actually selects. That script carries
 * its own control check — it must reproduce every already-shipped, boot-proven value — and it is what
 * gets re-run per band, where the counts can legitimately differ.
 *
 * <p><b>Why this guard is not vacuous.</b> Its converse checks are in
 * {@link #theDetectorFiresOnAMissingAllowAndClearsADeclaredOne()} and
 * {@link #theScanReachesTheRealMixins()}. The important one is the javadoc case: this codebase
 * documents its injectors heavily and {@code AbstractFurnaceSmeltMixin}'s class javadoc contains the
 * literal words <i>"Every injector carries {@code allow = 1}"</i>. A grep-shaped guard reads that
 * sentence as compliance and passes a file whose annotations declare nothing —
 * {@code grep -c 'allow *=' AbstractFurnaceSmeltMixin.java} really does report 5 for 4 injectors.
 * Comments are therefore stripped before anything is matched, and a test pins that behaviour.
 *
 * <p>⚠️ <b>This class must not live in {@code com.gmail.nossr50.fabric.mixin}</b>, next to the files
 * it polices, however natural that placement looks. That package is the {@code "package"} declared by
 * {@code mcmmo.mixins.json}, and the suite runs under {@code fabric-loader-junit}'s Knot classloader,
 * so the Mixin transformer claims <em>every</em> class in it — including a test. The result is not a
 * test failure but a load failure: <i>"Mixin transformation of … failed"</i>, before a single
 * assertion runs.
 */
class MixinAllowCoverageTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    /**
     * Every annotation that binds to instructions and therefore accepts {@code allow}.
     *
     * <p>Includes the MixinExtras injectors: they resolve their injection points through the same
     * {@code @At} machinery and fail the same way. {@code @Local} is deliberately absent — it is
     * MixinExtras <i>sugar</i> on a handler parameter, not an injector, and has no site to bound.
     */
    private static final List<String> INJECTORS = List.of(
            "Inject",
            "Redirect",
            "ModifyArg",
            "ModifyArgs",
            "ModifyVariable",
            "ModifyConstant",
            "ModifyExpressionValue",
            "ModifyReturnValue",
            "ModifyReceiver",
            "WrapOperation",
            "WrapWithCondition",
            "WrapMethod");

    private static final Pattern INJECTOR_AT = Pattern.compile(
            "@(" + String.join("|", INJECTORS) + ")\\s*\\(");

    private static final Pattern DECLARES_ALLOW = Pattern.compile("\\ballow\\s*=\\s*\\d+");

    // --- The property ----------------------------------------------------------------------------

    @Test
    void everyInjectorDeclaresAllow() {
        final List<String> offenders = new ArrayList<>();
        for (Path file : mixinSources()) {
            for (String body : injectorBodies(strip(read(file)))) {
                if (!DECLARES_ALLOW.matcher(body).find()) {
                    offenders.add(file.getFileName() + "  ->  " + oneLine(body));
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                () -> "These injectors declare no allow=, so nothing detects them binding to MORE "
                        + "sites than intended (require= only catches too FEW). Measure the real "
                        + "count with scripts/mixin-allow-audit.py — do not guess it:\n  "
                        + String.join("\n  ", offenders));
    }

    // --- Converse checks: prove the guard can fail ------------------------------------------------

    @Test
    void theDetectorFiresOnAMissingAllowAndClearsADeclaredOne() {
        final String missing = """
                @Mixin(Foo.class)
                class Example {
                    @Inject(method = "bar", at = @At("HEAD"))
                    private void mcmmo$hook(CallbackInfo ci) { }
                }
                """;
        final String declared = """
                @Mixin(Foo.class)
                class Example {
                    @Inject(method = "bar", allow = 1, at = @At("HEAD"))
                    private void mcmmo$hook(CallbackInfo ci) { }
                }
                """;
        assertEquals(1, uncovered(missing), "an injector without allow= must be reported");
        assertEquals(0, uncovered(declared), "an injector with allow= must not be reported");
    }

    @Test
    void javadocSayingAllowDoesNotCountAsDeclaringIt() {
        // The specific way a grep-shaped version of this guard passes while proving nothing.
        // AbstractFurnaceSmeltMixin's real javadoc contains this sentence.
        final String prose = """
                /**
                 * Every injector carries {@code allow = 1}: each target appears exactly once.
                 */
                @Mixin(Foo.class)
                class Example {
                    @Inject(method = "bar", at = @At("HEAD"))
                    private void mcmmo$hook(CallbackInfo ci) { }
                }
                """;
        assertEquals(1, uncovered(prose),
                "a javadoc mention of allow must NOT satisfy the guard — the annotation declares "
                        + "nothing here");

        final String lineComment = """
                @Mixin(Foo.class)
                class Example {
                    // allow = 3 was measured but never applied
                    @Redirect(method = "bar", at = @At("INVOKE", target = "Lx;y()V"))
                    private void mcmmo$hook() { }
                }
                """;
        assertEquals(1, uncovered(lineComment), "a line comment must not satisfy the guard either");
    }

    @Test
    void nestedParenthesesAndDescriptorsDoNotTruncateTheBody() {
        // Injector bodies are full of nested @At(...) groups and of string literals containing
        // parentheses and semicolons (JVM descriptors). A body scan that is not literal-aware ends
        // early and then reports a false MISSING — or worse, a false PASS on the next annotation.
        final String nested = """
                @Mixin(Foo.class)
                class Example {
                    @ModifyArg(
                            method = "use(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/InteractionHand;",
                            at = @At(value = "INVOKE",
                                    target = "Lnet/minecraft/Foo;bar(Ljava/util/Collection;)V"),
                            index = 3,
                            allow = 2)
                    private void mcmmo$hook() { }
                }
                """;
        assertEquals(0, uncovered(nested),
                "allow= sitting after a nested @At and a descriptor literal must still be found");
    }

    @Test
    void sugarAndNonInjectorAnnotationsAreNotPoliced() {
        // @Local, @Shadow, @Accessor and @Invoker have no injection point and take no allow.
        // Demanding one would make the guard un-satisfiable and it would be deleted.
        final String sugar = """
                @Mixin(Foo.class)
                class Example {
                    @Shadow private int x;
                    @Accessor("brewTime") int mcmmo$getBrewTime();
                    @Inject(method = "bar", allow = 1, at = @At("HEAD"))
                    private void mcmmo$hook(CallbackInfo ci, @Local ItemStack stack) { }
                }
                """;
        assertEquals(0, uncovered(sugar));
    }

    @Test
    void theScanReachesTheRealMixins() {
        // If the working directory moves, the walk finds nothing and everything above passes for
        // the wrong reason. Pin that it saw the real tree.
        final List<Path> files = mixinSources();
        assertTrue(files.size() >= 40,
                () -> "expected the whole mixin package; found only " + files.size() + " files");

        final int injectors = files.stream()
                .mapToInt(f -> injectorBodies(strip(read(f))).size())
                .sum();
        assertTrue(injectors >= 55,
                () -> "expected the real injector population; found only " + injectors
                        + ". A parser that silently matches nothing passes this guard trivially.");
    }

    @Test
    void theKnownMultiSiteInjectorsStillDeclareTheirMeasuredCounts() {
        // Not every injector binds once, and the ones that do not are the interesting ones: an
        // @At("RETURN") binds at every return instruction, and FishingBobberUseMixin's @ModifyArg
        // binds at BOTH of vanilla's FISHING_ROD_HOOKED triggers. Pinning a few measured values
        // here means a careless "tidy them all to 1" sweep reddens instead of silently disarming
        // the guard. The authority for these numbers is scripts/mixin-allow-audit.py against the
        // Loom jar; they are expected to differ per band, which is exactly why the script exists.
        assertAllowOf("LivingEntityDamageMixin.java", "mcmmo$reduceAppliedDamage", 4);
        assertAllowOf("PlayerEntityInteractMixin.java", "mcmmo$endInteraction", 4);
        assertAllowOf("FishingBobberUseMixin.java", "mcmmo$onFishCaught", 2);
        assertAllowOf("MobConversionOriginMixin.java", "mcmmo$carryOriginThroughConversion", 3);
    }

    // --- Plumbing ---------------------------------------------------------------------------------

    private static void assertAllowOf(String fileName, String handler, int expected) {
        final Path file = mixinSources().stream()
                .filter(p -> p.getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no such mixin: " + fileName));
        final String code = strip(read(file));
        final int at = code.indexOf(handler);
        assertTrue(at > 0, () -> handler + " not found in " + fileName);

        // The annotation governing a handler is the last injector body that STARTS before it.
        // Positions must come from the scan itself, not from indexOf(body): two injectors can have
        // byte-identical bodies (same method selector, same @At), and indexOf would then resolve
        // both to the first one and silently assert against the wrong annotation.
        String governing = null;
        final Matcher scan = INJECTOR_AT.matcher(code);
        while (scan.find() && scan.start() < at) {
            final int open = scan.end() - 1;
            final int close = matchingParen(code, open);
            if (close > open) {
                governing = code.substring(open, close + 1);
            }
        }
        final String body = governing;
        assertTrue(body != null, () -> "no injector annotation precedes " + handler);

        final Matcher m = DECLARES_ALLOW.matcher(body);
        assertTrue(m.find(), () -> handler + " declares no allow=");
        assertEquals("allow = " + expected, m.group().replaceAll("\\s+", " "),
                () -> handler + " in " + fileName + " should allow " + expected
                        + " site(s); re-measure with scripts/mixin-allow-audit.py before changing "
                        + "this expectation");
    }

    /** Every injector annotation body (parentheses included) in already-comment-stripped source. */
    private static List<String> injectorBodies(String code) {
        final List<String> bodies = new ArrayList<>();
        final Matcher m = INJECTOR_AT.matcher(code);
        while (m.find()) {
            final int open = m.end() - 1;
            final int close = matchingParen(code, open);
            if (close > open) {
                bodies.add(code.substring(open, close + 1));
            }
        }
        return bodies;
    }

    /**
     * Index of the {@code )} closing the {@code (} at {@code open}, skipping string literals.
     *
     * <p>Literal-awareness is load-bearing: {@code target = "Lnet/minecraft/Foo;bar(I)V"} contains an
     * unbalanced-looking parenthesis pair inside a string, and a naive depth counter closes the
     * annotation in the wrong place.
     */
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

    private static int uncovered(String source) {
        return (int) injectorBodies(strip(source)).stream()
                .filter(b -> !DECLARES_ALLOW.matcher(b).find())
                .count();
    }

    private static String oneLine(String body) {
        final String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() <= 140 ? flat : flat.substring(0, 137) + "...";
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

    /**
     * Blank out comments, string literals and char literals, preserving newlines.
     *
     * <p>A single character scan rather than successive regex passes: stripping comments first
     * corrupts a string containing {@code "/*"}, and stripping strings first corrupts a comment
     * containing a quote. Both shapes are present in this package.
     *
     * <p>String literals are blanked rather than removed, so a descriptor's parentheses cannot
     * unbalance the body scan that runs afterwards.
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

    @Test
    void theStripperKeepsBodiesBalancedAndKillsProse() {
        // A descriptor's parenthesis must survive as an empty literal, not as a stray '('.
        assertEquals("@Inject(method = \"\", allow = 1)",
                strip("@Inject(method = \"bar(I)V\", allow = 1)"));
        // …and a comment must not leave its text behind.
        assertFalse(strip("/* allow = 9 */ @Inject(method = \"b\")").contains("allow"));
    }
}
