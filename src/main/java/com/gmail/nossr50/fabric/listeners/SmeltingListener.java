package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.smelting.SmeltingManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The K7 Smelting XP hook: awards Smelting XP when a furnace the player owns completes a smelt
 * (CONVERSION_TODO §B). Replaces the XP slice of legacy {@code InventoryListener#onFurnaceSmelt}
 * (which read the Bukkit {@code FurnaceSmeltEvent}).
 *
 * <p>Vanilla has no smelt event, so this has two parts:
 * <ul>
 *   <li><b>Owner tracking</b> — {@link UseBlockCallback} records the last player to right-click each
 *       furnace (keyed by {@link BlockPos#asLong()}), mirroring legacy's furnace-owner map. Without an
 *       owner a furnace earns no one XP.</li>
 *   <li><b>Smelt detection</b> — {@code fabric/mixin/AbstractFurnaceSmeltMixin} injects at the
 *       {@code craftRecipe} call inside {@code AbstractFurnaceBlockEntity#tick} (only reached when a
 *       smelt completes) and calls {@link #onFurnaceSmelt}.</li>
 * </ul>
 *
 * <p>The smelted <em>input</em> material's registry path ({@code minecraft:iron_ore} → {@code
 * "Iron_Ore"}) is the config string {@code ExperienceConfig.getSmeltingXP} is keyed on; the MC-free
 * award lives on {@link SmeltingManager#awardSmeltingXP(String)}.
 *
 * <p>Two more furnace behaviours ride the same owner map and the same mixin:
 * <ul>
 *   <li><b>Second Smelt</b> ({@link #onSmeltComplete}) — a chance at a second copy of the result,
 *       applied just after vanilla's {@code craftRecipe} has merged the first one in;</li>
 *   <li><b>Fuel Efficiency</b> ({@link #boostFuelTime}) — multiplies vanilla's burn time for the fuel
 *       item the furnace is about to consume.</li>
 * </ul>
 *
 * <p><b>Understanding the Art</b> ({@link #beginFurnaceExtract} / {@link #boostVanillaXp}) does
 * <em>not</em> use the owner map: legacy's {@code FurnaceExtractEvent} boosted the vanilla XP of the
 * player doing the extracting, whoever that is. It rides a different pair of seams — see
 * {@link #beginFurnaceExtract}.
 *
 * <p><b>Port caveat:</b> owners are keyed by block position only (not dimension). In singleplayer the
 * owner is always the one player, so a same-coordinates furnace in another dimension awards the same
 * person — harmless. The map is cleared on server stop via {@link #clearOwners()}.
 */
public final class SmeltingListener {

    /** Furnace {@link BlockPos#asLong()} → owner UUID. See the class doc for the single-key caveat. */
    private static final Map<Long, UUID> FURNACE_OWNERS = new ConcurrentHashMap<>();

    /**
     * The Understanding the Art multiplier for the furnace extraction currently in flight, or unset
     * when there is none. Set at the head of {@code FurnaceOutputSlot#onCrafted} and cleared at its
     * return, so it is live for exactly the nested {@code dropExperienceForRecipesUsed} call that
     * spawns the orbs — the {@code CombatUtils.IN_MCMMO_DAMAGE} / {@code Archery.beginBowShot} shape.
     */
    private static final ThreadLocal<Integer> VANILLA_XP_MULTIPLIER = new ThreadLocal<>();

    /**
     * The items that come out of a smelting recipe whose input is an ore block — the port of legacy
     * {@code ItemUtils.isSmelted}, which asked the same question per call by scanning
     * {@code Server#getRecipesFor(item)}. Derived once from the loaded recipe set (see
     * {@link #indexSmeltedOreProducts}) rather than per extraction, and rebuilt whenever data packs
     * are re-read. Empty until the server has started, which fails safe: no boost, vanilla XP.
     */
    private static volatile Set<Item> smeltedOreProducts = Set.of();

    /**
     * A throwaway input for {@code SmeltingRecipe#craft}, which is the only public way to read a
     * recipe's result stack ({@code SingleStackRecipe#result()} is protected). Bytecode-verified:
     * {@code SingleStackRecipe#craft} ignores both arguments and returns {@code result.copy()}.
     */
    private static final SingleStackRecipeInput EMPTY_RECIPE_INPUT =
            new SingleStackRecipeInput(ItemStack.EMPTY);

    private SmeltingListener() {
    }

    /**
     * Register the owner-tracking interaction hook and the smelted-ore-product indexing hooks. Called
     * once at mod load from {@code McMMOMod}.
     */
    public static void register() {
        UseBlockCallback.EVENT.register(SmeltingListener::onUseBlock);
        // The index is built the moment recipes are available rather than lazily on first extraction:
        // it is a one-off pass, it keeps the extraction path allocation-free, and it means the boot
        // log says out loud whether the scan found anything.
        ServerLifecycleEvents.SERVER_STARTED.register(SmeltingListener::indexSmeltedOreProducts);
        // A data-pack reload can add, remove or re-target smelting recipes, so rebuild from the new set.
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
                (server, resourceManager, success) -> indexSmeltedOreProducts(server));
    }

    /**
     * Drop all per-world Smelting state — tracked furnace owners and the derived smelted-ore-product
     * index — so the next world starts clean. Called on server stop.
     */
    public static void clearOwners() {
        FURNACE_OWNERS.clear();
        smeltedOreProducts = Set.of();
    }

    /** Right-click a furnace → remember this player as its owner for XP-award purposes. */
    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand,
            BlockHitResult hitResult) {
        if (!(player instanceof ServerPlayerEntity)) {
            return ActionResult.PASS; // client-side fire — the server copy does the bookkeeping.
        }
        final BlockPos pos = hitResult.getBlockPos();
        if (world.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity) {
            FURNACE_OWNERS.put(pos.asLong(), player.getUuid());
        }
        return ActionResult.PASS; // observe only; never cancel opening the furnace.
    }

    /**
     * Award Smelting XP to the furnace's owner for a freshly smelted item. Called from the furnace
     * smelt mixin. A no-op when the furnace has no tracked owner, the owner isn't loaded, or the
     * smelted material carries no configured Smelting XP.
     *
     * @param world the furnace's world
     * @param pos   the furnace position
     * @param input the item that was smelted (the input slot's stack, read before it is consumed)
     */
    public static void onFurnaceSmelt(ServerWorld world, BlockPos pos, ItemStack input) {
        if (input.isEmpty()) {
            return;
        }
        final SmeltingManager smelting = ownerSkill(pos);
        if (smelting == null) {
            return;
        }
        smelting.awardSmeltingXP(materialConfigString(input));
    }

    /**
     * Second Smelt: give the owner a chance at one extra copy of a just-finished smelt. Called from
     * the furnace mixin at the point vanilla has already merged the smelt result into the output slot
     * (legacy {@code processDoubleSmelt}, which ran on {@code FurnaceSmeltEvent} and bumped the
     * event's result stack by one).
     *
     * <p>The room check reads the output stack <em>after</em> that merge — see
     * {@link SmeltingManager#hasRoomForSecondSmelt} for why that is the same test legacy made against
     * the pre-merge count. The stack is the furnace's live inventory entry (bytecode-verified:
     * {@code LockableContainerBlockEntity#getStack} returns {@code getHeldStacks().get(slot)}, not a
     * copy), so {@code increment} mutates the furnace exactly as vanilla's own {@code craftRecipe}
     * does, and vanilla's end-of-tick {@code markDirty} covers it.
     *
     * <p><b>Deviation (deliberate, and non-observable under the shipped configs):</b> legacy reached
     * {@code processDoubleSmelt} only after {@code onFurnaceSmeltEvent}'s {@code ItemUtils.isSmeltable}
     * gate on the furnace's <em>input</em>. We cannot re-check that here — {@code craftRecipe} has
     * already decremented the input, which is empty whenever the last of it was just consumed — so the
     * only gate is the {@code Bonus_Drops.Smelting} entry for the <em>result</em>. Those two coincide
     * for every vanilla furnace recipe: each result listed under {@code Bonus_Drops.Smelting} is
     * produced only from inputs that carry Smelting XP. An operator who added, say, a cooked food to
     * {@code Bonus_Drops.Smelting} would get a Second Smelt where legacy gave none.
     *
     * @param output the furnace's output slot stack, holding the freshly smelted result
     */
    public static void onSmeltComplete(BlockPos pos, ItemStack output) {
        if (output.isEmpty()) {
            return; // craftRecipe always fills this; guard anyway so an odd recipe can't NPE the tick.
        }
        if (!SmeltingManager.hasRoomForSecondSmelt(output.getCount(), output.getMaxCount())) {
            return; // no room for the extra item — checked before the RNG, as legacy did.
        }
        final SmeltingManager smelting = ownerSkill(pos);
        if (smelting == null) {
            return;
        }
        if (smelting.canSecondSmelt(materialConfigString(output))) {
            output.increment(1);
        }
    }

    /**
     * Fuel Efficiency: multiply the burn time of the fuel item a furnace is about to consume, for the
     * furnace's owner. This is the legacy {@code onFurnaceBurnEvent} arm — including its gate that the
     * furnace must actually be smelting something mcMMO counts as smeltable, so cooking food burns at
     * vanilla speed.
     *
     * @param burnTime vanilla's own burn time for the fuel
     * @param input    the furnace's input slot stack (what it is about to smelt)
     * @return the boosted burn time, or {@code burnTime} unchanged when the bonus does not apply
     */
    public static int boostFuelTime(int burnTime, BlockPos pos, ItemStack input) {
        if (burnTime <= 0 || input.isEmpty()) {
            return burnTime;
        }
        if (!SmeltingManager.isSmeltable(materialConfigString(input))) {
            return burnTime;
        }
        final SmeltingManager smelting = ownerSkill(pos);
        if (smelting == null) {
            return burnTime;
        }
        return smelting.boostFuelTime(burnTime);
    }

    /**
     * Understanding the Art, half one: work out this extraction's vanilla-XP multiplier and park it
     * where {@link #boostVanillaXp} can find it. This is the legacy {@code onFurnaceExtractEvent}
     * arm, which multiplied {@code event.getExpToDrop()} by the extracting player's rank.
     *
     * <p>Vanilla has no extract event. The equivalent funnel is
     * {@code FurnaceOutputSlot#onCrafted(ItemStack)}: both ways of getting an item out of a furnace
     * output slot — a normal take ({@code onTakeItem}) and a shift-click
     * ({@code onQuickTransfer} → {@code onCrafted(ItemStack, int)}) — route through it
     * (bytecode-verified), and it is the sole caller of
     * {@code AbstractFurnaceBlockEntity#dropExperienceForRecipesUsed}, which is what actually spawns
     * the orbs. Because the spawn is nested inside that one call, a thread-local carries the
     * multiplier down to it; {@link #endFurnaceExtract} clears it on the way out.
     *
     * <p>Nothing is stored when the boost would not apply, so the orb path stays vanilla for anyone
     * without the sub-skill and for the non-player drop path (breaking a furnace with stored XP calls
     * {@code getRecipesUsedAndDropExperience} directly, never this).
     *
     * @param player    the player taking the item (legacy used the extractor, not the furnace owner)
     * @param extracted the stack being taken out of the output slot
     */
    public static void beginFurnaceExtract(PlayerEntity player, ItemStack extracted) {
        if (!(player instanceof ServerPlayerEntity) || extracted.isEmpty()) {
            return; // client-side copy of the screen handler, or nothing actually taken.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(player.getUuid());
        if (mmoPlayer == null) {
            return; // player data not loaded — behave exactly like vanilla.
        }
        final int multiplier = mmoPlayer.getSmeltingManager().getVanillaXpBoostMultiplier();
        if (multiplier <= 1) {
            return; // unranked or sub-skill off — checked before the item gate, it is the cheaper test.
        }
        if (!smeltedOreProducts.contains(extracted.getItem())) {
            return; // legacy's ItemUtils.isSmelted gate: only ore smelts get the boost.
        }
        VANILLA_XP_MULTIPLIER.set(multiplier);
    }

    /** Clear the in-flight extraction multiplier. Pairs with {@link #beginFurnaceExtract}. */
    public static void endFurnaceExtract() {
        VANILLA_XP_MULTIPLIER.remove();
    }

    /**
     * Understanding the Art, half two: multiply the size of an XP orb the furnace is about to drop.
     * Called from the {@code dropExperience} hook; a no-op unless {@link #beginFurnaceExtract} put a
     * multiplier in play for the extraction currently running on this thread.
     *
     * <p>Legacy multiplied Bukkit's already-rounded {@code getExpToDrop}, and vanilla's own
     * {@code dropExperience} does that same floor-plus-fractional-chance rounding before the spawn
     * call this hooks — so scaling here reproduces legacy's arithmetic, not just its intent.
     *
     * @param amount the orb size vanilla computed for this batch of used recipes
     */
    public static int boostVanillaXp(int amount) {
        final Integer multiplier = VANILLA_XP_MULTIPLIER.get();
        if (multiplier == null || amount <= 0) {
            return amount;
        }
        return amount * multiplier;
    }

    /**
     * Build the answer set for legacy {@code ItemUtils.isSmelted} — "is this item the product of a
     * furnace recipe whose input is an ore block?" — by walking the loaded recipes once. Legacy asked
     * that question per extraction via {@code Server#getRecipesFor(item)}; vanilla has no reverse
     * index from result to recipe, and building one costs a single pass, so it is done up front.
     *
     * <p>Runs on server start and again after any data-pack reload, both of which are the points at
     * which the recipe set is known-good. The count is logged because a silently empty index would
     * simply mean nobody ever gets the boost.
     */
    private static void indexSmeltedOreProducts(MinecraftServer server) {
        final List<RegistryEntry<Item>> ores = oreBlockItems();
        final Set<Item> products = new HashSet<>();
        for (RecipeEntry<?> entry : server.getRecipeManager().values()) {
            // SmeltingRecipe specifically, not AbstractCookingRecipe: legacy matched Bukkit's
            // FurnaceRecipe, so blasting/smoking/campfire variants of the same ore are excluded.
            if (!(entry.value() instanceof SmeltingRecipe recipe) || !hasOreBlockInput(recipe, ores)) {
                continue;
            }
            final ItemStack result = recipe.craft(EMPTY_RECIPE_INPUT, server.getRegistryManager());
            if (!result.isEmpty()) {
                products.add(result.getItem());
            }
        }
        smeltedOreProducts = Set.copyOf(products);
        McMMOMod.LOGGER.info("Smelting: {} ore block(s) smelt into {} product(s) eligible for the "
                + "Understanding the Art vanilla-XP boost", ores.size(), products.size());
    }

    /**
     * Legacy tested a recipe's single input with {@code getInput().getType().isBlock()} and
     * {@code MaterialUtils.isOre}. A vanilla {@code Ingredient} can accept several items (an ore and
     * its deepslate variant, say), so the test is asked the other way round — does the ingredient
     * accept any known ore block? — which also avoids {@code Ingredient#getMatchingItems},
     * deprecated in this version.
     */
    private static boolean hasOreBlockInput(SmeltingRecipe recipe, List<RegistryEntry<Item>> ores) {
        final Ingredient ingredient = recipe.ingredient();
        return ores.stream().anyMatch(ingredient::acceptsItem);
    }

    /**
     * Every registered item that is both a placeable block and one of mcMMO's ores — legacy's
     * {@code isBlock() && MaterialUtils.isOre(...)} pair, resolved against the live item registry so
     * the store's stale pre-1.13 ids ({@code quartz_ore}, {@code lapis_lazuli_ore}) simply never match.
     */
    private static List<RegistryEntry<Item>> oreBlockItems() {
        return Registries.ITEM.stream()
                .filter(item -> item instanceof BlockItem)
                .filter(item -> McMMOMod.getMaterialMapStore()
                        .isOre(Registries.ITEM.getId(item).getPath()))
                .map(Registries.ITEM::getEntry)
                .toList();
    }

    /**
     * The {@link SmeltingManager} of the player who owns the furnace at {@code pos}, or {@code null}
     * when nobody has interacted with it this session or the owner's data is not loaded. A furnace
     * with no tracked owner behaves exactly like vanilla.
     */
    private static SmeltingManager ownerSkill(BlockPos pos) {
        final UUID ownerId = FURNACE_OWNERS.get(pos.asLong());
        if (ownerId == null) {
            return null; // no one has interacted with this furnace this session.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(ownerId);
        if (mmoPlayer == null) {
            return null; // owner data not loaded (offline / not yet joined).
        }
        return mmoPlayer.getSmeltingManager();
    }

    /** e.g. {@code minecraft:iron_ore} → {@code "Iron_Ore"}, the key the smelting configs use. */
    private static String materialConfigString(ItemStack stack) {
        return ConfigStringUtils.getMaterialConfigString(
                Registries.ITEM.getId(stack.getItem()).getPath());
    }
}
