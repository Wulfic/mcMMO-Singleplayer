package com.gmail.nossr50.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.util.PlacedBlockTracker;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link PlacedBlockStore}, the §A/K9 cross-restart persistence of the hand-placed
 * block flags. The interesting cases are not the happy round trip but the failure modes, since every
 * one of them is deliberately fail-open: a missing, foreign, truncated or future-versioned file must
 * leave the tracker empty and the world loadable, never throw into world load.
 *
 * <p>MC-free like the tracker it serialises — positions are packed the way {@code BlockPos#asLong()}
 * packs them, and the store treats each {@code long} as opaque.
 */
class PlacedBlockStoreTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @TempDir
    Path worldDir;

    private Path file;
    private PlacedBlockStore store;
    private PlacedBlockTracker tracker;

    @BeforeEach
    void setUp() {
        file = worldDir.resolve("mcmmo").resolve("placed_blocks.dat");
        store = new PlacedBlockStore(file);
        tracker = new PlacedBlockTracker();
    }

    /** Replicates {@code BlockPos#asLong()}'s bit layout so a test position reads as (x, y, z). */
    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Load into a fresh tracker, the way a new world session does. */
    private PlacedBlockTracker reload() {
        final PlacedBlockTracker reloaded = new PlacedBlockTracker();
        new PlacedBlockStore(file).load(reloaded);
        return reloaded;
    }

    // --- the round trip that closes the exploit -----------------------------

    @Test
    void aPlacedBlockIsStillIneligibleAfterAReload() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));
        store.save(tracker);

        final PlacedBlockTracker reloaded = reload();

        assertTrue(reloaded.isIneligible(OVERWORLD, pack(10, 64, -20)));
        assertTrue(reloaded.isEligible(OVERWORLD, pack(11, 64, -20)));
    }

    @Test
    void everyWorldAndPositionSurvivesTheRoundTrip() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));
        tracker.setIneligible(OVERWORLD, pack(-3000000, 5, 2999999)); // far out, negative coords
        tracker.setIneligible(NETHER, pack(0, 70, 0));
        store.save(tracker);

        final PlacedBlockTracker reloaded = reload();

        assertEquals(3, reloaded.size());
        assertTrue(reloaded.isIneligible(OVERWORLD, pack(10, 64, -20)));
        assertTrue(reloaded.isIneligible(OVERWORLD, pack(-3000000, 5, 2999999)));
        assertTrue(reloaded.isIneligible(NETHER, pack(0, 70, 0)));
        assertTrue(reloaded.isEligible(NETHER, pack(10, 64, -20))); // worlds stay isolated on disk
    }

    /** Breaking a placed block must survive too — otherwise the flag resurrects on the next load. */
    @Test
    void aBrokenPlacedBlockIsEligibleAfterAReload() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));
        tracker.setEligible(OVERWORLD, pack(10, 64, -20));
        store.save(tracker);

        assertTrue(reload().isEligible(OVERWORLD, pack(10, 64, -20)));
    }

    @Test
    void saveCreatesTheModDataDirectory() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));

        store.save(tracker);

        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void savingAnEmptyTrackerWritesALoadableFile() {
        store.save(tracker);

        assertTrue(Files.isRegularFile(file));
        assertEquals(0, reload().size());
    }

    /** The temp file is an implementation detail; it must never be left behind after a good save. */
    @Test
    void saveLeavesNoTempFileBehind() throws IOException {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));
        store.save(tracker);

        assertFalse(Files.exists(file.resolveSibling(file.getFileName() + ".tmp")));
    }

    @Test
    void aSecondSaveReplacesTheFirst() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));
        store.save(tracker);

        tracker.setEligible(OVERWORLD, pack(10, 64, -20));
        tracker.setIneligible(OVERWORLD, pack(11, 64, -20));
        store.save(tracker);

        final PlacedBlockTracker reloaded = reload();
        assertEquals(1, reloaded.size());
        assertTrue(reloaded.isEligible(OVERWORLD, pack(10, 64, -20)));
        assertTrue(reloaded.isIneligible(OVERWORLD, pack(11, 64, -20)));
    }

    // --- fail-open loading --------------------------------------------------

    /** First run / a world that predates the feature: no file, every block eligible, no throw. */
    @Test
    void loadingAMissingFileLeavesEveryBlockEligible() {
        store.load(tracker);

        assertEquals(0, tracker.size());
        assertTrue(tracker.isEligible(OVERWORLD, pack(10, 64, -20)));
    }

    /**
     * A failed load must also <em>clear</em>, not merely skip — otherwise opening world B would
     * inherit world A's flags from the tracker, which lives for the whole JVM.
     */
    @Test
    void loadingAMissingFileDiscardsThePreviousSessionsFlags() {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));

        store.load(tracker);

        assertEquals(0, tracker.size());
    }

    @Test
    void loadingAForeignFileDoesNotThrowAndLeavesTheTrackerEmpty() throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "this is not an mcMMO placed-block file, it is a text file");

        store.load(tracker);

        assertEquals(0, tracker.size());
    }

    @Test
    void loadingATruncatedFileDoesNotThrowAndLeavesTheTrackerEmpty() throws IOException {
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));
        tracker.setIneligible(OVERWORLD, pack(11, 64, -20));
        store.save(tracker);

        final byte[] whole = Files.readAllBytes(file);
        Files.write(file, java.util.Arrays.copyOf(whole, whole.length - 4)); // cut a position in half

        final PlacedBlockTracker reloaded = reload();
        assertEquals(0, reloaded.size());
    }

    /** An empty file has no magic to read at all — the EOF must be caught like any other corruption. */
    @Test
    void loadingAnEmptyFileDoesNotThrow() throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[0]);

        store.load(tracker);

        assertEquals(0, tracker.size());
    }

    /** A file written by a future build with an incompatible layout must be refused, not misread. */
    @Test
    void loadingAFutureFormatVersionLeavesTheTrackerEmpty() throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream raw = Files.newOutputStream(file);
                DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(0x4D434D4F); // the real magic...
            out.writeInt(99);         // ...but a format this build cannot read
            out.writeInt(0);
        }

        store.load(tracker);

        assertEquals(0, tracker.size());
    }

    /**
     * A corrupt count must not be trusted into an allocation — positions are read one at a time, so
     * an absurd count hits EOF instead of trying to reserve gigabytes.
     */
    @Test
    void loadingAnAbsurdPositionCountDoesNotThrow() throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream raw = Files.newOutputStream(file);
                DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(0x4D434D4F);
            out.writeInt(1);
            out.writeInt(1);                  // one world
            out.writeUTF(OVERWORLD);
            out.writeInt(Integer.MAX_VALUE);  // ...claiming 2 billion positions
            out.writeLong(pack(10, 64, -20)); // but holding exactly one
        }

        store.load(tracker);

        assertEquals(0, tracker.size());
    }

    @Test
    void loadingANegativeWorldCountLeavesTheTrackerEmpty() throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream raw = Files.newOutputStream(file);
                DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(0x4D434D4F);
            out.writeInt(1);
            out.writeInt(-1);
        }

        store.load(tracker);

        assertEquals(0, tracker.size());
    }

    /** A directory where the file should be: the save must log and give up, not propagate. */
    @Test
    void saveSwallowsAnUnwritableTarget() throws IOException {
        Files.createDirectories(file); // the "file" is now a directory
        tracker.setIneligible(OVERWORLD, pack(10, 64, -20));

        store.save(tracker); // must not throw

        assertTrue(Files.isDirectory(file));
    }
}
