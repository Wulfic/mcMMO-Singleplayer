package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import net.minecraft.advancements.triggers.BredAnimalsTrigger;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Husbandry's breeding-XP hook (Pass 2 stage 1): the one point in vanilla where "player P bred
 * animals A and B, producing C" exists as a single call.
 *
 * <h2>⚠️ Why an advancement criterion and not {@code AnimalEntity#breed}</h2>
 * The plan named {@code AnimalEntity#breed(ServerWorld, AnimalEntity, PassiveEntity)} as the
 * universal funnel. It is not. {@code FoxEntity$MateGoal#breed()} and
 * {@code TurtleEntity$MateGoal#breed()} re-implement the entire breeding sequence inline — child
 * creation, loving-player resolution, breeding-age reset, love reset, XP orb — and never call
 * {@code AnimalEntity.breed} in either overload. A hook there would have paid <b>zero</b> for foxes
 * and turtles, both of which {@code experience.yml} prices, and nothing would have reported it.
 *
 * <p>{@code Criteria.BRED_ANIMALS.trigger} is the only thing all three paths share — verified as the
 * sole reference to {@code BredAnimalsCriterion} anywhere in {@code net.minecraft.entity}. It also
 * arrives pre-gated on a real {@code ServerPlayerEntity} (vanilla resolves the loving player from
 * either parent before calling), so AI-driven breeding needs no filter of ours, and it fires once
 * per breeding rather than once per parent.
 *
 * <h2>The descriptor is load-bearing</h2>
 * {@code BredAnimalsCriterion} inherits {@code trigger(ServerPlayerEntity, Predicate)} from
 * {@code AbstractCriterion}, so the target is spelled out in full. A bare {@code "trigger"} would be
 * ambiguous.
 */
@Mixin(BredAnimalsTrigger.class)
public abstract class BredAnimalsCriterionMixin {

    @Inject(
            method = "trigger(Lnet/minecraft/server/level/ServerPlayer;"
                    + "Lnet/minecraft/world/entity/animal/Animal;"
                    + "Lnet/minecraft/world/entity/animal/Animal;"
                    + "Lnet/minecraft/world/entity/AgeableMob;)V", allow = 1,
            at = @At("HEAD"))
    private void mcmmo$onAnimalsBred(ServerPlayer breeder, Animal parent,
            Animal mate, AgeableMob child, CallbackInfo ci) {
        // child is null for the egg-laying breeders (frog, sniffer, turtle) — the listener pays the
        // breeding regardless and skips only Twins, which needs a baby to copy.
        HusbandryListener.onAnimalsBred(breeder, parent, mate, child);
    }
}
