package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.platform.MobOrigins;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.command.SummonCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hunter's anti-farm gate, {@code /summon} half — the third sibling of
 * {@code MobSpawnerOriginMixin} and {@code TrialSpawnerOriginMixin}, needed for exactly the reason
 * they are: this call site reaches
 * {@code EntityType.loadEntityWithPassengers(NbtCompound, World, Function)}, whose signature carries
 * no {@code SpawnReason}, so the reason is supplied here by the caller that knows it.
 *
 * <h2>&#9888; Why {@code EntityTypeSpawnOriginMixin} does not already cover this</h2>
 * That injector targets {@code EntityType#create(ServerWorld, Consumer, BlockPos, SpawnReason, ZZ)},
 * the factory every <em>reason-carrying</em> spawn bottoms out in. A spawn egg does reach it —
 * verified in bytecode as {@code SpawnEggItem} → {@code EntityType.spawnFromItemStack} →
 * {@code spawn(…SpawnReason,ZZ)} → that {@code create} — and so do dispensers and portals.
 * {@code /summon} does not: it rebuilds the entity from NBT instead, and on the Minecraft versions
 * where {@code loadEntityWithPassengers} takes no {@code SpawnReason} there is nothing on its path
 * left to read a reason from. The gap is silent — the mob simply spawns unmarked and counts toward
 * mob mastery — which is why a live kill in {@code gameplay-smoke.sh} found it and neither the
 * mixin-allow audit nor {@code MixinApplicationTest} could.
 *
 * <p>{@code COMMAND} maps to {@link com.gmail.nossr50.datatypes.mobs.MobOrigin#PLAYER_PLACED}, which
 * {@link MobOrigins} already documents as deliberate: a {@code /summon} is the same cheese as using
 * a spawn egg by hand. This mixin is what makes that mapping true on this band rather than merely
 * declared.
 *
 * <p>The returned entity is null when the NBT names no valid type; {@link MobOrigins#stampOnSpawn}
 * takes the null case, and vanilla throws immediately afterwards regardless.
 */
@Mixin(SummonCommand.class)
public abstract class SummonCommandOriginMixin {

    @ModifyExpressionValue(
            method = "summon(Lnet/minecraft/server/command/ServerCommandSource;"
                    + "Lnet/minecraft/registry/entry/RegistryEntry$Reference;"
                    + "Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/nbt/NbtCompound;Z)"
                    + "Lnet/minecraft/entity/Entity;", allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityType;loadEntityWithPassengers("
                            + "Lnet/minecraft/nbt/NbtCompound;Lnet/minecraft/world/World;"
                            + "Ljava/util/function/Function;)Lnet/minecraft/entity/Entity;"))
    private static Entity mcmmo$stampSummonOrigin(Entity summoned) {
        MobOrigins.stampOnSpawn(summoned, SpawnReason.COMMAND);
        return summoned;
    }
}
