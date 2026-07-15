package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.BlastMiningListener;
import net.minecraft.entity.TntEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The Blast Mining "Bigger Bombs" hook: scales the blast radius of an mcMMO-detonated TNT by the
 * detonator's rank. Replaces legacy's {@code ExplosionPrimeEvent} handler
 * ({@code EntityListener#onExplosionPrime} → {@code event.setRadius(...)}), which vanilla has no
 * event for.
 *
 * <p>The seam is the single {@code World#createExplosion(...)} call inside the private
 * {@code TntEntity#explode()}: its {@code float} power argument is exactly Bukkit's "radius", so a
 * {@link ModifyArg} on it needs no local capture. {@code explode()} only reaches that call on a
 * {@code ServerWorld} (and with the {@code tntExplodes} game rule on), so no side guard is needed.
 * Every other TNT — hand-lit, dispenser-lit, chain-detonated — is untracked and passes through with
 * its vanilla power; see {@link BlastMiningListener} for how a tracked charge is recognised.
 */
@Mixin(TntEntity.class)
public abstract class TntExplodeMixin {

    @ModifyArg(
            method = "explode",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;createExplosion("
                            + "Lnet/minecraft/entity/Entity;"
                            + "Lnet/minecraft/entity/damage/DamageSource;"
                            + "Lnet/minecraft/world/explosion/ExplosionBehavior;"
                            + "DDDFZ"
                            + "Lnet/minecraft/world/World$ExplosionSourceType;)V"),
            index = 6)
    private float mcmmo$applyBiggerBombs(float power) {
        return BlastMiningListener.applyBiggerBombs((TntEntity) (Object) this, power);
    }
}
