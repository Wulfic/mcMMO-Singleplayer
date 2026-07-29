package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Husbandry's milk verb, mooshroom variant (Pass 2 stage 4): a bowl of stew out of a mooshroom.
 *
 * <p>The same verb as {@link CowMilkMixin} and it shares that one payout and that one per-animal
 * cooldown, so a mooshroom cannot be milked and then stewed for two awards in the same breath. Pricing
 * the soup differently would make the <em>variant</em> the thing this skill rewards rather than the
 * act of keeping the animal, which is the boundary rule the whole skill is organised around.
 *
 * <p>This needs its own mixin only because {@code MooshroomEntity} overrides {@code interactMob} to
 * add the bowl and flower routes before falling through to {@code super} — which is where the ordinary
 * bucket-milking still happens, so that half is already covered.
 *
 * <p><b>The flower route is deliberately not hooked.</b> Feeding a brown mooshroom a flower changes
 * which effects its stew will carry; it harvests nothing and produces nothing, so it is not a harvest
 * verb. It also lives on a different branch, so excluding it costs nothing.
 */
@Mixin(MooshroomEntity.class)
public abstract class MooshroomStewMixin {

    /**
     * Pay the milk verb for a bowl of stew.
     *
     * <p>Anchored on the four-argument {@code ItemUsage.exchangeStack} — a <em>different overload</em>
     * from the three-argument one the bucket path uses, which is what keeps the two hooks from
     * colliding on a mooshroom that inherits both paths.
     */
    @Inject(method = "interactMob(Lnet/minecraft/entity/player/PlayerEntity;"
            + "Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemUsage;exchangeStack("
                            + "Lnet/minecraft/item/ItemStack;"
                            + "Lnet/minecraft/entity/player/PlayerEntity;"
                            + "Lnet/minecraft/item/ItemStack;Z)Lnet/minecraft/item/ItemStack;"))
    private void mcmmo$onStewed(PlayerEntity player, Hand hand,
            CallbackInfoReturnable<ActionResult> cir) {
        HusbandryListener.onMilked((Entity) (Object) this, player);
    }
}
