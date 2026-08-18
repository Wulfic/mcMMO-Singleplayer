package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.GlideListener;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Agility's air domain: <b>Fleet Footed (air)</b> and <b>Glide</b>, applied to elytra flight.
 *
 * <p>Elytra flight is the one movement domain with no attribute behind it — bytecode-verified,
 * {@code LivingEntity#travelGliding} computes a velocity via {@code calcGlidingVelocity} and hands it
 * straight to {@code setVelocity}/{@code move}, consulting no {@code EntityAttribute} at all. So
 * unlike the land and water bodies there is nothing for
 * {@link com.gmail.nossr50.platform.SkillAttributeService} to manage, and the bonus has to be
 * written into the velocity itself.
 *
 * <p><b>Why a mixin rather than writing velocity from the tick sweep.</b> The obvious approach —
 * nudge {@code player.setVelocity(...)} once per tick in
 * {@link com.gmail.nossr50.fabric.listeners.PlayerMovementTracker} — does not work for a player's
 * own client. {@code EntityTrackerEntry} pushes velocity through
 * {@code TrackerPacketSender#sendToListeners}, and for a player entity the "listeners" are the
 * <em>other</em> nearby players; the moving player never receives their own velocity update
 * (bytecode-verified). Server-side velocity writes would therefore be silently overwritten by the
 * client's own flight simulation. Injecting into the shared movement code instead means the client
 * and the integrated server compute the identical velocity, so there is nothing to sync and nothing
 * to rubber-band.
 *
 * <p>{@code calcGlidingVelocity}'s return value is the exact seam: it is the only call to that method
 * in {@code travelGliding}, and its result is what becomes the tick's velocity.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityGlideMixin {

    /**
     * Scale the glide velocity vanilla just computed by the player's Agility bonuses — faster
     * forward (Fleet Footed air), slower downward (Glide).
     */
    @ModifyExpressionValue(
            method = "travelGliding",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;calcGlidingVelocity"
                            + "(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;"),
            // One call site in this method; cap it so a future remap that widens the match is a
            // build failure rather than a silent second injection.
            allow = 1)
    private Vec3d mcmmo$applyGlideBonus(Vec3d glideVelocity) {
        return GlideListener.modifyGlideVelocity((LivingEntity) (Object) this, glideVelocity);
    }
}
