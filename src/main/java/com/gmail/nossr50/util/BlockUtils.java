package com.gmail.nossr50.util;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Block classification helpers — the singleplayer port of the legacy Bukkit {@code BlockUtils},
 * built the same way as its sibling {@link ItemUtils}: thin, MC-typed wrappers that extract a live
 * block's vanilla registry-id path (e.g. {@code oak_log}) and delegate the actual decision to an
 * already-unit-tested, MC-free layer.
 *
 * <p>Two backing layers, keyed differently (both proven MC-free elsewhere, this class only bridges a
 * live {@link Block}/{@link BlockState} to them):
 * <ul>
 *   <li>the hardcoded whitelists in {@link MaterialMapStore} (ability/tool/mossy/shroomy/herbalism/
 *       block-cracker/tree-feller/ore sets) — keyed on the <b>registry path</b> ({@code oak_log});</li>
 *   <li>the per-block XP tables in {@link ExperienceConfig} — keyed on the <b>config string</b>
 *       ({@code Oak_Log}, produced by {@link ConfigStringUtils#getMaterialConfigString}). These
 *       {@code affectedBy*}/{@code hasWoodcuttingXP} checks null-guard the config so they are usable
 *       before a server session has loaded {@code experience.yml} (return {@code false}, matching
 *       "no XP" — see {@link com.gmail.nossr50.skills.BlockBreakXp}).</li>
 * </ul>
 *
 * <p>The id-path extraction needs live registries, so these are exercised in {@code BlockUtilsTest}
 * under the {@code fabric-loader-junit} harness ({@code Bootstrap.initialize()} in a {@code @BeforeAll}).
 *
 * <p>The placed-block eligibility bridge <em>is</em> ported here now (see {@link #markPlaced}/
 * {@link #markNatural}/{@link #isRewardIneligible}), backed by the MC-free {@link PlacedBlockTracker} —
 * this is legacy {@code setUnnaturalBlock}/{@code setNaturalBlock} on a live world + {@link BlockPos}.
 *
 * <p><b>Deliberately NOT ported here</b> (each needs an adapter or config mcMMO doesn't have yet; PORT
 * breadcrumbs for when the consuming body lands): the remaining metadata mutators
 * ({@code markDropsAsBonus}/{@code cleanupBlockMetadata} — need the Bukkit-metadata adapter),
 * {@code checkDoubleDrops} (RNG + {@code McMMOPlayer}),
 * {@code shouldBeWatched} (a listener-filter convenience — the block-break listener already routes
 * XP), the live-state predicates ({@code isFullyGrown}/{@code Ageable}, {@code isWithinWorldBounds},
 * {@code isPistonPiece}), {@code getTransparentBlocks}/{@code getShortGrass} (whole-registry sweeps),
 * and the mcMMO-anvil identity ({@code isMcMMOAnvil} + the anvil exclusion in {@link
 * #canActivateTools} — both need the still-unported Repair/Salvage item configs' {@code anvilMaterial}).
 */
public final class BlockUtils {

    private BlockUtils() {}

    /**
     * The vanilla registry-id <em>path</em> of a block (e.g. {@code oak_log} for
     * {@code minecraft:oak_log}) — the key {@link MaterialMapStore} is keyed on.
     */
    private static @NotNull String idPath(@NotNull Block block) {
        return Registries.BLOCK.getId(block).getPath();
    }

    /** Whether the block grants XP for the given skill in the loaded {@code experience.yml}. */
    private static boolean givesSkillXp(@NotNull Block block, @NotNull PrimarySkillType skill) {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (config == null) {
            return false; // configs not loaded (e.g. unit tests before a session): treat as no XP.
        }
        return config.doesBlockGiveSkillXP(skill, ConfigStringUtils.getMaterialConfigString(idPath(block)));
    }

    // --- Super-ability activation gates -------------------------------------

    /**
     * Whether a block should allow super-ability activation (step of the right/left-click trigger).
     * A short, hardcoded blacklist (e.g. interactive blocks) is excluded.
     */
    public static boolean canActivateAbilities(@NotNull Block block) {
        return !McMMOMod.getMaterialMapStore().isAbilityActivationBlackListed(idPath(block));
    }

    public static boolean canActivateAbilities(@NotNull BlockState blockState) {
        return canActivateAbilities(blockState.getBlock());
    }

    /**
     * Whether a block should allow tool activation — step 1 of the 2-step super-ability activation.
     *
     * <p>PORT: legacy also excludes the Repair/Salvage anvil materials here; those come from the
     * not-yet-ported Repair/Salvage item configs, so only the MaterialMapStore blacklist is applied
     * for now (no vanilla block is an mcMMO anvil until an operator configures one).
     */
    public static boolean canActivateTools(@NotNull Block block) {
        return !McMMOMod.getMaterialMapStore().isToolActivationBlackListed(idPath(block));
    }

    public static boolean canActivateTools(@NotNull BlockState blockState) {
        return canActivateTools(blockState.getBlock());
    }

    /** Whether a block can activate Herbalism abilities (Green Terra double-drop reach). */
    public static boolean canActivateHerbalism(@NotNull Block block) {
        return McMMOMod.getMaterialMapStore().isHerbalismAbilityWhiteListed(idPath(block));
    }

    public static boolean canActivateHerbalism(@NotNull BlockState blockState) {
        return canActivateHerbalism(blockState.getBlock());
    }

    // --- Super-ability affected-block checks --------------------------------

    /**
     * Whether a block is affected by Super Breaker (Mining super ability). True if the block is
     * intended to be broken by a pickaxe, or grants Mining XP in the config.
     */
    public static boolean affectedBySuperBreaker(@NotNull Block block) {
        if (McMMOMod.getMaterialMapStore().isIntendedToolPickaxe(idPath(block))) {
            return true;
        }
        return givesSkillXp(block, PrimarySkillType.MINING);
    }

    public static boolean affectedBySuperBreaker(@NotNull BlockState blockState) {
        return affectedBySuperBreaker(blockState.getBlock());
    }

    /** Whether a block is affected by Giga Drill Breaker (Excavation super ability). */
    public static boolean affectedByGigaDrillBreaker(@NotNull Block block) {
        return givesSkillXp(block, PrimarySkillType.EXCAVATION);
    }

    public static boolean affectedByGigaDrillBreaker(@NotNull BlockState blockState) {
        return affectedByGigaDrillBreaker(blockState.getBlock());
    }

    /** Whether a block is affected by Green Terra (Herbalism super ability) — grants Herbalism XP. */
    public static boolean affectedByGreenTerra(@NotNull Block block) {
        return givesSkillXp(block, PrimarySkillType.HERBALISM);
    }

    public static boolean affectedByGreenTerra(@NotNull BlockState blockState) {
        return affectedByGreenTerra(blockState.getBlock());
    }

    /** Whether a block is affected by Block Cracker (Berserk turning smooth blocks to cracked). */
    public static boolean affectedByBlockCracker(@NotNull Block block) {
        return McMMOMod.getMaterialMapStore().isBlockCrackerWhiteListed(idPath(block));
    }

    public static boolean affectedByBlockCracker(@NotNull BlockState blockState) {
        return affectedByBlockCracker(blockState.getBlock());
    }

    /**
     * Whether Berserk insta-breaks this block. Ports the {@code BERSERK} branch of legacy
     * {@code SuperAbilityType#blockCheck(Block)}, which lands here rather than back on the enum so
     * {@link com.gmail.nossr50.datatypes.skills.SuperAbilityType} stays MC-free. That switch's other
     * branches are each just a sibling check already exposed by this class
     * ({@link #affectedByGigaDrillBreaker}/{@link #canMakeMossy}/{@link #affectedBySuperBreaker}/
     * {@link #hasWoodcuttingXP}) called directly at their single call site, so re-adding the
     * dispatch would only duplicate them.
     */
    public static boolean affectedByBerserk(@NotNull BlockState blockState) {
        return affectedByGigaDrillBreaker(blockState)
                || blockState.isOf(Blocks.SNOW)
                || McMMOMod.getMaterialMapStore().isGlass(idPath(blockState.getBlock()));
    }

    // --- Woodcutting / tree ------------------------------------------------

    /** Whether a block grants Woodcutting XP (i.e. is a log addressed by the config). */
    public static boolean hasWoodcuttingXP(@NotNull Block block) {
        return givesSkillXp(block, PrimarySkillType.WOODCUTTING);
    }

    public static boolean hasWoodcuttingXP(@NotNull BlockState blockState) {
        return hasWoodcuttingXP(blockState.getBlock());
    }

    /** Whether a block is a non-wood part of a tree (leaves, mushroom caps, warts) for Tree Feller. */
    public static boolean isNonWoodPartOfTree(@NotNull Block block) {
        return McMMOMod.getMaterialMapStore().isTreeFellerDestructible(idPath(block));
    }

    public static boolean isNonWoodPartOfTree(@NotNull BlockState blockState) {
        return isNonWoodPartOfTree(blockState.getBlock());
    }

    /** Whether a block is any part of a tree — a Woodcutting log, or a non-wood tree part. */
    public static boolean isPartOfTree(@NotNull Block block) {
        return hasWoodcuttingXP(block) || isNonWoodPartOfTree(block);
    }

    public static boolean isPartOfTree(@NotNull BlockState blockState) {
        return isPartOfTree(blockState.getBlock());
    }

    // --- Mining / ore ------------------------------------------------------

    /** Whether a block is an ore. */
    public static boolean isOre(@NotNull Block block) {
        return McMMOMod.getMaterialMapStore().isOre(idPath(block));
    }

    public static boolean isOre(@NotNull BlockState blockState) {
        return isOre(blockState.getBlock());
    }

    // --- Herbalism block conversions ---------------------------------------

    /** Whether a block can be made mossy (Green Terra / mossify conversion whitelist). */
    public static boolean canMakeMossy(@NotNull Block block) {
        return McMMOMod.getMaterialMapStore().isMossyWhiteListed(idPath(block));
    }

    public static boolean canMakeMossy(@NotNull BlockState blockState) {
        return canMakeMossy(blockState.getBlock());
    }

    /** Whether a block can be made into Mycelium (Shroom Thumb conversion whitelist). */
    public static boolean canMakeShroomy(@NotNull Block block) {
        return McMMOMod.getMaterialMapStore().isShroomyWhiteListed(idPath(block));
    }

    public static boolean canMakeShroomy(@NotNull BlockState blockState) {
        return canMakeShroomy(blockState.getBlock());
    }

    /**
     * Classify a block into its Hylian Luck {@code Drops_From} group ({@code "Flowers"},
     * {@code "Bushes"} or {@code "Pots"}), or {@code null} if it is not a Hylian Luck source block.
     * This is the live-block half of legacy {@code TreasureConfig.registerHylianDrops}: legacy expanded
     * the three groups into a material-keyed {@code hylianMap} at config load, but two of the groups
     * (Bushes' saplings, and all of Pots) come from the vanilla {@code saplings}/{@code flower_pots}
     * block tags — which are only bound once the datapacks have loaded, not necessarily at the
     * {@code SERVER_STARTING} config load. So the port keys {@code hylianMap} by the raw group name and
     * resolves membership here, at block-break time, where the world session (and its tags) are fully
     * live. The result is identical to legacy's expanded lookup: a block matches at most one group.
     *
     * <p>Matched exactly as legacy did: the nine small flowers are a hardcoded list (legacy lists them
     * individually, not via the broader {@code small_flowers} tag — see
     * {@link MaterialMapStore#isHylianLuckFlower}); {@code fern}/{@code short_grass}/{@code dead_bush}
     * are hardcoded bush members; saplings come from {@link BlockTags#SAPLINGS} and flower pots from
     * {@link BlockTags#FLOWER_POTS}.
     *
     * @param blockState the broken block's state
     * @return the group name, or {@code null} if the block drops no Hylian treasure
     */
    public static @Nullable String getHylianTreasureGroup(@NotNull BlockState blockState) {
        final String id = idPath(blockState.getBlock());
        final MaterialMapStore store = McMMOMod.getMaterialMapStore();
        if (store.isHylianLuckFlower(id)) {
            return "Flowers";
        }
        if (store.isHylianLuckBushBlock(id) || blockState.isIn(BlockTags.SAPLINGS)) {
            return "Bushes";
        }
        if (blockState.isIn(BlockTags.FLOWER_POTS)) {
            return "Pots";
        }
        return null;
    }

    // --- Crop maturity (legacy Bukkit Ageable) ------------------------------

    /**
     * The current and maximum value of a block's {@code age} state property — the vanilla equivalent
     * of Bukkit's {@code Ageable} that legacy Herbalism read to decide crop maturity. Vanilla has no
     * single {@code Ageable} interface, so this scans for the {@link IntProperty} named {@code "age"}
     * (every crop/plant that legacy treated as ageable exposes exactly one), returning {@code null}
     * for a block with no such property (stone, a log, a flower).
     *
     * @param blockState the (pre-break) block state to inspect
     * @return the age info, or {@code null} if the block has no {@code age} property
     */
    public static @Nullable AgeableState getAgeableState(@NotNull BlockState blockState) {
        final IntProperty ageProperty = ageProperty(blockState);
        if (ageProperty == null) {
            return null;
        }
        final int maxAge = ageProperty.getValues().stream()
                .mapToInt(Integer::intValue).max().orElse(0);
        return new AgeableState(blockState.get(ageProperty), maxAge);
    }

    /**
     * Returns {@code blockState} with its {@code age} state property set to {@code age} (clamped to
     * the property's valid range), or the state unchanged if it has no {@code age} property. The
     * vanilla equivalent of Bukkit's {@code Ageable.setAge(int)} that legacy {@code DelayedCropReplant}
     * used to re-seed a harvested crop under Green Thumb. Every other property is preserved (notably a
     * cocoa pod's facing), so a caller replanting off the pre-break state need not rebuild it. Clamping
     * keeps {@link BlockState#with} from throwing when a high Green Thumb stage would exceed a short
     * crop's maximum age.
     *
     * @param blockState the crop state to re-age
     * @param age the desired age
     * @return the re-aged state, or the original if it has no {@code age} property
     */
    public static @NotNull BlockState withAge(@NotNull BlockState blockState, int age) {
        final IntProperty ageProperty = ageProperty(blockState);
        if (ageProperty == null) {
            return blockState;
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int value : ageProperty.getValues()) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return blockState.with(ageProperty, Math.max(min, Math.min(age, max)));
    }

    /**
     * The {@link IntProperty} named {@code "age"} on a block, or {@code null} if it has none. Vanilla
     * has no single {@code Ageable} interface, so both {@link #getAgeableState} and {@link #withAge}
     * locate crop maturity through this one scan; every crop/plant legacy treated as ageable exposes
     * exactly one such property, and the {@code "age"} filter skips {@code stage}/{@code layers}/etc.
     */
    private static @Nullable IntProperty ageProperty(@NotNull BlockState blockState) {
        for (Property<?> property : blockState.getProperties()) {
            if (property instanceof IntProperty ageProperty && "age".equals(ageProperty.getName())) {
                return ageProperty;
            }
        }
        return null;
    }

    /**
     * The current and maximum {@code age} of an ageable block (legacy {@code Ageable.getAge()} /
     * {@code getMaximumAge()}).
     */
    public record AgeableState(int age, int maxAge) {}

    // --- Placed-block reward eligibility (legacy UserBlockTracker) ----------

    /**
     * Record that a player hand-placed the block at this position, so gathering skills give it no
     * rewards (legacy {@code BlockUtils#setUnnaturalBlock} → {@code UserBlockTracker#setIneligible}).
     * Its caller is {@code BlockPlaceMixin} (vanilla's {@code BlockItem#place} being the only
     * hand-placement seam), so grown / fallen / world-gen blocks are never marked by <em>this</em>
     * path — see {@link #markUnnatural} for the manufactured-block sources that are.
     *
     * <p>A vanilla {@link BlockState} carries no location (Bukkit's {@code Block} did), so these take a
     * live {@link World} + {@link BlockPos} and pack them into the MC-free
     * {@link PlacedBlockTracker}'s two keys (the world's registry key + {@code BlockPos#asLong()}).
     */
    public static void markPlaced(@NotNull World world, @NotNull BlockPos pos) {
        markUnnatural(world, pos);
    }

    /**
     * Record that the block at this position did not get there naturally, so gathering skills give it
     * no rewards (legacy {@code BlockUtils#setUnnaturalBlock}). The general form of
     * {@link #markPlaced}: hand placement is one source, but a block a player <em>manufactured</em>
     * without touching it is the same exploit and the same flag.
     *
     * <p>Callers other than the block-place seam gate themselves on their own {@code ExploitFix} key
     * before calling (lava formation, snow golems, pistons) — this method enforces only the tracker's
     * master switch.
     */
    public static void markUnnatural(@NotNull World world, @NotNull BlockPos pos) {
        if (!isPlacedBlockTrackingEnabled()) {
            // Don't just refuse to *read* the flags when the gate is off -- refuse to write them.
            // Otherwise every placed block still costs memory and still lands in placed_blocks.dat,
            // and switching the gate back on resurrects a session's worth of stale flags.
            return;
        }
        McMMOMod.getPlacedBlockTracker().setIneligible(worldKey(world), pos.asLong());
    }

    /**
     * Deny gathering rewards for a block that a lava/water interaction just manufactured — the
     * cobblestone, stone, obsidian and basalt "generators" (legacy {@code BlockListener#onBlockFormEvent},
     * {@code ExploitFix.LavaStoneAndCobbleFarming}).
     *
     * <p><b>The §A/K9 tracker cannot reach this on its own.</b> Its only writer is the hand-placement
     * seam, on the reasoning that a block nobody placed needs no flag. A generated block <em>is</em>
     * a block nobody placed, and it is manufactured on demand, for free, forever: at the shipped
     * prices a basalt generator pays 40 Mining XP per block and a stone generator 15, both fully
     * automatable. Conservative tracking is right about grown and world-gen blocks and wrong about
     * these, which is why legacy hooks them explicitly.
     *
     * <p>Two conditions, both legacy's:
     * <ul>
     *   <li><b>Obsidian is exempt.</b> Making it consumes the lava source, so the loop cannot repeat
     *       without hauling another bucket — it is not a generator, it is a lava-to-obsidian trade.</li>
     *   <li><b>The block must actually pay Mining XP.</b> Plain cobblestone has no entry in the
     *       shipped table, so the classic cobble generator was never worth anything anyway; flagging
     *       it would only grow the tracker for nothing.</li>
     * </ul>
     *
     * @param formed the block that has just appeared at {@code pos}
     */
    public static void markLavaFormed(@NotNull World world, @NotNull BlockPos pos,
            @NotNull Block formed) {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (config == null || !config.preventStoneLavaFarming()) {
            return;
        }
        if (formed == Blocks.OBSIDIAN) {
            return;
        }
        if (!givesSkillXp(formed, PrimarySkillType.MINING)) {
            return;
        }
        markUnnatural(world, pos);
    }

    /**
     * Deny Excavation rewards for a snow layer a snow golem just laid down
     * ({@code ExploitFix.SnowGolemExcavation}, legacy {@code BlockListener#onEntityBlockFormEvent}).
     *
     * <p>A snow golem walking in a pen lays snow indefinitely, for free, with nobody at the keyboard;
     * the shipped table pays 20 Excavation XP for a layer and 40 for a block. Like the lava
     * generators this is invisible to the §A/K9 tracker, because the golem is not a player and the
     * snow was never placed by hand.
     *
     * <p>Gated on the snow actually being worth Excavation XP, so a resource pack or config that
     * zeroes it stops the tracker growing for nothing.
     *
     * @param formed the block the golem just created — always {@code snow} in vanilla, passed in so
     *               the caller reads the world rather than this method assuming it
     */
    public static void markSnowGolemFormed(@NotNull World world, @NotNull BlockPos pos,
            @NotNull Block formed) {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (config == null || !config.isSnowExploitPrevented()) {
            return;
        }
        if (!givesSkillXp(formed, PrimarySkillType.EXCAVATION)) {
            return;
        }
        markUnnatural(world, pos);
    }

    /**
     * Carry placed-block flags along with the blocks a piston just pushed or pulled
     * ({@code ExploitFix.PistonCheating}, legacy {@code BlockListener#onBlockPistonExtend}/
     * {@code onBlockPistonRetract}).
     *
     * <p>Without this the flags stay at the old coordinates while the blocks walk away from them, so
     * <em>place → push → mine</em> launders a hand-placed block back into a rewarding one. Legacy
     * closes it by marking every destination unnatural.
     *
     * <p><b>This port moves the flag instead, which is strictly narrower.</b> Legacy's blanket mark
     * is a consequence of its over-marking design; applied to this tracker it would introduce the
     * false positive the tracker was built to avoid — pushing a <em>natural</em> stone wall one block
     * sideways would make it worthless forever. A piston moves blocks, it does not create them, so
     * the honest update is to move what we know.
     *
     * <p>The three passes are not decoration. Sources and destinations overlap whenever a piston
     * pushes a column (the destination of one block is the source of the next), so reading, clearing
     * and re-setting have to happen as separate phases or a flag is cleared after it was just
     * written and the middle of every pushed column silently loses its flag.
     *
     * @param moved    the positions the blocks occupied <em>before</em> the push
     * @param broken   positions destroyed by the push (a torch in the way) — those blocks are gone,
     *                 so their flags are simply dropped
     * @param motion   the direction the blocks travelled
     */
    public static void movePlacedFlags(@NotNull World world, @NotNull List<BlockPos> moved,
            @NotNull List<BlockPos> broken, @NotNull Direction motion) {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        if (config == null || !config.isPistonCheatingPrevented()
                || !isPlacedBlockTrackingEnabled()) {
            return;
        }
        final PlacedBlockTracker tracker = McMMOMod.getPlacedBlockTracker();
        final String worldKey = worldKey(world);

        // 1. Read every source flag before anything is written.
        final List<BlockPos> destinations = new ArrayList<>();
        for (BlockPos pos : moved) {
            if (tracker.isIneligible(worldKey, pos.asLong())) {
                destinations.add(pos.offset(motion));
            }
        }
        // 2. Vacate every source (and every broken block — that one is simply gone).
        for (BlockPos pos : moved) {
            tracker.setEligible(worldKey, pos.asLong());
        }
        for (BlockPos pos : broken) {
            tracker.setEligible(worldKey, pos.asLong());
        }
        // 3. Only now write the destinations.
        for (BlockPos pos : destinations) {
            tracker.setIneligible(worldKey, pos.asLong());
        }
    }

    /**
     * Clear the placed-block flag at this position — the block there is gone (broken / blasted /
     * felled), so the location is natural again (legacy {@code setNaturalBlock} →
     * {@code UserBlockTracker#setEligible}). Idempotent for a position that was never placed, and it
     * bounds the tracker's memory to still-standing placed blocks.
     */
    public static void markNatural(@NotNull World world, @NotNull BlockPos pos) {
        McMMOMod.getPlacedBlockTracker().setEligible(worldKey(world), pos.asLong());
    }

    /**
     * Whether the block at this position must give no gathering rewards because a player placed it
     * (legacy's {@code !mcMMO.getUserBlockTracker().isIneligible(block)} guard on every gathering
     * branch). Any block never hand-placed reads as eligible (the default).
     */
    public static boolean isRewardIneligible(@NotNull World world, @NotNull BlockPos pos) {
        if (!isPlacedBlockTrackingEnabled()) {
            return false;
        }
        return McMMOMod.getPlacedBlockTracker().isIneligible(worldKey(world), pos.asLong());
    }

    /**
     * Whether the placed-block gate is switched on ({@code ExploitFix.PlacedBlocks}, GitHub #9).
     *
     * <p>Gated at both ends of the tracker rather than at its call sites: this class is the only
     * bridge to it, and the alternative — a check in each of the four consumers (block break, blast
     * mining, tree feller, the crop diversion) — is four chances to add a fifth consumer and forget.
     *
     * <p>Defaults to <b>on</b> when no config is loaded yet. A gate whose config has not arrived must
     * fail closed; failing open would pay full rewards for hand-placed blocks during world load,
     * which is the exploit itself.
     */
    private static boolean isPlacedBlockTrackingEnabled() {
        final ExperienceConfig config = McMMOMod.getExperienceConfig();
        return config == null || config.isPlacedBlockTrackingEnabled();
    }

    /** The world's registry key, stringified — the {@link PlacedBlockTracker}'s per-world key. */
    private static @NotNull String worldKey(@NotNull World world) {
        return world.getRegistryKey().getValue().toString();
    }
}
