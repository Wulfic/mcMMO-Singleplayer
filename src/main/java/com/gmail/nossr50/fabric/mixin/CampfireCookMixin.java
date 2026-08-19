package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.CookingListener;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
 * <h2>The seam ({@code javap -c -p net.minecraft.block.entity.CampfireBlockEntity})</h2>
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
 * The two {@code argsOnly} captures are likewise the only {@code World} and {@code BlockPos} among
 * {@code litServerTick}'s parameters. ({@code @ModifyArg} handlers do not receive the target's own
 * arguments, unlike {@code @Inject} — hence the sugar.)
 *
 * <h2>⚠️⚠️ Capture the world as {@code World} and narrow it here, never as {@code ServerWorld}</h2>
 * {@code litServerTick}'s world parameter has been spelled both {@code World} and {@link ServerWorld}
 * across supported versions, and an {@code argsOnly} capture is matched <b>by type</b>. Asking for a
 * {@code ServerWorld} where the parameter is a {@code World} matches no argument, and MixinExtras then
 * reports {@code "(0/1) succeeded. Scanned 0 target(s)"} — which reads like a <em>missing method</em>
 * and sends you looking for a renamed {@code litServerTick} that is right where it always was.
 *
 * <p>🔑 <b>{@code scripts/mixin-allow-audit.py} cannot see this class of defect.</b> It resolves the
 * injection point and counts sites; it does not type-check the handler's own parameter list. This
 * injector sat in an {@code OK ... computed=1} row while failing at class-load — so a green ship gate
 * 2 is necessary and not sufficient, and {@code MixinApplicationTest} is what actually catches it.
 * Capturing the broadest type and narrowing in the body is correct on every band and costs one
 * {@code instanceof}.
 */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireCookMixin {

    /**
     * Award the campfire's owner for a finished cook and let Master Chef add a second helping to the
     * stack about to be scattered.
     *
     * @param result the stack vanilla is about to throw on the floor; returned unchanged unless
     *               Master Chef procs
     * @param world  captured as {@code World} and narrowed below — see the class note. Also the
     *               client guard: a campfire ticked on a client world pays nothing.
     */
    @ModifyArg(
            method = "litServerTick",
            allow = 1,
            index = 4,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/ItemScatterer;spawn("
                            + "Lnet/minecraft/world/World;DDD"
                            + "Lnet/minecraft/item/ItemStack;)V"))
    private static ItemStack mcmmo$onCampfireCook(ItemStack result,
            @Local(argsOnly = true) World world,
            @Local(argsOnly = true) BlockPos pos,
            @Local SingleStackRecipeInput cooked) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return result;
        }
        return CookingListener.onCampfireCook(serverWorld, pos, cooked.item(), result);
    }
}
