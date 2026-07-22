package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.AlchemyListener;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The K7 Alchemy hook: makes mcMMO take over brewing on the vanilla brewing stand (CONVERSION_TODO
 * §B). mcMMO's brewing tree includes recipes vanilla does not know, and every brew must award
 * Alchemy XP, so recipe recognition and the craft are both replaced — while vanilla still owns the
 * fuel, the brew timer, the progress bar, and the particles.
 *
 * <p>Three injections, all into {@code static} vanilla methods (hence the {@code static} handlers):
 * <ul>
 *   <li>{@code canCraft} (HEAD, cancellable) — forces the return value to {@code true} when the stand
 *       holds a recognised mcMMO brew ({@link AlchemyListener#isValidBrew}). This is what lets a
 *       custom (non-vanilla) recipe start and keep brewing; vanilla-valid recipes are unaffected
 *       (they still return true on their own).</li>
 *   <li>{@code craft} (HEAD, cancellable) — for a recognised mcMMO brew, runs
 *       {@link AlchemyListener#onBrewCraft} (transform bottles → child potions, consume the
 *       ingredient, award XP) and cancels vanilla's craft. A recipe mcMMO does not recognise falls
 *       through to vanilla unchanged.</li>
 *   <li>{@code tick} (HEAD) — the Catalysis brew-speed hook: {@link AlchemyListener#applyCatalysis}
 *       burns extra ticks off the stand's brew timer <i>before</i> vanilla's own decrement, which is
 *       what replaces the legacy {@code AlchemyBrewTask}'s hand-rolled brew loop.</li>
 * </ul>
 *
 * <p>{@code craft} is only invoked by {@code tick} once the brew timer reaches zero (with
 * {@code canCraft} still true), so it is the faithful analogue of the legacy Bukkit {@code BrewEvent}
 * completion — and it stays that way under Catalysis, because the speed-up never drives the timer to
 * zero itself (see {@code CatalysisTimer.MIN_BREW_TIME}).
 *
 * <p>The {@code tick} hook needs no client-side guard: {@code BrewingStandBlock#getTicker} returns
 * {@code null} on a client world (bytecode-verified), so this only ever runs server-side.
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {

    @Inject(method = "canCraft", at = @At("HEAD"), cancellable = true)
    private static void mcmmo$forceMcMMOBrewRecognition(BrewingRecipeRegistry registry,
            DefaultedList<ItemStack> slots, CallbackInfoReturnable<Boolean> cir) {
        if (AlchemyListener.isValidBrew(slots)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "craft", at = @At("HEAD"), cancellable = true)
    private static void mcmmo$onBrewCraft(World world, BlockPos pos,
            DefaultedList<ItemStack> slots, CallbackInfo ci) {
        if (AlchemyListener.isValidBrew(slots)) {
            AlchemyListener.onBrewCraft(world, pos, slots);
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private static void mcmmo$applyCatalysisBrewSpeed(World world, BlockPos pos, BlockState state,
            BrewingStandBlockEntity blockEntity, CallbackInfo ci) {
        AlchemyListener.applyCatalysis(pos, blockEntity);
    }
}
