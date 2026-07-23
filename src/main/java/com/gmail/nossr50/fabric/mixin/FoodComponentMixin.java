package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.FoodListener;
import net.minecraft.component.type.ConsumableComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The food-consumption hook behind Herbalism's Farmer's Diet and Fishing's Fisherman's Diet — the port's
 * replacement for Bukkit's {@code FoodLevelChangeEvent} (legacy {@code EntityListener#onFoodLevelChange}).
 *
 * <p>{@code FoodComponent#onConsume} is the seam because it is where vanilla itself applies the food:
 * bytecode-verified, its body plays the eat sound and then, for a {@code PlayerEntity}, calls
 * {@code getHungerManager().eat(this)}. It carries everything the diets need and Bukkit's event did not
 * hand over cleanly — the {@link World} (so we can reject the client half of a singleplayer session), the
 * eating {@link LivingEntity}, and <b>the eaten {@link ItemStack} itself</b>. That last one collapses
 * legacy's whole main-hand/off-hand {@code isFood} probe, which only existed because the Bukkit event
 * reported a food <em>level</em> with no idea what had been eaten.
 *
 * <p>We inject at {@code TAIL} — after vanilla's own {@code eat} — and top up the hunger bar in
 * {@link FoodListener}, rather than modifying the component's nutrition on the way in: {@code FoodComponent}
 * is a record shared by every stack of that item, so it must never be rewritten per-player.
 */
@Mixin(FoodComponent.class)
public abstract class FoodComponentMixin {

    @Inject(method = "onConsume", at = @At("TAIL"))
    private void mcmmo$onFoodConsumed(World world, LivingEntity user, ItemStack stack,
            ConsumableComponent consumable, CallbackInfo ci) {
        FoodListener.onFoodConsumed(world, user, stack, (FoodComponent) (Object) this);
    }
}
