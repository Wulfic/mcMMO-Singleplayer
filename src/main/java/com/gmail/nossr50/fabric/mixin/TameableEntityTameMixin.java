package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.TamingListener;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The K7 Taming hook for {@link TameableEntity} (wolves, cats, parrots). {@code setOwner} is the
 * vanilla "tamed by this player" entry — called from each mob's {@code interactMob} on a successful
 * tame, <em>not</em> on NBT load (load restores the owner via {@code setOwnerUuid}), so it fires once
 * per real taming. We inject at {@code TAIL} (after the entity is marked tamed) and route to
 * {@link TamingListener#onEntityTamed}, which awards Taming XP.
 *
 * <p><b>BAND:</b> this entry point is named {@code setTamedBy} on newer versions and
 * {@code setOwner(PlayerEntity)} here — the same method, renamed, and the rename is invisible to the
 * compiler because a mixin selector is a string.
 *
 * <p>⚠️ The "not on NBT load" half is the load-bearing claim, so it was re-verified against this
 * band's own bytecode rather than carried over: {@code readCustomDataFromNbt} calls
 * {@code setOwnerUuid} and {@code setTamed} <b>directly</b> and never routes through
 * {@code setOwner}, so a stored pet does not re-award Taming XP every time the world loads. The
 * callers are {@code WolfEntity}, {@code CatEntity} and {@code ParrotEntity} — one each, matching the
 * three species named above.
 */
@Mixin(TameableEntity.class)
public abstract class TameableEntityTameMixin {

    @Inject(method = "setOwner", allow = 1, at = @At("TAIL"))
    private void mcmmo$onTamed(PlayerEntity player, CallbackInfo ci) {
        TamingListener.onEntityTamed(player, (TameableEntity) (Object) this);
    }
}
