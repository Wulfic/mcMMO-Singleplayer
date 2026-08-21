package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Husbandry's {@code Multi-Breed} hook (Pass 2 stage 1): a player has just put an animal into love
 * mode, so nearby animals of the same species go into love mode too, off that one breeding item.
 *
 * <h2>⚠️ Why {@code lovePlayer} and not {@code interactMob}</h2>
 * The obvious target is {@code AnimalEntity#interactMob}, where vanilla checks the held stack
 * against {@code isBreedingItem}. But {@code AbstractHorseEntity}, {@code CamelEntity},
 * {@code LlamaEntity} and {@code PandaEntity} each override {@code interactMob} and call
 * {@code lovePlayer} themselves rather than deferring to {@code AnimalEntity}'s implementation, so
 * an {@code interactMob} hook would leave Multi-Breed silently dead on four species — horses among
 * them, the most expensive line in the breeding table.
 *
 * <p>{@code lovePlayer(PlayerEntity)} is the shared callee those five paths funnel into (verified:
 * they are the only classes in {@code net.minecraft.entity} that reference it) and it is the only
 * method vanilla uses to attribute an animal's love to a player. By {@code TAIL} the fed animal's
 * own love ticks and loving-player reference are already set, so the spread runs against a settled
 * state.
 *
 * <h2>The re-entrancy guard is not optional</h2>
 * The spread is performed by calling {@code lovePlayer} on each neighbour — this exact method.
 * {@code HusbandryListener} holds a {@code ThreadLocal} flag for the duration; without it, one piece
 * of wheat would propagate outward animal by animal until the stack overflowed.
 */
@Mixin(Animal.class)
public abstract class AnimalLovePlayerMixin {

    @Inject(method = "lovePlayer", allow = 1, at = @At("TAIL"))
    private void mcmmo$onLovePlayer(Player player, CallbackInfo ci) {
        HusbandryListener.onLovePlayer((Animal) (Object) this, player);
    }
}
