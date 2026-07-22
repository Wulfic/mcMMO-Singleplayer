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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The K7 Smelting hooks. Vanilla fires no furnace events at all, so three of mcMMO's four furnace
 * behaviours are injected into the static {@code AbstractFurnaceBlockEntity#tick}, and routed through
 * {@link SmeltingListener}, which resolves the furnace's owner:
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
 * <p>The fourth behaviour, <b>Understanding the Art</b>, rides a different method on the same class:
 * the private static {@code dropExperience}, whose {@code ExperienceOrbEntity#spawn} argument is the
 * furnace XP a player is about to collect. It is not part of {@code tick} — the trigger is a player
 * taking the result out, picked up by {@code FurnaceOutputSlotMixin}, which leaves the multiplier in
 * a thread-local for {@link #mcmmo$boostVanillaXp} to read.
 *
 * <p>Every injector carries {@code allow = 1}: each of these targets appears exactly once in its
 * target method today, and a silent second bind (say, if a future version calls {@code setLastRecipe}
 * on another branch) would double-apply the bonus rather than fail loudly. {@code defaultRequire = 1}
 * alone does not catch that — {@code require} is a minimum.
 *
 * <p>No client guard is needed: {@code tick} is only ever handed a {@link ServerWorld}, and
 * {@code dropExperience} takes one.
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

    /**
     * Understanding the Art: scale the orb a furnace drops for its stored recipe XP. This is the
     * legacy {@code onFurnaceExtractEvent}'s {@code event.setExpToDrop(...)}.
     *
     * <p>{@code dropExperience} is the last step of both drop paths — a player extracting
     * ({@code dropExperienceForRecipesUsed}) and the furnace being broken — and by the time it calls
     * {@code ExperienceOrbEntity#spawn} it has already done its floor-plus-fractional-chance rounding,
     * which is exactly the figure Bukkit put in {@code getExpToDrop}. The player-driven path is the
     * only one that leaves a multiplier in {@link SmeltingListener}'s thread-local, so breaking a
     * furnace still drops vanilla XP.
     *
     * <p>It is injected here rather than at the {@code dropExperience} call inside
     * {@code getRecipesUsedAndDropExperience}, because that call site lives in a lambda.
     */
    @ModifyArg(
            method = "dropExperience(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/util/math/Vec3d;IF)V",
            allow = 1,
            index = 2,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ExperienceOrbEntity;spawn("
                            + "Lnet/minecraft/server/world/ServerWorld;"
                            + "Lnet/minecraft/util/math/Vec3d;I)V"))
    private static int mcmmo$boostVanillaXp(int amount) {
        return SmeltingListener.boostVanillaXp(amount);
    }
}
