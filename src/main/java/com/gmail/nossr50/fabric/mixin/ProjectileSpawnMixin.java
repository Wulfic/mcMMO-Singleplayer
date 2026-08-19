package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.ProjectileListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Archery's launch mark: stamps a player-fired arrow with where it came from and how hard the bow was
 * drawn, so {@code ArcheryManager} can pay distance and force XP when it lands. Vanilla fires no
 * shoot event, so this rides the projectile's own ownership assignment.
 *
 * <h2>The seam: {@code ProjectileEntity#setOwner(Entity)}</h2>
 * The mod has also used a static {@code ProjectileEntity#spawn(…)} funnel where one exists. It does
 * not on every supported version, and {@code setOwner} is the seam that does — bytecode-verified
 * against the merged jar:
 *
 * <ul>
 *   <li>It is the <b>only</b> way a projectile acquires an owner at launch. The owner-taking
 *       {@code PersistentProjectileEntity(EntityType, LivingEntity, World, …)} constructor positions
 *       the arrow from the shooter and then calls {@code setOwner} — so position and UUID are both
 *       live by the time this runs, which is all {@link ProjectileListener#onProjectileSpawn} reads.</li>
 *   <li>🔑 <b>NBT load does not come through here.</b> {@code readCustomDataFromNbt} assigns the
 *       {@code owner} and {@code ownerUuid} <em>fields directly</em>, never calling this method, so a
 *       chunk reload cannot re-mark arrows that were already fired. That property is what makes this
 *       seam safe rather than merely convenient, and it is worth re-checking per band.</li>
 * </ul>
 *
 * <p>It is declared on {@code ProjectileEntity} itself, so no projectile subclass can dodge it by
 * failing to call {@code super} — the same property the mob-origin stamp is chosen for.
 *
 * <p>The narrowing to a player-owned {@code ArrowEntity} is deliberately left to
 * {@link ProjectileListener#onProjectileSpawn}, which is where legacy's
 * {@code getShooter() instanceof Player} check lived; this side stays a plain funnel.
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileSpawnMixin {

    @Inject(method = "setOwner(Lnet/minecraft/entity/Entity;)V", allow = 1, at = @At("TAIL"))
    private void mcmmo$onProjectileSpawn(Entity owner, CallbackInfo ci) {
        final ProjectileEntity projectile = (ProjectileEntity) (Object) this;
        // Client-side projectiles run this too; Archery's marks are server state only.
        if (!(projectile.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        ProjectileListener.onProjectileSpawn(projectile, serverWorld);
    }
}
