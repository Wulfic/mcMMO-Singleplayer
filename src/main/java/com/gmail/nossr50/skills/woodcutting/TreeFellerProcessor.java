package com.gmail.nossr50.skills.woodcutting;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.BlockDrops;
import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.skills.woodcutting.TreeFellerTraversal.FelledBlock;
import com.gmail.nossr50.skills.woodcutting.TreeFellerTraversal.TreeBlockType;
import com.gmail.nossr50.util.BlockUtils;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.skills.SkillUtils;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

/**
 * The MC-typed half of Tree Feller (legacy {@code WoodcuttingManager#processTreeFeller} /
 * {@code dropTreeFellerLootFromBlocks} / {@code handleDurabilityLoss}). Given the block the player
 * just broke with Tree Feller active, it discovers the rest of the tree via the MC-free
 * {@link TreeFellerTraversal}, drains the axe's durability, then fells every discovered block —
 * spawning tool-aware drops (logs + Harvest Lumber bonuses; leaves only occasionally, or their
 * saplings via Knock on Wood) and the reduced per-log XP. Sibling of {@code AlchemyPotionBrewer}:
 * the numeric decisions live on the MC-free {@link WoodcuttingManager}/{@link TreeFellerTraversal},
 * this class owns the vanilla world/item mutation.
 *
 * <p>The <b>starting</b> block is intentionally not felled here — vanilla already broke and dropped
 * it (this runs from {@code PlayerBlockBreakEvents.AFTER}), matching legacy where the triggering
 * break is handled by the normal path and {@code processTree} only walks the neighbours.
 *
 * <p>PORT (deferred, breadcrumbs):
 * <ul>
 *   <li>the Tree Feller "fizz" activation sound (needs the block-position sound overload);</li>
 *   <li>the random self-damage legacy deals when the axe can't survive the fell — there's no
 *       self-harm adapter yet, so a splinter just aborts the fell (the durability was still spent).</li>
 * </ul>
 */
public final class TreeFellerProcessor {

    private TreeFellerProcessor() {
    }

    /**
     * Fell the tree around a block broken with Tree Feller active.
     *
     * @param world the server world the tree is in
     * @param startPos the block the player broke to trigger the ability (already gone; the search
     *     runs over its neighbours)
     * @param breaker the felling player (loot/durability context)
     * @param mmoPlayer the breaker's mcMMO data
     */
    public static void process(@NotNull ServerWorld world, @NotNull BlockPos startPos,
            @NotNull ServerPlayerEntity breaker, @NotNull McMMOPlayer mmoPlayer) {
        final WoodcuttingManager woodcutting = mmoPlayer.getWoodcuttingManager();
        if (woodcutting == null) {
            return;
        }

        final List<FelledBlock> felled = TreeFellerTraversal.collect(
                startPos.getX(), startPos.getY(), startPos.getZ(),
                woodcutting.getTreeFellerThreshold(),
                (x, y, z) -> classify(world, x, y, z));
        if (felled.isEmpty()) {
            return;
        }

        final ItemStack axe = breaker.getMainHandStack();
        if (!canSustainDurabilityLoss(axe, felled)) {
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.SUBSKILL_MESSAGE_FAILED,
                    "Woodcutting.Skills.TreeFeller.Splinter");
            // PORT: legacy also deals random self-damage on a splinter; no self-harm adapter yet, so
            // the fell just aborts (the durability was already spent, matching legacy ordering).
            return;
        }

        dropFelledBlocks(world, breaker, mmoPlayer, woodcutting, axe, felled);
    }

    /** Classify the live block at a coordinate for the traversal (LOG / LEAF / OTHER). */
    private static @NotNull TreeBlockType classify(@NotNull ServerWorld world, int x, int y, int z) {
        final BlockState state = world.getBlockState(new BlockPos(x, y, z));
        if (BlockUtils.hasWoodcuttingXP(state)) {
            return TreeBlockType.LOG;
        }
        if (BlockUtils.isNonWoodPartOfTree(state)) {
            return TreeBlockType.LEAF;
        }
        return TreeBlockType.OTHER;
    }

    /**
     * Applies the accumulated durability cost to the axe and reports whether the tool survives.
     * Mirrors legacy {@code handleDurabilityLoss}: only logs cost durability, unbreakable/
     * non-damageable tools are free, and a tool that would break can't sustain the fell. The
     * durability is spent whether or not the tool survives (legacy applies the change before the check).
     */
    private static boolean canSustainDurabilityLoss(@NotNull ItemStack axe,
            @NotNull List<FelledBlock> felled) {
        final PlatformItem tool = new PlatformItem(axe);
        if (tool.isUnbreakable() || !tool.isDamageable()) {
            return true;
        }

        int woodCount = 0;
        for (FelledBlock felledBlock : felled) {
            if (felledBlock.type() == TreeBlockType.LOG) {
                woodCount++;
            }
        }

        final int durabilityLoss = woodCount * McMMOMod.getGeneralConfig().getAbilityToolDamage();
        SkillUtils.handleDurabilityChange(tool, durabilityLoss);
        return tool.getDurability() < tool.getMaxDurability();
    }

    /**
     * Fells every discovered block: logs drop their loot + Harvest Lumber bonuses and give reduced
     * XP; leaves drop rarely (or their saplings, with Knock on Wood) and may pop an XP orb. The
     * summed XP is awarded once at the end (legacy {@code dropTreeFellerLootFromBlocks}).
     */
    private static void dropFelledBlocks(@NotNull ServerWorld world,
            @NotNull ServerPlayerEntity breaker, @NotNull McMMOPlayer mmoPlayer,
            @NotNull WoodcuttingManager woodcutting, @NotNull ItemStack axe,
            @NotNull List<FelledBlock> felled) {
        int xp = 0;
        int processedLogCount = 0;

        for (FelledBlock felledBlock : felled) {
            final BlockPos pos = new BlockPos(felledBlock.x(), felledBlock.y(), felledBlock.z());
            final BlockState state = world.getBlockState(pos);
            final BlockEntity blockEntity = world.getBlockEntity(pos);
            final String blockId = Registries.BLOCK.getId(state.getBlock()).toString();

            if (felledBlock.type() == TreeBlockType.LOG) {
                final int beforeXp = xp;
                xp += WoodcuttingManager.processTreeFellerXPGains(
                        ConfigStringUtils.getMaterialConfigString(blockId), processedLogCount);

                dropStacks(world, pos, state, blockEntity, breaker, axe);

                final int bonusRounds = woodcutting.rollHarvestLumberBonusDropCount(blockId);
                if (bonusRounds > 0) {
                    BlockDrops.dropBonusLoot(world, pos, state, blockEntity, breaker, axe, bonusRounds);
                }

                world.breakBlock(pos, false);

                // Only advance the reduction counter when a log actually paid out XP (legacy parity).
                if (beforeXp != xp) {
                    processedLogCount++;
                }
            } else {
                processFelledLeaf(world, pos, state, blockEntity, breaker, mmoPlayer, axe);
                world.breakBlock(pos, false);
            }
        }

        if (xp > 0) {
            woodcutting.applyXpGain((float) xp, XPGainReason.PVE, XPGainSource.SELF);
        }
    }

    /**
     * Handles a felled non-wood tree part (leaves/wart/mushroom cap): drop its loot 24% of the time,
     * else salvage only saplings/propagules from it if Knock on Wood is unlocked; then roll the Knock
     * on Wood (rank 2) experience-orb bonus. Mirrors legacy {@code dropTreeFellerLootFromBlocks}'s
     * non-wood branch, including its {@code nextInt(100) > 75} drop chance.
     */
    private static void processFelledLeaf(@NotNull ServerWorld world, @NotNull BlockPos pos,
            @NotNull BlockState state, BlockEntity blockEntity, @NotNull ServerPlayerEntity breaker,
            @NotNull McMMOPlayer mmoPlayer, @NotNull ItemStack axe) {
        if (ThreadLocalRandom.current().nextInt(100) > 75) {
            dropStacks(world, pos, state, blockEntity, breaker, axe);
        } else if (RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.WOODCUTTING_KNOCK_ON_WOOD)) {
            for (ItemStack stack : Block.getDroppedStacks(state, world, pos, blockEntity, breaker, axe)) {
                if (!stack.isEmpty() && isSaplingOrPropagule(stack)) {
                    Block.dropStack(world, pos, stack);
                }
            }
        }

        if (RankUtils.hasUnlockedSubskill(mmoPlayer, SubSkillType.WOODCUTTING_KNOCK_ON_WOOD)
                && RankUtils.hasReachedRank(2, mmoPlayer, SubSkillType.WOODCUTTING_KNOCK_ON_WOOD)
                && McMMOMod.getAdvancedConfig().isKnockOnWoodXPOrbEnabled()
                && ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.WOODCUTTING, mmoPlayer, 10)) {
            final int orbCount = Math.max(1, ThreadLocalRandom.current().nextInt(100));
            ExperienceOrbEntity.spawn(world, Vec3d.ofCenter(pos), orbCount);
        }
    }

    /** Spawn a block's natural, tool-aware loot at its position (legacy {@code spawnItemsFromCollection}). */
    private static void dropStacks(@NotNull ServerWorld world, @NotNull BlockPos pos,
            @NotNull BlockState state, BlockEntity blockEntity, @NotNull ServerPlayerEntity breaker,
            @NotNull ItemStack tool) {
        for (ItemStack stack : Block.getDroppedStacks(state, world, pos, blockEntity, breaker, tool)) {
            if (!stack.isEmpty()) {
                Block.dropStack(world, pos, stack);
            }
        }
    }

    /** Whether a dropped item is a sapling or mangrove propagule (Knock on Wood salvage filter). */
    private static boolean isSaplingOrPropagule(@NotNull ItemStack stack) {
        final String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return path.contains(WoodcuttingManager.SAPLING) || path.contains(WoodcuttingManager.PROPAGULE);
    }
}
