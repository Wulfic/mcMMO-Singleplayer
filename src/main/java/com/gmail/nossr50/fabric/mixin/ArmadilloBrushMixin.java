package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import java.util.function.BiConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.ArmadilloEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Husbandry's brush verb (Pass 2 stage 4): armadillo scute XP, plus {@code Bountiful Harvest}'s bonus
 * drop and durability save.
 *
 * <h2>🔑 {@code forEachBrushedItem} is {@code forEachShearedItem}'s sibling, and the better of the two</h2>
 * Stage 3 built the shear verb on {@code LivingEntity#forEachShearedItem}, the loot funnel one level
 * below the interaction. Brushing has an exact counterpart —
 * {@code LivingEntity#forEachBrushedItem(ServerWorld, RegistryKey, Entity, ItemStack, BiConsumer)},
 * jar-verified as referenced only by {@code ArmadilloEntity} — and it improves on the shear one in the
 * way that matters most: <b>it takes the brushing entity as a parameter</b>. So the real-player gate
 * needs no interaction stash and no identity check, just a look at an argument.
 *
 * <p>That gate is load-bearing rather than theoretical. <b>Vanilla ships a dispenser that brushes
 * armadillos</b> ({@code DispenserBehavior$5}), which the plan did not mention, and it passes
 * {@code null} for the brusher — so an AFK brush farm is excluded by the signature itself.
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

    /**
     * Pay the brush verb, and let {@code Bountiful Harvest} deliver the scute a second time.
     *
     * <p>The full-argument handler form is used so the {@code Entity} doing the brushing arrives with
     * the handler — that is the whole reason this seam beats the shear one, and it is what makes the
     * dispenser exclusion a property of the signature rather than of a check that could rot.
     *
     * <p>⚠️ <b>The {@code @At} owner is {@code ArmadilloEntity}, not the declaring
     * {@code LivingEntity}</b>, and getting that wrong costs a boot. {@code forEachBrushedItem} is
     * inherited and invoked on {@code this}, so the {@code invokevirtual}'s owner is the subclass.
     * That is easy to misread off {@code javap}, which prints <b>no class prefix at all</b> when an
     * invoke's owner is the class being disassembled — so an inherited call on {@code this} looks
     * identical to a call to a method declared right there. Naming the superclass failed the injection
     * check with "Scanned 0 target(s)", which reads like a missing <em>method</em> rather than a
     * mismatched owner and sends you hunting for the wrong thing.
     */
    @ModifyArg(method = "brushScute", allow = 1, index = 4,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/passive/ArmadilloEntity;forEachBrushedItem("
                            + "Lnet/minecraft/server/world/ServerWorld;"
                            + "Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/entity/Entity;"
                            + "Lnet/minecraft/item/ItemStack;Ljava/util/function/BiConsumer;)Z"))
    private BiConsumer<ServerWorld, ItemStack> mcmmo$onBrushedItems(ServerWorld world,
            RegistryKey<LootTable> lootTable, Entity brusher, ItemStack brush,
            BiConsumer<ServerWorld, ItemStack> dropper) {
        return HusbandryListener.onBrushedItems((Entity) (Object) this, brusher, dropper);
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
