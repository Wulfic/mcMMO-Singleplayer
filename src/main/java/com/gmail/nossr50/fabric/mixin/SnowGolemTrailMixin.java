package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.util.BlockUtils;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Blocks;
import net.minecraft.entity.passive.SnowGolemEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Denies Excavation rewards for the snow a snow golem lays behind it — mcMMO's replacement for
 * Bukkit's {@code EntityBlockFormEvent} ({@code ExploitFix.SnowGolemExcavation}, legacy
 * {@code BlockListener#onEntityBlockFormEvent}).
 *
 * <p>The farm is a golem in a pen over an auto-breaker: it lays snow forever, at no cost, with
 * nobody at the keyboard, and the shipped table pays 20 Excavation XP a layer. The §A/K9 tracker
 * cannot see it — a golem is not a player and the snow was never hand-placed.
 *
 * <p><b>The seam</b> ({@code javap -c -p net.minecraft.entity.passive.SnowGolemEntity}):
 * {@code tickMovement()} builds the target {@code BlockPos} from its own floored coordinates and,
 * if the spot is air and the state can be placed there, calls
 * {@code World#setBlockState(BlockPos, BlockState)} exactly once. That single call is the injection
 * point, so {@code allow = 1} pins it.
 *
 * <p>The position is a <b>local</b>, not a parameter — {@code tickMovement()} takes none — so it is
 * captured with MixinExtras' {@link Local}. There is exactly one {@code BlockPos} local in the
 * method (slot 7, bytecode-verified), so the implicit by-type match is unambiguous.
 */
@Mixin(SnowGolemEntity.class)
public abstract class SnowGolemTrailMixin {

    @Inject(
            method = "tickMovement()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;setBlockState"
                            + "(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Z"),
            allow = 1)
    private void mcmmo$onSnowGolemLaidSnow(CallbackInfo ci, @Local BlockPos pos) {
        final World world = ((SnowGolemEntity) (Object) this).getEntityWorld();
        if (world instanceof ServerWorld) {
            // Injected *before* the call lands, so read the block vanilla is about to place rather
            // than the air that is still there.
            BlockUtils.markSnowGolemFormed(world, pos, Blocks.SNOW);
        }
    }
}
