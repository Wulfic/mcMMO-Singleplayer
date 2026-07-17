package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.treasure.FishingTreasure;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.ItemSpecBuilder;
import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.skills.fishing.FishingManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
 * <p><b>Treasure Hunter loot is now wired</b> ({@link #maybeCatchTreasure}): after the base XP award,
 * we roll {@link FishingManager#rollFishingTreasure} and, on a hit, build the reward with
 * {@link ItemSpecBuilder} and inject it into the same caught-loot collection the mixin handed us — the
 * exact {@code ObjectArrayList} that {@code FishingBobberEntity#use} iterates to spawn the reeled-in
 * item entities (verified by bytecode), so the treasure flies to the player like a normal catch with no
 * bespoke entity-spawn glue. Faithful to legacy: with {@code Extra_Fish} off (the shipped default) the
 * treasure <i>replaces</i> the fish, with it on both are kept; the base catch XP is awarded either way.
 *
 * <p><b>Still deferred:</b> Magic Hunter enchant loot (needs the dynamic enchant registry + K3
 * enchant-write; its {@code FishingTreasureConfig} enchant/book tables are deferred with it), the
 * {@code Shake} ability, Master Angler wait-time mutation, and the exploit item-removal punishment (we
 * skip the XP <i>and</i> the treasure roll on an exploiting catch — the same early-return gate).
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

        // Base catch XP is awarded on the *original* caught items, before the treasure roll can replace
        // them below — legacy pays base + treasure XP even when the treasure supplants the fish.
        for (ItemStack stack : caught) {
            if (stack.isEmpty()) {
                continue;
            }
            final String materialConfigString = ConfigStringUtils.getMaterialConfigString(
                    Registries.ITEM.getId(stack.getItem()).getPath());
            fishingManager.awardFishingXP(materialConfigString);
        }

        maybeCatchTreasure(serverPlayer, fishingManager, caught);
    }

    /**
     * Roll the Treasure Hunter reward and, on a hit, inject it into {@code caught} (the loot collection
     * vanilla spawns) and award its bonus XP. Ports the treasure half of legacy {@code processFishing}:
     * with {@code Extra_Fish} off the treasure replaces the fish, with it on the fish is kept too.
     */
    private static void maybeCatchTreasure(ServerPlayerEntity serverPlayer,
            FishingManager fishingManager, Collection<ItemStack> caught) {
        final ThreadLocalRandom rng = ThreadLocalRandom.current();
        final Optional<FishingTreasure> rolled = fishingManager.rollFishingTreasure(
                rng.nextDouble() * 100.0, luckOfTheSeaLevel(serverPlayer), rng::nextInt);
        if (rolled.isEmpty()) {
            return;
        }

        final Optional<ItemStack> built = ItemSpecBuilder.build(rolled.get().getDrop());
        if (built.isEmpty()) {
            return; // treasure material has no vanilla item (logged by Materials) — no drop, no XP.
        }
        final ItemStack treasureStack = built.get();
        applyRandomWear(treasureStack, rng);

        // Extra_Fish off (the shipped default) => the treasure supplants the fish; on => keep both.
        if (!McMMOMod.getGeneralConfig().getFishingExtraFish()) {
            caught.clear();
        }
        caught.add(treasureStack);

        fishingManager.awardFishingTreasureXP(rolled.get().getXp());
    }

    /**
     * A fished tool/armor piece arrives worn: legacy set a random durability on any damageable treasure.
     * Bukkit durability maps to vanilla damage, so a random damage in {@code [0, maxDamage)} reproduces
     * it. A no-op for non-damageable items.
     */
    private static void applyRandomWear(ItemStack stack, ThreadLocalRandom rng) {
        final int maxDamage = stack.getMaxDamage();
        if (maxDamage > 0) {
            stack.setDamage(rng.nextInt(maxDamage));
        }
    }

    /**
     * The Luck of the Sea level on the rod the player is fishing with. Reads the main hand when it holds
     * a fishing rod, otherwise the off hand (a catch guarantees the rod is in one of them) — legacy's
     * exact lookup. Enchantment level resolves off the stack's component with no world context needed.
     */
    private static int luckOfTheSeaLevel(ServerPlayerEntity player) {
        final ItemStack main = player.getMainHandStack();
        final ItemStack rod = main.isOf(Items.FISHING_ROD) ? main : player.getOffHandStack();
        return new PlatformItem(rod).getEnchantmentLevel(Enchantments.LUCK_OF_THE_SEA);
    }
}
