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
 * <h2>BAND: there is no brush loot funnel on this version — the whole verb hangs off {@code interactMob}</h2>
 * On newer versions the scute arrives through
 * {@code LivingEntity#forEachBrushedItem(ServerWorld, RegistryKey, Entity, ItemStack, BiConsumer)},
 * the exact sibling of the shear verb's {@code forEachShearedItem} funnel, and the verb rides that.
 * <b>That method does not exist here.</b> {@code brushScute()} takes no arguments and inlines the whole
 * thing — {@code dropStack(serverWorld, new ItemStack(Items.ARMADILLO_SCUTE))}, a game event and a
 * sound — so there is no loot funnel to wrap and no {@code BiConsumer} to modify. Re-verified against
 * <b>both</b> of this band's merged jars rather than adapted by analogy; a {@code @ModifyArg} rename
 * would have resolved to nothing and taken {@code ArmadilloEntity} — and through it
 * {@code EntityType}, {@code Items} and {@code Blocks} — out of the classloader entirely.
 *
 * <h2>⚠️ So the dispenser exclusion stops being free, and this is how it is kept structural</h2>
 * On the funnel seam the brusher arrives as a <em>parameter</em>, and vanilla's armadillo-brushing
 * dispenser passes {@code null} there, so an AFK brush farm is excluded <b>by the signature</b>.
 * Here {@code brushScute()} has no brusher parameter at all and {@code DispenserBehavior$6} calls it
 * <b>directly</b> — jar-confirmed on both of this band's versions, where its only two callers are
 * that dispenser and {@code ArmadilloEntity#interactMob}.
 *
 * <p>Hooking {@code brushScute} would therefore have paid the dispenser, so the verb hooks
 * {@code interactMob} instead. It carries the real {@code PlayerEntity}, and the dispenser never
 * enters it — so the exclusion stays a property of the call graph rather than becoming a check of our
 * own that could rot. {@code ArmadilloBrushDispenserExclusionTest} pins that call graph from bytecode,
 * because a positive-only test would pass just as happily with the gate missing entirely.
 *
 * <h2>⚠️ The plan's rate limit for this verb does not exist</h2>
 * The plan filed brushing as low farm risk on the strength of "vanilla's own scute cooldown". There
 * isn't one on this path. {@code brushScute} returns {@code true} for any adult armadillo, and the
 * {@code nextScuteShedCooldown} timer that its name suggests governs only the <em>passive</em> shed in
 * {@code mobTick} — {@code brushScute} never reads it and never resets it. Brushing one armadillo is
 * therefore repeatable as fast as a player can click, bounded only by brush durability. That is why
 * D-H5's cooldown covers brushing as well as milking.
 */
@Mixin(ArmadilloEntity.class)
public abstract class ArmadilloBrushMixin {

    /**
     * A scute has just changed hands: pay the brush verb, and let {@code Bountiful Harvest} deliver a
     * second one.
     *
     * <p>⚠️ <b>The injection point is load-bearing and is not just "somewhere in {@code interactMob}".</b>
     * Vanilla's method is {@code isOf(BRUSH) && brushScute()} guarding a single block that wears the
     * tool and returns {@code SUCCESS}. Only that block is reachable once a scute has actually been
     * delivered, so injecting after the {@code damage} call preserves the invariant the funnel seam
     * gave for free on newer bands: <b>the XP hangs off an item being handed over, never off the
     * attempt</b>. Brushing has no upstream gate the way shearing has {@code isShearable()}, so that
     * distinction is the only proof available that a harvest happened. Injecting at {@code HEAD} would
     * pay for waving a brush at a baby armadillo.
     *
     * <p>{@code Shift.AFTER} rather than at the {@code INVOKE} itself so the callback lands on an empty
     * operand stack, and so it cannot interleave with {@link #mcmmo$saveBrushDurability} — which
     * modifies an argument of that very call and must run first, or a saved brush would still be
     * announced as a brush that cost durability. There is exactly one such {@code damage} call in the
     * method on both of this band's versions, which is what {@code allow = 1} pins.
     */
    @Inject(method = "interactMob(Lnet/minecraft/entity/player/PlayerEntity;"
            + "Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", allow = 1,
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/item/ItemStack;damage(ILnet/minecraft/entity/"
                            + "LivingEntity;Lnet/minecraft/entity/EquipmentSlot;)V"))
    private void mcmmo$onScuteBrushed(PlayerEntity brusher, Hand hand,
            CallbackInfoReturnable<ActionResult> cir) {
        HusbandryListener.onArmadilloBrushed((Entity) (Object) this, brusher);
    }

    /**
     * {@code Bountiful Harvest}: spare the brush the 16 durability this would have cost.
     *
     * <p>A much larger effect than the shear verb's equivalent, and worth knowing when tuning: a brush
     * has 64 durability and vanilla charges <b>16</b> of it per armadillo, so a brush is worth four
     * uses and each save is worth a quarter of the tool.
     *
     * <p>Unchanged across bands — vanilla has always worn the tool back in {@code interactMob} rather
     * than in the harvest itself, so this half never rode the funnel and had nothing to lose when the
     * funnel turned out to be absent. There is exactly one {@code damage} call in the method.
     *
     * <p>⚠️ The dispenser wears its own brush through a <em>different</em> overload
     * ({@code damage(int, ServerWorld, ServerPlayerEntity, Consumer)}, jar-confirmed on both of this
     * band's versions), so this selector excludes automation structurally too.
     */
    @ModifyArg(method = "interactMob(Lnet/minecraft/entity/player/PlayerEntity;"
            + "Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", allow = 1, index = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;damage(ILnet/minecraft/entity/"
                            + "LivingEntity;Lnet/minecraft/entity/EquipmentSlot;)V"))
    private int mcmmo$saveBrushDurability(int damageAmount, LivingEntity holder,
            EquipmentSlot slot) {
        return HusbandryListener.onBrushToolDamaged((Entity) (Object) this, damageAmount);
    }
}
