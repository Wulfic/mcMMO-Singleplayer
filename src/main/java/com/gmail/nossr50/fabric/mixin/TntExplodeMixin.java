package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.BlastMiningListener;
import net.minecraft.world.entity.item.PrimedTnt;
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
@Mixin(PrimedTnt.class)
public abstract class TntExplodeMixin {

    @ModifyArg(
            method = "explode", allow = 1,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;createExplosion("
                            + "Lnet/minecraft/world/entity/Entity;"
                            + "Lnet/minecraft/world/damagesource/DamageSource;"
                            + "Lnet/minecraft/world/level/ExplosionDamageCalculator;"
                            + "DDDFZ"
                            + "Lnet/minecraft/world/level/Level$ExplosionInteraction;)V"),
            index = 6)
    private float mcmmo$applyBiggerBombs(float power) {
        return BlastMiningListener.applyBiggerBombs((PrimedTnt) (Object) this, power);
    }
}
