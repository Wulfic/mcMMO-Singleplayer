package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.ArmadilloEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Husbandry's brush verb (Pass 2 stage 4): armadillo scute XP, plus {@code Bountiful Harvest}'s bonus
 * drop and durability save.
 *
 * <h2>⚠️⚠️ BAND mc/1.21.5 — {@code forEachBrushedItem} does not exist, so the seam moved</h2>
 * Newer versions route the drop through
 * {@code LivingEntity#forEachBrushedItem(ServerWorld, RegistryKey, Entity, ItemStack, BiConsumer)} and
 * hand the brushing entity to it, so master's real-player gate is just a look at an argument — and its
 * dispenser exclusion is a property of the <em>signature</em>, because vanilla's armadillo-brushing
 * dispenser passes {@code null} there.
 *
 * <p>On this band that funnel is absent. {@code ArmadilloEntity#brushScute()} takes <em>no
 * arguments</em>, consults no loot table, and drops a flat {@code ItemStack(Items.ARMADILLO_SCUTE)}
 * through {@code Entity#dropStack} inline. There is no brusher to inspect and no per-item handler to
 * wrap.
 *
 * <p>🔑🔑 <b>And the dispenser is still here</b> — a binary grep of <em>this band's</em> jar for
 * {@code brushScute} returns exactly {@code ArmadilloEntity} and
 * {@code net/minecraft/block/dispenser/DispenserBehavior$5}. So on this band an AFK dispenser brush
 * farm is <em>not</em> excluded by any signature, and a seam inside {@code brushScute} would pay it.
 * <b>That is why this band hooks {@code interactMob} instead:</b> the dispenser never goes near it,
 * so choosing the seam is what restores the exclusion master gets for free. This is the one place
 * where this band's port is a behaviour decision rather than a rename.
 *
 * <h2>⚠️ The plan's rate limit for this verb does not exist</h2>
 * The plan filed brushing as low farm risk on the strength of "vanilla's own scute cooldown". There
 * isn't one on this path. {@code brushScute} returns {@code true} for any adult armadillo,
 * {@code brush/armadillo.json} drops a scute with <b>no conditions whatsoever</b>, and the
 * {@code nextScuteShedCooldown} timer that timer's name suggests governs only the <em>passive</em> shed
 * in {@code mobTick} — {@code brushScute} never reads it and never resets it. Brushing one armadillo is
 * therefore repeatable as fast as a player can click, bounded only by brush durability. That is why
 * D-H5's cooldown covers brushing as well as milking, and why the XP hangs off an item actually being
 * delivered rather than off the attempt.
 */
@Mixin(ArmadilloEntity.class)
public abstract class ArmadilloBrushMixin {

    private static final String INTERACT_MOB = "interactMob(Lnet/minecraft/entity/player/PlayerEntity;"
            + "Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;";

    /**
     * The one instruction in {@code interactMob} that is reached only by a <b>successful</b> brush.
     *
     * <p>Vanilla's shape here is {@code if (brushScute()) { stack.damage(16, player, slot); return
     * SUCCESS; }} — so this call sits inside the taken branch. Anchoring on it rather than on
     * {@code brushScute} itself is deliberate: {@code brushScute} returns {@code false} for a baby
     * armadillo, and an injection on the <em>call</em> would fire for that refusal too. Reading the
     * boolean back off the stack is not available without a wrapper library, and the branch-unique
     * instruction says the same thing with nothing extra to depend on.
     *
     * <p>There is exactly one {@code ItemStack#damage} call in the method, which is what
     * {@code allow = 1} pins — the same anchor the durability save below uses.
     */
    @Inject(method = INTERACT_MOB, allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;damage(ILnet/minecraft/entity/"
                            + "LivingEntity;Lnet/minecraft/entity/EquipmentSlot;)V"))
    private void mcmmo$onBrushed(PlayerEntity player, Hand hand,
            CallbackInfoReturnable<ActionResult> cir) {
        HusbandryListener.onBrushed((Entity) (Object) this, player);
    }

    /**
     * {@code Bountiful Harvest}: spare the brush the 16 durability this would have cost.
     *
     * <p>A much larger effect than the shear verb's equivalent, and worth knowing when tuning: a brush
     * has 64 durability and vanilla charges <b>16</b> of it per armadillo, so a brush is worth four
     * uses and each save is worth a quarter of the tool.
     *
     * <p>Like the shear save, this cannot ride the loot funnel — vanilla wears the tool back in
     * {@code interactMob}, after {@code brushScute} has returned — so it hangs off that call instead.
     * There is exactly one {@code damage} call in the method.
     */
    @ModifyArg(method = INTERACT_MOB, allow = 1, index = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;damage(ILnet/minecraft/entity/"
                            + "LivingEntity;Lnet/minecraft/entity/EquipmentSlot;)V"))
    private int mcmmo$saveBrushDurability(int damageAmount, LivingEntity holder,
            EquipmentSlot slot) {
        return HusbandryListener.onBrushToolDamaged((Entity) (Object) this, damageAmount);
    }
}
