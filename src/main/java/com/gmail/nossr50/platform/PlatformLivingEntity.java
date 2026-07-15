package com.gmail.nossr50.platform;

import com.gmail.nossr50.util.ItemUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
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
 * {@code phase-2-adapter-layer}.
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

    // --- Equipment (Bukkit EntityEquipment#getArmorContents) ----------------

    /**
     * The entity's worn armor, as mcMMO counts it: the stacks in the four humanoid armor slots that
     * {@link ItemUtils#isArmor} recognises. Ports Bukkit's
     * {@code getEquipment().getArmorContents()} filtered by the {@code armor != null &&
     * ItemUtils.isArmor(armor)} test every legacy caller wraps it in ({@code Axes.hasArmor},
     * {@code AxesManager#impactCheck}).
     *
     * <p>Restricted to {@link EquipmentSlot.Type#HUMANOID_ARMOR} (helmet/chestplate/leggings/boots)
     * because that is exactly what {@code getArmorContents()} returned; the modern {@code BODY} and
     * {@code SADDLE} slots (wolf/horse armor) were not part of that array and their items are not in
     * the {@code MaterialMapStore} armor set either, so including them would both deviate and be inert.
     *
     * <p><b>These wrap the entity's live stacks, not copies</b> — a {@link PlatformItem#setDurability}
     * on a returned piece damages the armor the entity is actually wearing, which is what Armor Impact
     * needs. (Bukkit's {@code getArmorContents()} copy-vs-mirror semantics vary by entity type; going
     * through the live stack here makes the behaviour explicit rather than incidental.)
     *
     * @return the worn armor pieces, empty if the entity wears none or cannot hold equipment
     */
    public @NotNull List<PlatformItem> getArmorPieces() {
        final List<PlatformItem> pieces = new ArrayList<>(4);
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            final ItemStack stack = handle.getEquippedStack(slot);
            if (!stack.isEmpty() && ItemUtils.isArmor(stack)) {
                pieces.add(new PlatformItem(stack));
            }
        }
        return pieces;
    }

    // --- Velocity -----------------------------------------------------------

    /**
     * Fling this entity along {@code source}'s look direction, scaled by {@code multiplier}. Ports
     * Bukkit's {@code target.setVelocity(player.getLocation().getDirection().normalize()
     * .multiply(m))} — Axes' Greater Impact knockback.
     *
     * <p>Unlike {@code LivingEntity#takeKnockback} this overwrites the velocity outright and ignores
     * knockback resistance, matching what {@code setVelocity} did. {@code velocityDirty} is raised so
     * the change is sent to clients this tick rather than only surfacing as position drift.
     */
    public void setVelocityAlongLookDirection(@NotNull PlatformPlayer source, double multiplier) {
        handle.setVelocity(source.unwrap().getRotationVector().normalize().multiply(multiplier));
        handle.velocityDirty = true;
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
