package com.gmail.nossr50.fabric.mixin;

import com.gmail.nossr50.fabric.listeners.CookingListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Cooking crafting-XP hook — the crafting-grid twin of {@link FurnaceOutputSlotMixin}, and
 * deliberately the same shape.
 *
 * <p>{@code onCrafted(ItemStack)} is the funnel: both ways of getting an item out of a crafting
 * result slot go through it (bytecode-verified — a normal take is {@code onTakeItem} →
 * {@code onCrafted(stack)}, a shift-click is {@code onQuickTransfer} →
 * {@code onCrafted(stack, amount)} → {@code onCrafted(stack)}). See {@link CookingListener} for why
 * this seam, rather than a recipe- or item-level one, is what keeps the 1.21 auto-crafter out.
 *
 * <h2>⚠️ HEAD is mandatory, and RETURN would fail silently</h2>
 * The batch size is the slot's private {@code amount}, and the <b>last statement of the method sets
 * it to 0</b> (bytecode: {@code aload_0; iconst_0; putfield amount}). A RETURN injection would
 * therefore read {@code 0} for every craft ever made, award nothing, and compile perfectly.
 *
 * <p>No {@code allow} is needed: this is a HEAD injection into a named method rather than a call-site
 * anchor, so there is no slice to be silently dropped (contrast {@code FishingWaitTimeMixin}).
 *
 * <p>No environment guard is used — {@link CookingListener#onCraftedItemTaken} bails on its first
 * line for a non-{@code ServerPlayerEntity}, which is the client's own copy of the screen handler.
 */
@Mixin(ResultSlot.class)
public abstract class CraftingResultSlotMixin {

    /** The slot's owner. Vanilla reads this same field to attribute the craft. */
    @Shadow
    @Final
    private Player player;

    /**
     * How many items this take produced. Accumulated by {@code takeStack} and
     * {@code onCrafted(ItemStack, int)}, consumed by {@code onCraftByPlayer}, and zeroed on the way
     * out — which is why the injection below is at HEAD.
     */
    @Shadow
    private int amount;

    @Inject(method = "checkTakeAchievements(Lnet/minecraft/world/item/ItemStack;)V", allow = 1, at = @At("HEAD"))
    private void mcmmo$onCraftedItemTaken(ItemStack stack, CallbackInfo ci) {
        CookingListener.onCraftedItemTaken(player, stack, amount);
    }
}
