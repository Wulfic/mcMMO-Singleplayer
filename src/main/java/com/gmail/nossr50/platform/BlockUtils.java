package com.gmail.nossr50.platform;

import com.gmail.nossr50.util.BlockRules;
import com.gmail.nossr50.util.PlacedBlockTracker;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Minecraft-typed bridge to {@link BlockRules} — the singleplayer port of the legacy Bukkit
 * {@code BlockUtils}, built the same way as its sibling {@link ItemUtils}.
 *
 * <p><b>This class holds no decisions.</b> Phase 2 (ruling P2-d) moved every one of them into the
 * MC-free {@link BlockRules}; what is left here is key extraction and nothing else:
 *
 * <ul>
 *   <li>a live {@link Block}/{@link BlockState} → its vanilla registry-id <b>path</b>
 *       ({@code oak_log}), which is what {@link com.gmail.nossr50.util.MaterialMapStore} and (via
 *       {@code ConfigStringUtils}) the {@code experience.yml} tables are keyed on;</li>
 *   <li>a live {@link World} + {@link BlockPos} → the {@link PlacedBlockTracker}'s two keys (the
 *       world's registry key, and {@link BlockPos#asLong()}).</li>
 * </ul>
 *
 * <p>The id-path extraction needs live registries, so these are exercised in {@code BlockUtilsTest}
 * under the {@code fabric-loader-junit} harness ({@code Bootstrap.initialize()} in a
 * {@code @BeforeAll}); the decisions behind them are exercised MC-free in {@code BlockRulesTest}.
 * That split is the point: proving *the bridge connects* needs a real registry, proving *the rules
 * are right* does not.
 *
 * <h2>The three things that genuinely stay MC-typed</h2>
 * <ol>
 *   <li><b>Two block-identity checks</b> — {@code isOf(Blocks.SNOW)} in {@link #affectedByBerserk}
 *       and {@code == Blocks.OBSIDIAN} in {@link #markLavaFormed}. Re-keying them on the id path
 *       would silently broaden them across namespaces; see the {@link BlockRules} class note.</li>
 *   <li><b>Crop maturity</b> ({@link #getAgeableState}/{@link #withAge}) — reads live state
 *       properties, which have no MC-free spelling.</li>
 *   <li><b>The two Hylian block tags</b>, handed to {@link BlockRules#hylianTreasureGroup} as
 *       suppliers so they stay unevaluated unless reached — {@code isIn(TagKey)} throws while tags
 *       are unbound.</li>
 * </ol>
 *
 * <p><b>Deliberately NOT ported</b> (each needs an adapter or config mcMMO doesn't have yet; PORT
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
     * {@code minecraft:oak_log}) — the key {@link BlockRules} is keyed on.
     */
    private static @NotNull String idPath(@NotNull Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }

    // --- Super-ability activation gates -------------------------------------

    /** @see BlockRules#canActivateAbilities */
    public static boolean canActivateAbilities(@NotNull Block block) {
        return BlockRules.canActivateAbilities(idPath(block));
    }

    public static boolean canActivateAbilities(@NotNull BlockState blockState) {
        return canActivateAbilities(blockState.getBlock());
    }

    /** @see BlockRules#canActivateTools */
    public static boolean canActivateTools(@NotNull Block block) {
        return BlockRules.canActivateTools(idPath(block));
    }

    public static boolean canActivateTools(@NotNull BlockState blockState) {
        return canActivateTools(blockState.getBlock());
    }

    /** @see BlockRules#canActivateHerbalism */
    public static boolean canActivateHerbalism(@NotNull Block block) {
        return BlockRules.canActivateHerbalism(idPath(block));
    }

    public static boolean canActivateHerbalism(@NotNull BlockState blockState) {
        return canActivateHerbalism(blockState.getBlock());
    }

    // --- Super-ability affected-block checks --------------------------------

    /** @see BlockRules#affectedBySuperBreaker */
    public static boolean affectedBySuperBreaker(@NotNull Block block) {
        return BlockRules.affectedBySuperBreaker(idPath(block));
    }

    public static boolean affectedBySuperBreaker(@NotNull BlockState blockState) {
        return affectedBySuperBreaker(blockState.getBlock());
    }

    /** @see BlockRules#affectedByGigaDrillBreaker */
    public static boolean affectedByGigaDrillBreaker(@NotNull Block block) {
        return BlockRules.affectedByGigaDrillBreaker(idPath(block));
    }

    public static boolean affectedByGigaDrillBreaker(@NotNull BlockState blockState) {
        return affectedByGigaDrillBreaker(blockState.getBlock());
    }

    /** @see BlockRules#affectedByGreenTerra */
    public static boolean affectedByGreenTerra(@NotNull Block block) {
        return BlockRules.affectedByGreenTerra(idPath(block));
    }

    public static boolean affectedByGreenTerra(@NotNull BlockState blockState) {
        return affectedByGreenTerra(blockState.getBlock());
    }

    /** @see BlockRules#affectedByBlockCracker */
    public static boolean affectedByBlockCracker(@NotNull Block block) {
        return BlockRules.affectedByBlockCracker(idPath(block));
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
     *
     * <p>⚠️ The snow arm is an <b>identity</b> check on the snow <em>layer</em>
     * ({@code minecraft:snow}, not {@code snow_block}) and therefore stays on this side of the
     * boundary — {@code "snow".equals(idPath)} would also match another namespace's block of that
     * name, which is a broadening this refactor has no mandate to make.
     */
    public static boolean affectedByBerserk(@NotNull BlockState blockState) {
        return BlockRules.affectedByBerserkExceptSnowLayer(idPath(blockState.getBlock()))
                || blockState.is(Blocks.SNOW);
    }

    // --- Woodcutting / tree ------------------------------------------------

    /** @see BlockRules#hasWoodcuttingXP */
    public static boolean hasWoodcuttingXP(@NotNull Block block) {
        return BlockRules.hasWoodcuttingXP(idPath(block));
    }

    public static boolean hasWoodcuttingXP(@NotNull BlockState blockState) {
        return hasWoodcuttingXP(blockState.getBlock());
    }

    /** @see BlockRules#isNonWoodPartOfTree */
    public static boolean isNonWoodPartOfTree(@NotNull Block block) {
        return BlockRules.isNonWoodPartOfTree(idPath(block));
    }

    public static boolean isNonWoodPartOfTree(@NotNull BlockState blockState) {
        return isNonWoodPartOfTree(blockState.getBlock());
    }

    /** @see BlockRules#isPartOfTree */
    public static boolean isPartOfTree(@NotNull Block block) {
        return BlockRules.isPartOfTree(idPath(block));
    }

    public static boolean isPartOfTree(@NotNull BlockState blockState) {
        return isPartOfTree(blockState.getBlock());
    }

    // --- Mining / ore ------------------------------------------------------

    /** @see BlockRules#isOre */
    public static boolean isOre(@NotNull Block block) {
        return BlockRules.isOre(idPath(block));
    }

    public static boolean isOre(@NotNull BlockState blockState) {
        return isOre(blockState.getBlock());
    }

    // --- Herbalism block conversions ---------------------------------------

    /** @see BlockRules#canMakeMossy */
    public static boolean canMakeMossy(@NotNull Block block) {
        return BlockRules.canMakeMossy(idPath(block));
    }

    public static boolean canMakeMossy(@NotNull BlockState blockState) {
        return canMakeMossy(blockState.getBlock());
    }

    /** @see BlockRules#canMakeShroomy */
    public static boolean canMakeShroomy(@NotNull Block block) {
        return BlockRules.canMakeShroomy(idPath(block));
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
     * <p>⚠️ The two {@code isIn} reads are passed as method references, not as evaluated booleans:
     * {@code isIn(TagKey)} <b>throws</b> while tags are unbound, and the hardcoded flower/bush arms
     * return before either tag is consulted. See {@link BlockRules#hylianTreasureGroup}.
     *
     * @param blockState the broken block's state
     * @return the group name, or {@code null} if the block drops no Hylian treasure
     */
    public static @Nullable String getHylianTreasureGroup(@NotNull BlockState blockState) {
        return BlockRules.hylianTreasureGroup(idPath(blockState.getBlock()),
                () -> blockState.isIn(BlockTags.SAPLINGS),
                () -> blockState.is(BlockTags.FLOWER_POTS));
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
        final IntegerProperty ageProperty = ageProperty(blockState);
        if (ageProperty == null) {
            return null;
        }
        final int maxAge = ageProperty.getPossibleValues().stream()
                .mapToInt(Integer::intValue).max().orElse(0);
        return new AgeableState(blockState.getValue(ageProperty), maxAge);
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
        final IntegerProperty ageProperty = ageProperty(blockState);
        if (ageProperty == null) {
            return blockState;
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int value : ageProperty.getPossibleValues()) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return blockState.setValue(ageProperty, Math.max(min, Math.min(age, max)));
    }

    /**
     * The {@link IntProperty} named {@code "age"} on a block, or {@code null} if it has none. Vanilla
     * has no single {@code Ageable} interface, so both {@link #getAgeableState} and {@link #withAge}
     * locate crop maturity through this one scan; every crop/plant legacy treated as ageable exposes
     * exactly one such property, and the {@code "age"} filter skips {@code stage}/{@code layers}/etc.
     */
    private static @Nullable IntegerProperty ageProperty(@NotNull BlockState blockState) {
        for (Property<?> property : blockState.getProperties()) {
            if (property instanceof IntegerProperty ageProperty && "age".equals(ageProperty.getName())) {
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
     * live {@link World} + {@link BlockPos} and pack them into the {@link PlacedBlockTracker}'s two
     * keys.
     */
    public static void markPlaced(@NotNull Level world, @NotNull BlockPos pos) {
        markUnnatural(world, pos);
    }

    /** @see BlockRules#markUnnatural */
    public static void markUnnatural(@NotNull Level world, @NotNull BlockPos pos) {
        BlockRules.markUnnatural(worldKey(world), pos.asLong());
    }

    /**
     * Deny gathering rewards for a block that a lava/water interaction just manufactured
     * ({@code ExploitFix.LavaStoneAndCobbleFarming}) — see {@link BlockRules#markLavaFormed} for why
     * the §A/K9 tracker cannot see these on its own.
     *
     * <p>⚠️ <b>Obsidian's exemption lives here, not in the rules layer.</b> Making it consumes the
     * lava source, so the loop cannot repeat without hauling another bucket — it is a trade, not a
     * generator. Legacy exempts it by identity and so does this: {@code "obsidian".equals(idPath)}
     * would also exempt another namespace's block of that name.
     *
     * @param formed the block that has just appeared at {@code pos}
     */
    public static void markLavaFormed(@NotNull Level world, @NotNull BlockPos pos,
            @NotNull Block formed) {
        if (formed == Blocks.OBSIDIAN) {
            return;
        }
        BlockRules.markLavaFormed(worldKey(world), pos.asLong(), idPath(formed));
    }

    /** @see BlockRules#markSnowGolemFormed */
    public static void markSnowGolemFormed(@NotNull Level world, @NotNull BlockPos pos,
            @NotNull Block formed) {
        BlockRules.markSnowGolemFormed(worldKey(world), pos.asLong(), idPath(formed));
    }

    /**
     * Carry placed-block flags along with the blocks a piston just pushed or pulled
     * ({@code ExploitFix.PistonCheating}). The three-phase flag shuffle — and the column-overlap
     * hazard it exists for — is {@link BlockRules#movePlacedFlags}; this method owns only the
     * direction arithmetic, which is the one part that needs a Minecraft type.
     *
     * @param moved    the positions the blocks occupied <em>before</em> the push
     * @param broken   positions destroyed by the push (a torch in the way) — those blocks are gone,
     *                 so their flags are simply dropped
     * @param motion   the direction the blocks travelled
     */
    public static void movePlacedFlags(@NotNull Level world, @NotNull List<BlockPos> moved,
            @NotNull List<BlockPos> broken, @NotNull Direction motion) {
        final long[] movedFrom = new long[moved.size()];
        final long[] movedTo = new long[moved.size()];
        for (int i = 0; i < moved.size(); i++) {
            final BlockPos pos = moved.get(i);
            movedFrom[i] = pos.asLong();
            movedTo[i] = pos.offset(motion).asLong();
        }
        final long[] brokenKeys = new long[broken.size()];
        for (int i = 0; i < broken.size(); i++) {
            brokenKeys[i] = broken.get(i).asLong();
        }
        BlockRules.movePlacedFlags(worldKey(world), movedFrom, movedTo, brokenKeys);
    }

    /** @see BlockRules#markNatural */
    public static void markNatural(@NotNull Level world, @NotNull BlockPos pos) {
        BlockRules.markNatural(worldKey(world), pos.asLong());
    }

    /** @see BlockRules#isRewardIneligible */
    public static boolean isRewardIneligible(@NotNull Level world, @NotNull BlockPos pos) {
        return BlockRules.isRewardIneligible(worldKey(world), pos.asLong());
    }

    /** The world's registry key, stringified — the {@link PlacedBlockTracker}'s per-world key. */
    private static @NotNull String worldKey(@NotNull Level world) {
        return world.dimension().identifier().toString();
    }
}
