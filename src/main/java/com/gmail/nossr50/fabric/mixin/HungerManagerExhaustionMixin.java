package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.AthleteListener;
import net.minecraft.entity.player.HungerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Agility → <b>Athlete</b>: sprinting costs less hunger.
 *
 * <p>{@code HungerManager#addExhaustion} is every exhaustion source in the game funnelled into one
 * method — sprinting, jumping, swimming, mining, taking damage, regenerating. Scaling all of it
 * would turn a sprint perk into a general "you barely get hungry" perk, so the gate lives in
 * {@link AthleteListener} and only discounts exhaustion while the player is actually sprinting.
 *
 * <p>The hunger manager does not know whose it is, which is why the owning player is resolved by
 * identity in {@link AthleteListener#scaleExhaustion} rather than being read off {@code this}.
 */
@Mixin(HungerManager.class)
public abstract class HungerManagerExhaustionMixin {

    @ModifyVariable(method = "addExhaustion", allow = 1, at = @At("HEAD"), argsOnly = true)
    private float mcmmo$applyAthlete(float exhaustion) {
        return AthleteListener.scaleExhaustion((HungerManager) (Object) this, exhaustion);
    }
}
