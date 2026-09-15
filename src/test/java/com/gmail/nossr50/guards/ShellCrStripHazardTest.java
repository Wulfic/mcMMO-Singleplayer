package com.gmail.nossr50.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * <b>The CR-strip guard</b> (multi-version TODO §66): no shell script under {@code scripts/} may
 * write a carriage return as a bare {@code $'\r'}. It must come from a variable —
 * {@code CR=$(printf '\r')}, then {@code "${line%"$CR"}"}.
 *
 * <h2>What actually breaks</h2>
 * A bare {@code $'\r'} is <b>silently discarded inside a command substitution</b>. Measured with
 * {@code od -An -tx1} on the MSYS bash this repo is developed on, over the string {@code stop\r}:
 *
 * <ul>
 *   <li>{@code "${line%$'\r'}"} at top level → {@code 73 74 6f 70} — it strips.</li>
 *   <li>the same expansion inside {@code $( )} → {@code 73 74 6f 70 0d} — <b>the CR survives</b>.</li>
 *   <li>a bare {@code $'\r'} passed as an argument inside {@code $( )} → empty output.</li>
 *   <li>{@code "x"$'\r'} inside {@code $( )} → {@code 78} — the CR is gone.</li>
 *   <li>{@code CR=$(printf '\r'); "${line%"$CR"}"} → {@code 73 74 6f 70} <b>in both positions</b>.</li>
 * </ul>
 *
 * <p>Same syntax, same exit status, <b>no error</b>. ⚠️ Only CR behaves this way: {@code $'\t'}
 * survives the same round trip intact, which is why {@code ci-watch.sh}'s three tab expansions are
 * deliberately <em>not</em> covered by this guard and must not be "fixed" —
 * {@link #tabIsNotRefused()} pins that, so the scope claim cannot quietly widen into churn on a file
 * under the cross-branch identity guard.
 *
 * <h2>Why a guard, when the shipped code was already correct</h2>
 * It was, and this guard fixes no bug. It fixes the fact that <b>nothing would have noticed</b>. The
 * documented symptom is the catastrophic one: {@code gameplay-smoke.sh}'s first run lost every
 * {@code gamerule}, every {@code mine continuous} and every {@code attack continuous} — brigadier
 * reads {@code false\r} as an invalid boolean — while commands with a greedy last argument still went
 * through, <b>so the run looked like a partly-working scenario rather than a broken pipe.</b> A
 * green-ish smoke run is the failure mode, and that harness is the instrument nine branches ship on.
 *
 * <p>Moving that expansion into a shared helper called through {@code $( )} is an ordinary,
 * well-intentioned refactor. Before this test, every instrument in the repo stayed green through it.
 *
 * <p><b>Why it lives in the JUnit suite rather than in a script.</b> A script is "somebody remembers
 * to run it", which is the R8/R11 failure mode this repo keeps paying for. The suite runs unattended
 * on every push.
 *
 * <h2>Why this guard is not vacuous</h2>
 * Full-line comments are stripped before anything is matched, and that is load-bearing in the
 * opposite direction to {@code MixinAllowCoverageTest}: there, a javadoc sentence read as
 * <em>compliance</em>; here, the comment above each fixed site quotes the hazardous form verbatim to
 * explain it, and a naive grep reads that explanation as a <em>violation</em>.
 * {@link #aFullLineCommentIsNotAViolation()} pins it, and {@link #theScanReachesTheRealScripts()}
 * asserts the scan actually read the two real files and found the real immune form in them — a guard
 * that scans zero files passes forever.
 */
class ShellCrStripHazardTest {

    private static final Path SCRIPTS = Path.of("scripts");

    /** The five source characters {@code $ ' \ r '} — the form that re-lexes away. */
    private static final String BARE_CR = "$'\\r'";

    /** The five source characters {@code $ ' \ t '} — measured to survive, deliberately allowed. */
    private static final String BARE_TAB = "$'\\t'";

    /** The form that is correct in both positions, asserted to be present by the reach check. */
    private static final String IMMUNE_STRIP = "${line%\"$CR\"}";

    // --- The property ----------------------------------------------------------------------------

    @Test
    void noShellScriptWritesABareCarriageReturn() {
        final List<String> offenders = new ArrayList<>();
        for (Path script : shellScripts()) {
            offenders.addAll(violations(script.getFileName().toString(), read(script)));
        }
        assertTrue(offenders.isEmpty(),
                () -> "A bare " + BARE_CR + " is silently discarded inside $( ), so this strips "
                        + "NOTHING the moment the expansion is moved into a command substitution — "
                        + "same syntax, same exit status, no error. Hold the CR in a variable "
                        + "instead: CR=$(printf '\\r') then \"${line%\\\"$CR\\\"}\", which is "
                        + "measured correct both nested and at top level:\n  "
                        + String.join("\n  ", offenders));
    }

    // --- Converse checks: prove the guard can fail ------------------------------------------------

    @Test
    void theDetectorFiresOnTheBareFormAndClearsTheImmuneOne() {
        assertEquals(1, violations("x.sh", "line=\"${line%" + BARE_CR + "}\"\n").size(),
                "the bare form must be reported");
        assertEquals(1, violations("x.sh", "printf '%s' " + BARE_CR + "\n").size(),
                "a bare CR outside a parameter expansion loses its CR nested too — also reported");
        assertEquals(0, violations("x.sh", "CR=$(printf '\\r')\nline=\"" + IMMUNE_STRIP + "\"\n")
                        .size(),
                "the immune form must not be reported");
    }

    @Test
    void aFullLineCommentIsNotAViolation() {
        final String documented = """
                # NOT style: an unquoted %s re-lexes to an EMPTY word inside a command
                    # substitution, so ${line%%%s} strips nothing at all once it is moved.
                CR=$(printf '\\r')
                """.formatted(BARE_CR, BARE_CR);
        assertEquals(0, violations("x.sh", documented).size(),
                "the comment that EXPLAINS the hazard quotes it verbatim; reading that as a "
                        + "violation would make the guard unsatisfiable for any file that "
                        + "documents itself");
    }

    @Test
    void tabIsNotRefused() {
        assertEquals(0, violations("ci-watch.sh", "id=\"${match%%" + BARE_TAB + "*}\"\n").size(),
                "$'\\t' survives the re-lex (measured: 'field' nested and at top level), so "
                        + "ci-watch.sh's tab expansions are correct and must not be churned");
    }

    // --- Reach: prove the scan reads the real files ------------------------------------------------

    @Test
    void theScanReachesTheRealScripts() {
        final List<Path> scripts = shellScripts();
        assertTrue(scripts.size() >= 5,
                () -> "expected the real scripts/ tree, found " + scripts.size() + " .sh files");

        final List<String> carryingTheImmuneForm = new ArrayList<>();
        for (Path script : scripts) {
            if (read(script).contains(IMMUNE_STRIP)) {
                carryingTheImmuneForm.add(script.getFileName().toString());
            }
        }
        // Named rather than counted: these are the two sites §66.1 converted, and a scan that
        // silently stopped reading files would still satisfy a >= 0 assertion.
        assertTrue(carryingTheImmuneForm.contains("gameplay-smoke.sh")
                        && carryingTheImmuneForm.contains("gen-milestone-advancements.sh"),
                () -> "the two converted sites must still carry " + IMMUNE_STRIP
                        + "; found it in " + carryingTheImmuneForm);
    }

    // --- Machinery ---------------------------------------------------------------------------------

    /**
     * Every violation in one file, as {@code name:line  ->  text}.
     *
     * <p>Only <b>full-line</b> comments are stripped — a line whose first non-blank character is
     * {@code #}. A trailing {@code #} is not treated as a comment on purpose: telling one from a
     * {@code #} inside a string or a {@code ${x#pat}} expansion needs a shell parser, and guessing
     * wrong in that direction would let a real violation through. This direction only ever produces a
     * loud false positive, which is the side to fail on.
     */
    private static List<String> violations(String name, String content) {
        final List<String> found = new ArrayList<>();
        final String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            if (line.stripLeading().startsWith("#")) {
                continue;
            }
            if (line.contains(BARE_CR)) {
                found.add(name + ":" + (i + 1) + "  ->  " + line.strip());
            }
        }
        return found;
    }

    private static List<Path> shellScripts() {
        try (Stream<Path> walk = Files.walk(SCRIPTS)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sh"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + SCRIPTS.toAbsolutePath(), e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }
}
