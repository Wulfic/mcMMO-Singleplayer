package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.EntityDamageListener;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The K1/K2 damage hook. mcMMO needs to see and <em>reduce</em> the final damage applied to a living
 * entity (Acrobatics Roll cuts fall damage), which no Fabric API event exposes —
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

    @ModifyReturnValue(method = "modifyAppliedDamage", at = @At("RETURN"))
    private float mcmmo$reduceAppliedDamage(float appliedDamage, DamageSource source, float amount) {
        return EntityDamageListener.onModifyAppliedDamage(
                (LivingEntity) (Object) this, source, appliedDamage);
    }
}
