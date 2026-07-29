package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractCowEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Husbandry's milk verb (Pass 2 stage 4): a cow milked into a bucket.
 *
 * <p>{@code AbstractCowEntity#interactMob} is the whole player path for milking, and
 * {@code MooshroomEntity#interactMob} calls {@code super} at the end of its own body — verified in
 * bytecode — so milking a mooshroom arrives here too, and only its stew-in-a-bowl route needs a mixin
 * of its own ({@link MooshroomStewMixin}).
 *
 * <p><b>The real-player gate is the signature.</b> Unlike the shear verb, this needs no interaction
 * stash and no identity check: the method takes a {@code PlayerEntity}, the milk branch is written
 * inline inside it with no shared callee anything else could reach, and vanilla ships no dispenser
 * behaviour that milks a cow. That last point was checked rather than assumed — a hive harvest and an
 * armadillo brush both turn out to have one.
 *
 * <p><b>Vanilla rate-limits this verb by nothing whatsoever</b>, which is D-H5: the same cow can be
 * milked as fast as a player can click, forever, for free. What bounds it is mcMMO's own per-animal
 * harvest cooldown in the listener, not any game mechanic.
 */
@Mixin(AbstractCowEntity.class)
public abstract class CowMilkMixin {

    /**
     * Pay the milk verb.
     *
     * <p>Anchored on {@code ItemUsage.exchangeStack}, the bucket-for-milk-bucket swap. That call is
     * the point of no return in the milk branch — vanilla has already confirmed a bucket in hand and
     * an adult animal — and it is the only {@code exchangeStack} in the method, so the match is
     * unambiguous without an ordinal.
     */
    @Inject(method = "interactMob(Lnet/minecraft/entity/player/PlayerEntity;"
            + "Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemUsage;exchangeStack("
                            + "Lnet/minecraft/item/ItemStack;"
                            + "Lnet/minecraft/entity/player/PlayerEntity;"
                            + "Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;"))
    private void mcmmo$onMilked(PlayerEntity player, Hand hand,
            CallbackInfoReturnable<ActionResult> cir) {
        HusbandryListener.onMilked((Entity) (Object) this, player);
    }
}
