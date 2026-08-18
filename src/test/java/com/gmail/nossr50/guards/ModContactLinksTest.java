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
 * <b>The fork-pointer guard</b> (multi-version TODO &sect;14.1): the links this project hands a
 * <em>player</em> must lead to <em>this</em> project.
 *
 * <p><b>Why this guard exists.</b> This repository is a GitHub fork of {@code mcMMO-Dev/mcMMO}, and
 * {@code fabric.mod.json}'s {@code contact} block was inherited wholesale. For the entire life of
 * the port it pointed all three of {@code homepage}, {@code sources} and {@code issues} at upstream.
 * Two things follow, and neither produces an error anywhere:
 *
 * <ul>
 *   <li>{@code issues} is the URL behind <b>ModMenu's "Issues" button</b>. A player who hits a bug
 *       in this mod files it on the Bukkit/Spigot plugin's tracker &mdash; noise for a project that
 *       cannot act on it, and invisible to the project that can.</li>
 *   <li>{@code sources} is the <b>GPL-3.0 "how to obtain source" link baked into the shipped jar</b>.
 *       Pointing it at upstream means the link does not lead to the source of <em>this</em> binary,
 *       which is the one obligation the licence is most specific about.</li>
 * </ul>
 *
 * <p><b>Why the wrong value is invisible without a test.</b> Fabric Loader never validates a contact
 * URL &mdash; any string loads. The button works, it is simply aimed at the wrong project, so every
 * gate this repo already runs (build, boot check, gameplay smoke, drift audit) is green with the
 * values wrong. Nothing but this test connects the URL to the repository that publishes the jar.
 *
 * <p><b>What is deliberately NOT asserted.</b> Links to {@code mcMMO-Dev/mcMMO} as <em>attribution</em>
 * are correct and must survive: {@code README.md}, {@code wiki/Home.md},
 * {@code wiki/Differences-from-mcMMO.md}, {@code wiki/Installation.md} and {@code wiki/_Footer.md}
 * all credit upstream or point a multiplayer user there on purpose, and two of those are GPL-3.0
 * obligations. A guard that banned the upstream URL outright would be wrong, and would be "fixed" by
 * deleting the credit. The rule encoded here is narrower and is the one that actually matters:
 * <b>nothing may route a reader to upstream's issue tracker</b>, and the jar's own contact block must
 * describe this fork. See {@link #noDocumentSendsAReaderToUpstreamsIssueTracker()}.
 *
 * <p>&#9888; <b>This class must not live in {@code com.gmail.nossr50.fabric.mixin}.</b> That package
 * is the one declared by {@code mcmmo.mixins.json}, and the suite runs under
 * {@code fabric-loader-junit}'s Knot classloader, so the Mixin transformer claims every class in it
 * &mdash; including a test, which then fails to load before a single assertion runs. Same reason
 * {@link BandVersionLabelTest} lives here.
 */
class ModContactLinksTest {

    /** Relative to the project dir, which Gradle sets as the test working directory. */
    private static final Path FABRIC_MOD_JSON =
            Path.of("src", "main", "resources", "fabric.mod.json");

    /** The repository that actually publishes this jar. */
    private static final String OWN_REPO = "https://github.com/Wulfic/mcMMO-Singleplayer";

    /** The upstream Bukkit/Spigot project this is a fork of. */
    private static final String UPSTREAM = "github.com/mcMMO-Dev/mcMMO";

    /** Upstream's issue tracker &mdash; the one upstream URL no document may point a reader at. */
    private static final String UPSTREAM_ISSUES = "github.com/mcMMO-Dev/mcMMO/issues";

    /**
     * The three {@code contact} keys and the exact value each must carry. {@code issues} is pinned to
     * the tracker specifically: a bare repo root there would still "work" and would still be wrong.
     */
    private static final String[][] REQUIRED_CONTACT = {
        {"homepage", OWN_REPO},
        {"sources", OWN_REPO},
        {"issues", OWN_REPO + "/issues"},
    };

    /**
     * Every player-facing document. A missing file is a failure, not a skip &mdash; a guard that
     * silently passes because it read nothing is the vacuity this repo has been bitten by repeatedly.
     */
    private static final Path[] PLAYER_FACING_DOCS = {
        Path.of("README.md"),
        Path.of("wiki", "Home.md"),
        Path.of("wiki", "Troubleshooting.md"),
        Path.of("wiki", "Installation.md"),
        Path.of("wiki", "Differences-from-mcMMO.md"),
        Path.of("wiki", "Building-from-Source.md"),
        Path.of("wiki", "_Footer.md"),
    };

    // --- the jar's own metadata ----------------------------------------------

    /**
     * Each contact key exists and names this fork. The presence half is load-bearing: without it,
     * deleting the whole {@code contact} block would satisfy every "does not mention upstream"
     * assertion for free, while removing the GPL-3.0 source link from the shipped jar.
     */
    @Test
    void everyContactLinkPointsAtThisFork() {
        final String json = read(FABRIC_MOD_JSON);

        for (final String[] entry : REQUIRED_CONTACT) {
            final String key = entry[0];
            final String expected = entry[1];

            final Matcher matcher =
                    Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
            assertTrue(
                    matcher.find(),
                    FABRIC_MOD_JSON
                            + " declares no contact." + key + ". All three of homepage/sources/issues"
                            + " are required: `sources` is the GPL-3.0 'how to obtain source' link"
                            + " baked into the jar, and `issues` is ModMenu's Issues button.");

            assertEquals(
                    expected,
                    matcher.group(1),
                    FABRIC_MOD_JSON
                            + "'s contact." + key + " must point at the repository that publishes"
                            + " this jar. Pointing it at upstream mcMMO sends bug reports to a"
                            + " project that cannot act on them, and (for `sources`) breaks the"
                            + " licence's source-availability link.");
        }
    }

    /**
     * The converse, stated over the block rather than key by key: no contact URL may name upstream at
     * all. This catches a fourth key being added later with an inherited value, which the key-by-key
     * check above cannot see.
     */
    @Test
    void noContactLinkNamesUpstream() {
        final String contactBlock = contactBlockOf(read(FABRIC_MOD_JSON));

        assertFalse(
                contactBlock.contains(UPSTREAM),
                "fabric.mod.json's contact block still names " + UPSTREAM + ":\n" + contactBlock
                        + "\nCredit to upstream belongs in README.md and LICENSE. The contact block"
                        + " answers 'where do I go about THIS build', and the answer is this fork.");
    }

    /**
     * The extractor above is itself guarded. One that returned {@code ""} on a shape it did not
     * anticipate would make {@link #noContactLinkNamesUpstream()} pass for free &mdash; a guard
     * asserting over nothing, which is how a vacuous test survives review.
     */
    @Test
    void theContactBlockExtractorActuallyFindsTheBlock() {
        final String contactBlock = contactBlockOf(read(FABRIC_MOD_JSON));

        assertTrue(
                contactBlock.contains("homepage")
                        && contactBlock.contains("sources")
                        && contactBlock.contains("issues"),
                "the contact-block extractor returned something that is not the contact block, so"
                        + " noContactLinkNamesUpstream() is asserting over the wrong text:\n"
                        + contactBlock);

        // And it is a *slice*, not the whole file — otherwise the "no upstream" assertion would be
        // ranging over text where an upstream mention is deliberately allowed.
        assertFalse(
                contactBlock.contains("entrypoints"),
                "the contact-block extractor over-matched and swallowed the rest of the manifest");
    }

    // --- the documents a player reads ----------------------------------------

    /**
     * No player-facing document may link upstream's <em>issue tracker</em>.
     *
     * <p>This is the narrow rule, and the narrowness is the point. Linking
     * {@code github.com/mcMMO-Dev/mcMMO} as credit, or as "use upstream for multiplayer", is correct
     * and appears in five of these files on purpose. Linking upstream's {@code /issues} is never
     * correct here: it routes a report about this fork to a project that cannot act on it.
     */
    @Test
    void noDocumentSendsAReaderToUpstreamsIssueTracker() {
        final List<String> offenders = new ArrayList<>();

        for (final Path doc : PLAYER_FACING_DOCS) {
            assertTrue(
                    Files.exists(doc),
                    doc + " is missing. This guard's file list is hand-maintained; a renamed or"
                            + " deleted page must be re-pointed here, not silently skipped.");

            final String[] lines = read(doc).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains(UPSTREAM_ISSUES)) {
                    offenders.add(doc + ":" + (i + 1) + " — " + lines[i].strip());
                }
            }
        }

        assertTrue(
                offenders.isEmpty(),
                "these lines point a reader at UPSTREAM's issue tracker; bug reports about this fork"
                        + " belong on " + OWN_REPO + "/issues:\n  "
                        + String.join("\n  ", offenders));
    }

    /**
     * The converse guard, and the reason it is not redundant: a test that only forbids the upstream
     * tracker is satisfied by a page that links <em>no</em> tracker at all. That was the actual state
     * of these pages &mdash; four separate "please file an issue" sentences with nothing to click, on
     * pages whose only mcMMO link went upstream.
     */
    @Test
    void thePagesThatAskForBugReportsSayWhereToFileThem() {
        final Path[] pagesThatAskForReports = {
            Path.of("README.md"),
            Path.of("wiki", "Home.md"),
            Path.of("wiki", "Troubleshooting.md"),
            Path.of("wiki", "Building-from-Source.md"),
        };

        for (final Path doc : pagesThatAskForReports) {
            final String text = read(doc);

            assertTrue(
                    Stream.of("file issues", "file an issue", "open an issue", "bug report")
                            .anyMatch(text::contains),
                    doc + " no longer asks for bug reports at all. If that is deliberate, drop this"
                            + " page from the list; if it is not, the request was lost in an edit.");

            assertTrue(
                    text.contains(OWN_REPO + "/issues"),
                    doc + " asks the reader to file a report but never links "
                            + OWN_REPO + "/issues. The only mcMMO link on these pages used to be"
                            + " upstream's, so 'file an issue' with no link sent people there.");
        }
    }

    // --- helpers -------------------------------------------------------------

    /**
     * The {@code "contact": { ... }} object, brace-matched rather than regex-matched. A
     * {@code \{[^}]*\}} pattern would stop at the first nested close brace and quietly return a
     * partial block, which is exactly the over-narrow read that makes a "does not contain" assertion
     * meaningless.
     */
    private static String contactBlockOf(String json) {
        final int key = json.indexOf("\"contact\"");
        if (key < 0) {
            return "";
        }
        final int open = json.indexOf('{', key);
        if (open < 0) {
            return "";
        }

        int depth = 0;
        for (int i = open; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(open, i + 1);
                }
            }
        }
        return "";
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file.toAbsolutePath(), e);
        }
    }
}
