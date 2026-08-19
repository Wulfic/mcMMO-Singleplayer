package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.ParticleEffectUtils;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes mcMMO's celebratory fireworks harmless.
 *
 * <p><b>The defect this exists to prevent.</b> A firework rocket is not a cosmetic entity.
 * {@code FireworkRocketEntity#explode} deals {@code 5.0f + 2 × explosions.size()} damage to the
 * rocket's shooter and, via a five-block sweep of {@code getNonSpectatingEntities}, to every other
 * {@code LivingEntity} in range with line of sight. mcMMO spawns its fireworks <em>at the player's
 * feet</em>, so without this the mod would deal seven damage to a player for the crime of reaching
 * level 100 — and would also splash any pet, villager or passive mob standing next to them.
 *
 * <p><b>Why HEAD of {@code explode} is the right seam, and the only one.</b> The detonation is two
 * halves in {@code explodeAndRemove}:
 *
 * <pre>
 *   sendEntityStatus(this, 17)   // the burst -- client-side visual + sound
 *   emitGameEvent(EXPLODE, ...)  // vibration
 *   explode(...)                 // 100% damage, nothing else
 *   discard()
 * </pre>
 *
 * <p>⚠⚠ <b>{@code explode}'s parameter list is NOT stable across supported versions</b> — it has
 * been spelled both {@code explode()} and {@code explode(ServerWorld)}. The {@code method} selector
 * matches on name and so binds either way, but an {@code @Inject} handler must mirror the target's
 * own parameters exactly, and a mismatch is an {@code InvalidInjectionException} at class-load.
 * Declaring only the {@link CallbackInfo} is correct wherever the target takes no arguments; this
 * hook needs neither the world nor any argument, so it asks for nothing it does not use.
 *
 * <p>🔑 {@code scripts/mixin-allow-audit.py} <b>cannot catch that</b>: it resolves the injection
 * point and counts sites, and this injector sat in an {@code OK ... computed=1} row while failing to
 * apply. {@code MixinApplicationTest} is the gate that sees it, which is why this class is on its
 * list.
 *
 * <p>The visual has already been sent to the client by the time {@code explode} is entered, so
 * cancelling {@code explode} outright keeps the whole firework show and removes only the harm. An
 * injector on {@code explodeAndRemove} or {@code tick} would have had to suppress the burst too, and
 * a {@code @Redirect} of the damage calls would need to catch two separate call sites (the shooter,
 * then the swept entities) that vanilla is free to refactor into one.
 *
 * <p><b>Scope.</b> Only rockets carrying {@link ParticleEffectUtils#COSMETIC_FIREWORK_KEY} are
 * touched. A firework the player crafts, launches from a crossbow, or fires from a dispenser is
 * untagged and detonates with full vanilla damage. The flag is dropped as it fires — the rocket is
 * discarded on the same tick and {@link MetadataStore} is a strong map, so leaving it would leak one
 * entry per firework for the life of the server.
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {

    @Inject(method = "explode", allow = 1, at = @At("HEAD"), cancellable = true)
    private void mcmmo$skipCosmeticFireworkDamage(CallbackInfo ci) {
        final FireworkRocketEntity self = (FireworkRocketEntity) (Object) this;
        if (MetadataStore.has(self, ParticleEffectUtils.COSMETIC_FIREWORK_KEY)) {
            MetadataStore.clear(self);
            ci.cancel();
        }
    }
}
