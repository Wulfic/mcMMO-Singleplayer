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
 * <p><b>Deferred (K3 ItemStack/inventory adapter):</b> the Second Smelt result-doubling and the
 * vanilla-XP boost application — both mutate the furnace {@code ItemStack}s / dropped experience.
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
        final UUID ownerId = FURNACE_OWNERS.get(pos.asLong());
        if (ownerId == null) {
            return; // no one has interacted with this furnace this session — nobody to award.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(ownerId);
        if (mmoPlayer == null) {
            return; // owner data not loaded (offline / not yet joined).
        }
        final SmeltingManager smelting = mmoPlayer.getSmeltingManager();
        if (smelting == null) {
            return;
        }

        final String materialConfigString = ConfigStringUtils.getMaterialConfigString(
                Registries.ITEM.getId(input.getItem()).getPath());
        smelting.awardSmeltingXP(materialConfigString);
    }
}
