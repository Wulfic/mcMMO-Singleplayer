package com.gmail.nossr50.skills.herbalism;

import com.gmail.nossr50.util.MaterialMapStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The pure, MC-free heart of Herbalism's multi-block plant handling: given the coordinate and type of
 * the block a player just broke, it decides the full set of plant blocks that break along with it, so
 * every one of them can be rewarded. Faithful port of legacy
 * {@code HerbalismManager#getBrokenHerbalismBlocks} / {@code addBrokenBlocksMultiBlockPlants} /
 * {@code addChorusTreeBrokenBlocks} / {@code addCactusBlocks} / {@code addBlocksBrokenAboveOrBelow},
 * lifted out of the Bukkit {@code Block}/{@code BlockFace} world into integer space so the three
 * search shapes can be unit-tested without a live world. The MC-typed half — capturing block states,
 * awarding XP, spawning bonus drops — lives in
 * {@link com.gmail.nossr50.fabric.listeners.BlockBreakListener}, which supplies the {@link PlantLookup}
 * over a real world.
 *
 * <p>There are three search shapes, picked off the broken block's type exactly as legacy does:
 * <ul>
 *   <li><b>Chorus tree</b> (the origin is a {@code chorus_plant} branch): a recursive flood fill over
 *       UP/NORTH/SOUTH/EAST/WEST through every connected {@code chorus_plant}/{@code chorus_flower}.</li>
 *   <li><b>Cactus</b> ({@code cactus}/{@code cactus_flower}): a recursive walk up <i>and</i> down the
 *       column (a cactus can be broken in the middle, taking the segments above with it).</li>
 *   <li><b>Everything else</b> (sugar cane, kelp, bamboo, tall grass, vines…): a straight vertical
 *       scan, upwards by default or downwards for a
 *       {@link MaterialMapStore#isMultiBlockHangingPlant(String) hanging} plant, stopping at the first
 *       block that isn't a multi-block plant.</li>
 * </ul>
 *
 * <p><b>⚠️ UPSTREAM BUG (fixed here): legacy's chorus and cactus traversals never ran.</b> Both
 * recursive helpers guard against re-visiting with {@code if (!traversed.add(currentBlock)) return;},
 * but their only caller ({@code getBrokenHerbalismBlocks}) had <i>already</i> put the origin block
 * into that same set one line earlier — and Bukkit's {@code CraftBlock} has value equality by world +
 * coordinates, so the very first {@code add} returned {@code false} and the recursion bailed before
 * looking at a single neighbour. Upstream therefore rewards exactly one block for a chorus tree or a
 * cactus column, leaving the whole recursive search, the {@code chorus_plant: 22} break limit and the
 * delayed chorus XP task as dead code. This port seeds the traversal with an <i>empty</i> set so the
 * origin is added by the recursion itself, which is plainly the intended behaviour. The vertical scan
 * was never affected — it is a plain {@code for} loop with no re-visit guard.
 */
public final class MultiBlockPlantTraversal {

    /** Legacy's chorus flood-fill cap ("who needs more than 256 chorus anyways"). */
    private static final int CHORUS_LIMIT = 256;

    /** Legacy's cactus cap — a natural cactus is at most 3 tall, plus its flower. */
    private static final int CACTUS_LIMIT = 4;

    /** Legacy's vertical scan cap, counted from (and including) the origin block. */
    private static final int VERTICAL_LIMIT = 512;

    private static final String CHORUS_PLANT = "chorus_plant";
    private static final String CHORUS_FLOWER = "chorus_flower";
    private static final String CACTUS = "cactus";
    private static final String CACTUS_FLOWER = "cactus_flower";

    /** Reads the block type at an integer world coordinate. */
    @FunctionalInterface
    public interface PlantLookup {
        /**
         * @return the vanilla registry <i>path</i> of the block at this coordinate (e.g.
         *     {@code "sugar_cane"}), or {@code null} when the coordinate can't be read (outside the
         *     world, unloaded chunk…), which the traversal treats as "not part of the plant"
         */
        @Nullable String pathAt(int x, int y, int z);
    }

    /** One block the break will take down, in discovery order (the origin is always first). */
    public record PlantCoord(int x, int y, int z, @NotNull String path) {}

    private MultiBlockPlantTraversal() {
    }

    /** Whether a block is part of a chorus tree — the branch body or its flower tip. */
    public static boolean isChorusTree(@NotNull String path) {
        return path.equals(CHORUS_PLANT) || path.equals(CHORUS_FLOWER);
    }

    /** Whether a block is a chorus <i>branch</i>, the only entry point into the flood fill. */
    public static boolean isChorusBranch(@NotNull String path) {
        return path.equals(CHORUS_PLANT);
    }

    private static boolean isCactus(@NotNull String path) {
        return path.equals(CACTUS) || path.equals(CACTUS_FLOWER);
    }

    /**
     * Collects every plant block that comes down with the block at {@code (startX, startY, startZ)}.
     *
     * <p>Callers should only invoke this when the origin is actually a multi-block plant; a
     * single-block plant returns just itself, which is wasted work on the common break path.
     *
     * @param startX the broken block's X
     * @param startY the broken block's Y
     * @param startZ the broken block's Z
     * @param originPath the broken block's registry path (read before the break removed it)
     * @param materials the multi-block plant tables ({@code MaterialMapStore})
     * @param lookup reads the block type at any coordinate
     * @return the broken plant blocks in discovery order, always starting with the origin
     */
    public static @NotNull List<PlantCoord> collect(int startX, int startY, int startZ,
            @NotNull String originPath, @NotNull MaterialMapStore materials,
            @NotNull PlantLookup lookup) {
        // A LinkedHashMap gives legacy's HashSet dedup while keeping discovery order, so the origin
        // stays first. That matters for the tall-plant XP cap, which legacy keyed off an *arbitrary*
        // member of an unordered HashSet (see BlockBreakListener) — here the caller can rely on
        // element 0 being the block the player actually hit.
        final Map<Long, PlantCoord> found = new LinkedHashMap<>();

        if (isChorusBranch(originPath)) {
            addChorusTree(startX, startY, startZ, found, lookup);
        } else if (isCactus(originPath)) {
            addCactusColumn(startX, startY, startZ, found, lookup);
        } else {
            addVerticalColumn(startX, startY, startZ, originPath, found, materials, lookup);
        }
        return new ArrayList<>(found.values());
    }

    /**
     * Flood fill through a connected chorus tree (legacy {@code addChorusTreeBrokenBlocks}). Searches
     * up and sideways only — never down, since the block below is what holds the tree up and is
     * therefore not brought down by this break.
     */
    private static void addChorusTree(int x, int y, int z, Map<Long, PlantCoord> found,
            PlantLookup lookup) {
        final String path = lookup.pathAt(x, y, z);
        if (path == null || !isChorusTree(path)) {
            return;
        }
        if (found.size() > CHORUS_LIMIT) {
            return;
        }
        if (found.putIfAbsent(key(x, y, z), new PlantCoord(x, y, z, path)) != null) {
            return; // already traversed
        }
        addChorusTree(x, y + 1, z, found, lookup);
        addChorusTree(x, y, z - 1, found, lookup); // north
        addChorusTree(x, y, z + 1, found, lookup); // south
        addChorusTree(x + 1, y, z, found, lookup); // east
        addChorusTree(x - 1, y, z, found, lookup); // west
    }

    /**
     * Walk a cactus column in both directions (legacy {@code addCactusBlocks}). Unlike sugar cane, a
     * cactus is searched downwards as well — breaking the middle of a column detaches the segments
     * above, and legacy chose to reward the whole column either way.
     */
    private static void addCactusColumn(int x, int y, int z, Map<Long, PlantCoord> found,
            PlantLookup lookup) {
        final String path = lookup.pathAt(x, y, z);
        if (path == null || !isCactus(path)) {
            return;
        }
        if (found.size() > CACTUS_LIMIT) {
            return;
        }
        if (found.putIfAbsent(key(x, y, z), new PlantCoord(x, y, z, path)) != null) {
            return; // already traversed
        }
        addCactusColumn(x, y + 1, z, found, lookup);
        addCactusColumn(x, y - 1, z, found, lookup);
    }

    /**
     * Scan straight up (or down, for a hanging plant) collecting multi-block plant blocks until the
     * first block that isn't one — legacy {@code addBlocksBrokenAboveOrBelow}. Legacy started its
     * loop at the origin itself; the origin is seeded here instead, so the scan starts one block out.
     */
    private static void addVerticalColumn(int x, int y, int z, String originPath,
            Map<Long, PlantCoord> found, MaterialMapStore materials, PlantLookup lookup) {
        found.put(key(x, y, z), new PlantCoord(x, y, z, originPath));
        final int step = materials.isMultiBlockHangingPlant(originPath) ? -1 : 1;
        for (int offset = 1; offset < VERTICAL_LIMIT; offset++) {
            final int currentY = y + offset * step;
            final String path = lookup.pathAt(x, currentY, z);
            if (path == null || isOneBlockPlant(path, materials)) {
                return; // end of the plant
            }
            found.put(key(x, currentY, z), new PlantCoord(x, currentY, z, path));
        }
    }

    /**
     * Whether a block stands alone rather than being part of a multi-block plant — legacy
     * {@code isOneBlockPlant}. Note this is true of every ordinary block too (stone, air…), which is
     * exactly what makes it a usable stop condition for the vertical scan.
     */
    public static boolean isOneBlockPlant(@NotNull String path, @NotNull MaterialMapStore materials) {
        return !materials.isMultiBlockPlant(path) && !materials.isMultiBlockHangingPlant(path);
    }

    /** Packs a coordinate into a dedup key; the ranges cover every legal world coordinate. */
    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }
}
