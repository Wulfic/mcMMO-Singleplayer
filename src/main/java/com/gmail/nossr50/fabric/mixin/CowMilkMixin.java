package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Husbandry's milk verb (Pass 2 stage 4): an animal milked into a bucket.
 *
 * <h2>⚠️ There is no single milking funnel — this mixin needs one target per family</h2>
 * {@code CowEntity#interactMob} carries the whole player path for the cow family, and
 * {@code MooshroomEntity#interactMob} calls {@code super} at the end of its own body — verified in
 * bytecode — so milking a mooshroom arrives here too, and only its stew-in-a-bowl route needs a mixin
 * of its own ({@link MooshroomStewMixin}).
 *
 * <p><b>{@code GoatEntity} is not in that family at all.</b> It extends {@code AnimalEntity} directly
 * and <b>re-implements the entire bucket-for-milk-bucket branch inline</b> in its own
 * {@code interactMob}, so a target list of {@code CowEntity} alone paid <b>zero</b> for every
 * goat ever milked — silently, because nothing about a verb that simply never fires looks broken. The
 * tell was that goats already paid for breeding, raising and feeding: <b>an animal that pays for five
 * of six verbs is a wiring symptom, not a balance choice.</b>
 *
 * <p>🔑 <b>How the roster was settled, and how to re-settle it after a version bump:</b> not from a
 * species list and not from method names, but by binary-grepping the extracted Minecraft jar for the
 * item the verb actually produces — {@code MILK_BUCKET}. Across all 1040 entity classes in 1.21.11
 * that returns exactly three: {@code CowEntity}, {@code GoatEntity}, and
 * {@code WanderingTraderEntity} (a trade offer, not a milking, and correctly excluded). A method
 * listing cannot answer this question — {@code javap} shows a method where it is <em>declared</em>,
 * which is not the same as where it is reachable.
 *
 * <p><b>The real-player gate is the signature.</b> Unlike the shear verb, this needs no interaction
 * stash and no identity check: both methods take a {@code PlayerEntity}, the milk branch is written
 * inline inside each with no shared callee anything else could reach, and vanilla ships no dispenser
 * behaviour that milks anything. That last point was checked rather than assumed, for both targets —
 * no class under {@code net/minecraft/block/dispenser} references {@code MILK_BUCKET} — and it was
 * worth checking, because a hive harvest and an armadillo brush both turn out to have one.
 *
 * <p><b>Vanilla rate-limits this verb by nothing whatsoever</b>, which is D-H5: the same cow or goat
 * can be milked as fast as a player can click, forever, for free. What bounds it is mcMMO's own
 * per-animal harvest cooldown in the listener, not any game mechanic — and because that cooldown lives
 * inside {@code HusbandryListener#onMilked}, adding a target here puts it under the gate for free.
 */
@Mixin({CowEntity.class, GoatEntity.class})
public abstract class CowMilkMixin {

    /**
     * Pay the milk verb.
     *
     * <p>Anchored on {@code ItemUsage.exchangeStack}, the bucket-for-milk-bucket swap. That call is
     * the point of no return in the milk branch — vanilla has already confirmed a bucket in hand and
     * an adult animal — and it is the only {@code exchangeStack} in the method, so the match is
     * unambiguous without an ordinal.
     *
     * <p><b>One anchor covers both targets, verified rather than assumed.</b> {@code GoatEntity}
     * declares {@code interactMob} with the identical descriptor and reaches the identical three-arg
     * {@code exchangeStack}, and each target contains exactly one such call — which is what lets
     * {@code allow = 1} stay. ⚠️ {@code allow} is checked <em>per target</em>, so if a future version
     * adds a second {@code exchangeStack} to either method this fails loudly at mixin-apply time rather
     * than quietly paying twice.
     */
    @Inject(method = "interactMob(Lnet/minecraft/entity/player/PlayerEntity;"
            + "Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;", allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemUsage;exchangeStack("
                            + "Lnet/minecraft/item/ItemStack;"
                            + "Lnet/minecraft/entity/player/PlayerEntity;"
                            + "Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;"))
    private void mcmmo$onMilked(PlayerEntity player, Hand hand,
            CallbackInfoReturnable<ActionResult> cir) {
        HusbandryListener.onMilked((Entity) (Object) this, player);
    }
}
