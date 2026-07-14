package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.treasure.ItemSpec;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.BlockDrops;
import com.gmail.nossr50.platform.ItemSpecBuilder;
import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.skills.BlockBreakXp;
import com.gmail.nossr50.skills.excavation.ExcavationManager;
import com.gmail.nossr50.skills.herbalism.HerbalismManager;
import com.gmail.nossr50.skills.mining.MiningManager;
import com.gmail.nossr50.skills.woodcutting.TreeFellerProcessor;
import com.gmail.nossr50.skills.woodcutting.WoodcuttingManager;
import com.gmail.nossr50.util.BlockUtils;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.skills.SkillUtils;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Drives the gathering-skill block-break hooks (CONVERSION_TODO Phase 3): gathering XP for every
 * skill, plus the block-break side effects — Mining/Woodcutting/Herbalism bonus (double/triple)
 * drops, Excavation treasure, and the Tree Feller / Giga Drill Breaker super abilities. Replaces the XP + drop slice of the
 * legacy {@code BlockListener#onBlockBreak} / {@code MiningManager#miningBlockCheck} /
 * {@code HerbalismManager#checkDoubleDropsOnBrokenPlants}. Remaining side effects (Herbalism Green
 * Thumb replant, super-ability tool damage) land as their inventory/scheduler seams follow this
 * same pattern.
 *
 * <p>Uses Fabric's {@link PlayerBlockBreakEvents#AFTER}, which fires only once the break actually
 * succeeded (server side), so we never award XP or drops for a cancelled or client-predicted break.
 * The creative-mode / level-cap guards for XP live in the shared pipeline
 * ({@link McMMOPlayer#beginXpGain}); bonus drops are guarded against creative separately here (a
 * creative break spawns no vanilla loot, so bonus copies would be a duplication bug).
 */
public final class BlockBreakListener {

    private BlockBreakListener() {
    }

    /** Register the block-break hook. Called once at mod load from {@code McMMOMod#onInitialize}. */
    public static void register() {
        PlayerBlockBreakEvents.AFTER.register(BlockBreakListener::onBlockBroken);
    }

    private static void onBlockBroken(World world, PlayerEntity player, BlockPos pos,
            BlockState state, @Nullable BlockEntity blockEntity) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || !(world instanceof ServerWorld serverWorld)) {
            return; // client-side prediction / non-server context: ignore.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return; // data not loaded (e.g. mid-join).
        }

        final String blockId = Registries.BLOCK.getId(state.getBlock()).toString();

        awardBlockXp(mmoPlayer, blockId);
        // Bonus drops never fire in creative (no vanilla loot spawns there to complement). Each
        // skill's bonus-drop path self-gates on its own config section (a log is never a Mining
        // bonus block and an ore is never a Woodcutting one), so running both per break is safe.
        if (!serverPlayer.isCreative()) {
            awardMiningBonusDrops(mmoPlayer, serverWorld, pos, state, blockEntity, serverPlayer,
                    blockId);
            awardWoodcuttingBonusDrops(mmoPlayer, serverWorld, pos, state, blockEntity, serverPlayer,
                    blockId);
            awardHerbalismBonusDrops(mmoPlayer, serverWorld, pos, state, blockEntity, serverPlayer,
                    blockId);
            awardExcavationTreasures(mmoPlayer, serverWorld, pos, blockId);
            // Giga Drill Breaker re-processes the block twice more (3× drops/XP) and wears the
            // shovel. It stays inside the creative gate with the base treasure roll: creative breaks
            // spawn no vanilla loot, so bonus treasure copies there would be a duplication bug.
            maybeProcessGigaDrillBreaker(mmoPlayer, serverWorld, pos, state, serverPlayer, blockId);
        }
        // Tree Feller self-gates on the super-ability mode + an axe, so it can run in creative too
        // (matching legacy, which never creative-gated it); the starting block above is untouched —
        // this fells the rest of the tree around it.
        maybeProcessTreeFeller(mmoPlayer, serverWorld, pos, state, serverPlayer);
    }

    /**
     * Fell the surrounding tree when the player broke a log with Tree Feller active while holding an
     * axe (legacy {@code BlockListener} Woodcutting branch → {@code canUseTreeFeller} →
     * {@code processTreeFeller}). The heavy lifting — the recursive search, drops and durability —
     * lives in {@link TreeFellerProcessor}; this only reproduces the trigger gate.
     */
    private static void maybeProcessTreeFeller(McMMOPlayer mmoPlayer, ServerWorld world, BlockPos pos,
            BlockState state, ServerPlayerEntity breaker) {
        if (!mmoPlayer.getAbilityMode(SuperAbilityType.TREE_FELLER)) {
            return;
        }
        if (!BlockUtils.hasWoodcuttingXP(state) || !ItemUtils.isAxe(breaker.getMainHandStack())) {
            return;
        }
        TreeFellerProcessor.process(world, pos, breaker, mmoPlayer);
    }

    /**
     * Re-process an excavated block twice more and wear down the shovel when Giga Drill Breaker is
     * active (legacy {@code ExcavationManager#gigaDrillBreaker}: two extra {@code excavationBlockCheck}
     * calls + {@code SkillUtils.handleDurabilityChange}). The base excavation XP and one treasure roll
     * already ran ({@link #awardBlockXp} / {@link #awardExcavationTreasures}); these two bonus rounds
     * bring it to the classic 3× excavation drops and XP, then the ability's extra tool damage is
     * applied to the shovel.
     *
     * <p>Gated exactly like legacy {@code BlockListener}'s excavation branch: the super-ability mode is
     * on, the block gives Excavation XP ({@link BlockUtils#affectedByGigaDrillBreaker}), and a shovel is
     * in hand. Called from inside the creative gate, so the bonus treasure spawns match the base path.
     */
    private static void maybeProcessGigaDrillBreaker(McMMOPlayer mmoPlayer, ServerWorld world,
            BlockPos pos, BlockState state, ServerPlayerEntity breaker, String blockId) {
        if (!mmoPlayer.getAbilityMode(SuperAbilityType.GIGA_DRILL_BREAKER)) {
            return;
        }
        final ItemStack tool = breaker.getMainHandStack();
        if (!BlockUtils.affectedByGigaDrillBreaker(state) || !ItemUtils.isShovel(tool)) {
            return;
        }
        // Two bonus excavation checks (the base one already ran) → 3× total drops/XP. Each treasure
        // roll is independent RNG, matching legacy's separate excavationBlockCheck calls.
        for (int i = 0; i < 2; i++) {
            awardBlockXp(mmoPlayer, blockId);
            awardExcavationTreasures(mmoPlayer, world, pos, blockId);
        }
        // Giga Drill Breaker wears the shovel harder than a normal break (extra ability tool damage).
        SkillUtils.handleDurabilityChange(new PlatformItem(tool),
                McMMOMod.getGeneralConfig().getAbilityToolDamage());
    }

    private static void awardBlockXp(McMMOPlayer mmoPlayer, String blockId) {
        final BlockBreakXp.Reward reward = BlockBreakXp.resolve(blockId);
        if (reward != null) {
            mmoPlayer.beginXpGain(reward.skill(), reward.xp(), XPGainReason.PVE, XPGainSource.SELF);
        }
    }

    private static void awardMiningBonusDrops(McMMOPlayer mmoPlayer, ServerWorld world, BlockPos pos,
            BlockState state, @Nullable BlockEntity blockEntity, ServerPlayerEntity breaker,
            String blockId) {
        final MiningManager mining = mmoPlayer.getMiningManager();
        if (mining == null) {
            return;
        }
        final ItemStack tool = breaker.getMainHandStack();
        if (!mining.isBonusDropsEligible(blockId, BlockDrops.hasSilkTouch(world, tool))) {
            return;
        }
        final int rounds = mining.rollBonusDropCount();
        if (rounds > 0) {
            BlockDrops.dropBonusLoot(world, pos, state, blockEntity, breaker, tool, rounds);
        }
    }

    private static void awardWoodcuttingBonusDrops(McMMOPlayer mmoPlayer, ServerWorld world,
            BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
            ServerPlayerEntity breaker, String blockId) {
        final WoodcuttingManager woodcutting = mmoPlayer.getWoodcuttingManager();
        if (woodcutting == null) {
            return;
        }
        // Harvest Lumber / Clean Cuts duplicate the felled log regardless of Silk Touch, so (unlike
        // Mining) there is no enchantment gate here — the config toggle + rank + RNG live in the roll.
        final int rounds = woodcutting.rollHarvestLumberBonusDropCount(blockId);
        if (rounds > 0) {
            BlockDrops.dropBonusLoot(world, pos, state, blockEntity, breaker,
                    breaker.getMainHandStack(), rounds);
        }
    }

    private static void awardHerbalismBonusDrops(McMMOPlayer mmoPlayer, ServerWorld world,
            BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
            ServerPlayerEntity breaker, String blockId) {
        final HerbalismManager herbalism = mmoPlayer.getHerbalismManager();
        if (herbalism == null) {
            return;
        }
        // Herbalism double drops have no Silk Touch gate (plants don't drop via Silk Touch); the
        // config toggle + rank live in the eligibility check, the Green-Terra triple in the roll.
        if (!herbalism.isBonusDropsEligible(blockId)) {
            return;
        }
        final int rounds = herbalism.rollBonusDropCount();
        if (rounds > 0) {
            BlockDrops.dropBonusLoot(world, pos, state, blockEntity, breaker,
                    breaker.getMainHandStack(), rounds);
        }
    }

    private static void awardExcavationTreasures(McMMOPlayer mmoPlayer, ServerWorld world,
            BlockPos pos, String blockId) {
        final ExcavationManager excavation = mmoPlayer.getExcavationManager();
        if (excavation == null) {
            return;
        }
        final ExcavationManager.ExcavationRewards rewards = excavation.rollTreasureRewards(blockId);
        if (rewards.isEmpty()) {
            return;
        }
        // Build each MC-free ItemSpec into a live stack and scatter it at the block (an unknown
        // material is skipped, having already been logged by Materials).
        for (ItemSpec spec : rewards.treasures()) {
            ItemSpecBuilder.build(spec).ifPresent(stack -> Block.dropStack(world, pos, stack));
        }
        if (!rewards.experienceOrbs().isEmpty()) {
            final Vec3d center = Vec3d.ofCenter(pos);
            for (int amount : rewards.experienceOrbs()) {
                if (amount > 0) {
                    ExperienceOrbEntity.spawn(world, center, amount);
                }
            }
        }
        // Treasure XP is a bonus on top of the base block XP already awarded in awardBlockXp.
        if (rewards.treasureXp() > 0) {
            mmoPlayer.beginXpGain(PrimarySkillType.EXCAVATION, rewards.treasureXp(),
                    XPGainReason.PVE, XPGainSource.SELF);
        }
    }
}
