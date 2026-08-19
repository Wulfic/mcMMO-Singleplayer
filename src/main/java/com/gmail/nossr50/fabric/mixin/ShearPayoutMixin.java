package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.BoggedEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.SnowGolemEntity;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Husbandry's shear verb (Pass 2 stage 3): XP, Hidden Bounty, and the {@code Bountiful Harvest}
 * decision — everything that happens <em>once per shear</em> rather than once per item.
 *
 * <h2>The seam: a window around {@code sheared}, because there is no loot funnel here</h2>
 * Where vanilla routes a species' shear loot through a single {@code BiConsumer} handler, this hook
 * rides that handler and needs no window. <b>Where it does not, nothing sees every sheared item</b> —
 * each species drops inline, by its own route, verified against the merged jar:
 *
 * <ul>
 *   <li>{@code SheepEntity} — a {@code dropItem(ItemConvertible, int)} loop</li>
 *   <li>{@code SnowGolemEntity} — one {@code dropStack(ItemStack, float)} for the carved pumpkin</li>
 *   <li>{@code BoggedEntity} — delegates to a private {@code dropShearedItems()}, which rolls a loot
 *       table and calls {@code dropStack} per item.&#9888; <b>A different method</b>, so an injector
 *       scoped to {@code sheared} would never reach it</li>
 *   <li>{@code MooshroomEntity} — converts itself to a cow, then builds {@code ItemEntity}s directly
 *       in a fixed-count loop; it never touches the shared drop path at all</li>
 * </ul>
 *
 * <p>&#9888; {@code dropItem(ItemConvertible, int)}'s {@code int} is a <b>y-offset, not a count</b> —
 * it is widened to a {@code float} and handed straight to {@code dropStack}. Doubling it moves the
 * drop rather than duplicating it, which looks plausible in a diff and is silently wrong in game.
 *
 * <p>So this mixin owns only the once-per-shear half. The per-item half is delivered by
 * {@code EntityShearDropMixin} (the three species that do bottom out in {@code dropStack}) and
 * {@code MooshroomShearDropsMixin} (the one that does not).
 *
 * <p>The four targets are the same set {@code ShearableInteractMixin} already proves, so the roster
 * is checked against the jar in two places rather than asserted here.
 *
 * <p>{@code TAIL} rather than {@code RETURN}: the window must close exactly once, and it must close
 * even when the shear dropped nothing at all.
 *
 * <p>&#9888; {@code allow = 1}, not 4, on a four-target mixin: the ship gate evaluates it <b>per
 * target class</b>, and each of the four has exactly one site. Reading it as a total is the obvious
 * mistake and it fails the gate as a MISMATCH rather than binding wrongly.
 */
@Mixin({SheepEntity.class, MooshroomEntity.class, SnowGolemEntity.class, BoggedEntity.class})
public abstract class ShearPayoutMixin {

    @Inject(method = "sheared(Lnet/minecraft/sound/SoundCategory;)V", allow = 1, at = @At("HEAD"))
    private void mcmmo$beginShear(SoundCategory soundCategory, CallbackInfo ci) {
        HusbandryListener.beginShear((LivingEntity) (Object) this);
    }

    @Inject(method = "sheared(Lnet/minecraft/sound/SoundCategory;)V", allow = 1, at = @At("TAIL"))
    private void mcmmo$endShear(SoundCategory soundCategory, CallbackInfo ci) {
        HusbandryListener.endShear();
    }
}
