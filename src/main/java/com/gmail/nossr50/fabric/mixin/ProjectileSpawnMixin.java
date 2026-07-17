package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.ProjectileListener;
import java.util.function.Consumer;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The projectile-launch hook: mcMMO's replacement for Bukkit's {@code ProjectileLaunchEvent}, which
 * legacy used to mark arrows for Archery's Arrow Retrieval (see {@code EntityListener#onProjectileLaunch}).
 *
 * <p>Targets the four-argument {@code ProjectileEntity#spawn} static, which is vanilla's single
 * projectile-spawn funnel — bytecode-verified: the three-argument {@code spawn} and all three
 * {@code spawnWithVelocity} overloads delegate to it, and {@code RangedWeaponItem#shootAll} (the
 * shared bow/crossbow firing path) calls it once per arrow. That makes it the faithful analogue of
 * Bukkit's event, which likewise fired for every projectile from every source; the listener does the
 * narrowing (a player-owned {@code ArrowEntity}), exactly as legacy's handler did.
 *
 * <p>Injected at {@code TAIL} rather than {@code HEAD}: {@code spawn} runs the caller's
 * {@code Consumer} (which is what applies the shot's velocity) and then {@code world.spawnEntity}
 * before returning, so only at the tail is the projectile both fully initialised and actually in the
 * world — the point at which Bukkit fired its event.
 *
 * <p>The {@link ItemStack} parameter here is the <em>projectile</em> stack (the arrow item), not the
 * bow: {@code shootAll} passes the ammo stack. The firing weapon is read off the arrow itself via
 * {@code PersistentProjectileEntity#getWeaponStack()} in the listener.
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileSpawnMixin {

    @Inject(
            method = "spawn(Lnet/minecraft/entity/projectile/ProjectileEntity;"
                    + "Lnet/minecraft/server/world/ServerWorld;"
                    + "Lnet/minecraft/item/ItemStack;"
                    + "Ljava/util/function/Consumer;)"
                    + "Lnet/minecraft/entity/projectile/ProjectileEntity;",
            at = @At("TAIL"))
    private static void mcmmo$onProjectileSpawn(ProjectileEntity projectile, ServerWorld world,
            ItemStack projectileStack, Consumer<ProjectileEntity> beforeSpawn,
            CallbackInfoReturnable<ProjectileEntity> cir) {
        ProjectileListener.onProjectileSpawn(projectile, world);
    }
}
