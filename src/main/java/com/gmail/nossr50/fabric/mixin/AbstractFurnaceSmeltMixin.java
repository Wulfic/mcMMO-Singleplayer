package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.SmeltingListener;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The K7 Smelting hooks. Vanilla fires no furnace events at all, so all three of mcMMO's furnace
 * behaviours are injected into the static {@code AbstractFurnaceBlockEntity#tick}, and all three are
 * routed through {@link SmeltingListener}, which resolves the furnace's owner:
 *
 * <ul>
 *   <li><b>Smelting XP</b> — at the {@code craftRecipe} call. That call is only reached when a cook
 *       finishes (cook time hit its total and the output slot can accept the result), so it is the
 *       faithful analogue of the legacy {@code FurnaceSmeltEvent}. The injection point is the invoke
 *       itself (default shift = before), so the input slot ({@code INPUT_SLOT_INDEX = 0}) still holds
 *       the item being smelted — {@code craftRecipe} is what decrements it.</li>
 *   <li><b>Second Smelt</b> — at the {@code setLastRecipe} call immediately after it.
 *       {@code setLastRecipe} is called <i>only</i> on the branch where {@code craftRecipe} returned
 *       true (bytecode-verified), which makes it a free "the smelt succeeded" marker, and by then the
 *       result has been merged into the output slot ({@code OUTPUT_SLOT_INDEX = 2}) — exactly what
 *       the bonus item has to be added to. Splitting the two hooks this way is what lets the XP hook
 *       read the input and the bonus hook read the output.</li>
 *   <li><b>Fuel Efficiency</b> — the value returned by {@code getFuelTime}, which {@code tick} assigns
 *       straight into {@code litTimeRemaining} (and then {@code litTotalTime}, so the fuel gauge
 *       scales with it). It is reached only on the branch that starts a new burn, i.e. exactly when
 *       the legacy {@code FurnaceBurnEvent} fired. {@code getFuelTime} is protected and {@code tick}
 *       is static, so MixinExtras' {@link ModifyExpressionValue} is used rather than a
 *       {@code @Redirect} that would need an {@code @Invoker} just to call the original.</li>
 * </ul>
 *
 * <p>Every injector carries {@code allow = 1}: each of these targets appears exactly once in
 * {@code tick} today, and a silent second bind (say, if a future version calls {@code setLastRecipe}
 * on another branch) would double-apply the bonus rather than fail loudly. {@code defaultRequire = 1}
 * alone does not catch that — {@code require} is a minimum.
 *
 * <p>No client guard is needed: {@code tick} is only ever handed a {@link ServerWorld}.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceSmeltMixin {

    @Inject(
            method = "tick",
            allow = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/entity/AbstractFurnaceBlockEntity;craftRecipe("
                            + "Lnet/minecraft/registry/DynamicRegistryManager;"
                            + "Lnet/minecraft/recipe/RecipeEntry;"
                            + "Lnet/minecraft/recipe/input/SingleStackRecipeInput;"
                            + "Lnet/minecraft/util/collection/DefaultedList;I)Z"))
    private static void mcmmo$onSmeltComplete(ServerWorld world, BlockPos pos, BlockState state,
            AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {
        final ItemStack input = blockEntity.getStack(0); // INPUT_SLOT_INDEX
        SmeltingListener.onFurnaceSmelt(world, pos, input);
    }

    @Inject(
            method = "tick",
            allow = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/entity/AbstractFurnaceBlockEntity;setLastRecipe("
                            + "Lnet/minecraft/recipe/RecipeEntry;)V"))
    private static void mcmmo$onSecondSmelt(ServerWorld world, BlockPos pos, BlockState state,
            AbstractFurnaceBlockEntity blockEntity, CallbackInfo ci) {
        final ItemStack output = blockEntity.getStack(2); // OUTPUT_SLOT_INDEX
        SmeltingListener.onSmeltComplete(pos, output);
    }

    @ModifyExpressionValue(
            method = "tick",
            allow = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/entity/AbstractFurnaceBlockEntity;getFuelTime("
                            + "Lnet/minecraft/item/FuelRegistry;"
                            + "Lnet/minecraft/item/ItemStack;)I"))
    private static int mcmmo$applyFuelEfficiency(int burnTime, ServerWorld world, BlockPos pos,
            BlockState state, AbstractFurnaceBlockEntity blockEntity) {
        return SmeltingListener.boostFuelTime(burnTime, pos, blockEntity.getStack(0));
    }
}
