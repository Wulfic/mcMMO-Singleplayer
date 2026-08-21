package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.EntityDamageListener;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The K1/K2 damage hook. mcMMO needs to see and <em>reduce</em> the final damage applied to a living
 * entity (Agility Roll cuts fall damage), which no Fabric API event exposes —
 * {@code ServerLivingEntityEvents.ALLOW_DAMAGE} is cancel-only. So we intercept the return of
 * {@link LivingEntity#modifyAppliedDamage(DamageSource, float)}, the vanilla method that yields the
 * post-armor/enchantment damage about to be dealt, and route it through
 * {@link EntityDamageListener}.
 *
 * <p>Uses MixinExtras {@link ModifyReturnValue} (bundled with the Fabric loader) so the handler simply
 * transforms the returned float, composing cleanly with any other mod that touches the same method.
 * The listener no-ops for everything except server players taking fall damage today.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    @ModifyReturnValue(method = "modifyAppliedDamage", allow = 4, at = @At("RETURN"))
    private float mcmmo$reduceAppliedDamage(float appliedDamage, DamageSource source, float amount) {
        return EntityDamageListener.onModifyAppliedDamage(
                (LivingEntity) (Object) this, source, appliedDamage);
    }

    /**
     * The <b>pre-armor</b> half of the same hook: capture the incoming damage on its way <em>into</em>
     * vanilla's armor mitigation, so Unarmored's XP can be paid on what was thrown at the player
     * rather than on what got through.
     *
     * <p>The reason this second seam has to exist is that mcMMO's only damage window is
     * {@code modifyAppliedDamage}, which is post-armor — and Unarmored's Iron Skin <em>is</em> armor.
     * Paying XP on the post-armor figure would make the skill throttle its own progress: at the
     * diamond tier vanilla soaks roughly two thirds of a hit, so the last and longest stretch of the
     * grind would crawl at a third rate. See {@code UnarmoredManager#getUnarmoredXp}.
     *
     * <p><b>Why a stash-and-consume join is safe here.</b> Bytecode-verified in both
     * {@code LivingEntity#applyDamage} and its {@code PlayerEntity} override (the latter does not
     * call super, it re-implements — but calls both of these inherited methods itself):
     * <pre>
     *   amount = applyArmorToDamage(source, amount);   // captured here
     *   amount = modifyAppliedDamage(source, amount);  // consumed there
     * </pre>
     * The two calls are adjacent, unconditional and on the same entity and thread, with nothing
     * between them that could re-enter the damage pipeline. {@code applyArmorToDamage} is not
     * overridden by {@code PlayerEntity} or {@code ServerPlayerEntity}, so this injector is the only
     * one needed to cover players. The consumer still verifies entity <em>and</em> source identity
     * before trusting the reading — see {@code EntityDamageListener#recordPreArmorDamage}.
     */
    @Inject(method = "applyArmorToDamage", allow = 1, at = @At("HEAD"))
    private void mcmmo$capturePreArmorDamage(DamageSource source, float amount,
            CallbackInfoReturnable<Float> cir) {
        EntityDamageListener.recordPreArmorDamage((LivingEntity) (Object) this, source, amount);
    }
}
