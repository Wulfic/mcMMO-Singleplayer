package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.skills.smelting.SmeltingManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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
 * <p><b>Deferred:</b> the Understanding the Art vanilla-XP boost — see {@link SmeltingManager} for
 * the seam and what still blocks it.
 *
 * <p><b>Port caveat:</b> owners are keyed by block position only (not dimension). In singleplayer the
 * owner is always the one player, so a same-coordinates furnace in another dimension awards the same
 * person — harmless. The map is cleared on server stop via {@link #clearOwners()}.
 */
public final class SmeltingListener {

    /** Furnace {@link BlockPos#asLong()} → owner UUID. See the class doc for the single-key caveat. */
    private static final Map<Long, UUID> FURNACE_OWNERS = new ConcurrentHashMap<>();

    private SmeltingListener() {
    }

    /** Register the owner-tracking interaction hook. Called once at mod load from {@code McMMOMod}. */
    public static void register() {
        UseBlockCallback.EVENT.register(SmeltingListener::onUseBlock);
    }

    /** Drop all tracked furnace owners (called on server stop so the next world starts clean). */
    public static void clearOwners() {
        FURNACE_OWNERS.clear();
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
