package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Publishes "this player is interacting with this entity, right now" for the duration of one
 * interaction.
 *
 * <p>Husbandry's feed verb needs it. Feeding a baby ends up in
 * {@code PassiveEntity#growUp(int, boolean)}, which is the only thing vanilla's six feeding paths
 * share — and it takes an {@code int} and nothing else. There is no player to be found from inside
 * it, and {@code growUp} is reached by things that are not players at all (a lamb eating grass, a
 * tadpole ageing), so the identity of the interacting player is the only thing that separates a feed
 * from an AFK farm. See {@code PassiveEntityGrowthMixin} for the full reasoning.
 *
 * <h2>Why this method</h2>
 * {@code PlayerEntity#interact(Entity, Hand)} is the single funnel: it is what the server's
 * interact-entity packet handler dispatches to (verified through
 * {@code ServerPlayNetworkHandler$1#interact}'s bootstrap method), it is not overridden by
 * {@code ServerPlayerEntity}, and everything species-specific happens below it in
 * {@code Entity#interact} → {@code interactMob}. Hooking it costs one {@code ThreadLocal} set and
 * clear per right-click on an entity.
 *
 * <p>HEAD and RETURN as a pair give try/finally semantics: {@code @At("RETURN")} matches every exit,
 * including the spectator early-return, so the stash cannot outlive the interaction that set it.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityInteractMixin {

    @Inject(method = "interact(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/Hand;)"
            + "Lnet/minecraft/util/ActionResult;", at = @At("HEAD"))
    private void mcmmo$beginInteraction(Entity target, Hand hand,
            CallbackInfoReturnable<ActionResult> cir) {
        HusbandryListener.beginPlayerInteraction((PlayerEntity) (Object) this, target);
    }

    @Inject(method = "interact(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/Hand;)"
            + "Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
    private void mcmmo$endInteraction(Entity target, Hand hand,
            CallbackInfoReturnable<ActionResult> cir) {
        HusbandryListener.endPlayerInteraction();
    }
}
