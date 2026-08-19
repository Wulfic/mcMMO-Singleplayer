package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.ArmadilloEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Husbandry's brush verb (Pass 2 stage 4): armadillo scute XP, plus {@code Bountiful Harvest}'s bonus
 * drop and durability save.
 *
 * <h2>&#9888; The brush seam is not the same shape on every version</h2>
 * Where vanilla exposes a brush loot funnel one level below the interaction —
 * {@code forEachBrushedItem(ServerWorld, RegistryKey, Entity, ItemStack, BiConsumer)}, the sibling of
 * the shear verb's funnel — this hook rides it, and <b>it takes the brushing entity as a
 * parameter</b>, so the real-player gate is a look at an argument.
 *
 * <p><b>Where that funnel does not exist, neither does that gate.</b> {@code brushScute()} then takes
 * no arguments and drops the scute inline, so there is nothing to inspect. The hook moves up one level
 * to {@code interactMob}, and the gate becomes <b>which method it hangs off</b> rather than a
 * signature — see {@link #mcmmo$onBrushedItems}.
 *
 * <p>Either way the exclusion is load-bearing rather than theoretical: <b>vanilla ships a dispenser
 * that brushes armadillos</b> ({@code DispenserBehavior$5}), which the plan did not mention. It passes
 * {@code null} to the funnel where one exists, and calls {@code brushScute} directly without going
 * through {@code interactMob} where one does not — so an AFK brush farm is excluded on both shapes.
 *
 * <h2>⚠️ The plan's rate limit for this verb does not exist</h2>
 * The plan filed brushing as low farm risk on the strength of "vanilla's own scute cooldown". There
 * isn't one on this path. {@code brushScute} returns {@code true} for any adult armadillo,
 * the scute it hands over carries <b>no conditions whatsoever</b> on any supported version, and the
 * {@code nextScuteShedCooldown} timer that timer's name suggests governs only the <em>passive</em> shed
 * in {@code mobTick} — {@code brushScute} never reads it and never resets it. Brushing one armadillo is
 * therefore repeatable as fast as a player can click, bounded only by brush durability. That is why
 * D-H5's cooldown covers brushing as well as milking, and why the XP hangs off an item actually being
 * delivered rather than off the attempt.
 */
@Mixin(ArmadilloEntity.class)
public abstract class ArmadilloBrushMixin {

    /**
     * Pay the brush verb, and let {@code Bountiful Harvest} deliver the scute a second time.
     *
     * <h2>&#9888; The seam moved, and so did the reason the dispenser is excluded</h2>
     * Where vanilla routes brush loot through a {@code forEachBrushedItem} funnel, this hook rides
     * that funnel and takes the brushing {@code Entity} as a parameter — so vanilla's own
     * armadillo-brushing dispenser, which passes {@code null} there, is excluded <b>by the signature
     * itself</b>. <b>That is not available here.</b> {@code brushScute()} takes no arguments and drops
     * the scute inline; there is no funnel and no brusher to inspect.
     *
     * <p>So the gate becomes <b>the call site</b>: {@code interactMob} is only reached by a player,
     * and the dispenser behaviour calls {@code brushScute} directly without going through it. That is
     * a <em>stricter</em> gate than the signature one, so the behaviour is preserved — but the reason
     * is different, and a comment claiming the signature excludes the dispenser would be false here.
     *
     * <p>{@code @ModifyExpressionValue} rather than {@code @Inject} because {@code brushScute}'s own
     * return value is the "a scute was actually delivered" signal — it refuses a baby and succeeds for
     * any adult — which is exactly what the verb must pay on. The value is passed straight through;
     * this hook never changes whether vanilla thinks the brush worked.
     *
     * <p>&#128273; <b>That signal is handed to the listener rather than acted on here.</b> It is the
     * gate the whole verb rests on, and a gate that lives in a mixin body is a gate no unit test can
     * reach — so this method stays a pass-through and
     * {@link HusbandryListener#onBrushed(Entity, Entity, boolean)} owns the decision.
     *
     * <p>The bonus scute is dropped here rather than by doubling vanilla's stack, because by the time
     * {@code brushScute} has returned the original {@code ItemEntity} already exists. Dropping from
     * the armadillo puts the second one in the same place, by the same route.
     */
    @ModifyExpressionValue(method = "interactMob(Lnet/minecraft/entity/player/PlayerEntity;"
            + "Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/passive/ArmadilloEntity;brushScute()Z"))
    private boolean mcmmo$onBrushedItems(boolean brushed, PlayerEntity player, Hand hand) {
        final ArmadilloEntity armadillo = (ArmadilloEntity) (Object) this;
        // `brushed` is false for a baby armadillo: vanilla dropped nothing, so there is nothing to
        // pay. That decision belongs to the listener, which is where a test can reach it.
        if (HusbandryListener.onBrushed(armadillo, player, brushed)) {
            armadillo.dropStack(new ItemStack(Items.ARMADILLO_SCUTE));
        }
        return brushed;
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
