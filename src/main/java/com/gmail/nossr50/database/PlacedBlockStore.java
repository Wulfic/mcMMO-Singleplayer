package com.gmail.nossr50.database;

import com.gmail.nossr50.util.LogUtils;
import com.gmail.nossr50.util.PlacedBlockTracker;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-restart persistence for the §A {@link PlacedBlockTracker} — the port's replacement for
 * legacy's per-chunk {@code McMMOSimpleRegionFile}s under {@code util/blockmeta/}. Without it a
 * hand-placed block re-mined <em>after</em> a world reload pays out again, which is the residual half
 * of the place → mine → repeat XP farm the tracker exists to close.
 *
 * <p>Bound to one file inside the world save ({@code <worldRoot>/mcmmo/placed_blocks.dat}), loaded at
 * server start and written at autosave and server stop, so the flags follow the world rather than the
 * JVM. Legacy sharded by chunk because a Bukkit server holds every world's flags at once; a
 * singleplayer session has one world open and a set bounded by still-standing hand-placed blocks, so
 * one document is enough.
 *
 * <p><b>Format</b> — binary, not the YAML the profile store uses, because the payload is millions of
 * opaque {@code long}s at worst and 8 bytes each is the difference between a 400 KB file and a
 * multi-megabyte one:
 * <pre>
 * int    magic          0x4D434D4F ("MCMO")
 * int    version        1
 * int    worldCount
 *   per world:
 *     UTF  worldKey     e.g. "minecraft:overworld"
 *     int  positionCount
 *     long × positionCount   packed BlockPos#asLong() values
 * </pre>
 *
 * <p><b>Every failure path is fail-open and logged.</b> A missing, truncated, or foreign file leaves
 * the tracker empty, which degrades to exactly the pre-persistence behaviour (placed blocks pay out
 * again) rather than throwing into world load. Writes go to a sibling {@code .tmp} and are moved into
 * place, so a crash mid-write cannot leave a half-written file where the previous good one was.
 */
public final class PlacedBlockStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcMMO/PlacedBlockStore");

    /** {@code "MCMO"} in ASCII — rejects a file that is not ours before any count is trusted. */
    private static final int MAGIC = 0x4D434D4F;

    /** Bumped only on an incompatible layout change; an unknown version loads as empty. */
    private static final int FORMAT_VERSION = 1;

    /**
     * How many positions {@link #read} buffers before it has to grow. Caps the allocation a declared
     * {@code positionCount} can trigger, so an honest count still lands one allocation while a
     * corrupt one costs 8 KB and an {@link EOFException}.
     */
    private static final int INITIAL_POSITION_CAPACITY = 1024;

    private final @NotNull Path file;

    /**
     * @param file the flat file holding this world's flags. Its parent directory is created on
     *             demand at save time, not here, so constructing a store never touches the disk.
     */
    public PlacedBlockStore(@NotNull Path file) {
        this.file = file;
    }

    /** The file this store reads and writes (for logging and tests). */
    public @NotNull Path getFile() {
        return file;
    }

    /**
     * Load the world's flags into {@code tracker}, replacing whatever it held. A world with no file
     * yet (first run, or a world that predates this feature) loads as empty — the correct default,
     * since an untracked position is eligible.
     */
    public void load(@NotNull PlacedBlockTracker tracker) {
        if (!Files.isRegularFile(file)) {
            LogUtils.debug("No placed-block data at " + file + "; starting with every block eligible.");
            tracker.restore(Map.of());
            return;
        }

        final Map<String, long[]> byWorld;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            byWorld = read(in);
        } catch (EOFException e) {
            LOGGER.error("Placed-block data at {} is truncated (interrupted write?); "
                    + "discarding it — blocks placed before this restart will reward again.", file, e);
            tracker.restore(Map.of());
            return;
        } catch (IOException | IllegalStateException e) {
            LOGGER.error("Failed to read placed-block data at {}; discarding it — blocks placed "
                    + "before this restart will reward again.", file, e);
            tracker.restore(Map.of());
            return;
        }

        tracker.restore(byWorld);
        if (tracker.size() > 0) {
            // INFO on purpose: this is the one line that proves cross-restart persistence actually
            // round-tripped, and a headless boot is the only place it can be observed before §G.
            LOGGER.info("Loaded {} hand-placed block flag(s) across {} world(s); they stay ineligible "
                    + "for gathering rewards.", tracker.size(), byWorld.size());
        } else {
            LogUtils.debug("Placed-block data at " + file + " holds no flags.");
        }
    }

    /**
     * Parsed separately from {@link #load} so every validation failure can throw and be reported by
     * the one handler there.
     *
     * <p>A count read out of the file is never trusted as an allocation size: positions are read one
     * at a time into a buffer grown on demand, so a corrupt {@code positionCount} runs out of
     * <em>file</em> ({@link EOFException}) long before it runs out of heap. Pre-sizing from the count
     * instead lets a single flipped byte turn a world load into an {@code OutOfMemoryError}.
     */
    private static @NotNull Map<String, long[]> read(@NotNull DataInputStream in) throws IOException {
        final int magic = in.readInt();
        if (magic != MAGIC) {
            throw new IllegalStateException("Not an mcMMO placed-block file (magic 0x"
                    + Integer.toHexString(magic) + ")");
        }
        final int version = in.readInt();
        if (version != FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported placed-block format version " + version
                    + " (this build reads " + FORMAT_VERSION + ")");
        }

        final int worldCount = in.readInt();
        if (worldCount < 0) {
            throw new IllegalStateException("Negative world count " + worldCount);
        }

        final Map<String, long[]> byWorld = new LinkedHashMap<>();
        for (int w = 0; w < worldCount; w++) {
            final String worldKey = in.readUTF();
            final int positionCount = in.readInt();
            if (positionCount < 0) {
                throw new IllegalStateException("Negative position count " + positionCount
                        + " for world " + worldKey);
            }
            long[] positions = new long[Math.min(positionCount, INITIAL_POSITION_CAPACITY)];
            int read = 0;
            for (int i = 0; i < positionCount; i++) {
                if (read == positions.length) {
                    positions = Arrays.copyOf(positions,
                            Math.max(INITIAL_POSITION_CAPACITY, positions.length * 2));
                }
                positions[read++] = in.readLong(); // EOF here = truncated file; the caller reports it.
            }
            byWorld.put(worldKey, positions.length == read ? positions
                    : Arrays.copyOf(positions, read));
        }
        return byWorld;
    }

    /**
     * Write the tracker's current flags to the world save. Called on the autosave tick and at server
     * stop (before the tracker is cleared). Failures are logged and swallowed: losing the flags costs
     * an exploit window, whereas throwing here would abort the shutdown save of everything after it.
     */
    public void save(@NotNull PlacedBlockTracker tracker) {
        final Map<String, long[]> byWorld = tracker.snapshot();
        final Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            final Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (DataOutputStream out = new DataOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(MAGIC);
                out.writeInt(FORMAT_VERSION);
                out.writeInt(byWorld.size());
                for (Map.Entry<String, long[]> entry : byWorld.entrySet()) {
                    out.writeUTF(entry.getKey());
                    final long[] positions = entry.getValue();
                    out.writeInt(positions.length);
                    for (long packedPos : positions) {
                        out.writeLong(packedPos);
                    }
                }
            }

            moveIntoPlace(tmp);
            LogUtils.debug("Saved " + tracker.size() + " hand-placed block flag(s) to " + file);
        } catch (IOException e) {
            LOGGER.error("Failed to save placed-block data to {}; blocks placed this session will "
                    + "reward again after a restart.", file, e);
            deleteQuietly(tmp);
        }
    }

    /**
     * Replace the live file with the freshly written temp file. {@code ATOMIC_MOVE} is the point of
     * the temp file; not every filesystem offers it, so fall back to a plain replace rather than
     * failing the whole save (the fallback's crash window is the same one we had before persistence).
     */
    private void moveIntoPlace(@NotNull Path tmp) throws IOException {
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            LOGGER.warn("Filesystem holding {} does not support atomic moves; "
                    + "falling back to a non-atomic replace.", file, e);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Best-effort cleanup of a temp file left behind by a failed write. */
    private static void deleteQuietly(@NotNull Path tmp) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException e) {
            LOGGER.warn("Could not remove the partial placed-block file {}", tmp, e);
        }
    }
}
