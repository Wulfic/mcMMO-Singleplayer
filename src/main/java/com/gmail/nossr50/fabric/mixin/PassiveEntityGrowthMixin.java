package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import net.minecraft.entity.passive.PassiveEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Husbandry's growth hooks (Pass 2 stage 2): the <b>raise</b> verb, the <b>feed</b> verb, and
 * {@code Accelerated Growth}'s double-feed roll.
 *
 * <p>Both injections sit on {@code PassiveEntity} rather than on the methods a player actually
 * touches, and in both cases that is the point — every species reaches these two methods, and the
 * per-species entry points do not agree on anything.
 *
 * <h2>⚠️ Why {@code setBreedingAge} and not {@code onGrowUp}</h2>
 * The plan named {@code PassiveEntity#onGrowUp()} as the raise seam, and warned that it fires on
 * both age transitions and on every chunk load of every baby (because {@code readCustomData} routes
 * through {@code setBreedingAge}). All true — but bytecode turned up a fourth problem the plan
 * missed, and it is the one that would have shipped: <b>{@code HoglinEntity#onGrowUp()} and
 * {@code GoatEntity#onGrowUp()} never call {@code super}.</b> ({@code HappyGhastEntity},
 * {@code TurtleEntity} and {@code VillagerEntity} do.) An injection on {@code onGrowUp} would
 * therefore have paid <b>exactly zero</b> raise XP for goats and hoglins — priced at 400 and 900 in
 * {@code experience.yml} — while passing every test written with a cow.
 *
 * <p>{@code setBreedingAge(int)} is declared only on {@code PassiveEntity}, nothing overrides it,
 * and it is where the {@code onGrowUp()} call itself lives. Every path to growing up goes through
 * it. The listener decides what counts as growing up; this class only reports the change.
 *
 * <h2>⚠️ Why {@code growUp} carries the feed verb, and why it needs the interaction stash</h2>
 * Feeding a baby is spread across six methods — {@code AnimalEntity#interactMob},
 * {@code DolphinEntity#interactMob}, {@code PandaEntity#interactMob}, and {@code receiveFood} on
 * {@code AbstractHorseEntity}, {@code CamelEntity} and {@code LlamaEntity} — whose only shared
 * callee is {@code growUp}. Note which four species those are: the same overriding four that already
 * moved Multi-Breed off {@code interactMob} in stage 1.
 *
 * <p>But {@code growUp} is a growth funnel, not a feeding one. {@code SheepEntity#onEatingGrass}
 * calls it, and so does {@code TadpoleEntity} while ageing itself — so hooking it unconditionally
 * would pay a player for a lamb standing in a field, which is the AFK farm this skill's plan spends
 * a page warning about. Hence the stash: {@code PlayerEntityInteractMixin} publishes who is
 * interacting with what, and the listener only treats growth as a feed when a player is
 * mid-interaction with this very animal.
 *
 * <p>⚠️ The boolean parameter is <b>not</b> a player/AI discriminator, despite looking like one:
 * horse, camel and llama all feed through the one-argument {@code growUp(int)}, which passes
 * {@code false} — the same value {@code SheepEntity#onEatingGrass} uses.
 */
@Mixin(PassiveEntity.class)
public abstract class PassiveEntityGrowthMixin {

    /**
     * Read at the head of {@code setBreedingAge} to recover the age being replaced.
     *
     * <p>Shadowed rather than read back through {@code getBreedingAge()} so the value is exactly
     * what vanilla is about to overwrite, with no dependence on that getter staying unoverridden.
     */
    @Shadow
    protected int breedingAge;

    /**
     * Report every breeding-age change; the listener picks out the baby→adult crossing.
     *
     * <p>This runs for babies ageing up and for adults counting down their post-breeding cooldown,
     * so a handful of entities per tick at most — vanilla skips the call entirely once the age
     * settles at zero, which is where every idle adult sits.
     */
    @Inject(method = "setBreedingAge(I)V", at = @At("HEAD"))
    private void mcmmo$onBreedingAgeChange(int newAge, CallbackInfo ci) {
        HusbandryListener.onBreedingAgeChange((PassiveEntity) (Object) this, this.breedingAge,
                newAge);
    }

    /**
     * Pay the feed verb, and let {@code Accelerated Growth} double the growth this feed grants.
     *
     * <p>{@code argsOnly} with {@code ordinal = 0} targets the {@code int seconds} parameter itself
     * rather than a call site, which is what makes this cover all six feeding paths — including the
     * three that arrive via the one-argument overload — from a single injection.
     */
    @ModifyVariable(method = "growUp(IZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int mcmmo$onGrowthApplied(int growthSeconds) {
        return HusbandryListener.onGrowthApplied((PassiveEntity) (Object) this, growthSeconds);
    }
}
