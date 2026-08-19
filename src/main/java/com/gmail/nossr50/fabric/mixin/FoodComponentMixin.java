package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.FoodListener;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The food-consumption hook behind Herbalism's Farmer's Diet and Fishing's Fisherman's Diet — the port's
 * replacement for Bukkit's {@code FoodLevelChangeEvent} (legacy {@code EntityListener#onFoodLevelChange}).
 *
 * <p>The seam is wherever vanilla itself applies the food, because that is the one place carrying
 * everything the diets need and Bukkit's event did not hand over cleanly — the {@link World} (so we can
 * reject the client half of a singleplayer session), the eating {@link LivingEntity}, and <b>the eaten
 * {@link ItemStack} itself</b>. That last one collapses legacy's whole main-hand/off-hand {@code isFood}
 * probe, which only existed because the Bukkit event reported a food <em>level</em> with no idea what had
 * been eaten.
 *
 * <p><b>Which method that is differs by version.</b> Where the consumption logic has been lifted onto the
 * item-data components, the seam is the component's own consume callback. At this version it has not
 * been: {@code LivingEntity#eatFood(World, ItemStack, FoodComponent)} is the funnel, and there is no
 * separate consumable component to hook. The class keeps its name because the <em>hook</em> is the same
 * one, not because the target class is.
 *
 * <p>⚠️ <b>{@code TAIL}, and it must stay {@code TAIL}</b>, because the hunger bar has to be full before
 * {@link FoodListener} tops it up. {@code PlayerEntity} overrides this method and applies
 * {@code getHungerManager().eat(...)} <em>before</em> delegating up (bytecode-verified against this
 * version's merged jar: {@code HungerManager.eat} at offset 5, the {@code super} call last), so a tail
 * injection on the {@code LivingEntity} declaration runs after vanilla has finished eating.
 *
 * <p>Topping up in {@link FoodListener} is also why nothing here rewrites the component's nutrition on the
 * way in: {@code FoodComponent} is a record shared by every stack of that item, so it must never be
 * rewritten per-player.
 */
@Mixin(LivingEntity.class)
public abstract class FoodComponentMixin {

    @Inject(method = "eatFood(Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/component/type/FoodComponent;)Lnet/minecraft/item/ItemStack;",
            allow = 1, at = @At("TAIL"))
    private void mcmmo$onFoodConsumed(World world, ItemStack stack, FoodComponent food,
            CallbackInfoReturnable<ItemStack> cir) {
        FoodListener.onFoodConsumed(world, (LivingEntity) (Object) this, stack, food);
    }
}
