package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.skills.BlockBreakXp;
import com.gmail.nossr50.util.player.UserManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Awards gathering-skill XP when a player breaks a block (CONVERSION_TODO Phase 3). Replaces the XP
 * slice of the legacy {@code BlockListener#onBlockBreak}; the deferred bonus-drop / super-ability
 * side effects (double drops, Tree Feller, Giga Drill Breaker, …) still await the item-spawn adapter
 * and land on top of this later.
 *
 * <p>Uses Fabric's {@link PlayerBlockBreakEvents#AFTER}, which fires only once the break actually
 * succeeded (server side), so we never award XP for a cancelled or client-predicted break. The
 * creative-mode / level-cap guards live in the shared XP pipeline
 * ({@link McMMOPlayer#beginXpGain}), so they are not re-checked here.
 */
public final class BlockBreakListener {

    private BlockBreakListener() {
    }

    /** Register the block-break hook. Called once at mod load from {@code McMMOMod#onInitialize}. */
    public static void register() {
        PlayerBlockBreakEvents.AFTER.register(
                (world, player, pos, state, blockEntity) -> onBlockBroken(player, state));
    }

    private static void onBlockBroken(PlayerEntity player, BlockState state) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return; // client-side prediction / non-server player: ignore.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return; // data not loaded (e.g. mid-join).
        }

        final String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        final BlockBreakXp.Reward reward = BlockBreakXp.resolve(blockId);
        if (reward == null) {
            return;
        }
        mmoPlayer.beginXpGain(reward.skill(), reward.xp(), XPGainReason.PVE, XPGainSource.SELF);
    }
}
