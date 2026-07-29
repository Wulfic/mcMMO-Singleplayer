package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Husbandry's {@code Brood} (Pass 2 stage 5): thrown eggs that hatch, and sometimes hatch in fours.
 *
 * <h2>What vanilla does, and where the two dice are</h2>
 * {@code EggEntity#onCollision} rolls {@code random.nextInt(8) == 0} to decide whether the egg hatches
 * at all, then {@code random.nextInt(32) == 0} to decide whether that hatch produces four chicks
 * instead of one. Those are the only two {@code Random.nextInt} calls in the method, in that order, so
 * {@code ordinal} distinguishes them and {@code allow = 1} keeps each injector honest about it.
 *
 * <p>Both hooks return {@code 0} to force vanilla's own success branch rather than rewriting the bound.
 * That composes correctly: Brood's chance <b>layers on top of</b> the vanilla roll instead of replacing
 * it, so a configured value can only ever improve the odds. Rewriting {@code nextInt(8)} to
 * {@code nextInt(n)} would have made any {@code n > 8} a silent downgrade on vanilla.
 *
 * <h2>Why an egg farm still earns nothing</h2>
 * Laying is a passive timer — {@code ChickenEntity.eggLayTime} is ticked in {@code tickMovement} — so a
 * hopper under a coop is fully AFK. Brood therefore pays <b>no XP at all</b>; it is a yield sub-skill.
 * The chick it hatches is also given no bred-by marker, which matters more than it looks: a marker
 * would have turned that same AFK egg farm into a raise-XP farm twenty minutes later, once the chicks
 * came of age. Both properties are pinned by tests.
 *
 * <p>The thrower is resolved through {@code ProjectileEntity#getOwner()}, which also closes the
 * dispenser: eggs are dispensable in vanilla, and a dispensed egg has no player owner.
 */
@Mixin(EggEntity.class)
public abstract class EggHatchMixin {

    private static final String ON_COLLISION =
            "onCollision(Lnet/minecraft/util/hit/HitResult;)V";
    private static final String NEXT_INT =
            "Lnet/minecraft/util/math/random/Random;nextInt(I)I";

    /**
     * Rescue an egg vanilla was about to waste.
     *
     * <p>Returning {@code 0} makes vanilla take its own hatch branch, so this reads as "Brood's chance
     * that a failed egg hatches anyway" and the effective rate is {@code 12.5% + chance × 87.5%}.
     */
    @ModifyExpressionValue(method = ON_COLLISION, allow = 1,
            at = @At(value = "INVOKE", target = NEXT_INT, ordinal = 0))
    private int mcmmo$broodHatchesMoreEggs(int roll, HitResult hitResult) {
        return HusbandryListener.onEggHatchRoll((EggEntity) (Object) this, roll);
    }

    /**
     * Turn a hatch into a full clutch.
     *
     * <p>Vanilla's own rare case, reached one time in thirty-two hatches; on a successful Brood roll it
     * is taken deliberately. This is the second of the sub-skill's two rolls, so the listener scales it
     * by hand — {@code ProbabilityUtil} keys its chance off the {@code SubSkillType}, and only one
     * effect per sub-skill can live there.
     */
    @ModifyExpressionValue(method = ON_COLLISION, allow = 1,
            at = @At(value = "INVOKE", target = NEXT_INT, ordinal = 1))
    private int mcmmo$broodHatchesFullClutches(int roll, HitResult hitResult) {
        return HusbandryListener.onFullClutchRoll((EggEntity) (Object) this, roll);
    }
}
