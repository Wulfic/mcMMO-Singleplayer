package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.FishingListener;
import java.util.Collection;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The K7 Fishing hook: detects when a player reels in a catch. Vanilla fires no event, so we tap the
 * one unambiguous seam inside {@code FishingBobberEntity#use} — the
 * {@code Criteria.FISHING_ROD_HOOKED.trigger(player, rod, bobber, caughtLoot)} call. Its fourth
 * argument is the caught-loot {@code Collection<ItemStack>}, so a {@link ModifyArg} on that argument
 * gives us the exact items with no local-variable capture (robust across mappings).
 *
 * <p>{@code use} runs only server-side (it early-returns when the world is client or the owner is
 * null before any trigger call), so no client guard is needed here. The criterion also fires for the
 * reel-in-a-hooked-entity branch, but there vanilla passes {@code Collections.emptyList()} — the
 * listener treats an empty collection as a no-op.
 *
 * <p>The fourth argument is the very same {@code ObjectArrayList} the method then iterates to spawn the
 * reeled-in item entities (bytecode-verified: the criterion call and the spawn loop both read the one
 * local slot), so the listener may mutate it in place to inject a Treasure Hunter reward — that reward
 * then flies to the player exactly like a normal catch. We return the (possibly mutated) collection;
 * mutating it also lets the criterion see the reward, a harmless advancement-trigger deviation.
 */
@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberUseMixin {

    @ModifyArg(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/advancement/criterion/FishingRodHookedCriterion;trigger("
                            + "Lnet/minecraft/server/network/ServerPlayerEntity;"
                            + "Lnet/minecraft/item/ItemStack;"
                            + "Lnet/minecraft/entity/projectile/FishingBobberEntity;"
                            + "Ljava/util/Collection;)V"),
            index = 3)
    private Collection<ItemStack> mcmmo$onFishCaught(Collection<ItemStack> caught) {
        FishingListener.onFishCaught((FishingBobberEntity) (Object) this, caught);
        return caught;
    }
}
