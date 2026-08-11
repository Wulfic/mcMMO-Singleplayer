package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.platform.MobOrigins;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.conversion.EntityConversionContext;
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
 * <h2>The four-argument overload is the funnel</h2>
 * {@code MobEntity} declares two {@code convertTo} methods and the three-argument one is a one-line
 * delegate — it pushes {@code SpawnReason.CONVERSION} and calls the four-argument one (bytecode
 * verified: {@code getstatic SpawnReason.CONVERSION} then {@code invokevirtual convertTo}). Injecting
 * into the four-argument overload therefore covers both, and injecting into both would double-write.
 *
 * <p>{@code RETURN} rather than {@code TAIL} because the method has a single exit but can return
 * {@code null} on a failed conversion; {@link MobOrigins#carryThroughConversion} handles that.
 */
@Mixin(MobEntity.class)
public abstract class MobConversionOriginMixin {

    @Inject(
            method = "convertTo(Lnet/minecraft/entity/EntityType;"
                    + "Lnet/minecraft/entity/conversion/EntityConversionContext;"
                    + "Lnet/minecraft/entity/SpawnReason;"
                    + "Lnet/minecraft/entity/conversion/EntityConversionContext$Finalizer;)"
                    + "Lnet/minecraft/entity/mob/MobEntity;",
            at = @At("RETURN"))
    private void mcmmo$carryOriginThroughConversion(EntityType<?> type,
            EntityConversionContext context, SpawnReason reason,
            EntityConversionContext.Finalizer<?> finalizer,
            CallbackInfoReturnable<MobEntity> cir) {
        MobOrigins.carryThroughConversion((MobEntity) (Object) this, cir.getReturnValue());
    }
}
