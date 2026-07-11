package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.SmeltingListener;
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
 * The K7 Smelting hook: detects when a furnace completes a smelt. Vanilla fires no event, so we
 * inject into the static {@code AbstractFurnaceBlockEntity#tick} at its call to {@code craftRecipe}
 * — that call is only reached when a cook finishes (cook time hit its total and the output slot can
 * accept the result), so it is the faithful analogue of the legacy {@code FurnaceSmeltEvent}.
 *
 * <p>The injection point is the {@code craftRecipe} invoke itself (default shift = before), so the
 * input slot ({@code INPUT_SLOT_INDEX = 0}) still holds the item being smelted — {@code craftRecipe}
 * decrements it. We read that stack and hand it to {@link SmeltingListener#onFurnaceSmelt}, which
 * resolves the furnace owner and awards Smelting XP.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceSmeltMixin {

    @Inject(
            method = "tick",
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
}
