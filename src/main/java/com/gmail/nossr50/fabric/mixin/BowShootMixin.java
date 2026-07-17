package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.skills.archery.Archery;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * mcMMO's replacement for Bukkit's {@code EntityShootBowEvent}, which legacy used to read the bow's
 * draw force ({@code event.getForce()}) and stamp it on the arrow for Archery's force-scaled XP (see
 * {@code EntityListener#onEntityShootBow}). Vanilla fires no such event, and the arrow-spawn funnel the
 * launch mark rides ({@code ProjectileSpawnMixin}) only sees the arrow, not the bow — so the force is
 * captured here, one level up, where the bow is in hand and its pull is known.
 *
 * <p>Bytecode-verified that {@code BowItem#onStoppedUsing} computes the shot's pull exactly as this
 * does — {@code getPullProgress(getMaxUseTime(stack, user) - remainingUseTicks)} — then calls
 * {@code shootAll}, which spawns the arrow through the funnel. Capturing the force at this method's
 * {@code HEAD} and clearing it at every {@code RETURN} brackets that spawn on the server thread, so the
 * {@link Archery} {@code ThreadLocal} it is handed to is live for exactly the one arrow this bow fires
 * (see {@code Archery#beginBowShot}). A below-threshold pull ({@code < 0.1}) returns without shooting;
 * the force is set and cleared with nothing reading it, which is harmless.
 *
 * <p>Gated on a {@link PlayerEntity} user, as legacy's {@code event.getEntity() instanceof Player} was:
 * skeletons fire through their own attack goal, not this method, and only the player earns Archery XP.
 */
@Mixin(BowItem.class)
public abstract class BowShootMixin {

    @Inject(
            method = "onStoppedUsing(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/entity/LivingEntity;I)Z",
            at = @At("HEAD"))
    private void mcmmo$captureBowForce(ItemStack stack, World world, LivingEntity user,
            int remainingUseTicks, CallbackInfoReturnable<Boolean> cir) {
        if (!(user instanceof PlayerEntity)) {
            return;
        }
        final BowItem bow = (BowItem) (Object) this;
        final int useTicks = bow.getMaxUseTime(stack, user) - remainingUseTicks;
        Archery.beginBowShot(BowItem.getPullProgress(useTicks));
    }

    @Inject(
            method = "onStoppedUsing(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/entity/LivingEntity;I)Z",
            at = @At("RETURN"))
    private void mcmmo$clearBowForce(ItemStack stack, World world, LivingEntity user,
            int remainingUseTicks, CallbackInfoReturnable<Boolean> cir) {
        Archery.endBowShot();
    }
}
