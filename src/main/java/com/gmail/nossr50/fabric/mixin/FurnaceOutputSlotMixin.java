package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.SmeltingListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.FurnaceResultSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The K7 hook for Smelting's Understanding the Art — the port of Bukkit's {@code FurnaceExtractEvent},
 * which vanilla has no equivalent of.
 *
 * <p>{@code onCrafted(ItemStack)} is the funnel: both routes out of a furnace output slot go through
 * it (bytecode-verified — a normal take is {@code onTakeItem} → {@code onCrafted(stack)}, a
 * shift-click is {@code onQuickTransfer} → {@code onCrafted(stack, amount)} → {@code onCrafted(stack)}),
 * and it is the only caller of {@code AbstractFurnaceBlockEntity#dropExperienceForRecipesUsed}, the
 * method that spawns the furnace's stored XP. Bracketing it at HEAD and RETURN therefore spans exactly
 * the orb spawns that belong to this extraction, which is what lets a thread-local carry the
 * multiplier down to {@code AbstractFurnaceSmeltMixin}'s {@code dropExperience} hook — the orb spawn
 * itself knows neither the player nor what was taken.
 *
 * <p>No {@code allow} is needed: these are HEAD/RETURN injections into a named method rather than
 * call-site anchors, so there is no slice to silently drop (contrast {@code FishingWaitTimeMixin}).
 * A RETURN {@code @Inject} does bind to every return point, which is what the clear-up wants.
 *
 * <p>Both handlers are cheap on the client — {@link SmeltingListener#beginFurnaceExtract} bails on the
 * first line for a non-{@code ServerPlayerEntity}, and the clear-up is a thread-local removal — so no
 * environment guard is used here.
 */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceOutputSlotMixin {

    /** The slot's owner. Vanilla reads this same field to decide whether to drop the stored XP. */
    @Shadow
    @Final
    private Player player;

    @Inject(method = "onCrafted(Lnet/minecraft/world/item/ItemStack;)V", allow = 1, at = @At("HEAD"))
    private void mcmmo$beginFurnaceExtract(ItemStack stack, CallbackInfo ci) {
        SmeltingListener.beginFurnaceExtract(player, stack);
    }

    @Inject(method = "onCrafted(Lnet/minecraft/world/item/ItemStack;)V", allow = 1, at = @At("RETURN"))
    private void mcmmo$endFurnaceExtract(ItemStack stack, CallbackInfo ci) {
        SmeltingListener.endFurnaceExtract();
    }
}
