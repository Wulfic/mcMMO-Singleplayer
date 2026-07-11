package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.TamingListener;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The K7 Taming hook for {@link AbstractHorseEntity} (horses, donkeys, mules, llamas, camels) —
 * these do not extend {@code TameableEntity}, so they need their own hook. {@code bondWithPlayer} is
 * the vanilla horse-taming method; it returns {@code true} only when the bond succeeds (the horse was
 * untamed and the RNG temper roll passed). We inject at {@code RETURN} and award Taming XP via
 * {@link TamingListener#onEntityTamed} only on a successful bond, avoiding rewarding failed attempts.
 */
@Mixin(AbstractHorseEntity.class)
public abstract class AbstractHorseBondMixin {

    @Inject(method = "bondWithPlayer", at = @At("RETURN"))
    private void mcmmo$onBond(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            TamingListener.onEntityTamed(player, (AbstractHorseEntity) (Object) this);
        }
    }
}
