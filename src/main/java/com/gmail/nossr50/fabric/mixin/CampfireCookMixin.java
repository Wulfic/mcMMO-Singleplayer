package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.CookingListener;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The Cooking campfire hook — Cooking XP and Master Chef for a cook that finishes on a lit campfire
 * or soul campfire.
 *
 * <p>It needs its own mixin because <b>{@code CampfireBlockEntity extends BlockEntity implements
 * Clearable}</b> and is <em>not</em> an {@code AbstractFurnaceBlockEntity} (javap-verified), so
 * {@link AbstractFurnaceSmeltMixin} does not reach it. Both campfire variants share this one block
 * entity and one {@code CampfireBlock}, so both are covered by this single injector.
 *
 * <h2>The seam ({@code javap -c -p net.minecraft.world.level.block.entity.CampfireBlockEntity})</h2>
 * {@code litServerTick} walks the four cooking slots, and for a slot whose {@code cookingTimes}
 * entry has reached its {@code cookingTotalTimes} entry it does, in order:
 * <pre>
 *   ItemStack result = matchGetter.getFirstMatch(input, world).map(craft).orElse(rawStack);
 *   if (result.isItemEnabled(world.getEnabledFeatures())) {
 *       ItemScatterer.spawn(world, x, y, z, result);   // &lt;-- the injection point
 *       itemsBeingCooked.set(i, ItemStack.EMPTY);
 *       ...
 *   }
 * </pre>
 * That {@code ItemScatterer.spawn(World, DDD, ItemStack)} call is reached <b>only</b> when a cook has
 * actually finished, which makes it the campfire's analogue of the furnace's {@code craftRecipe}
 * invoke. It occurs exactly once in the method, so {@code allow = 1} pins it — a silent second bind
 * would pay twice.
 *
 * <p><b>A campfire has no output slot.</b> The cooked item is thrown on the floor, which is why this
 * is a {@link ModifyArg} on the scattered stack rather than the furnace's split
 * "XP before / bonus after" pair of injectors: there is one moment, and it carries both the raw input
 * and the finished result at once.
 *
 * <h2>⚠️ Every captured local is disambiguated by TYPE, never by a numeric LVT index</h2>
 * The two things this needs from the method body are the raw input and the finished result, and both
 * are {@code ItemStack} locals in scope at the injection point ({@code index 7} and {@code index 9}
 * respectively, in this build). Capturing them as {@code @Local(index = ...)} would have compiled,
 * bound, and booted cleanly <b>with the two swapped</b> — and a swap is invisible: the XP would be
 * looked up for {@code Cooked_Beef} under {@code Experience_Values.Cooking.Cook}, which prices only
 * raw inputs, so the whole feature would silently pay nothing. That is the exact shape of failure
 * this port has been bitten by repeatedly, so neither index is used:
 * <ul>
 *   <li>the <b>result</b> is the argument being modified — it needs no capture at all;</li>
 *   <li>the <b>input</b> is read off the {@link SingleStackRecipeInput} vanilla built to query the
 *       recipe, which is the <em>only</em> local of that type in the method. MixinExtras' implicit
 *       {@code @Local} mode requires exactly one match and <b>fails loudly at apply time</b>
 *       otherwise, so a future refactor that introduces a second one breaks the build instead of
 *       breaking the skill.</li>
 * </ul>
 * The two {@code argsOnly} captures are likewise the only {@code ServerWorld} and {@code BlockPos}
 * among {@code litServerTick}'s parameters. ({@code @ModifyArg} handlers do not receive the target's
 * own arguments, unlike {@code @Inject} — hence the sugar.)
 *
 * <p>No client guard is needed: {@code litServerTick} only ever takes a {@link ServerWorld}.
 */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireCookMixin {

    /**
     * Award the campfire's owner for a finished cook and let Master Chef add a second helping to the
     * stack about to be scattered.
     *
     * @param result the stack vanilla is about to throw on the floor; returned unchanged unless
     *               Master Chef procs
     */
    @ModifyArg(
            method = "cookTick",
            allow = 1,
            index = 4,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/Containers;dropItemStack("
                            + "Lnet/minecraft/world/level/Level;DDD"
                            + "Lnet/minecraft/world/item/ItemStack;)V"))
    private static ItemStack mcmmo$onCampfireCook(ItemStack result,
            @Local(argsOnly = true) ServerLevel world,
            @Local(argsOnly = true) BlockPos pos,
            @Local SingleRecipeInput cooked) {
        return CookingListener.onCampfireCook(world, pos, cooked.item(), result);
    }
}
