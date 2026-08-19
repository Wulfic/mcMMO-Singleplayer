package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.platform.MobOrigins;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.block.spawner.TrialSpawnerLogic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hunter's anti-farm gate, trial-spawner half — the sibling of {@code MobSpawnerOriginMixin}, and
 * needed for the same reason: the trial spawner reaches
 * {@code EntityType.loadEntityWithPassengers(NbtCompound, World, Function)}, which carries no
 * {@code SpawnReason}, so the reason is supplied here from the call site that knows it.
 *
 * <p>It is a separate file rather than a second target on the monster-spawner mixin because the two
 * classes spawn from differently-named methods ({@code trySpawnMob} against {@code serverTick}); a
 * shared mixin would name a method that does not exist on one of its targets.
 *
 * <p>{@code TRIAL_SPAWNER} and {@code SPAWNER} both map to {@link MobOrigins}' spawner origin, so a
 * trial-spawner mob is excluded from mob mastery exactly as an ordinary spawner's is. Stamping the
 * more specific reason keeps the first-mark log honest about which one fired.
 */
@Mixin(TrialSpawnerLogic.class)
public abstract class TrialSpawnerOriginMixin {

    @ModifyExpressionValue(
            method = "trySpawnMob(Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/util/math/BlockPos;)Ljava/util/Optional;", allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityType;loadEntityWithPassengers("
                            + "Lnet/minecraft/nbt/NbtCompound;Lnet/minecraft/world/World;"
                            + "Ljava/util/function/Function;)Lnet/minecraft/entity/Entity;"))
    private Entity mcmmo$stampTrialSpawnerOrigin(Entity spawned) {
        MobOrigins.stampOnSpawn(spawned, SpawnReason.TRIAL_SPAWNER);
        return spawned;
    }
}
