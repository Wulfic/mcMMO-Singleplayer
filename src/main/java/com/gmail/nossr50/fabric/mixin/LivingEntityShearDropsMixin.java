package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import java.util.function.BiConsumer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Husbandry's shear verb (Pass 2 stage 3): shear XP and {@code Bountiful Harvest}'s bonus drop.
 *
 * <h2>⚠️ Why this sits on {@code LivingEntity} and not on four {@code interactMob}s</h2>
 * The plan named {@code SheepEntity}, {@code MooshroomEntity}, {@code SnowGolemEntity} and
 * {@code BoggedEntity} and said to hook {@code interactMob} on each. That enumeration was
 * <b>already wrong when it was written</b>: 1.21.11 ships a <b>fifth</b> {@code Shearable},
 * {@code CopperGolemEntity}. Hand-maintained species lists are the mistake this skill has now made
 * four times, and the previous three all failed silently.
 *
 * <p>{@code LivingEntity#forEachShearedItem(ServerWorld, RegistryKey, ItemStack, BiConsumer)} is
 * the loot funnel every shear-with-drops passes through — verified by jar-grep as referenced by
 * exactly those four species and nothing else, once each. One injection therefore covers all of
 * them, and covers whatever shearable a future version adds.
 *
 * <p><b>The copper golem's exclusion is a property of this seam, not a blacklist.</b> It is the one
 * shearable with no loot table: shearing it takes the poppy out of its hand and drops that, so it
 * never reaches here. Which is exactly right — {@code CopperGolemEntity#isShearable()} is only "is
 * holding a flower from {@code SHEARABLE_FROM_COPPER_GOLEM}", so you can hand it another poppy and
 * shear it again indefinitely. Under the plan's per-species hook that would have shipped as a
 * click-for-300-XP loop on day one.
 *
 * <h2>⚠️ The dispenser reaches this method too</h2>
 * {@code ShearsDispenserBehavior} calls {@code Shearable#sheared}, which lands here like any other
 * shear — this is the AFK wool farm the skill's plan spends a page warning about, and the reason
 * hooking {@code Shearable#sheared} directly was ruled out. It is closed in the listener, by the
 * same player-interaction stash the feed verb uses: a shear pays only while a player is
 * mid-interaction with this very entity, and a dispenser opens no interaction.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityShearDropsMixin {

    /**
     * Pay the shear verb, and let {@code Bountiful Harvest} deliver everything a second time.
     *
     * <p>Modifying the {@code BiConsumer} rather than injecting at {@code HEAD} is what makes the
     * bonus drop species-agnostic: each shearable passes its own handler here — a sheep drops wool
     * at its feet, a mooshroom's routes through its cow conversion — so calling that handler again
     * reuses whatever the species already does instead of re-implementing four drop behaviours.
     *
     * <p>{@code argsOnly} with {@code ordinal = 0} targets the parameter itself; it is the only
     * {@code BiConsumer} in the signature, so the match is unambiguous.
     */
    @ModifyVariable(method = "forEachShearedItem", allow = 1, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BiConsumer<ServerWorld, ItemStack> mcmmo$onShearedItems(
            BiConsumer<ServerWorld, ItemStack> dropper) {
        return HusbandryListener.onShearedItems((LivingEntity) (Object) this, dropper);
    }
}
