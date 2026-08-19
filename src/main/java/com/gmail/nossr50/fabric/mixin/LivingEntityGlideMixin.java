package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.GlideListener;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Agility's air-domain hook — <b>Fleet Footed (air)</b> and <b>Glide</b>. The maths lives in
 * {@link GlideListener}; this file is only the seam.
 *
 * <h2>The seam: the tail of {@code travel}</h2>
 * Where vanilla exposes a discrete gliding-velocity helper, this hook rides that helper's return
 * value. Where it does not — the gliding maths is inlined into {@code travel} itself, with no
 * intermediate call to intercept — the equivalent seam is the <b>end of {@code travel}</b>, gated on
 * {@code isFallFlying()}, rewriting the velocity vanilla just stored.
 *
 * <p>🔑 <b>Bytecode-verified that {@code travel} has exactly one {@code return}</b> and that every
 * branch, the gliding one included, converges on it. That is what makes {@code TAIL} reachable for a
 * glider rather than silently dead, and it is the thing to re-check per band — a gliding branch that
 * returned early would leave this injector bound, green, and never firing.
 *
 * <p>⚠️ <b>Deliberately not a {@code @Slice}.</b> Narrowing to the gliding region of {@code travel}
 * would place the change nearer vanilla's own computation, but an unresolvable {@code @Slice} is
 * <em>silently dropped and the injector still applies</em> — it would widen to the whole method
 * instead of failing, which is the one outcome this hook cannot survive.
 *
 * <h2>⚠️ One tick of latency, and why the steady state still matches</h2>
 * Riding the helper's return value modifies the velocity <em>before</em> {@code travel}'s own
 * {@code move(…)} consumes it; injecting at the tail modifies it <em>after</em>. So the first gliding
 * tick moves the player by vanilla's figure and the bonus lands from the following tick on. Beyond
 * that the two are equivalent: both forms write the stored velocity that the next tick's gliding
 * maths starts from, so the bonus compounds identically either way.
 *
 * <p>Runs on both logical sides, exactly as {@link GlideListener} documents — the client simulates
 * its own flight, and applying the same factor on both is what makes the boost visible without a
 * per-tick velocity packet.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityGlideMixin {

    @Inject(method = "travel(Lnet/minecraft/util/math/Vec3d;)V", allow = 1, at = @At("TAIL"))
    private void mcmmo$applyGlideBonus(Vec3d movementInput, CallbackInfo ci) {
        final LivingEntity self = (LivingEntity) (Object) this;
        if (!self.isFallFlying()) {
            return;
        }
        final Vec3d vanilla = self.getVelocity();
        final Vec3d boosted = GlideListener.modifyGlideVelocity(self, vanilla);
        // modifyGlideVelocity returns the argument itself when nothing applies — the common case.
        if (boosted != vanilla) {
            self.setVelocity(boosted);
        }
    }
}
