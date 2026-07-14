package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.skills.alchemy.AlchemyPotionBrewer;
import com.gmail.nossr50.util.player.UserManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The K7 Alchemy XP hook: awards Alchemy XP when a brewing stand the player owns completes an mcMMO
 * brew (CONVERSION_TODO §B). Replaces the XP + brew slice of the legacy Bukkit
 * {@code InventoryListener#onBrew} / {@code AlchemyBrewTask} path.
 *
 * <p>Like the Smelting hook, vanilla exposes no brew-owner concept, so this has two parts:
 * <ul>
 *   <li><b>Owner tracking</b> — {@link UseBlockCallback} records the last player to right-click each
 *       brewing stand (keyed by {@link BlockPos#asLong()}), mirroring the furnace-owner map. Without
 *       an owner a brew still completes (custom potions are not left stuck) but earns no one XP.</li>
 *   <li><b>Brew detection + craft</b> — {@code fabric/mixin/BrewingStandBlockEntityMixin} injects into
 *       the block entity's private {@code canCraft}/{@code craft} statics and calls
 *       {@link #isValidBrew} / {@link #onBrewCraft}. The brew resolution + inventory mutation live in
 *       {@link AlchemyPotionBrewer}; this listener only supplies the owner for the XP award.</li>
 * </ul>
 *
 * <p><b>Port caveat</b> (same as {@code SmeltingListener}): owners are keyed by block position only
 * (not dimension). In singleplayer the owner is always the one player, so this is harmless. The map
 * is cleared on server stop via {@link #clearOwners()}.
 */
public final class AlchemyListener {

    /** Brewing-stand {@link BlockPos#asLong()} → owner UUID. See the class doc for the single-key caveat. */
    private static final Map<Long, UUID> BREWING_STAND_OWNERS = new ConcurrentHashMap<>();

    private AlchemyListener() {
    }

    /** Register the owner-tracking interaction hook. Called once at mod load from {@code McMMOMod}. */
    public static void register() {
        UseBlockCallback.EVENT.register(AlchemyListener::onUseBlock);
    }

    /** Drop all tracked brewing-stand owners (called on server stop so the next world starts clean). */
    public static void clearOwners() {
        BREWING_STAND_OWNERS.clear();
    }

    /** Right-click a brewing stand → remember this player as its owner for XP-award purposes. */
    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand,
            BlockHitResult hitResult) {
        if (!(player instanceof ServerPlayerEntity)) {
            return ActionResult.PASS; // client-side fire — the server copy does the bookkeeping.
        }
        final BlockPos pos = hitResult.getBlockPos();
        if (world.getBlockEntity(pos) instanceof BrewingStandBlockEntity) {
            BREWING_STAND_OWNERS.put(pos.asLong(), player.getUuid());
        }
        return ActionResult.PASS; // observe only; never cancel opening the brewing stand.
    }

    /**
     * Whether the brewing stand's contents form a valid mcMMO brew. Called from the {@code canCraft}
     * mixin to force vanilla to start/continue a brew for recipes it does not itself recognise.
     */
    public static boolean isValidBrew(DefaultedList<ItemStack> slots) {
        return AlchemyPotionBrewer.isValidBrew(slots);
    }

    /**
     * Complete an mcMMO brew and award XP to the stand's owner. Called from the {@code craft} mixin
     * (which then cancels vanilla's craft). The brew still finishes when the stand has no tracked
     * owner or the owner is not loaded — it just earns no XP.
     *
     * @param world the brewing stand's world
     * @param pos   the brewing stand position
     * @param slots the brewing-stand inventory to transform in place
     */
    public static void onBrewCraft(World world, BlockPos pos, DefaultedList<ItemStack> slots) {
        McMMOPlayer owner = null;
        final UUID ownerId = BREWING_STAND_OWNERS.get(pos.asLong());
        if (ownerId != null) {
            owner = UserManager.getPlayer(ownerId);
        }
        AlchemyPotionBrewer.finishBrewing(slots, owner);
    }
}
