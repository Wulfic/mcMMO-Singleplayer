package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.platform.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Denies gathering rewards for cobblestone, obsidian and basalt manufactured by a lava/water
 * interaction — mcMMO's replacement for Bukkit's {@code BlockFormEvent}
 * ({@code ExploitFix.LavaStoneAndCobbleFarming}, legacy {@code BlockListener#onBlockFormEvent}).
 *
 * <p><b>Why the K9 tracker does not already cover this.</b> Its only writer is the hand-placement
 * seam, on the argument that a block nobody placed needs no flag. A generated block is exactly that
 * — nobody placed it — and it is manufactured for free, on demand, forever. At the shipped Mining
 * prices a basalt generator is 40 XP per block and fully automatable.
 *
 * <p><b>The seam</b> ({@code javap -c -p net.minecraft.block.FluidBlock}):
 * {@code receiveNeighborFluids(World, BlockPos, BlockState)} is the single funnel for all three
 * formations, and each one writes to {@code pos} — the lava block's own position, the second
 * argument — via {@code World#setBlockState}. Its boolean return is the unambiguous marker:
 * <b>{@code false} means a block formed</b> (both formation branches end {@code iconst_0 ireturn}),
 * {@code true} means the lava was left alone.
 *
 * <p>⚠️ <b>No {@code allow} here.</b> The method has <b>three</b> return instructions, so {@code RETURN}
 * legitimately binds three times; an {@code allow = 1} would fail to apply and take the gate with it
 * (the trap that bit the Husbandry build). {@code require = 1} — the default — is the guard that
 * belongs on a method with more than one exit.
 *
 * @see BlockUtils#markLavaFormed
 */
@Mixin(FluidBlock.class)
public abstract class FluidBlockFormationMixin {

    @Inject(
            method = "receiveNeighborFluids(Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z", allow = 3,
            at = @At("RETURN"))
    private void mcmmo$onFluidFormedBlock(World world, BlockPos pos, BlockState state,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return; // true = nothing formed; the lava is still lava.
        }
        if (!(world instanceof ServerWorld)) {
            return; // the client world runs this too; the tracker is server-side session state.
        }
        // Read the block back rather than re-deriving which branch ran: the state at pos is now
        // whatever vanilla decided, so this stays correct if Mojang adds a fourth formation.
        BlockUtils.markLavaFormed(world, pos, world.getBlockState(pos).getBlock());
    }
}
