package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.platform.BlockDrops;
import com.gmail.nossr50.skills.BlockBreakXp;
import com.gmail.nossr50.skills.mining.MiningManager;
import com.gmail.nossr50.util.player.UserManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Drives the gathering-skill block-break hooks (CONVERSION_TODO Phase 3): gathering XP for every
 * skill, plus Mining's double/triple bonus drops. Replaces the XP + double-drop slice of the legacy
 * {@code BlockListener#onBlockBreak} / {@code MiningManager#miningBlockCheck}. Remaining side effects
 * (Woodcutting Harvest Lumber, Excavation treasure, super-ability tool damage) land as their
 * item-spawn/scheduler seams follow this same pattern.
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
        // Bonus drops never fire in creative (no vanilla loot spawns there to complement).
        if (!serverPlayer.isCreative()) {
            awardMiningBonusDrops(mmoPlayer, serverWorld, pos, state, blockEntity, serverPlayer,
                    blockId);
        }
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
}
