package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.platform.MobOrigins;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hunter's D-HU1 anti-farm gate, second half: a mob that converts into another mob takes its origin
 * with it.
 *
 * <h2>Why this is not optional</h2>
 * {@code EntityTypeSpawnOriginMixin} alone leaves one large hole, and it is a hole people build on
 * purpose. A drowned farm is a zombie spawner over a water column: the zombies are stamped
 * {@link com.gmail.nossr50.datatypes.mobs.MobOrigin#SPAWNER}, then each one drowns into a
 * <em>different entity</em> that vanilla creates fresh through
 * {@code EntityType.create(world, SpawnReason.CONVERSION)}. {@code CONVERSION} counts, so the drowned
 * would arrive unmarked and the farm would launder its own origin. The same applies to every other
 * conversion a farm can be built around — a pig struck by lightning, a villager zombified in a
 * cured-villager loop, a hoglin walked out of the nether.
 *
 * <p>Fabric's {@code copyOnDeath} does not cover this. Conversion is not death, and vanilla builds a
 * genuinely new entity rather than transferring the old one, so nothing carries the attachment across
 * on its own.
 *
 * <h2>There is one funnel at this version</h2>
 * Where conversion is routed through a context object, {@code MobEntity} declares two {@code convertTo}
 * methods and the shorter one is a delegate, so the longer one is the single funnel and injecting into
 * both would double-write. <b>At this version there is no context object and no pair:</b>
 * {@code convertTo(EntityType, boolean)} is the only declaration, so it is the funnel by default.
 *
 * <p>{@code RETURN} rather than {@code TAIL} because the method can return {@code null} on a failed
 * conversion; {@link MobOrigins#carryThroughConversion} handles that.
 *
 * <p>⚠️ {@code allow = N} is a per-version bytecode fact — one bind per return instruction, not per
 * execution. Re-measure with {@code scripts/mixin-allow-audit.py} (ship gate 2); a guard clause added
 * upstream changes the number, and the count from another version is not evidence about this one.
 */
@Mixin(MobEntity.class)
public abstract class MobConversionOriginMixin {

    @Inject(method = "convertTo(Lnet/minecraft/entity/EntityType;Z)Lnet/minecraft/entity/mob/MobEntity;",
            allow = 3, at = @At("RETURN"))
    private void mcmmo$carryOriginThroughConversion(EntityType<?> type, boolean keepEquipment,
            CallbackInfoReturnable<MobEntity> cir) {
        MobOrigins.carryThroughConversion((MobEntity) (Object) this, cir.getReturnValue());
    }
}
