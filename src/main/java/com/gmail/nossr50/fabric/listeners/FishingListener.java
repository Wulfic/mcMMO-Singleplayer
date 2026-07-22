package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.treasure.EnchantmentTreasure;
import com.gmail.nossr50.datatypes.treasure.FishingTreasure;
import com.gmail.nossr50.datatypes.treasure.Rarity;
import com.gmail.nossr50.datatypes.treasure.ShakeTreasure;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.ItemSpecBuilder;
import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.skills.fishing.FishingManager;
import com.gmail.nossr50.skills.fishing.FishingManager.MasterAnglerWaitTimes;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.skills.CombatUtils;
import com.gmail.nossr50.util.skills.RankUtils;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

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
 * <p><b>Master Angler is also wired here</b> ({@link #resolveWaitCountdown}), driven by
 * {@code fabric/mixin/FishingWaitTimeMixin} — see that class for the seam. It replaces legacy's
 * {@code MasterAnglerTask}, which needed a scheduled task only because a Bukkit event handler could
 * not sit where the wait is actually drawn.
 *
 * <p><b>Shake is wired here too</b> ({@link #onEntityHooked}), driven by the second injector in
 * {@code FishingBobberUseMixin}: reeling in a hooked mob is legacy's {@code CAUGHT_ENTITY} state, and
 * a successful roll knocks a configured drop off the mob, damages it and pays flat Shake XP.
 *
 * <p><b>Magic Hunter is wired here too</b> ({@link #maybeApplyMagicHunter}): a caught treasure that is
 * enchantable can arrive enchanted, rolled off the {@code Enchantment_Drop_Rates} tier curve and the
 * {@code Enchantments_Rarity} table. See that method for the three MC-typed pieces it owns (dynamic
 * registry resolution, applicability filtering, and the unsafe enchant write).
 *
 * <p><b>Still deferred:</b> enchanted-book treasures (legacy {@code FishingTreasureBook} draws a random
 * enchantment from the whole registry under a per-book whitelist/blacklist, a different mechanism from
 * the Magic Hunter table — see {@code FishingTreasureConfig}) and the exploit item-removal punishment
 * (we skip the XP <i>and</i> the treasure roll on an exploiting catch — the same early-return gate).
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

        maybeCatchTreasure(serverPlayer, mmoPlayer, fishingManager, caught);
    }

    /**
     * Roll the Treasure Hunter reward and, on a hit, inject it into {@code caught} (the loot collection
     * vanilla spawns) and award its bonus XP. Ports the treasure half of legacy {@code processFishing}:
     * with {@code Extra_Fish} off the treasure replaces the fish, with it on the fish is kept too.
     */
    private static void maybeCatchTreasure(ServerPlayerEntity serverPlayer, McMMOPlayer mmoPlayer,
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

        if (maybeApplyMagicHunter(serverPlayer, fishingManager, treasureStack, rng)) {
            NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                    "Fishing.Ability.TH.MagicFound");
        }

        // Extra_Fish off (the shipped default) => the treasure supplants the fish; on => keep both.
        if (!McMMOMod.getGeneralConfig().getFishingExtraFish()) {
            caught.clear();
        }
        caught.add(treasureStack);

        fishingManager.awardFishingTreasureXP(rolled.get().getXp());
    }

    /**
     * Magic Hunter: enchant a caught treasure. The MC-typed half of legacy
     * {@code FishingManager#processMagicHunter} — the two decisions it makes (which rarity band wins,
     * and which of that band's enchantments land) are MC-free on the manager; this method owns the
     * three things that need Minecraft:
     *
     * <ol>
     *   <li><b>Resolving the registry paths.</b> 1.21 enchantments live in a <i>dynamic</i>
     *       (datapack-driven) registry, so they cannot be resolved when {@code FishingTreasureConfig}
     *       loads — the table holds {@link EnchantmentTreasure} paths and we look them up here off the
     *       player's {@code DynamicRegistryManager}. This was the "dynamic enchant registry adapter"
     *       that deferred Magic Hunter for the whole port; it is one {@code Registry#getEntry} call.</li>
     *   <li><b>Filtering to applicable enchantments</b> — legacy {@code getPossibleEnchantments}, whose
     *       Bukkit {@code canEnchantItem} is vanilla's {@code Enchantment#isAcceptableItem}
     *       (bytecode-verified: {@code definition.supportedItems().contains(item)}). Legacy cached this
     *       per {@code Material}; the vanilla call is a {@code RegistryEntryList#contains} on a list of
     *       at most a few dozen items, so the cache buys nothing and is dropped.</li>
     *   <li><b>Writing the enchantments</b>, via the same {@code ItemEnchantmentsComponent.Builder}
     *       surface Arcane Forging uses. {@code Builder#set} applies no level restriction, so it is
     *       exactly legacy's {@code addUnsafeEnchantments} — which matters, because the shipped table
     *       is within vanilla limits but an operator's need not be.</li>
     * </ol>
     *
     * <p>Gated on {@link FishingManager#isMagicHunterEnabled()} (both Magic Hunter <i>and</i> Treasure
     * Hunter unlocked and enabled) and on the drop being enchantable at all — legacy
     * {@code ItemUtils.isEnchantable}, kept as the mcMMO {@code MaterialMapStore} whitelist rather than
     * re-derived from vanilla, so the set of items Magic Hunter will touch stays exactly upstream's.
     * It is the stricter of the two gates and short-circuits the whole path for an ordinary catch.
     *
     * @return whether any enchantment was applied (the caller sends the notification if so)
     */
    private static boolean maybeApplyMagicHunter(ServerPlayerEntity serverPlayer,
            FishingManager fishingManager, ItemStack treasureStack, ThreadLocalRandom rng) {
        if (!fishingManager.isMagicHunterEnabled()) {
            return false;
        }
        final String itemId = Registries.ITEM.getId(treasureStack.getItem()).getPath();
        if (!McMMOMod.getMaterialMapStore().isEnchantable(itemId)) {
            return false;
        }

        final Optional<Rarity> rarity = fishingManager.rollMagicHunterRarity(
                rng.nextDouble() * 100.0);
        if (rarity.isEmpty()) {
            return false; // the enchant roll cleared every band — an ordinary unenchanted treasure.
        }

        final Registry<Enchantment> enchantmentRegistry =
                serverPlayer.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        final Map<String, RegistryEntry<Enchantment>> resolved = new HashMap<>();
        final List<EnchantmentTreasure> candidates = new ArrayList<>();

        for (EnchantmentTreasure candidate : McMMOMod.getFishingTreasureConfig()
                .getEnchantmentTreasures(rarity.get())) {
            final Identifier id = Identifier.tryParse(candidate.enchantmentId());
            final Optional<RegistryEntry.Reference<Enchantment>> entry = id == null
                    ? Optional.empty()
                    : enchantmentRegistry.getEntry(id);

            if (entry.isEmpty()) {
                McMMOMod.LOGGER.warn("Skipping unknown Magic Hunter enchantment '{}' ({} band in"
                        + " fishing_treasures.yml) — no such enchantment in this world's registry.",
                        candidate.enchantmentId(), rarity.get());
                continue;
            }
            if (!entry.get().value().isAcceptableItem(treasureStack)) {
                continue; // legacy getPossibleEnchantments: this enchantment doesn't fit this item.
            }

            resolved.put(candidate.enchantmentId(), entry.get());
            candidates.add(candidate);
        }

        if (candidates.isEmpty()) {
            return false;
        }

        // Legacy shuffles so the halving walk doesn't permanently favour whoever is first in the file.
        Collections.shuffle(candidates, rng);

        final Set<RegistryEntry<Enchantment>> alreadyOnItem =
                EnchantmentHelper.getEnchantments(treasureStack).getEnchantments();
        final List<EnchantmentTreasure> chosen = fishingManager.selectMagicHunterEnchants(candidates,
                (selectedSoFar, candidate) -> conflictsWithAny(alreadyOnItem, selectedSoFar, resolved,
                        candidate),
                rng::nextInt);

        if (chosen.isEmpty()) {
            return false;
        }

        final ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(
                EnchantmentHelper.getEnchantments(treasureStack));
        for (EnchantmentTreasure treasure : chosen) {
            builder.set(resolved.get(treasure.enchantmentId()), treasure.level());
        }
        EnchantmentHelper.set(treasureStack, builder.build());
        return true;
    }

    /**
     * Whether {@code candidate} conflicts with anything already on the item or already picked in this
     * roll — legacy's {@code ItemMeta#hasConflictingEnchant}, whose CraftBukkit implementation is
     * vanilla's {@code Enchantment#canBeCombined} (bytecode-verified: not the same enchantment, and in
     * neither party's {@code exclusiveSet}).
     *
     * <p>The {@code selectedSoFar} half is this port's deviation — see
     * {@link FishingManager#selectMagicHunterEnchants} for why upstream's guard never fires.
     */
    private static boolean conflictsWithAny(Set<RegistryEntry<Enchantment>> alreadyOnItem,
            List<EnchantmentTreasure> selectedSoFar,
            Map<String, RegistryEntry<Enchantment>> resolved, EnchantmentTreasure candidate) {
        final RegistryEntry<Enchantment> entry = resolved.get(candidate.enchantmentId());

        for (RegistryEntry<Enchantment> existing : alreadyOnItem) {
            if (!Enchantment.canBeCombined(existing, entry)) {
                return true;
            }
        }
        for (EnchantmentTreasure selected : selectedSoFar) {
            if (!Enchantment.canBeCombined(resolved.get(selected.enchantmentId()), entry)) {
                return true;
            }
        }
        return false;
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
     * Shake: a player reels in a mob they hooked, and it drops something. Ports legacy
     * {@code FishingManager#shakeCheck}, reached from the {@code CAUGHT_ENTITY} arm of the legacy
     * {@code PlayerFishEvent} monitor. Called from {@code FishingBobberUseMixin} at the
     * {@code pullHookedEntity} call inside {@code FishingBobberEntity#use} — i.e. <i>before</i> vanilla
     * yanks the mob, which is exactly where CraftBukkit fired that event.
     *
     * <p>Unlike {@link #onFishCaught}, no anti-exploit gate applies: legacy's spam/same-spot checks
     * guard only the {@code CAUGHT_FISH} state.
     *
     * <p>Legacy's trailing {@code setFishingTarget()} is dropped for the same reason Master Angler drops
     * it — it discards the value it computes. Dropped with it: the {@code PLAYER} arm (the player-head
     * owner stamp and the {@code INVENTORY} steal), unreachable in singleplayer where the only player is
     * the angler — an honest collapse, the same call made for Disarm and Daze.
     *
     * @param bobber the bobber being reeled in (source of the owner and the hooked entity)
     */
    public static void onEntityHooked(FishingBobberEntity bobber) {
        if (!(bobber.getHookedEntity() instanceof LivingEntity target)) {
            return; // legacy canShake's `target instanceof LivingEntity` half (a hooked boat/item).
        }
        if (!(bobber.getPlayerOwner() instanceof ServerPlayerEntity serverPlayer)) {
            return; // client-side / null owner.
        }
        if (!(target.getEntityWorld() instanceof ServerWorld world)) {
            return; // the drop spawn and the damage are server-side only.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return; // data not loaded (e.g. mid-join).
        }
        final FishingManager fishingManager = mmoPlayer.getFishingManager();
        if (fishingManager == null || !fishingManager.canShake()
                || !fishingManager.rollShakeSuccess()) {
            return;
        }

        final String entityPath = Registries.ENTITY_TYPE.getId(target.getType()).getPath();
        final Optional<ShakeTreasure> rolled = fishingManager.rollShakeTreasure(entityPath,
                ThreadLocalRandom.current().nextInt(100));
        if (rolled.isEmpty()) {
            return; // this mob has no configured drops, or the roll cleared them all.
        }
        final Optional<ItemStack> built = ItemSpecBuilder.build(rolled.get().getDrop());
        if (built.isEmpty()) {
            return; // drop material has no vanilla item (logged by Materials) — no drop, no damage.
        }
        // Built before shearing so an unresolvable material can never shear a sheep for nothing. Legacy
        // could not hit that case (it held a real ItemStack from config load), so this is order-only.
        if (!shearIfWool(target, rolled.get().getDrop().getMaterialId())) {
            return; // an already-sheared sheep: legacy bails entirely — no drop, no damage, no XP.
        }

        final ItemEntity drop = new ItemEntity(world, target.getX(), target.getY(), target.getZ(),
                built.get());
        drop.setToDefaultPickupDelay(); // Bukkit's World#dropItem behaviour.
        world.spawnEntity(drop);

        // Attributed to the player, so it is mcMMO's own damage: the K1 seam passes it through and it
        // pays no combat XP — the role legacy's CUSTOM_DAMAGE marker played.
        CombatUtils.safeDealDamage(target, FishingManager.shakeDamage(target.getMaxHealth()),
                serverPlayer);
        fishingManager.awardShakeXP();
    }

    /**
     * Legacy's {@code SHEEP} arm of {@code shakeCheck}: shaking wool off a sheep shears it, and a sheep
     * that is already sheared yields nothing. A no-op (returning {@code true}) for any other mob, or for
     * a non-wool drop off a sheep.
     *
     * @param target the shaken mob
     * @param materialId the rolled drop's registry path, e.g. {@code "white_wool"}
     * @return whether the shake may proceed
     */
    private static boolean shearIfWool(LivingEntity target, String materialId) {
        if (!(target instanceof SheepEntity sheep) || !materialId.endsWith("wool")) {
            return true;
        }
        if (sheep.isSheared()) {
            return false;
        }
        sheep.setSheared(true);
        return true;
    }

    /**
     * Draw the bobber's next wait countdown, applying Master Angler when the owner qualifies. Ports
     * legacy {@code processMasterAngler}; called from {@code FishingWaitTimeMixin} in place of
     * vanilla's own {@code MathHelper.nextInt(random, 100, 600)}.
     *
     * <p>Any gate miss falls through to an unmodified vanilla draw, so a non-mcMMO player, an
     * unqualified one, or a bobber whose owner has left all fish exactly as they would without the mod.
     *
     * @param bobber the bobber drawing a new wait (source of the owner)
     * @param random the bobber's own RNG — used for both the vanilla and the reduced draw
     * @param vanillaMinWaitTicks vanilla's minimum wait bound (Bukkit's {@code getMinWaitTime})
     * @param vanillaMaxWaitTicks vanilla's maximum wait bound (Bukkit's {@code getMaxWaitTime})
     * @param lureReductionTicks the bobber's Lure reduction, which vanilla subtracts after this call
     * @return the wait countdown to store, in ticks
     */
    public static int resolveWaitCountdown(FishingBobberEntity bobber, Random random,
            int vanillaMinWaitTicks, int vanillaMaxWaitTicks, int lureReductionTicks) {
        final MasterAnglerWaitTimes times = masterAnglerWaitTimes(bobber, vanillaMinWaitTicks,
                vanillaMaxWaitTicks, lureReductionTicks);
        if (times == null) {
            return MathHelper.nextInt(random, vanillaMinWaitTicks, vanillaMaxWaitTicks);
        }

        final int drawn = MathHelper.nextInt(random, times.minWaitTicks(), times.maxWaitTicks());
        // Legacy's fishHook.setApplyLure(false): the Lure reduction has already been folded into the
        // max-wait reduction, so cancel the subtraction vanilla performs immediately after this call
        // rather than letting it apply twice.
        return times.disableLure() ? drawn + lureReductionTicks : drawn;
    }

    /**
     * The Master Angler gates from the legacy {@code PlayerFishEvent} {@code FISHING} arm, plus the
     * owner lookup. Returns {@code null} when Master Angler must not apply.
     *
     * <p>Legacy required a fishing rod in the main hand and skipped entirely when one was also in the
     * off hand ("prevent any potential odd behavior"); both are kept. Legacy read them at cast time and
     * we read them at wait-draw time — see {@code FishingWaitTimeMixin} for that deviation. Legacy's
     * trailing {@code setFishingTarget()} call is dropped: it discards the value it computes
     * ({@code getTargetBlock(...)} with no assignment), so it is dead code upstream.
     */
    private static MasterAnglerWaitTimes masterAnglerWaitTimes(FishingBobberEntity bobber,
            int minWaitTicks, int maxWaitTicks, int lureReductionTicks) {
        if (!(bobber.getPlayerOwner() instanceof ServerPlayerEntity serverPlayer)) {
            return null; // client-side / null owner.
        }
        if (!serverPlayer.getMainHandStack().isOf(Items.FISHING_ROD)
                || serverPlayer.getOffHandStack().isOf(Items.FISHING_ROD)) {
            return null;
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return null; // data not loaded (e.g. mid-join).
        }
        final FishingManager fishingManager = mmoPlayer.getFishingManager();
        if (fishingManager == null || !fishingManager.canMasterAngler()) {
            return null;
        }

        final boolean boatBonus = serverPlayer.getVehicle() instanceof AbstractBoatEntity;
        return fishingManager.resolveMasterAnglerWaitTimesFromLureTicks(minWaitTicks, maxWaitTicks,
                RankUtils.getRank(mmoPlayer, SubSkillType.FISHING_MASTER_ANGLER), boatBonus,
                lureReductionTicks);
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
