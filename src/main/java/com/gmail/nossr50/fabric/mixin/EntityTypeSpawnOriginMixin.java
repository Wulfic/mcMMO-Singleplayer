package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.platform.MobOrigins;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import java.util.function.Consumer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hunter's D-HU1 anti-farm gate, first half: records why a mob does not count toward mob mastery, at
 * the moment it is created.
 *
 * <h2>⚠️ Why this target and not {@code MobEntity#initialize}</h2>
 * {@code initialize(ServerWorldAccess, LocalDifficulty, SpawnReason, EntityData)} is the obvious
 * choice and is what the plan implied — both spawner logics call it explicitly with their reason. It
 * would have been wrong. 57 classes override it in 1.21.11 and exactly one does not call
 * {@code super}: {@code CaveSpiderEntity}, whose entire override is
 *
 * <pre>{@code   0: aload 4
 *   2: areturn}</pre>
 *
 * a bare pass-through that skips {@code SpiderEntity}'s jockey and potion-effect logic. Every cave
 * spider in the game would therefore have escaped the gate — and a mineshaft cave-spider spawner is
 * among the most commonly built grinders there is, so the miss would have landed precisely on the case
 * the gate exists for, while passing any test written with a zombie.
 *
 * <p>{@code EntityType#create(World, SpawnReason)} is the factory that all of it bottoms out in (the
 * four verified call chains are listed in {@link MobOrigins}). It is an instance method on a class with
 * no vanilla subclasses, so nothing can override it away, and its body ignores the
 * {@code SpawnReason} it is handed — vanilla passes the reason down purely so callers further up can
 * branch on it — which makes reading it here free of behavioural risk.
 *
 * <h2>The descriptor is load-bearing</h2>
 * {@code EntityType} declares two {@code create} methods. The six-argument one delegates to this one,
 * so injecting into both would double-stamp; the full descriptor picks the single lower method. The
 * generic {@code T} erases to {@code Entity}.
 *
 * <p>Note this fires for {@code SpawnReason.LOAD} as well, i.e. for every mob in every chunk that
 * loads. That is why {@link MobOrigins#stampOnSpawn} writes nothing for a qualifying origin: the work
 * on the hot path is one switch and one boolean, and a mob whose marker is about to be restored from
 * NBT must not have it overwritten first.
 * <h2>&#9888; On this band this injector is NOT the whole gate</h2>
 * Where vanilla has a single reason-carrying factory, one injector covers every spawn. Where it does
 * not — as here — this one reaches only the paths that <em>do</em> carry a {@code SpawnReason}: spawn
 * eggs, dispensers, and nether portals. <b>Spawners and breeding do not come through it</b>, and are
 * marked by {@code MobSpawnerOriginMixin}, {@code TrialSpawnerOriginMixin} and
 * {@code AnimalBreedOriginMixin}. See {@link MobOrigins} for why relying on this one alone binds
 * cleanly and silently leaves spawner-farmed mobs counting.
 */
@Mixin(EntityType.class)
public abstract class EntityTypeSpawnOriginMixin {

    @Inject(
            method = "create(Lnet/minecraft/server/world/ServerWorld;"
                    + "Ljava/util/function/Consumer;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/entity/SpawnReason;ZZ)Lnet/minecraft/entity/Entity;", allow = 2,
            at = @At("RETURN"))
    private void mcmmo$stampSpawnOrigin(ServerWorld world, Consumer<Entity> beforeSpawn,
            BlockPos pos, SpawnReason reason, boolean alignPosition, boolean invertY,
            CallbackInfoReturnable<Entity> cir) {
        // The return value is null when the entity type sits behind a disabled feature flag;
        // stampOnSpawn handles that, along with the client-side and non-living cases.
        MobOrigins.stampOnSpawn(world, reason, cir.getReturnValue());
    }
}
