package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.platform.MobOrigins;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.level.Level;
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
 * {@code EntityType} declares several {@code create} overloads and they form a chain, so injecting
 * into more than one would double-stamp. The full descriptor picks the single one at the bottom of
 * it: the {@code EntitySpawnRequest} overload is the only one that actually calls the entity factory
 * ({@code factory.create(type, level)}); the {@code EntitySpawnReason} overload wraps its argument in
 * {@code new EntitySpawnRequest(reason, false)} and delegates, and the six-argument overload calls
 * <em>that</em>. The generic {@code T} erases to {@code Entity}.
 *
 * <p>⚠️ <b>Read that chain off the bytecode before moving this injector, in either direction.</b>
 * The {@code EntitySpawnReason} overload was the bottom when this was written and is a pass-through
 * now, and the change is silent: an injector left on the wrapper still applies, still binds, and is
 * simply skipped by every caller that builds its own {@code EntitySpawnRequest}. The allow-audit
 * reported that as a count MISMATCH — one binding where two were declared — which is a hint, not a
 * diagnosis, and no test would have failed.
 *
 * <p>Note this fires for {@code SpawnReason.LOAD} as well, i.e. for every mob in every chunk that
 * loads. That is why {@link MobOrigins#stampOnSpawn} writes nothing for a qualifying origin: the work
 * on the hot path is one switch and one boolean, and a mob whose marker is about to be restored from
 * NBT must not have it overwritten first.
 */
@Mixin(EntityType.class)
public abstract class EntityTypeSpawnOriginMixin {

    @Inject(
            method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnRequest;)"
                    + "Lnet/minecraft/world/entity/Entity;", allow = 2,
            at = @At("RETURN"))
    private void mcmmo$stampSpawnOrigin(Level world, EntitySpawnRequest request,
            CallbackInfoReturnable<Entity> cir) {
        // Two returns, both stamped: the early null when a spawn check refuses, and the factory
        // result. The return value is also null when the entity type sits behind a disabled feature
        // flag; stampOnSpawn handles that, along with the client-side and non-living cases.
        MobOrigins.stampOnSpawn(world, request.reason(), cir.getReturnValue());
    }
}
