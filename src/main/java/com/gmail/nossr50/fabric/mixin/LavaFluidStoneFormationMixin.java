package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.util.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.LavaFluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The other half of the lava-generator gate: <b>stone</b>, which does not come from
 * {@code FluidBlock#receiveNeighborFluids} at all.
 *
 * <p>Lava flowing <em>downwards</em> into water turns into stone, and that branch lives in
 * {@code LavaFluid#flow} (bytecode-verified): {@code direction == DOWN} and the block below holding
 * water makes it {@code setBlockState(pos, Blocks.STONE.getDefaultState(), 3)} and return without
 * calling {@code super.flow}. A stone generator is the cheapest of all of these to build and pays 15
 * Mining XP a block, so missing it would leave the gate half-open — the sibling mixin
 * ({@link FluidBlockFormationMixin}) would never see it.
 *
 * <p>Injected at the {@code setBlockState} call rather than at {@code RETURN}, because both branches
 * of {@code flow} return {@code void} and are otherwise indistinguishable from the outside. There is
 * exactly one such call in the method, so {@code allow = 1} is correct here (unlike the sibling, whose
 * target has three exits) and pins the assumption: if Mojang adds a second {@code setBlockState} to
 * this method, the mixin fails loudly instead of silently marking the wrong block.
 */
@Mixin(LavaFluid.class)
public abstract class LavaFluidStoneFormationMixin {

    @Inject(
            method = "flow(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Direction;"
                    + "Lnet/minecraft/fluid/FluidState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/WorldAccess;setBlockState"
                            + "(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"),
            allow = 1)
    private void mcmmo$onLavaFormedStone(WorldAccess world, BlockPos pos, BlockState state,
            Direction direction, FluidState fluidState, CallbackInfo ci) {
        if (world instanceof ServerWorld serverWorld) {
            BlockUtils.markLavaFormed(serverWorld, pos, Blocks.STONE);
        }
    }
}
