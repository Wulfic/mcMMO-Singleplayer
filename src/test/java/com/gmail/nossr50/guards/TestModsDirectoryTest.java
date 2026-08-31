package com.gmail.nossr50.guards;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.junit.jupiter.api.Test;

/**
 * <b>The mods-directory guard</b> (multi-version TODO &sect;57, risk <b>R15</b>): the build must
 * hand every test fork a mods directory that <em>already exists</em>, so {@code fabric-loader}
 * never takes its racing create path.
 *
 * <p><b>Why this guard exists.</b> {@code fabric-loader} {@code 0.19.3},
 * {@code DirectoryModCandidateFinder.findCandidates()}:
 *
 * <pre>
 * if (!Files.exists(path)) {
 *     try { Files.createDirectory(path); return; }          // SINGULAR
 *     catch (IOException e) { throw new RuntimeException("Could not create directory " + path, e); }
 * }
 * </pre>
 *
 * <p>{@code Files.createDirectory} — singular — throws {@code FileAlreadyExistsException} when the
 * path exists. {@code build.gradle} runs {@code maxParallelForks = 4}, so on a <b>fresh checkout</b>
 * all four workers evaluate {@code !Files.exists} as true, one wins the create, and a loser's
 * exception leaves the {@code FabricLoaderLauncherSessionListener} constructor as a
 * {@code ServiceConfigurationError} — killing the executor <em>before a single test runs</em>.
 *
 * <p>Measured 2026-08-31 on {@code mc/26.1.2}'s release run {@code 33445589010}: <em>":test FAILED /
 * Could not start Gradle Test Executor 1"</em>, which held that band at {@code v1.3.3} while the
 * other eight published {@code v1.3.4}. {@code loader_version} is {@code 0.19.3} on all nine
 * branches, so it was a coin flip and not a band difference; a re-run of the identical commit
 * passed, which is what a race looks like.
 *
 * <p>&#9888;&#9888; <b>The defect cannot reproduce locally.</b> Any working copy that has ever run
 * this suite already carries an empty untracked {@code mods/} at the repo root, so {@code
 * Files.exists} is true and the window never opens. Green on every developer machine, red only on a
 * fresh checkout — the same geometry as the {@link BandVersionLabelTest} defect, which shipped to
 * five branches and blocked every release from 2026-08-13.
 *
 * <h2>Why there is no "the directory exists" assertion</h2>
 *
 * <p><b>&#9888;&#9888; It is unfalsifiable here, and that was measured, not reasoned.</b> The
 * obvious guard — assert the mods directory exists — cannot fail at test time <em>for the defect
 * this class exists to catch</em>, because {@code DirectoryModCandidateFinder} creates the
 * directory itself, on the racing path, before any test method runs. Mutation <b>M3</b> put the
 * build in exactly the broken state (the override set, the build creating nothing there) and an
 * existence check was <b>GREEN</b> while the marker assertion below was red. Such an assertion was
 * written, measured, and deleted; it would have been the seventeenth vacuous assertion found in
 * this repository. <b>Do not add it back.</b>
 *
 * <p>What catches the defect instead is a <b>marker file</b> that {@code build.gradle} writes into
 * the directory it pre-creates. The loader never writes one, so a loader-created directory is
 * distinguishable from a build-created one — which is the whole question this guard is asking, and
 * the only form of it that has an answer.
 *
 * <p>&#9888; The marker cannot be mistaken for a mod: {@code DirectoryModCandidateFinder.isValidFile}
 * requires {@code isRegularFile && !isHidden && endsWith(".jar") && !startsWith(".")}, and a
 * {@code .txt} fails the {@code .jar} test outright.
 *
 * <p>&#9888; {@code build.gradle} pairs the system property with an {@code inputs.property} carrying
 * the <b>relative</b> location. Without an input declaration {@code :test} serves a <b>cached pass</b>
 * after the wiring changes — measured 2026-08-18, where three genuine mutations all scored <em>"not
 * caught"</em> for exactly that reason. Relative rather than absolute because this build runs with
 * {@code org.gradle.caching=true}, and an absolute path in the cache key would miss the cache on
 * every machine.
 *
 * <p>&#9888; <b>This class must not live in {@code com.gmail.nossr50.fabric.mixin}</b> — same reason
 * as {@link CompilerErrorCapTest}: the Mixin transformer claims every class in the package
 * {@code mcmmo.mixins.json} declares, including a test.
 */
class TestModsDirectoryTest {

    /**
     * {@code fabric-loader}'s own override, read at its single resolution point —
     * {@code FabricLoaderImpl.getModsDirectory0()}:
     * {@code directory != null ? Paths.get(directory) : gameDir.resolve("mods")}.
     *
     * <p>Naming the loader's property rather than one of our own is what makes the first assertion
     * load bearing: nothing but the real wiring can satisfy it, and if it is unset the loader falls
     * back to {@code gameDir.resolve("mods")} and the race is back.
     */
    private static final String MODS_FOLDER_PROPERTY = "fabric.modsFolder";

    /** Written by {@code build.gradle}'s {@code doFirst}. The loader never writes one. */
    private static final String MARKER_FILE_NAME = "mcmmo-test-mods.txt";

    /** Caught by mutation M1 — the whole {@code doFirst} reverted. */
    @Test
    void theBuildPointsEveryForkAtAModsDirectoryItControls() {
        final String value = modsFolder();
        assertNotNull(value, "build.gradle no longer exports " + MODS_FOLDER_PROPERTY
                + ". Without it fabric-loader falls back to gameDir.resolve(\"mods\") -- the repo "
                + "root on a Gradle test worker -- and four forks race Files.createDirectory there "
                + "on any checkout where that directory does not yet exist. That is the failure "
                + "that held mc/26.1.2 at v1.3.3 on 2026-08-31 while the other eight branches "
                + "published v1.3.4. See TODO.md 57.");
        assertTrue(!value.trim().isEmpty(), () -> MODS_FOLDER_PROPERTY
                + " is set but blank, which fabric-loader reads as a path of \"\". See TODO.md 57.");
    }

    /**
     * The load-bearing assertion, and the only one that can see the real defect.
     *
     * <p>Caught mutation <b>M3</b> — the override pointed at a path the build does not create,
     * which is the race relocated rather than closed — while an existence check stayed green on the
     * same run. Also caught <b>M2</b>, the marker write alone removed.
     */
    @Test
    void theDirectoryCarriesTheBuildsMarkerAndNotJustTheLoaders() {
        final File dir = modsFolderFile();
        final File marker = new File(dir, MARKER_FILE_NAME);
        assertTrue(marker.isFile(), () -> "The mods directory '" + dir + "' holds no "
                + MARKER_FILE_NAME + ", so fabric-loader created it on its racing "
                + "Files.createDirectory path rather than build.gradle creating it before the forks "
                + "started. Restore the doFirst block in build.gradle's test { } -- see TODO.md 57. "
                + "⚠ Do NOT 'fix' this by asserting the directory merely exists: the loader creates "
                + "it in the broken state too, so that assertion is green either way. It was "
                + "written, measured against mutation M3, and deleted as vacuous.");
    }

    private static String modsFolder() {
        return System.getProperty(MODS_FOLDER_PROPERTY);
    }

    private static File modsFolderFile() {
        final String value = modsFolder();
        assertNotNull(value, "build.gradle no longer exports " + MODS_FOLDER_PROPERTY
                + ". See TODO.md 57.");
        return new File(value);
    }
}
