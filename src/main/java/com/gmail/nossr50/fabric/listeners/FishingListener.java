package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.fishing.FishingManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.Collection;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * The K7 Fishing XP hook: awards base Fishing XP when a player reels in a catch (CONVERSION_TODO §B).
 * Replaces the XP slice of the legacy {@code PlayerListener} {@code CAUGHT_FISH} handler (which read
 * the Bukkit {@code PlayerFishEvent}).
 *
 * <p>Vanilla fires no fishing event, so this is driven by {@code fabric/mixin/FishingBobberUseMixin},
 * which injects at the {@code Criteria.FISHING_ROD_HOOKED.trigger} call inside
 * {@code FishingBobberEntity#use} and hands us the caught-loot {@code Collection<ItemStack>} plus the
 * bobber. That criterion also fires for the reel-in-a-hooked-entity branch, but there vanilla passes an
 * empty collection — so an empty catch is a graceful no-op, faithfully matching legacy (which only
 * processed the {@code CAUGHT_FISH} state, not {@code CAUGHT_ENTITY}).
 *
 * <p>The base XP is keyed on the caught item's material config string
 * ({@code minecraft:cod} → {@code "Cod"}) via {@code ExperienceConfig.getXp(FISHING, ...)}; the MC-free
 * award lives on {@link FishingManager#awardFishingXP(String)}. The legacy anti-exploit gating
 * (spam-throttle {@link FishingManager#isFishingTooOften()} and same-spot
 * {@link FishingManager#processExploiting}/{@link FishingManager#isExploitingFishing()}, both keyed on
 * {@code ExploitFix.Fishing}) is replicated here using the bobber's position.
 *
 * <p><b>Deferred until the {@code FishingTreasureConfig} + ItemStack adapters (K7 remainder / K8):</b>
 * Treasure Hunter / Magic Hunter loot and its additive {@code treasureXp}, the Shake ability, Master
 * Angler wait-time mutation, and the exploit item-removal punishment (we skip the XP award but leave
 * the vanilla catch untouched — consistent with the award-only Smelting/Taming hooks).
 */
public final class FishingListener {

    private FishingListener() {
    }

    /**
     * Award base Fishing XP to the bobber's owner for a fresh catch. A no-op when the catch is empty
     * (reel-in-a-hooked-entity branch), the owner is not a loaded server player, or the anti-exploit
     * gate trips. Called from the fishing bobber mixin.
     *
     * @param bobber the fishing bobber being reeled in (source of the owner and cast position)
     * @param caught the items the vanilla loot roll produced for this catch
     */
    public static void onFishCaught(FishingBobberEntity bobber, Collection<ItemStack> caught) {
        if (caught.isEmpty()) {
            return; // reel-in-a-hooked-entity branch: no fishing loot, no XP (legacy CAUGHT_ENTITY).
        }
        if (!(bobber.getPlayerOwner() instanceof ServerPlayerEntity serverPlayer)) {
            return; // client-side / null owner — the authoritative award happens on the server.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return; // data not loaded (e.g. mid-join).
        }
        final FishingManager fishingManager = mmoPlayer.getFishingManager();
        if (fishingManager == null) {
            return;
        }

        // Anti-exploit gating, faithful to the legacy CAUGHT_FISH handler.
        if (McMMOMod.getExperienceConfig().isFishingExploitingPrevented()) {
            if (fishingManager.isFishingTooOften()) {
                return; // recast spam within the 1s window — no XP.
            }
            fishingManager.processExploiting(bobber.getX(), bobber.getY(), bobber.getZ());
            if (fishingManager.isExploitingFishing()) {
                return; // fishing the same spot past the OverFishLimit — no XP.
            }
        }

        for (ItemStack stack : caught) {
            if (stack.isEmpty()) {
                continue;
            }
            final String materialConfigString = ConfigStringUtils.getMaterialConfigString(
                    Registries.ITEM.getId(stack.getItem()).getPath());
            fishingManager.awardFishingXP(materialConfigString);
        }
    }
}
