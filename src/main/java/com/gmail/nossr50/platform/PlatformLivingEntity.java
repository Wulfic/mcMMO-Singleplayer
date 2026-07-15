package com.gmail.nossr50.platform;

import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter over a vanilla {@link LivingEntity}, replacing {@code org.bukkit.entity.LivingEntity}
 * (the mob combat targets mcMMO cares about: {@code target}/{@code defender}/{@code attacker},
 * ~50 refs). {@link PlatformPlayer} is the player-specific counterpart.
 *
 * <p>Grounded in mined usage: {@code getType}, {@code isValid}, {@code getLocation},
 * {@code getHealth}, {@code getWorld}, {@code getUniqueId}, custom-name mutation. Uses
 * {@link #unwrap()} for anything beyond this surface.
 *
 * <p>Deferred cross-cutting concern: Bukkit entity metadata ({@code get/set/has/removeMetadata},
 * the single largest slice of entity usage, ~50 refs) has no vanilla equivalent and is designed
 * separately (transient side-table keyed by entity, or Fabric data-attachment) — see memory
 * {@code phase-2-adapter-layer}. Equipment access needs the ItemStack adapter.
 */
public final class PlatformLivingEntity {

    private final LivingEntity handle;

    public PlatformLivingEntity(@NotNull LivingEntity handle) {
        this.handle = handle;
    }

    public @NotNull LivingEntity unwrap() {
        return handle;
    }

    // --- Type identity (Bukkit getType() -> EntityType comparisons) ----------

    public @NotNull EntityType<?> getType() {
        return handle.getType();
    }

    /** Registry id of the entity type, for name-based comparisons (e.g. {@code minecraft:zombie}). */
    public @NotNull Identifier getTypeId() {
        return Registries.ENTITY_TYPE.getId(handle.getType());
    }

    // --- State --------------------------------------------------------------

    /** Bukkit {@code isValid()}: alive and present in the world. */
    public boolean isValid() {
        return handle.isAlive();
    }

    public float getHealth() {
        return handle.getHealth();
    }

    /**
     * Write health directly, bypassing the damage pipeline. This is Bukkit {@code setHealth}'s role
     * in mcMMO: Rupture's damage-over-time is "pure" (see {@code advanced.yml}) and must not trigger
     * knockback, invulnerability frames, hurt animation or death attribution the way
     * {@link LivingEntity#damage} would.
     */
    public void setHealth(float health) {
        handle.setHealth(health);
    }

    public float getMaxHealth() {
        return handle.getMaxHealth();
    }

    public @NotNull UUID getUniqueId() {
        return handle.getUuid();
    }

    // --- World / position (Bukkit getLocation/getWorld) ---------------------

    public @NotNull World getWorld() {
        return handle.getEntityWorld();
    }

    public @NotNull BlockPos getBlockPos() {
        return handle.getBlockPos();
    }

    public @NotNull Vec3d getPos() {
        return handle.getEntityPos();
    }

    // --- Custom name --------------------------------------------------------

    public @Nullable Text getCustomName() {
        return handle.getCustomName();
    }

    public void setCustomName(@Nullable Text name) {
        handle.setCustomName(name);
    }

    public void setCustomNameVisible(boolean visible) {
        handle.setCustomNameVisible(visible);
    }
}
