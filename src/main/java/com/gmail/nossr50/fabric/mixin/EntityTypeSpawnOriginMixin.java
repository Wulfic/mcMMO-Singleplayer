package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.platform.MobOrigins;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
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
 * it. On this band that is the {@code EntitySpawnReason} overload: it is the only one that calls the
 * entity factory ({@code factory.create(type, level)}), and the six-argument overload delegates
 * straight into it. The generic {@code T} erases to {@code Entity}.
 *
 * <p>⚠️ Which overload sits at the bottom is NOT a fixed fact about {@code EntityType} — it has
 * already moved once, when a newer Minecraft introduced an {@code EntitySpawnRequest} wrapper and
 * pushed the bottom onto that. Re-read the chain per band; see the warning below.
 *
 * <p>⚠️ <b>Read that chain off the bytecode before moving this injector, in either direction.</b>
 * Which overload sits at the bottom is a per-band fact and it has already moved once. On this band
 * there is no {@code EntitySpawnRequest} type at all, so {@code create(Level, EntitySpawnReason)}
 * <em>is</em> the bottom: verified in the merged jar, it calls {@code factory.create(type, level)}
 * directly and returns from two places (the disabled-feature-flag {@code null} and the factory
 * result), which is what {@code allow = 2} expects. The six-argument overload delegates into it, so
 * nothing double-stamps.
 *
 * <p>The reason to re-read rather than reason it out: on a band where the wrapper and the bottom
 * have swapped, an injector left on the wrapper still applies, still binds, and is simply skipped by
 * every caller that goes around it. The allow-audit reports that as a count MISMATCH — one binding
 * where two were declared — which is a hint, not a diagnosis, and no test fails.
 *
 * <p>Note this fires for {@code SpawnReason.LOAD} as well, i.e. for every mob in every chunk that
 * loads. That is why {@link MobOrigins#stampOnSpawn} writes nothing for a qualifying origin: the work
 * on the hot path is one switch and one boolean, and a mob whose marker is about to be restored from
 * NBT must not have it overwritten first.
 */
@Mixin(EntityType.class)
public abstract class EntityTypeSpawnOriginMixin {

    @Inject(
            method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)"
                    + "Lnet/minecraft/world/entity/Entity;", allow = 2,
            at = @At("RETURN"))
    private void mcmmo$stampSpawnOrigin(Level world, EntitySpawnReason reason,
            CallbackInfoReturnable<Entity> cir) {
        // Two returns, both stamped: the early null when a spawn check refuses, and the factory
        // result. The return value is also null when the entity type sits behind a disabled feature
        // flag; stampOnSpawn handles that, along with the client-side and non-living cases.
        MobOrigins.stampOnSpawn(world, reason, cir.getReturnValue());
    }
}
