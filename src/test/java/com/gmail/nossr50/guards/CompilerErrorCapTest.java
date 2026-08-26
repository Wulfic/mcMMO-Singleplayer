package com.gmail.nossr50.guards;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * <b>The compiler error-cap guard</b> (multi-version TODO &sect;44): {@code javac} must be told to
 * report more than its default <b>100</b> errors.
 *
 * <p><b>Why this guard exists.</b> The 100-error cap does not fail the build and does not change the
 * exit code — it <em>truncates</em>, mentions it in one line, and hands back a number that looks
 * exactly as authoritative as the true one. Twice that turned a measurement into a guess:
 *
 * <ul>
 *   <li><b>&sect;27</b> — Minecraft {@code 26.2} read as a {@code platform/}-only break at the cap.
 *       With it lifted: <b>2,639</b> errors across 96 files, and the extra 2,539 decided the shape
 *       of the entire port.</li>
 *   <li><b>&sect;42</b> — {@code mc/26.1.2}'s test tree read <b>30</b> errors. Lifted: <b>61</b>,
 *       across 8 files.</li>
 * </ul>
 *
 * <p><b>&#9888;&#9888; What this guard deliberately does NOT do: grep {@code build.gradle} for the
 * string {@code -Xmaxerrs}.</b> That is the shape of the fourteenth and fifteenth vacuous
 * assertions found in this repository — <em>"the string appears in the file"</em> proves nothing.
 * It stays green when the flag sits in a {@code tasks.withType} block that never matches
 * {@code compileJava}, and when a later {@code compilerArgs = [...]} assignment drops it. Both
 * leave the string in the file.
 *
 * <p>So the fact checked here is the <b>resolved argument list of the realized {@code compileJava}
 * task</b>, handed over as {@code mcmmo.build.compilerArgs} the way ruling <b>R-aa</b> hands over
 * {@code mcmmo.build.javaVersion}. Nothing but the task's own configuration can produce it.
 *
 * <p>&#9888; {@code build.gradle} declares this property as an {@code inputs.property} as well.
 * Without that, {@code :test} serves a <b>cached pass</b> after the args change — measured
 * 2026-08-18, where three genuine mutations of {@code release.yml} all scored <em>"not caught"</em>
 * for exactly that reason.
 *
 * <p>&#9888; <b>This class must not live in {@code com.gmail.nossr50.fabric.mixin}</b> — same
 * reason as {@link BandToolchainLevelTest}: the Mixin transformer claims every class in the package
 * {@code mcmmo.mixins.json} declares, including a test.
 */
class CompilerErrorCapTest {

    /** The args {@code compileJava} resolved, handed over by {@code build.gradle}. */
    private static final String COMPILER_ARGS_PROPERTY = "mcmmo.build.compilerArgs";

    /** javac's own default. It is the number this guard exists to move off of. */
    private static final int JAVAC_DEFAULT_CAP = 100;

    /** {@code -Xmaxerrs 10000} — javac accepts the value as a separate argument. */
    private static final Pattern MAX_ERRS =
            Pattern.compile("(?:^|\\s)-Xmaxerrs[\\s=]+(\\d+)(?:\\s|$)");

    @Test
    void theBuildHandsOverTheArgumentsCompileJavaResolved() {
        assertNotNull(resolvedCompilerArgs(), "build.gradle no longer exports "
                + COMPILER_ARGS_PROPERTY + ". Without it this guard has nothing to read but the "
                + "text of build.gradle, and a guard that greps a file for a flag passes while the "
                + "flag is configured onto a task that is never compiled. See TODO.md 44.2.");
    }

    /**
     * The load-bearing assertion: the cap the compiler actually ran with is above javac's default.
     *
     * <p>It names {@code 100} on purpose. An assertion that merely required the flag to be present
     * would be satisfied by {@code -Xmaxerrs 100}, which is the defect itself spelled out.
     */
    @Test
    void compileJavaRunsWithTheErrorCapLiftedAboveJavacsDefault() {
        final String args = resolvedCompilerArgs();
        assertNotNull(args, "build.gradle no longer exports " + COMPILER_ARGS_PROPERTY + ".");

        final Matcher m = MAX_ERRS.matcher(args);
        assertTrue(m.find(), () -> "compileJava resolved compilerArgs " + quoted(args)
                + ", which pass no -Xmaxerrs. javac therefore stops reporting after "
                + JAVAC_DEFAULT_CAP + " errors, WITHOUT failing and WITHOUT changing its exit code. "
                + "Every error count read off this build above that number is then wrong and looks "
                + "right: TODO.md 27 sized MC 26.2 at the cap and the true figure was 2,639; "
                + "TODO.md 42 read 30 on mc/26.1.2's test tree and the true figure was 61. Restore "
                + "-Xmaxerrs in build.gradle's JavaCompile block.");

        final int cap = Integer.parseInt(m.group(1));
        assertTrue(cap > JAVAC_DEFAULT_CAP, () -> "compileJava runs with -Xmaxerrs " + cap
                + ", which is not above javac's own default of " + JAVAC_DEFAULT_CAP + ". The flag "
                + "is present and buys nothing -- this is the defect written out explicitly rather "
                + "than inherited. See TODO.md 44.");
    }

    /**
     * The flag has to reach the task that compiles the mod, not merely exist somewhere in the build.
     *
     * <p>Kept separate from the check above because it fails for a different reason and needs to say
     * so: {@code -Xlint:deprecation} has been on this task since long before &sect;44, so finding it
     * alongside {@code -Xmaxerrs} is what proves both were read off <em>one</em> task's resolved
     * configuration rather than assembled from somewhere else.
     */
    @Test
    void theCapIsOnTheSameTaskTheRestOfTheBuildsArgumentsAreOn() {
        final List<String> args = Arrays.asList(resolvedCompilerArgs().trim().split("\\s+"));
        assertTrue(args.contains("-Xlint:deprecation"), () -> "compileJava's resolved compilerArgs "
                + args + " no longer include -Xlint:deprecation, which has been on this task since "
                + "long before TODO.md 44. Either that was dropped -- a separate regression worth "
                + "its own look -- or build.gradle is now exporting the args of some OTHER task, in "
                + "which case this guard is reading the wrong compiler and cannot see the cap at "
                + "all.");
        assertTrue(args.contains("-Xmaxerrs"), () -> "compileJava's resolved compilerArgs " + args
                + " carry no -Xmaxerrs. See TODO.md 44.");
    }

    private static String resolvedCompilerArgs() {
        return System.getProperty(COMPILER_ARGS_PROPERTY);
    }

    private static String quoted(String s) {
        return "'" + s + "'";
    }
}
