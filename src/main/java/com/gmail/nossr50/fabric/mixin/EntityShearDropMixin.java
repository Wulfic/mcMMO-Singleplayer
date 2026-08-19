package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * {@code Bountiful Harvest}'s per-item half for the shear verb: doubles each stack a shear drops.
 *
 * <h2>&#9888; Why this sits on {@code Entity#dropStack} rather than on each species</h2>
 * {@code dropStack(ItemStack, float)} is the one call three of the four shearable species bottom out
 * in — {@code SheepEntity}'s {@code dropItem} loop widens into it, {@code SnowGolemEntity} calls it
 * directly, and {@code BoggedEntity} calls it from a <b>separate private method</b>
 * ({@code dropShearedItems}) that an injector scoped to {@code sheared} could never reach.
 *
 * <p>It is also how most of the game drops most of its items, which is exactly why this hook does
 * nothing unless {@code ShearPayoutMixin} has opened a window and that shear won its roll. The gate
 * lives in {@link HusbandryListener#onShearDropStack}, so the "is this ours?" decision has one home
 * rather than being re-derived here.
 *
 * <p>&#128273; The window is opened at {@code sheared}'s HEAD and closed at its TAIL, on the same
 * thread and the same call stack, so it cannot leak into an unrelated drop.
 *
 * <p>{@code allow = 1} because this modifies the target method's own argument rather than matching
 * call sites, so there is exactly one injection point by construction. It is declared rather than
 * omitted because {@code scripts/mixin-allow-audit.py} treats a bare injector as MISSING — every
 * injector states its count, including the ones that cannot drift.
 */
@Mixin(Entity.class)
public abstract class EntityShearDropMixin {

    @ModifyVariable(
            method = "dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;",
            allow = 1, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ItemStack mcmmo$doubleShearDrop(ItemStack stack) {
        return HusbandryListener.onShearDropStack(stack);
    }
}
