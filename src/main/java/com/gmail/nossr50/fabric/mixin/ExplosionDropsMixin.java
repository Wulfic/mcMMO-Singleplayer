package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.BlastMiningListener;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Blast Mining ore-yield hook: replaces the drops of an mcMMO-detonated blast with mcMMO's own.
 * Stands in for legacy's {@code EntityExplodeEvent} handler
 * ({@code EntityListener#onEnitityExplode}), which vanilla has no event for.
 *
 * <p>Two injections into {@code ExplosionImpl#destroyBlocks}, together reproducing what the Bukkit
 * handler did:
 * <ol>
 *   <li>at <b>HEAD</b> — the analogue of the event firing: the doomed block list is known but the
 *       blocks are still standing, so {@link BlastMiningListener#processBlastDrops} can read their
 *       states, spawn mcMMO's payout and award the XP;</li>
 *   <li>on the <b>drop collector</b> — the analogue of {@code event.setYield(0F)}: vanilla gathers
 *       each block's loot through the {@code BiConsumer} passed to {@code BlockState#onExploded},
 *       so swapping in a no-op collector suppresses the vanilla drops that mcMMO has just replaced,
 *       while leaving the rest of the explosion (block removal, block entities, chain-detonating
 *       neighbouring TNT) completely untouched.</li>
 * </ol>
 *
 * <p>Both are no-ops for any explosion mcMMO didn't cause — a creeper, a bed, a hand-lit TNT — which
 * keeps their vanilla drops. The HEAD injection decides that once and stashes it, rather than
 * re-resolving the detonator for every block destroyed; an {@code ExplosionImpl} is built per blast,
 * so the flag can't leak between explosions.
 */
@Mixin(ExplosionImpl.class)
public abstract class ExplosionDropsMixin {

    /** Whether mcMMO has already paid out this blast's drops, so vanilla's must be suppressed. */
    @Unique
    private boolean mcmmo$blastMiningHandled;

    @Inject(method = "destroyBlocks", at = @At("HEAD"))
    private void mcmmo$processBlastMiningDrops(List<BlockPos> blocks, CallbackInfo ci) {
        mcmmo$blastMiningHandled =
                BlastMiningListener.processBlastDrops((Explosion) (Object) this, blocks);
    }

    @ModifyArg(
            method = "destroyBlocks",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/BlockState;onExploded("
                            + "Lnet/minecraft/server/world/ServerWorld;"
                            + "Lnet/minecraft/util/math/BlockPos;"
                            + "Lnet/minecraft/world/explosion/Explosion;"
                            + "Ljava/util/function/BiConsumer;)V"),
            index = 3)
    private BiConsumer<ItemStack, BlockPos> mcmmo$suppressVanillaDrops(
            BiConsumer<ItemStack, BlockPos> dropCollector) {
        return mcmmo$blastMiningHandled ? (stack, pos) -> { } : dropCollector;
    }
}
