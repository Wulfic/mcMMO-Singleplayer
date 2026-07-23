package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.FishingListener;
import java.util.Collection;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The K7 Fishing hook: detects when a player reels in a catch. Vanilla fires no event, so we tap the
 * one unambiguous seam inside {@code FishingBobberEntity#use} — the
 * {@code Criteria.FISHING_ROD_HOOKED.trigger(player, rod, bobber, caughtLoot)} call. Its fourth
 * argument is the caught-loot {@code Collection<ItemStack>}, so a {@link ModifyArg} on that argument
 * gives us the exact items with no local-variable capture (robust across mappings).
 *
 * <p>{@code use} runs only server-side (it early-returns when the world is client or the owner is
 * null before any trigger call), so no client guard is needed here. The criterion also fires for the
 * reel-in-a-hooked-entity branch, but there vanilla passes {@code Collections.emptyList()} — the
 * listener treats an empty collection as a no-op.
 *
 * <p>The fourth argument is the very same {@code ObjectArrayList} the method then iterates to spawn the
 * reeled-in item entities (bytecode-verified: the criterion call and the spawn loop both read the one
 * local slot), so the listener may mutate it in place to inject a Treasure Hunter reward — that reward
 * then flies to the player exactly like a normal catch. We return the (possibly mutated) collection;
 * mutating it also lets the criterion see the reward, a harmless advancement-trigger deviation.
 *
 * <p><b>The second injector is the Shake seam</b> (legacy's {@code CAUGHT_ENTITY} state). {@code use}
 * opens with a {@code hookedEntity != null} branch that pulls the mob in, and that
 * {@code pullHookedEntity} call is the only one in the class — injecting there is both unambiguous and
 * faithfully ordered: CraftBukkit fired {@code PlayerFishEvent} <i>before</i> performing the pull, so
 * mcMMO's shake ran first there too. The hooked entity is read back through the public
 * {@code getHookedEntity()} (still set at this point; only {@code remove} clears it), which keeps the
 * mixin free of local capture.
 */
@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberUseMixin {

    @ModifyArg(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/advancement/criterion/FishingRodHookedCriterion;trigger("
                            + "Lnet/minecraft/server/network/ServerPlayerEntity;"
                            + "Lnet/minecraft/item/ItemStack;"
                            + "Lnet/minecraft/entity/projectile/FishingBobberEntity;"
                            + "Ljava/util/Collection;)V"),
            index = 3)
    private Collection<ItemStack> mcmmo$onFishCaught(Collection<ItemStack> caught) {
        FishingListener.onFishCaught((FishingBobberEntity) (Object) this, caught);
        return caught;
    }

    @Inject(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/projectile/FishingBobberEntity;pullHookedEntity("
                            + "Lnet/minecraft/entity/Entity;)V"),
            require = 1,
            allow = 1)
    private void mcmmo$onEntityHooked(ItemStack usedItem, CallbackInfoReturnable<Integer> cir) {
        FishingListener.onEntityHooked((FishingBobberEntity) (Object) this);
    }

    /**
     * The Treasure Hunter vanilla-XP-boost seam (legacy's {@code event.setExpToDrop(...)} on
     * {@code PlayerFishEvent}). Vanilla builds the orb inline in {@code use}'s loot loop as
     * {@code new ExperienceOrbEntity(world, x, y + 0.5, z + 0.5, this.random.nextInt(6) + 1)}, so the
     * amount is the 5th constructor argument (index 4 of {@code (World, D, D, D, I)}) — modifying it
     * is exactly equivalent to overwriting Bukkit's {@code expToDrop} before the orb is spawned.
     *
     * <p>Bytecode-verified: that constructor is invoked exactly once in {@code use}, hence
     * {@code allow = 1} — an unconstrained injector here would silently bind to any future orb spawn
     * added to the method.
     *
     * <p>The overfishing punishment empties the loot collection, so this loop body never runs for a
     * confiscated catch and the orb is destroyed rather than boosted, as intended.
     */
    @ModifyArg(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ExperienceOrbEntity;<init>("
                            + "Lnet/minecraft/world/World;DDDI)V"),
            index = 4,
            require = 1,
            allow = 1)
    private int mcmmo$boostVanillaFishingXp(int experience) {
        return FishingListener.boostVanillaXp((FishingBobberEntity) (Object) this, experience);
    }
}
