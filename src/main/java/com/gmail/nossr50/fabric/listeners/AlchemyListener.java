package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.fabric.mixin.BrewingStandBrewTimeAccessor;
import com.gmail.nossr50.skills.alchemy.AlchemyPotionBrewer;
import com.gmail.nossr50.skills.alchemy.CatalysisTimer;
import com.gmail.nossr50.util.Permissions;
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
 *   <li><b>Catalysis brew speed</b> — the same mixin's {@code tick} hook calls
 *       {@link #applyCatalysis}, which shortens the owner's brew by the Catalysis multiplier. This is
 *       what replaces the legacy {@code AlchemyBrewTask}, whose only other job (running the brew
 *       itself) vanilla already does. Fraction carrying lives in the MC-free
 *       {@link CatalysisTimer}.</li>
 * </ul>
 *
 * <p><b>Port caveat</b> (same as {@code SmeltingListener}): owners are keyed by block position only
 * (not dimension). In singleplayer the owner is always the one player, so this is harmless. The map
 * is cleared on server stop via {@link #clearOwners()}.
 */
public final class AlchemyListener {

    /** Brewing-stand {@link BlockPos#asLong()} → owner UUID. See the class doc for the single-key caveat. */
    private static final Map<Long, UUID> BREWING_STAND_OWNERS = new ConcurrentHashMap<>();

    /** Per-stand Catalysis state: the speed captured at brew start, plus its carried fraction. */
    private static final CatalysisTimer CATALYSIS_TIMER = new CatalysisTimer();

    private AlchemyListener() {
    }

    /** Register the owner-tracking interaction hook. Called once at mod load from {@code McMMOMod}. */
    public static void register() {
        UseBlockCallback.EVENT.register(AlchemyListener::onUseBlock);
    }

    /** Drop all tracked brewing-stand owners (called on server stop so the next world starts clean). */
    public static void clearOwners() {
        BREWING_STAND_OWNERS.clear();
        CATALYSIS_TIMER.clear();
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

    /**
     * Shorten a running brew by the stand owner's Catalysis brew speed. Called at the head of every
     * {@code BrewingStandBlockEntity#tick}, i.e. before vanilla's own one-tick decrement, so the two
     * together burn {@code brewSpeed} timer ticks per game tick — the rate the legacy
     * {@code AlchemyBrewTask} drove its own timer at.
     *
     * <p>No validity re-check is needed here: vanilla zeroes {@code brewTime} itself the moment the
     * recipe stops being craftable, so a non-zero timer already means a brew is genuinely in progress
     * — one this mod either recognises itself or let vanilla keep (both were sped up by legacy too,
     * since its potion tree subsumes the vanilla recipes).
     *
     * <p>This runs every tick for every brewing stand in a loaded chunk, so the owner lookup and the
     * speed calculation sit behind {@link CatalysisTimer}'s supplier and happen once per brew rather
     * than once per tick — which is also exactly when legacy resolved them.
     *
     * @param pos   the brewing stand position (the key the owner map is built on)
     * @param stand the ticking brewing stand
     */
    public static void applyCatalysis(BlockPos pos, BrewingStandBlockEntity stand) {
        final long standKey = pos.asLong();
        final BrewingStandBrewTimeAccessor timer = (BrewingStandBrewTimeAccessor) stand;
        final int brewTime = timer.getBrewTime();

        if (brewTime <= 0) {
            // Idle stand (or a brew that just completed) — forget it, so the next brew re-resolves
            // the owner's speed instead of inheriting this one's.
            CATALYSIS_TIMER.reset(standKey);
            return;
        }

        final int extraTicks = CATALYSIS_TIMER.extraTicks(standKey,
                () -> resolveBrewSpeed(standKey));
        if (extraTicks > 0) {
            timer.setBrewTime(CatalysisTimer.reducedBrewTime(brewTime, extraTicks));
        }
    }

    /**
     * The Catalysis brew speed for whoever owns this stand, or {@link CatalysisTimer#VANILLA_BREW_SPEED}
     * when there is no owner to credit. Legacy fell back to the same 1.0 whenever it could not resolve
     * the container's owner.
     */
    private static double resolveBrewSpeed(long standKey) {
        final UUID ownerId = BREWING_STAND_OWNERS.get(standKey);
        if (ownerId == null) {
            return CatalysisTimer.VANILLA_BREW_SPEED;
        }
        final McMMOPlayer owner = UserManager.getPlayer(ownerId);
        if (owner == null) {
            return CatalysisTimer.VANILLA_BREW_SPEED;
        }
        if (!Permissions.isSubSkillEnabled(owner.getPlayer(), SubSkillType.ALCHEMY_CATALYSIS)) {
            return CatalysisTimer.VANILLA_BREW_SPEED;
        }
        return owner.getAlchemyManager().calculateBrewSpeed(
                Permissions.lucky(owner.getPlayer(), PrimarySkillType.ALCHEMY));
    }
}
