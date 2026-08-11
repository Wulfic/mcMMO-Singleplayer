package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.BoggedEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.SnowGolemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * {@code Bountiful Harvest}'s durability save (Pass 2 stage 3): a shear that costs the shears
 * nothing.
 *
 * <h2>Why this one does name the species, when {@link LivingEntityShearDropsMixin} does not</h2>
 * The XP and bonus-drop half rides {@code LivingEntity#forEachShearedItem}, the shared loot funnel,
 * precisely so no species list has to be maintained. The durability save cannot: vanilla damages
 * the shears back in each entity's own {@code interactMob}, <em>after</em> {@code sheared} has
 * returned, and there is no shared method that call sits in.
 *
 * <p>Enumerating here is nonetheless safe in a way it would not have been for the XP, and the
 * difference is the failure mode. <b>An injector that cannot find its target fails at load</b>
 * (Mixin's {@code defaultRequire = 1}), so a renamed or removed species is a loud boot error. A
 * missing XP hook would simply have paid nothing forever, which is what the previous four seam
 * misses in this skill all did.
 *
 * <p><b>{@code CopperGolemEntity} is deliberately absent</b>, matching the other half: shearing a
 * copper golem is "take the flower out of its hand", is infinitely repeatable, and pays no XP here,
 * so there is nothing to save durability on. It also damages the shears through a <em>different</em>
 * overload — {@code ItemStack.damage(int, LivingEntity, Hand)} rather than the
 * {@code (int, LivingEntity, EquipmentSlot)} form these four use — so including it would not even
 * have compiled into one {@code @ModifyArg}.
 *
 * <p>All four use exactly one {@code ItemStack.damage} call in {@code interactMob}
 * (jar-verified), so no {@code ordinal} is needed to disambiguate.
 */
@Mixin({SheepEntity.class, MooshroomEntity.class, SnowGolemEntity.class, BoggedEntity.class})
public abstract class ShearableInteractMixin {

    /**
     * Spare the shears on a successful {@code Bountiful Harvest} roll.
     *
     * <p>The handler is an instance method, so {@code this} is the entity being sheared — which is
     * what lets the listener run the same player-interaction identity check the XP half uses,
     * rather than assuming that whoever holds the interaction stash is the shearer.
     */
    @ModifyArg(
            method = "interactMob(Lnet/minecraft/entity/player/PlayerEntity;"
                    + "Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;damage(ILnet/minecraft/entity/"
                            + "LivingEntity;Lnet/minecraft/entity/EquipmentSlot;)V"),
            index = 0)
    private int mcmmo$saveShearDurability(int damageAmount) {
        return HusbandryListener.onShearToolDamaged((LivingEntity) (Object) this, damageAmount);
    }
}
