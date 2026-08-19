package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.platform.MobOrigins;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.AnimalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Hunter's anti-farm gate, breeding half: marks the child of a bred pair so a passive-mob farm cannot
 * advance mob mastery.
 *
 * <h2>&#128273; Why breeding needs its own injector</h2>
 * Where vanilla funnels creation through one factory that carries a {@code SpawnReason}, roughly forty
 * {@code createChild} implementations reach it with {@code SpawnReason.BREEDING} and
 * {@code EntityTypeSpawnOriginMixin} covers them all. <b>Where it does not</b>, {@code createChild}
 * reaches {@code EntityType.create(World)} — no reason, and no shared marker.
 *
 * <p>{@code AnimalEntity#breed} is the seam that survives that difference: every vanilla breed goes
 * through it, it is declared once on {@code AnimalEntity}, and it holds the finished child at the
 * moment it hands it to the world. Injecting on the <em>spawn</em> rather than on {@code createChild}
 * also means a subclass overriding {@code createChild} cannot dodge the mark.
 *
 * <p>{@code @ModifyArg} on {@code spawnEntityAndPassengers}' only argument: the child is passed
 * straight back unchanged, so this adds a marker and changes nothing about the spawn itself.
 */
@Mixin(AnimalEntity.class)
public abstract class AnimalBreedOriginMixin {

    @ModifyArg(
            method = "breed(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/entity/passive/AnimalEntity;)V", allow = 1, index = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;spawnEntityAndPassengers("
                            + "Lnet/minecraft/entity/Entity;)V"))
    private Entity mcmmo$stampBredOrigin(Entity child) {
        MobOrigins.stampOnSpawn(child, SpawnReason.BREEDING);
        return child;
    }
}
