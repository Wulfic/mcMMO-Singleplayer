package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.skills.cooking.CookingManager;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The Cooking crafting-XP hook: awards Cooking XP when a player takes a crafted food out of a
 * crafting grid's result slot.
 *
 * <p>The furnace half of the skill does <em>not</em> live here — it rides the furnace-owner map and
 * the {@code craftRecipe} injector that Smelting already owns, so it is the food branch of
 * {@link SmeltingListener#onFurnaceSmelt}. This class is only the crafting-grid seam.
 *
 * <h2>🔑 Why {@code CraftingResultSlot#onCrafted(ItemStack)} and not a recipe- or item-level hook</h2>
 * It is the funnel and it is <b>player-only by construction</b>:
 * <ul>
 *   <li>Both routes out of the result slot reach it (bytecode-verified) — a normal take is
 *       {@code onTakeItem} → {@code onCrafted(stack)}, a shift-click is
 *       {@code onQuickTransfer} → {@code onCrafted(stack, amount)} → {@code onCrafted(stack)}. Exactly
 *       the {@code FurnaceOutputSlot} shape {@code SmeltingListener#beginFurnaceExtract} rides.</li>
 *   <li>{@code CrafterBlock} — the 1.21 auto-crafter — has its own
 *       {@code craft(BlockState, ServerWorld, BlockPos)} and <b>references
 *       {@code CraftingResultSlot} zero times</b> (javap-verified). A recipe-level or item-level hook
 *       would pay a redstone auto-crafter fed by a wheat farm; this one cannot.
 *       ⚠️ <b>Do not "generalise" this to a broader seam later without re-deriving that property.</b></li>
 * </ul>
 *
 * <h2>⚠️ The count comes from the slot's own {@code amount} field, and it is the whole batch</h2>
 * {@code onCrafted(ItemStack)} is called <b>once per take</b>, and the stack it is handed is a single
 * result — the batch size lives in the slot's private {@code amount}, which
 * {@code takeStack}/{@code onCrafted(stack, int)} accumulate and which the method passes to
 * {@code ItemStack#onCraftByPlayer(player, amount)}. Cooking XP is priced <b>per item</b> and
 * multiplied by that count, or one take of the cookie recipe pays for one cookie instead of eight
 * (and a shift-clicked stack pays 1/64th).
 */
public final class CookingListener {

    private CookingListener() {
    }

    /**
     * Award Cooking XP for a batch of crafted food. Called from {@code CraftingResultSlotMixin} at
     * the <b>head</b> of {@code onCrafted(ItemStack)}.
     *
     * <p>⚠️ The head, not the return: the method's last act is {@code this.amount = 0}, so a RETURN
     * injection would read a batch size of zero every time and pay nothing at all — silently, and
     * with a green compile.
     *
     * @param player the slot's owner; a non-{@link ServerPlayerEntity} is the client's own copy of
     *               the screen handler and is ignored, exactly as the furnace-extract hook does
     * @param result the crafted result stack (one item's worth — the count is in {@code items})
     * @param items  the slot's accumulated {@code amount}: how many items this take produced
     */
    public static void onCraftedItemTaken(PlayerEntity player, ItemStack result, int items) {
        if (!(player instanceof ServerPlayerEntity) || result.isEmpty() || items <= 0) {
            return; // client-side copy, an empty slot, or nothing actually taken.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(player.getUuid());
        if (mmoPlayer == null) {
            return; // player data not loaded — behave exactly like vanilla.
        }
        // Keyed on the RESULT, unlike the furnace path which is keyed on the input. The key
        // derivation itself is shared so the two can never drift apart.
        final String resultConfigString = SmeltingListener.materialConfigString(result);
        final CookingManager.CookAward award = mmoPlayer.getCookingManager()
                .onCraft(resultConfigString, items, player.getEntityWorld().getTime());
        if (award.capReached()) {
            // Once per window, not once per craft — see SmeltingListener#onFurnaceSmelt.
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Cooking.CookRateCap.Reached");
        }
    }
}
