package com.gmail.nossr50.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.ConfigLoader;
import com.gmail.nossr50.config.HiddenConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * <b>The config-table guard</b> (multi-version TODO &sect;56.1): the two config tables promise the
 * reader that the files they list are written to {@code .minecraft/config/mcmmo/} on first load.
 * That is true of eleven of the twelve bundled {@code .yml} resources and <b>false of
 * {@code hidden.yml}</b>, which {@link HiddenConfig} reads straight off the classpath -- it does not
 * extend {@link ConfigLoader}, so there is no {@code initConfig} write-out and no
 * {@code copyMissingDefaults} back-fill, and the file a player goes looking for is not there.
 *
 * <p>This landed alongside the same lie inside {@code hidden.yml}'s own header, and the docs half is
 * the worse one: the jar comment is read by whoever opens the jar, the table is read by every
 * player. It is the caveat-expiry blind spot stated in {@code AGENTS.md} -- <em>the page carrying
 * the stale claim is almost never the page the fix touched</em>.
 *
 * <p><b>It asserts BOTH directions, deliberately.</b> A guard that only checks for the warning goes
 * permanently green the day {@code HiddenConfig} is reworked to write itself to disk, leaving the
 * docs warning players away from a file that is now sitting right there. So the code fact is
 * measured by reflection first and the documents are held to whichever answer it gives.
 *
 * <p><b>What this cannot see:</b> a twelfth config growing the same divergence. The pairing below is
 * hand-kept because there is no filename-to-loader-class registry to walk; if one is ever built,
 * this test should read it instead. Until then {@link #hiddenYmlIsStillTheOnlyClasspathOnlyConfig}
 * pins the count so a new bundled resource cannot slip in unnoticed.
 */
class ConfigDocsMatchLoaderTest {

    /**
     * The claim under test, as it is worded in both documents. Chosen because only a row that
     * actually states the exception can produce it -- a generic "advanced users only" or
     * "rarely-touched" row cannot.
     */
    private static final String MARKER = "never written to disk";

    /** The table row both documents key on. */
    private static final String ROW_KEY = "`hidden.yml`";

    private static final List<Path> DOCS = List.of(
            Path.of("README.md"),
            Path.of("wiki", "Configuration.md"));

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    /**
     * The code fact everything below hangs on: {@code hidden.yml} has no disk copy because
     * {@link HiddenConfig} is not a {@link ConfigLoader}.
     */
    private static boolean hiddenConfigIsDiskBacked() {
        return ConfigLoader.class.isAssignableFrom(HiddenConfig.class);
    }

    @Test
    void configTablesFlagHiddenYmlExactlyWhenItHasNoDiskCopy() throws IOException {
        final boolean diskBacked = hiddenConfigIsDiskBacked();

        for (Path doc : DOCS) {
            assertTrue(Files.exists(doc), doc + " is missing -- this guard reads it from the repo"
                    + " root, so it must run with the project directory as the working directory");

            final List<String> rows = new ArrayList<>();
            for (String line : Files.readString(doc, StandardCharsets.UTF_8).split("\\R")) {
                if (line.startsWith("|") && line.contains(ROW_KEY)) {
                    rows.add(line);
                }
            }

            // Guard the guard: no row means every assertion below is satisfied by nothing at all.
            assertEquals(1, rows.size(),
                    doc + " must have exactly one config-table row naming " + ROW_KEY
                            + ", found " + rows.size() + ". This guard reads that row and cannot"
                            + " report on a table it failed to find.");

            final String row = rows.get(0);

            if (diskBacked) {
                assertFalse(row.contains(MARKER),
                        doc + " still warns that " + ROW_KEY + " is \"" + MARKER + "\", but"
                                + " HiddenConfig now extends ConfigLoader, so the file IS written"
                                + " to .minecraft/config/mcmmo/. Delete the warning -- it now sends"
                                + " players away from a config that exists.\nRow: " + row);
            } else {
                assertTrue(row.contains(MARKER),
                        doc + "'s config table lists " + ROW_KEY + " under a heading promising the"
                                + " listed files are written to .minecraft/config/mcmmo/ on first"
                                + " load. HiddenConfig does not extend ConfigLoader, so that file"
                                + " is never written and a player will not find it. The row must"
                                + " say so (\"" + MARKER + "\").\nRow: " + row);
            }
        }
    }

    /**
     * The exception must stay an exception. If a second bundled resource stops being disk-backed,
     * both documents' wording ("<em>the only config that is never written to disk</em>") becomes
     * false and the test above would happily stay green, because it only ever reads one row.
     */
    @Test
    void hiddenYmlIsStillTheOnlyClasspathOnlyConfig() throws IOException {
        assertTrue(Files.isDirectory(RESOURCES), RESOURCES + " is missing");

        final List<String> bundled = new ArrayList<>();
        try (var entries = Files.list(RESOURCES)) {
            entries.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".yml"))
                    .sorted()
                    .forEach(bundled::add);
        }

        // Not a magic number for its own sake: it is the denominator behind "the only config",
        // and a new bundled .yml has to be classified before that phrase can stay true.
        assertEquals(12, bundled.size(),
                "the set of bundled .yml resources changed: " + bundled + ". Decide whether the new"
                        + " file is disk-backed (a ConfigLoader subclass) or classpath-only like"
                        + " hidden.yml, then update the config tables in " + DOCS + " and this"
                        + " count together. \"The only config that is never written to disk\" is a"
                        + " claim about this whole set, not about hidden.yml alone.");

        assertTrue(bundled.contains("hidden.yml"), "hidden.yml is no longer bundled: " + bundled);
        assertFalse(hiddenConfigIsDiskBacked(),
                "HiddenConfig now extends ConfigLoader. That is a real behaviour change, not a"
                        + " refactor: hidden.yml would start being written to disk and back-filled."
                        + " Update both config tables and this guard in the same commit.");
    }
}
