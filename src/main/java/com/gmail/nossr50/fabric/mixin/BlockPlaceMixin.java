package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.util.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The block-place hook: mcMMO's replacement for Bukkit's {@code BlockPlaceEvent}. It marks a
 * hand-placed block ineligible for gathering rewards so a player cannot farm XP by re-mining blocks
 * they placed (legacy {@code BlockListener#onBlockPlace} → {@code BlockUtils#setUnnaturalBlock}).
 *
 * <p>Targets the inner {@code BlockItem#place(ItemPlacementContext, BlockState)} — bytecode-verified
 * to be exactly {@code context.getWorld().setBlockState(context.getBlockPos(), state, 11)}, so its
 * boolean return is an unambiguous "a block was placed here" signal and {@code context.getBlockPos()}
 * is the placement position. That is cleaner than the public {@code place(...)ActionResult}, whose
 * early {@code FAIL} returns would otherwise have to be filtered out of an {@code ActionResult} (and
 * would risk marking a block that was never placed).
 *
 * <p>Injected at {@code RETURN}: only a {@code true} return means the block state actually changed.
 * Gated to {@link ServerWorld}, since in singleplayer the client also runs {@code place} (block-place
 * prediction on the client world) and the tracker is authoritative server-side session state.
 *
 * <p>By construction this is the <em>only</em> writer of the placed-block flags, so grown / fallen /
 * world-gen blocks are never marked — the port needs none of legacy's "reset to natural" hooks to
 * walk back over-marking (see {@link com.gmail.nossr50.util.PlacedBlockTracker}).
 */
@Mixin(BlockItem.class)
public abstract class BlockPlaceMixin {

    @Inject(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;"
                    + "Lnet/minecraft/block/BlockState;)Z",
            at = @At("RETURN"))
    private void mcmmo$onBlockPlaced(ItemPlacementContext context, BlockState state,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return; // setBlockState reported no change: nothing was placed.
        }
        final World world = context.getWorld();
        if (world instanceof ServerWorld serverWorld) {
            BlockUtils.markPlaced(serverWorld, context.getBlockPos());
        }
    }
}
