package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.FishingListener;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * The Master Angler seam (CONVERSION_TODO §E): shortens how long a bobber waits for a bite. Replaces
 * legacy's {@code MasterAnglerTask}, which ran a tick after the Bukkit {@code PlayerFishEvent} and
 * mutated the {@code FishHook} through {@code setMinWaitTime}/{@code setMaxWaitTime}/
 * {@code setApplyLure} — none of which vanilla exposes.
 *
 * <p><b>The seam.</b> Bytecode-verified, {@code FishingBobberEntity#tickFishingLogic} ends with the
 * <i>only</i> place a fresh wait is drawn:</p>
 *
 * <pre>{@code
 * this.waitCountdown = MathHelper.nextInt(this.random, 100, 600);
 * this.waitCountdown = this.waitCountdown - this.waitTimeReductionTicks;
 * }</pre>
 *
 * <p>Those hardcoded {@code 100}/{@code 600} are exactly what Bukkit's {@code FishHook#getMinWaitTime}/
 * {@code getMaxWaitTime} returned by default, so redirecting this one call gives us legacy's three API
 * calls at once: the redirect receives vanilla's own bounds (nothing hardcoded here), draws from the
 * mcMMO-reduced range instead, and — since the reduction was already folded into the max-wait bonus —
 * adds {@code waitTimeReductionTicks} back so the subtraction on the next line cancels out. That
 * add-back <i>is</i> legacy's {@code setApplyLure(false)}, which existed to dodge a Minecraft bug where
 * Lure above level 3 breaks fishing.
 *
 * <p>{@code tickFishingLogic} casts the world to {@code ServerWorld} on its first line, so it is
 * server-only and needs no client guard. The redirect is anchored with a {@link Slice} starting at the
 * {@code 600} constant rather than by ordinal — {@code tickFishingLogic} makes two other
 * {@code MathHelper.nextInt} calls (the hook and fish-travel countdowns) and we must not touch those.
 *
 * <p><b>{@code allow = 1} is load-bearing, not decoration.</b> Verified by mutation: point the slice at
 * a constant that does not exist and Mixin does <i>not</i> fail the slice — it silently drops the
 * restriction and binds this redirect to <i>all three</i> {@code nextInt} calls, hijacking the hook and
 * fish-travel countdowns as well and corrupting vanilla fishing timings for everyone, while
 * {@code defaultRequire=1} still reports success. Capping the binding count is what turns that into a
 * loud startup failure ("3 succeeded of 1 allowed"). Any future slice-anchored injector in this mod
 * wants the same guard.
 *
 * <p><b>Deviation from legacy (documented):</b> legacy applied Master Angler once per cast; this fires
 * on every wait redraw, so a cast that cycles through several bite windows keeps the bonus instead of
 * reverting to vanilla timings after the first. The gates are also read at draw time rather than at
 * cast time, so swapping the rod out mid-cast changes the next wait.
 */
@Mixin(FishingBobberEntity.class)
public abstract class FishingWaitTimeMixin {

    /**
     * Vanilla's Lure reduction in ticks (100 per enchantment level), set once in the constructor. Read
     * only — we hand it to the listener so it can reproduce legacy's {@code convertedLureBonus} without
     * an enchantment-registry lookup.
     */
    @Shadow
    @Final
    private int waitTimeReductionTicks;

    @Redirect(
            method = "tickFishingLogic",
            slice = @Slice(from = @At(value = "CONSTANT", args = "intValue=600")),
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/math/MathHelper;nextInt("
                            + "Lnet/minecraft/util/math/random/Random;II)I"),
            require = 1,
            allow = 1)
    private int mcmmo$masterAnglerWaitCountdown(Random random, int minWaitTicks, int maxWaitTicks) {
        return FishingListener.resolveWaitCountdown((FishingBobberEntity) (Object) this, random,
                minWaitTicks, maxWaitTicks, this.waitTimeReductionTicks);
    }
}
