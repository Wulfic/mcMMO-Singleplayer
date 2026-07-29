package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Husbandry's {@code Selective Breeding} (Pass 2 stage 5): a foal that beats the dice.
 *
 * <h2>Where vanilla's inheritance actually lives</h2>
 * {@code AbstractHorseEntity.calculateAttributeBaseValue(parentA, parentB, min, max, random)} is the
 * whole of it, and jar-grep finds it referenced by {@code AbstractHorseEntity} and nothing else. It
 * takes the midpoint of the two parents, widens a bell curve by their spread plus 15% of the attribute
 * range, rolls three {@code nextDouble()}s to shape it, and reflects the result back inside
 * {@code [min, max]} if it lands outside.
 *
 * <p>The bias is applied to that method's <b>return value</b> rather than to the dice that produced it.
 * Biasing the roll would have meant reaching into three separate {@code nextDouble()} calls and
 * reasoning about the reflection step; biasing the outcome is one hook, is trivially monotonic, and
 * keeps the property that matters — good parents still give better foals, because the bias moves you
 * along the range rather than replacing it.
 *
 * <h2>⚠️ Why this needs a two-part join</h2>
 * {@code calculateAttributeBaseValue} is {@code static} and holds no player, so it cannot ask whose
 * sub-skill to roll. And it runs for <b>every</b> horse bred anywhere, including by players without
 * the sub-skill and by breeding with no player involved at all.
 *
 * <p>So the breeder is stashed around {@code setChildAttributes}, which is an instance method on a
 * parent and therefore has {@code getLovingPlayer()} to hand, and consumed inside the static call —
 * the same HEAD/RETURN stash shape the feed verb and the shear verb already use. The window is one
 * synchronous call on the server thread. Ordering is safe: {@code createChild} (and with it
 * {@code setChildAttributes}) runs <em>before</em> {@code AnimalEntity#breed} clears the loving player.
 */
@Mixin(AbstractHorseEntity.class)
public abstract class HorseChildAttributesMixin {

    private static final String SET_CHILD_ATTRIBUTES =
            "setChildAttributes(Lnet/minecraft/entity/passive/PassiveEntity;"
                    + "Lnet/minecraft/entity/passive/AbstractHorseEntity;)V";

    /**
     * Open the stash: the horse whose {@code setChildAttributes} is running knows who bred it.
     *
     * <p>{@code this} is one of the two parents, which is all that is needed — {@code getLovingPlayer}
     * is set on whichever animal the player fed, and vanilla only reaches breeding when at least one
     * parent has one.
     */
    @Inject(method = SET_CHILD_ATTRIBUTES, allow = 1, at = @At("HEAD"))
    private void mcmmo$beginChildAttributes(PassiveEntity child, AbstractHorseEntity other,
            CallbackInfo ci) {
        HusbandryListener.beginSelectiveBreeding((AbstractHorseEntity) (Object) this, other);
    }

    /** Close the stash on every exit, so it cannot outlive the breeding that opened it. */
    @Inject(method = SET_CHILD_ATTRIBUTES, allow = 1, at = @At("RETURN"))
    private void mcmmo$endChildAttributes(PassiveEntity child, AbstractHorseEntity other,
            CallbackInfo ci) {
        HusbandryListener.endSelectiveBreeding();
    }

    /**
     * Nudge one rolled stat toward the best the species allows.
     *
     * <p>{@code min} and {@code max} arrive as target parameters, which is what lets the bias be
     * expressed as "a fraction of the gap remaining" instead of a flat addition — a flat bonus would be
     * enormous on jump strength and invisible on health, since the three attributes this covers span
     * completely different ranges.
     *
     * <p>⚠️ <b>No {@code allow} here, deliberately.</b> The method has <b>three</b> {@code return}
     * statements — the in-range result plus the two that reflect an out-of-range roll back inside the
     * bounds — and all three are the same logical answer, so all three must be biased. An
     * {@code allow = 1} copied from the injectors above fails the load check with "3 succeeded of 1
     * allowed"; capping it at 1 would be worse than the error, because it would bias only the
     * in-range path and silently leave extreme rolls untouched.
     */
    @ModifyReturnValue(method = "calculateAttributeBaseValue(DDDDLnet/minecraft/util/math/random/"
            + "Random;)D", at = @At("RETURN"))
    private static double mcmmo$biasChildAttribute(double rolled, double parentA, double parentB,
            double min, double max, Random random) {
        return HusbandryListener.applySelectiveBreedingBias(rolled, min, max);
    }
}
