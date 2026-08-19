package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.platform.MobOrigins;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.block.spawner.MobSpawnerLogic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hunter's anti-farm gate, spawner half: marks every mob a monster spawner produces so it cannot
 * advance mob mastery.
 *
 * <h2>&#128273; Why this is a separate injector rather than part of the creation funnel</h2>
 * Where vanilla routes every entity creation through one factory that carries a {@code SpawnReason},
 * {@code EntityTypeSpawnOriginMixin} covers spawners too and this file is not needed. <b>Where it does
 * not, the spawner path carries no reason at all</b>: {@code serverTick} reaches
 * {@code EntityType.loadEntityWithPassengers(NbtCompound, World, Function)}, whose signature has no
 * {@code SpawnReason} — the {@code SpawnReason.SPAWNER} the method does hold goes only to
 * {@code canSpawn} and {@code initialize}, never to the entity being built.
 *
 * <p>So the reason is supplied here, from the call site that knows it. See {@link MobOrigins} for why
 * a single injector on the reason-carrying {@code create} overload is a trap on those versions: it
 * binds, the audit goes green, and spawner mobs are silently left counting.
 *
 * <h2>&#9888; Scoped to {@code serverTick}, and that scope is load-bearing</h2>
 * {@code MobSpawnerLogic} calls {@code loadEntityWithPassengers} in <b>two</b> methods — this one, and
 * {@code getRenderedEntity}, which builds the little spinning mob rendered inside the spawner block.
 * Marking that one would be harmless but meaningless; more importantly, widening the target to the
 * class would make {@code allow = 1} wrong and the count is the only thing guarding against exactly
 * that kind of drift.
 *
 * <p>{@code @ModifyExpressionValue} rather than {@code @Inject} because the freshly built entity is
 * the call's <em>return value</em>; it is handed straight back unchanged, so this never alters whether
 * vanilla thinks the spawn succeeded. A {@code null} return (a mob type behind a disabled feature
 * flag) is handled by {@link MobOrigins#stampOnSpawn(Entity, SpawnReason)}.
 */
@Mixin(MobSpawnerLogic.class)
public abstract class MobSpawnerOriginMixin {

    @ModifyExpressionValue(
            method = "serverTick(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/util/math/BlockPos;)V", allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityType;loadEntityWithPassengers("
                            + "Lnet/minecraft/nbt/NbtCompound;Lnet/minecraft/world/World;"
                            + "Ljava/util/function/Function;)Lnet/minecraft/entity/Entity;"))
    private Entity mcmmo$stampSpawnerOrigin(Entity spawned) {
        MobOrigins.stampOnSpawn(spawned, SpawnReason.SPAWNER);
        return spawned;
    }
}
