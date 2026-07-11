package com.gmail.nossr50.util;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.NotNull;

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
 * <p><b>Deliberately NOT ported here</b> (each needs an adapter or config mcMMO doesn't have yet; PORT
 * breadcrumbs for when the consuming body lands): the metadata mutators
 * ({@code markDropsAsBonus}/{@code setUnnaturalBlock}/{@code cleanupBlockMetadata} — need the
 * block-tracker + Bukkit-metadata adapters), {@code checkDoubleDrops} (RNG + {@code McMMOPlayer}),
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
}
