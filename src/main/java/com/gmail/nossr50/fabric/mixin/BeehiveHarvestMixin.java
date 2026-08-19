package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.HusbandryListener;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Husbandry's hive verb (Pass 2 stage 4): honey and honeycomb XP, {@code Beekeeper}, and
 * {@code Bountiful Harvest}'s share of the yield and the shears.
 *
 * <h2>⚠️ The plan's gate was not merely wrong, it was backwards</h2>
 * The plan said {@code BeehiveBlock#takeHoney} "has two overloads — one taking
 * {@code (World, BlockState, BlockPos, PlayerEntity, BeeState)} and one taking
 * {@code (World, BlockState, BlockPos)} — use the player overload as the gate; the 3-arg one is the
 * automated path."
 *
 * <p><b>Bytecode says that is not what those overloads are.</b> The 3-arg form is the bare
 * "set the honey level back to zero" primitive. The 5-arg form calls the 3-arg form and <em>then</em>
 * angers the hive. Which of the two {@code onUseWithItem} uses is decided by
 * {@code CampfireBlock.isLitCampfireInRange} — so <b>the player path calls the "automated" overload
 * every time a player harvests a hive over a campfire</b>, which is how essentially every bee farm in
 * the game is built. Gating on the "player overload" would have paid zero for every sheltered harvest
 * and full XP only for the careless ones that get you stung, with nothing anywhere to say so.
 *
 * <h2>Why {@code onUseWithItem} is the right seam anyway</h2>
 * It is the only path a human ever takes, and it closes the automation structurally rather than by a
 * check of our own. That matters here more than the plan realised: jar-grep finds <b>two</b> vanilla
 * dispenser behaviours reaching this verb — {@code ShearsDispenserBehavior} calls
 * {@code dropHoneycomb} and {@code DispenserBehavior$3} calls {@code takeHoney} — so both halves of a
 * hive harvest are fully automatable, and neither dispenser goes anywhere near {@code onUseWithItem}.
 *
 * <p>The two halves are hooked separately, at the one call each of them alone makes, because they
 * produce their yields by completely different means: the shears roll the {@code harvest/beehive} loot
 * table, while the bottle hands over one hard-coded {@code HONEY_BOTTLE}. One hook covering both would
 * have had to work out afterwards which had happened, from a held stack that vanilla has already
 * damaged or decremented — and the ambiguous case (the tool broke, or that was the last bottle) is
 * exactly the one a player would hit.
 */
@Mixin(net.minecraft.block.BeehiveBlock.class)
public abstract class BeehiveHarvestMixin {

    private static final String ON_USE_WITH_ITEM =
            "onUseWithItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/block/BlockState;"
                    + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;"
                    + "Lnet/minecraft/util/hit/BlockHitResult;)"
                    + "Lnet/minecraft/util/ItemActionResult;";

    private static final String IS_LIT_CAMPFIRE_IN_RANGE =
            "Lnet/minecraft/block/CampfireBlock;isLitCampfireInRange("
                    + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Z";

    /**
     * The shears half: pay the hive verb and add any bonus helpings.
     *
     * <p>Anchored on the {@code dropHoneycomb} call, which is reached only by the shears branch and
     * only after vanilla has confirmed a full hive on the server side, so it fires exactly once per
     * successful honeycomb harvest. The hive's honey level is still full at this point, which is what
     * lets the bonus be delivered as further rolls of vanilla's own loot table.
     */
    @Inject(method = ON_USE_WITH_ITEM, allow = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/BeehiveBlock;dropHoneycomb("
                            + "Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V"))
    private void mcmmo$onHoneycombHarvested(ItemStack stack, BlockState state, World world,
            BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit,
            CallbackInfoReturnable<ItemActionResult> cir) {
        HusbandryListener.onHoneycombHarvested(player, stack, state, world, pos);
    }

    /**
     * The bottle half: pay the hive verb and add any bonus helpings.
     *
     * <p>Anchored on the glass bottle being decremented — the one instruction unique to that branch,
     * and one vanilla only reaches once it has decided the harvest is going ahead.
     */
    @Inject(method = ON_USE_WITH_ITEM, allow = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;decrement(I)V"))
    private void mcmmo$onHoneyBottled(ItemStack stack, BlockState state, World world, BlockPos pos,
            PlayerEntity player, Hand hand, BlockHitResult hit,
            CallbackInfoReturnable<ItemActionResult> cir) {
        HusbandryListener.onHoneyBottled(player);
    }

    /**
     * {@code Beekeeper}: the bees do not mind being robbed.
     *
     * <p><b>Expressed as "there was a campfire after all" rather than as a bee-anger suppression, and
     * that is the whole trick.</b> Vanilla already has exactly one branch for "this harvest was
     * sheltered", and taking it closes <em>both</em> ways a harvest angers bees in a single stroke:
     * {@code angerNearbyBees}, which sets every bee within 8 blocks on the player, and
     * {@code takeHoney(..., BeeState.EMERGENCY)}, which drives the hive's own occupants out angry.
     * The plan proposed suppressing the first, which would have left the second firing — the bees
     * inside the hive, the ones actually being robbed, would still have come out for you.
     *
     * <p>Also the single call site: this expression is evaluated once per <em>successful</em> harvest
     * and never on a click that achieved nothing, since vanilla guards it behind its own success flag.
     */
    @ModifyExpressionValue(method = ON_USE_WITH_ITEM, allow = 1,
            at = @At(value = "INVOKE", target = IS_LIT_CAMPFIRE_IN_RANGE))
    private boolean mcmmo$beekeeperKeepsTheBeesCalm(boolean campfireInRange, ItemStack stack,
            BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
            BlockHitResult hit) {
        return campfireInRange || HusbandryListener.hiveHarvestLeavesBeesCalm(player);
    }

    /**
     * {@code Bountiful Harvest}: spare the shears this hive would have worn.
     *
     * <p>The full-argument handler form is used so the tool's holder arrives with it — that
     * {@code LivingEntity} is the harvesting player, which is what lets the listener resolve whose
     * sub-skill is being rolled without a stash or a second lookup. Only the shears branch damages
     * anything, so there is exactly one such call in the method.
     */
    @ModifyArg(method = ON_USE_WITH_ITEM, allow = 1, index = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/item/ItemStack;damage(ILnet/minecraft/entity/"
                            + "LivingEntity;Lnet/minecraft/entity/EquipmentSlot;)V"))
    private int mcmmo$saveHiveToolDurability(int damageAmount, LivingEntity holder,
            EquipmentSlot slot) {
        return HusbandryListener.onHiveToolDamaged(holder, damageAmount);
    }
}
