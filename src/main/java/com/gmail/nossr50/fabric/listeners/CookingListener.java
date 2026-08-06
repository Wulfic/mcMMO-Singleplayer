package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.skills.cooking.CookingManager;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Cooking's own two seams: the <b>crafting grid</b> (a player taking a crafted food out of a result
 * slot) and the <b>campfire</b> (a cook finishing on a lit campfire or soul campfire).
 *
 * <p>The furnace half of the skill does <em>not</em> live here — it rides the furnace-owner map and
 * the {@code craftRecipe} injector that Smelting already owns, so it is the food branch of
 * {@link SmeltingListener#onFurnaceSmelt}.
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

    /**
     * Campfire {@link BlockPos#asLong()} → owner UUID — the campfire twin of Smelting's
     * {@code FURNACE_OWNERS}, with the same position-only keying caveat (singleplayer has one player,
     * so a same-coordinates campfire in another dimension awards the same person).
     *
     * <h2>🔑 Why a plain owner map is enough, against the plan's "pos + slot" prescription</h2>
     * The Cooking plan expected a per-<em>slot</em> map, on the reasoning that
     * {@code CampfireBlockEntity#addItem(ServerWorld, LivingEntity, ItemStack)} is the only place the
     * owner is known and {@code litServerTick} — where the cook finishes — cannot see it. That is
     * true of {@code addItem} and irrelevant, because the same javap pass shows the campfire is
     * <b>reachable by a player right-click and by nothing else</b>:
     * <ul>
     *   <li>{@code addItem} has exactly <b>one caller</b> in the whole jar,
     *       {@code CampfireBlock#onUseWithItem}, which is a player interaction;</li>
     *   <li>{@code CampfireBlockEntity extends BlockEntity implements Clearable} — it is <b>not an
     *       {@code Inventory}</b>, so no hopper, dropper or minecart can put anything in it.</li>
     * </ul>
     * ⇒ {@link UseBlockCallback} sees every route food takes into a campfire, which is the same
     * seam and the same shape Smelting already uses, and it fires a full 600-tick cook before the
     * result is due. <b>A campfire is the one cooking block in the game that cannot be automated</b>,
     * which is also why it needs no rate gate of its own beyond the shared
     * {@code Max_Cooks_Per_Hour}.
     */
    private static final Map<Long, UUID> CAMPFIRE_OWNERS = new ConcurrentHashMap<>();

    private CookingListener() {
    }

    /** Register campfire owner tracking. Called once at mod load from {@code McMMOMod}. */
    public static void register() {
        UseBlockCallback.EVENT.register(CookingListener::onUseBlock);
    }

    /** Drop the tracked campfire owners so the next world session starts clean. */
    public static void clearOwners() {
        CAMPFIRE_OWNERS.clear();
    }

    /**
     * Right-click a campfire → remember this player as its owner for XP-award purposes. The exact
     * shape of {@code SmeltingListener#onUseBlock}, deliberately: a campfire is not an
     * {@code AbstractFurnaceBlockEntity}, so that hook does not reach it.
     *
     * <p>⚠️ Registered as a <em>second</em> {@link UseBlockCallback} rather than folded into
     * Smelting's, because the campfire is Cooking's alone — Smelting has no business in this map and
     * a shared one would have to be cleared by whichever listener happened to own it.
     *
     * <p>Package-private rather than private so {@code CookingListenerTest} can claim a campfire the
     * way a player does; there is no other route into {@link #CAMPFIRE_OWNERS}, and a hook with no
     * test route into it is how a mechanic ends up wired to nothing.
     */
    static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand,
            BlockHitResult hitResult) {
        if (!(player instanceof ServerPlayerEntity)) {
            // ⚠️ UseBlockCallback fires on BOTH logical sides and a client-side PASS is not neutral
            // — see the Repair anvil hook. The server copy does all the bookkeeping.
            return ActionResult.PASS;
        }
        final BlockPos pos = hitResult.getBlockPos();
        if (world.getBlockEntity(pos) instanceof CampfireBlockEntity) {
            CAMPFIRE_OWNERS.put(pos.asLong(), player.getUuid());
        }
        return ActionResult.PASS; // observe only; never cancel the interaction.
    }

    /**
     * Credit a finished campfire cook, and give Master Chef its roll at a second helping.
     *
     * <p>Called from {@code CampfireCookMixin} at the {@code ItemScatterer.spawn} call inside
     * {@code CampfireBlockEntity#litServerTick} — the one point at which a campfire cook has
     * finished. There is no output slot: the campfire <b>throws the cooked item on the floor</b>, so
     * the returned stack is what gets scattered and Master Chef's extra copy is an
     * {@code increment(1)} on it rather than a merge into an inventory. That is also why
     * {@code SmeltingManager#hasRoomForSecondSmelt} has no analogue here — the floor always has room.
     *
     * <h2>Why the XP is keyed on the input and the bonus drop on the result</h2>
     * Exactly as on the furnace, and for the same reason: {@code Experience_Values.Cooking.Cook} is
     * written against the raw input ({@code Beef}, {@code Kelp}) while {@code Bonus_Drops.Cooking} is
     * written against the result ({@code Cooked_Beef}). The campfire hands us both at once, so this
     * is the one seam where the two key spaces sit in the same method — do not "unify" them.
     *
     * <p>Kitchen Efficiency has no campfire arm on purpose: <b>a campfire burns no fuel</b>, so there
     * is no burn time to multiply. Master Chef does have one, because the shipped sub-skill
     * description says <em>"a second helping out of the heat"</em> and a campfire is heat — a
     * mechanic the tooltip promises and the code never delivers is worse than one that does not exist.
     *
     * <p>⚠️ The rate cap is deliberately <b>not</b> a condition of the bonus drop. The furnace path
     * behaves the same way ({@code onSmeltComplete} never consults the window), and a cap on XP that
     * silently also switched off an item drop would be two gates wearing one config key.
     *
     * @param world  the campfire's world — its time is the clock the rate cap is measured on
     * @param pos    the campfire position, the owner-map key
     * @param input  the raw stack that was being cooked, still intact at this point (vanilla clears
     *               the slot <em>after</em> the scatter)
     * @param result the stack vanilla is about to scatter
     * @return {@code result}, possibly incremented by Master Chef
     */
    public static ItemStack onCampfireCook(ServerWorld world, BlockPos pos, ItemStack input,
            ItemStack result) {
        if (input == result) {
            // ⚠️ Identity, not equality, and it is the precise test. litServerTick resolves the
            // result as `getFirstMatch(...).map(craft).orElse(rawStack)`, and `craft` returns a fresh
            // copy — so the result IS the input object only when no campfire recipe matched and the
            // raw item is being spat back out. That can only happen if a data pack reload removed the
            // recipe mid-cook, and it must pay nothing: nothing was cooked.
            return result;
        }
        if (input.isEmpty() || result.isEmpty()) {
            return result;
        }
        final McMMOPlayer owner = campfireOwner(pos);
        if (owner == null) {
            return result; // nobody has touched this campfire this session, or their data isn't loaded.
        }
        final CookingManager cooking = owner.getCookingManager();
        final CookingManager.CookAward award =
                cooking.onCook(SmeltingListener.materialConfigString(input), world.getTime());
        if (award.capReached()) {
            // Once per window, not once per cook — see SmeltingListener#onFurnaceSmelt.
            NotificationManager.sendPlayerInformation(owner, NotificationType.SUBSKILL_MESSAGE,
                    "Cooking.CookRateCap.Reached");
        }
        if (cooking.canSecondHelping(SmeltingListener.materialConfigString(result))) {
            result.increment(1);
        }
        return result;
    }

    /**
     * The player who owns the campfire at {@code pos}, or {@code null} when nobody has interacted
     * with it this session or their data is not loaded. An unowned campfire behaves exactly like
     * vanilla.
     */
    private static McMMOPlayer campfireOwner(BlockPos pos) {
        final UUID ownerId = CAMPFIRE_OWNERS.get(pos.asLong());
        return ownerId == null ? null : UserManager.getPlayer(ownerId);
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
