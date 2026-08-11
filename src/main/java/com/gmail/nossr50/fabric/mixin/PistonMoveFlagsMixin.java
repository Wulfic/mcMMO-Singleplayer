package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.platform.BlockUtils;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Carries placed-block flags along with the blocks a piston moves — mcMMO's replacement for Bukkit's
 * {@code BlockPistonExtendEvent}/{@code BlockPistonRetractEvent} ({@code ExploitFix.PistonCheating},
 * legacy {@code BlockListener#onBlockPistonExtend}/{@code onBlockPistonRetract}).
 *
 * <p>The flags are keyed by position, so without this they stay behind while the blocks walk away:
 * <em>place → push → mine</em> launders a hand-placed block into a rewarding one, which is the loop
 * the tracker exists to stop.
 *
 * <p><b>The seam</b> ({@code javap -c -p net.minecraft.block.PistonBlock}): the private
 * {@code move(World, BlockPos, Direction, boolean)} is where both directions bottom out. It builds a
 * {@link PistonHandler} (one such local, slot 6, stored before any return) and asks it for
 * {@code getMovedBlocks()} / {@code getBrokenBlocks()} / {@code getMotionDirection()} — the exact
 * three things the flag update needs, already computed, so nothing has to be re-derived from block
 * states that have by then already changed.
 *
 * <p>⚠️ <b>No {@code allow} here</b>: {@code move} has several returns, so {@code RETURN} binds more
 * than once by design and pinning it to one would fail to apply. The {@code false} return (the push
 * was refused, nothing moved) is filtered on the return value instead.
 */
@Mixin(PistonBlock.class)
public abstract class PistonMoveFlagsMixin {

    @Inject(
            method = "move(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/util/math/Direction;Z)Z",
            at = @At("RETURN"))
    private void mcmmo$onPistonMoved(World world, BlockPos pos, Direction dir, boolean retract,
            CallbackInfoReturnable<Boolean> cir, @Local PistonHandler handler) {
        if (!cir.getReturnValueZ()) {
            return; // the push was refused; nothing moved.
        }
        if (!(world instanceof ServerWorld)) {
            return;
        }
        BlockUtils.movePlacedFlags(world, handler.getMovedBlocks(), handler.getBrokenBlocks(),
                handler.getMotionDirection());
    }
}
