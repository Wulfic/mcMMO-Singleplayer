package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.TamingListener;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The K7 Taming hook for {@link TameableEntity} (wolves, cats, parrots). {@code setTamedBy} is the
 * vanilla "tamed by this player" entry — called from each mob's {@code interactMob} on a successful
 * tame, <em>not</em> on NBT load (load restores the owner via {@code setOwnerUuid}), so it fires once
 * per real taming. We inject at {@code TAIL} (after the entity is marked tamed) and route to
 * {@link TamingListener#onEntityTamed}, which awards Taming XP.
 */
@Mixin(TamableAnimal.class)
public abstract class TameableEntityTameMixin {

    @Inject(method = "tame", allow = 1, at = @At("TAIL"))
    private void mcmmo$onTamed(Player player, CallbackInfo ci) {
        TamingListener.onEntityTamed(player, (TamableAnimal) (Object) this);
    }
}
