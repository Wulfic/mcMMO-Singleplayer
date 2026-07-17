package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.treasure.ItemSpec;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.BlockDrops;
import com.gmail.nossr50.platform.ItemSpecBuilder;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.skills.BlockBreakXp;
import com.gmail.nossr50.skills.excavation.ExcavationManager;
import com.gmail.nossr50.skills.herbalism.HerbalismManager;
import com.gmail.nossr50.skills.mining.MiningManager;
import com.gmail.nossr50.skills.woodcutting.TreeFellerProcessor;
import com.gmail.nossr50.skills.woodcutting.WoodcuttingManager;
import com.gmail.nossr50.util.BlockUtils;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.skills.SkillUtils;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Drives the gathering-skill block-break hooks (CONVERSION_TODO Phase 3): gathering XP for every
 * skill, plus the block-break side effects — Mining/Woodcutting/Herbalism bonus (double/triple)
 * drops, Excavation treasure, and the Tree Feller / Giga Drill Breaker super abilities. Replaces the XP + drop slice of the
 * legacy {@code BlockListener#onBlockBreak} / {@code MiningManager#miningBlockCheck} /
 * {@code HerbalismManager#checkDoubleDropsOnBrokenPlants}, plus the Herbalism Green Thumb crop
 * replant (legacy {@code processGreenThumbPlants} → {@code DelayedCropReplant}). Remaining side
 * effects (super-ability tool damage) land as their inventory/scheduler seams follow this same
 * pattern.
 *
 * <p>Uses Fabric's {@link PlayerBlockBreakEvents#AFTER}, which fires only once the break actually
 * succeeded (server side), so we never award XP or drops for a cancelled or client-predicted break.
 * The creative-mode / level-cap guards for XP live in the shared pipeline
 * ({@link McMMOPlayer#beginXpGain}); bonus drops are guarded against creative separately here (a
 * creative break spawns no vanilla loot, so bonus copies would be a duplication bug).
 */
public final class BlockBreakListener {

    private BlockBreakListener() {
    }

    /** Register the block-break hook. Called once at mod load from {@code McMMOMod#onInitialize}. */
    public static void register() {
        PlayerBlockBreakEvents.AFTER.register(BlockBreakListener::onBlockBroken);
    }

    private static void onBlockBroken(World world, PlayerEntity player, BlockPos pos,
            BlockState state, @Nullable BlockEntity blockEntity) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || !(world instanceof ServerWorld serverWorld)) {
            return; // client-side prediction / non-server context: ignore.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return; // data not loaded (e.g. mid-join).
        }

        // §A: a hand-placed block gives no gathering rewards — legacy gated its whole XP/drop/
        // super-ability branch on !UserBlockTracker.isIneligible(block). Read the flag before clearing
        // it (clearing marks the location eligible), then clear it unconditionally: the block is gone
        // now, so its position is natural again and the tracker's memory is freed on the way out.
        final boolean handPlaced = BlockUtils.isRewardIneligible(serverWorld, pos);
        BlockUtils.markNatural(serverWorld, pos);

        final String blockId = Registries.BLOCK.getId(state.getBlock()).toString();

        // Ageable farm crops (wheat/carrots/beetroots/…) are the exception to the placed-flag gate:
        // legacy rewards them on MATURITY, not on who placed them (a mature crop pays whether the
        // player planted it or found it wild, and an immature one never pays). So they bypass the
        // hand-placed early-return below and are handled by maturity here. Bizarre ageables
        // (cactus/kelp/sugar cane/bamboo) and chorus fall through to the normal gathering path — both
        // are deferred multi-block plants whose age can't be trusted for maturity.
        final BlockUtils.AgeableState ageable = BlockUtils.getAgeableState(state);
        if (ageable != null) {
            final HerbalismManager herbalism = mmoPlayer.getHerbalismManager();
            if (herbalism != null && herbalism.isMaturityGatedCrop(blockId)) {
                processMaturityGatedCrop(mmoPlayer, herbalism, serverWorld, pos, state, blockEntity,
                        serverPlayer, blockId, ageable);
                return; // a farm crop is never a Mining/Woodcutting/Excavation or super-ability block.
            }
        }

        if (handPlaced) {
            return; // placed by the player: no XP, no bonus drops, no treasure, no Tree Feller.
        }

        awardBlockXp(mmoPlayer, blockId);
        // Bonus drops never fire in creative (no vanilla loot spawns there to complement). Each
        // skill's bonus-drop path self-gates on its own config section (a log is never a Mining
        // bonus block and an ore is never a Woodcutting one), so running both per break is safe.
        if (!serverPlayer.isCreative()) {
            awardMiningBonusDrops(mmoPlayer, serverWorld, pos, state, blockEntity, serverPlayer,
                    blockId);
            awardWoodcuttingBonusDrops(mmoPlayer, serverWorld, pos, state, blockEntity, serverPlayer,
                    blockId);
            awardHerbalismBonusDrops(mmoPlayer, serverWorld, pos, state, blockEntity, serverPlayer,
                    blockId);
            awardExcavationTreasures(mmoPlayer, serverWorld, pos, blockId);
            // Giga Drill Breaker re-processes the block twice more (3× drops/XP) and wears the
            // shovel. It stays inside the creative gate with the base treasure roll: creative breaks
            // spawn no vanilla loot, so bonus treasure copies there would be a duplication bug.
            maybeProcessGigaDrillBreaker(mmoPlayer, serverWorld, pos, state, serverPlayer, blockId);
        }
        // Tree Feller self-gates on the super-ability mode + an axe, so it can run in creative too
        // (matching legacy, which never creative-gated it); the starting block above is untouched —
        // this fells the rest of the tree around it.
        maybeProcessTreeFeller(mmoPlayer, serverWorld, pos, state, serverPlayer);
    }

    /**
     * Fell the surrounding tree when the player broke a log with Tree Feller active while holding an
     * axe (legacy {@code BlockListener} Woodcutting branch → {@code canUseTreeFeller} →
     * {@code processTreeFeller}). The heavy lifting — the recursive search, drops and durability —
     * lives in {@link TreeFellerProcessor}; this only reproduces the trigger gate.
     */
    private static void maybeProcessTreeFeller(McMMOPlayer mmoPlayer, ServerWorld world, BlockPos pos,
            BlockState state, ServerPlayerEntity breaker) {
        if (!mmoPlayer.getAbilityMode(SuperAbilityType.TREE_FELLER)) {
            return;
        }
        if (!BlockUtils.hasWoodcuttingXP(state) || !ItemUtils.isAxe(breaker.getMainHandStack())) {
            return;
        }
        TreeFellerProcessor.process(world, pos, breaker, mmoPlayer);
    }

    /**
     * Re-process an excavated block twice more and wear down the shovel when Giga Drill Breaker is
     * active (legacy {@code ExcavationManager#gigaDrillBreaker}: two extra {@code excavationBlockCheck}
     * calls + {@code SkillUtils.handleDurabilityChange}). The base excavation XP and one treasure roll
     * already ran ({@link #awardBlockXp} / {@link #awardExcavationTreasures}); these two bonus rounds
     * bring it to the classic 3× excavation drops and XP, then the ability's extra tool damage is
     * applied to the shovel.
     *
     * <p>Gated exactly like legacy {@code BlockListener}'s excavation branch: the super-ability mode is
     * on, the block gives Excavation XP ({@link BlockUtils#affectedByGigaDrillBreaker}), and a shovel is
     * in hand. Called from inside the creative gate, so the bonus treasure spawns match the base path.
     */
    private static void maybeProcessGigaDrillBreaker(McMMOPlayer mmoPlayer, ServerWorld world,
            BlockPos pos, BlockState state, ServerPlayerEntity breaker, String blockId) {
        if (!mmoPlayer.getAbilityMode(SuperAbilityType.GIGA_DRILL_BREAKER)) {
            return;
        }
        final ItemStack tool = breaker.getMainHandStack();
        if (!BlockUtils.affectedByGigaDrillBreaker(state) || !ItemUtils.isShovel(tool)) {
            return;
        }
        // Two bonus excavation checks (the base one already ran) → 3× total drops/XP. Each treasure
        // roll is independent RNG, matching legacy's separate excavationBlockCheck calls.
        for (int i = 0; i < 2; i++) {
            awardBlockXp(mmoPlayer, blockId);
            awardExcavationTreasures(mmoPlayer, world, pos, blockId);
        }
        // Giga Drill Breaker wears the shovel harder than a normal break (extra ability tool damage).
        SkillUtils.handleDurabilityChange(new PlatformItem(tool),
                McMMOMod.getGeneralConfig().getAbilityToolDamage());
    }

    /**
     * Reward a broken ageable Herbalism crop when it was fully mature, mirroring legacy
     * {@code awardXPForPlantBlocks} + {@code checkDoubleDropsOnBrokenPlants}: an immature crop pays
     * nothing, a mature one pays its Herbalism XP and rolls double/triple drops. Maturity — not the
     * placed-block flag — is the entire gate here (see the call site), so harvesting a crop you
     * planted still earns XP once it grew, while spam-breaking immature crops earns nothing (the
     * anti-farm role legacy's placestore played for crops). Bonus drops stay creative-gated, as on
     * the generic path (a creative break spawns no vanilla loot to complement).
     *
     * <p>Green Thumb replant runs first (see {@link #maybeProcessGreenThumbReplant}) whether or not
     * the crop was mature — legacy replants immature crops at age 0 too. It only touches the
     * inventory and schedules the block re-set; it never awards XP or drops, so the maturity gate
     * below still owns those.
     */
    private static void processMaturityGatedCrop(McMMOPlayer mmoPlayer, HerbalismManager herbalism,
            ServerWorld world, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
            ServerPlayerEntity breaker, String blockId, BlockUtils.AgeableState ageable) {
        final boolean mature = herbalism.isAgeableMature(blockId, ageable.age(), ageable.maxAge());
        maybeProcessGreenThumbReplant(mmoPlayer, herbalism, world, pos, state, breaker, blockId,
                mature);
        if (!mature) {
            return; // immature crop: no XP, no bonus drops (legacy's maturity gate).
        }
        awardBlockXp(mmoPlayer, blockId);
        if (!breaker.isCreative()) {
            awardHerbalismBonusDrops(mmoPlayer, world, pos, state, blockEntity, breaker, blockId);
        }
    }

    /**
     * Attempt Green Thumb crop replant on a broken ageable crop, porting legacy
     * {@code HerbalismManager#processGreenThumbPlants} → {@code processGrowingPlants} →
     * {@code startReplantTask} → {@code DelayedCropReplant}. On a successful Green Thumb roll (or
     * while Green Terra is active) the player spends one replant seed and the crop is re-set at a
     * rank-scaled age a second later — an immature crop restarts at age 0, a mature one at the age
     * {@link HerbalismManager#resolveGreenThumbReplant} decides.
     *
     * <p>Gate order is legacy's: not sneaking → the crop is a configured replantable
     * ({@code Green_Thumb_Replanting_Crops}) → a hoe (or an axe, cocoa only) in the main hand → the
     * crop has a known replant seed → the Green Thumb roll succeeds (Green Terra bypasses it) → the
     * seed is present (main or off hand). Only then is the seed consumed and the replant scheduled.
     *
     * <p>PORT deviations, both forced by the {@code PlayerBlockBreakEvents.AFTER} seam (the block is
     * already broken and its drops spawned when we run, unlike legacy's cancellable pre-break
     * {@code BlockBreakEvent}):
     * <ul>
     *   <li><b>Immature-crop drop suppression is dropped.</b> Legacy called
     *       {@code blockBreakEvent.setDropItems(false)} when it replanted an immature crop; the drops
     *       are already out by the time this fires, so an immature crop keeps its (typically
     *       one-seed) drop. Net: replanting an immature crop is one seed cheaper than legacy. The
     *       common mature-crop path is unaffected — legacy never suppressed those drops.</li>
     *   <li>The {@code RecentlyReplantedCropMeta} "don't let the player instantly re-break the fresh
     *       sprout" guard is dropped (a cosmetic anti-accident polish); instead the deferred set only
     *       lands if the position is still air (see {@link #scheduleReplant}), so it never overwrites
     *       a block placed in the interim.</li>
     * </ul>
     */
    private static void maybeProcessGreenThumbReplant(McMMOPlayer mmoPlayer,
            HerbalismManager herbalism, ServerWorld world, BlockPos pos, BlockState state,
            ServerPlayerEntity breaker, String blockId, boolean mature) {
        if (breaker.isSneaking()) {
            return; // legacy: sneaking suppresses Green Thumb replant.
        }
        final String materialConfigString = ConfigStringUtils.getMaterialConfigString(blockId);
        if (!McMMOMod.getGeneralConfig().isGreenThumbReplantableCrop(materialConfigString)) {
            return;
        }
        // A hoe replants any crop; an axe replants cocoa only (legacy processGreenThumbPlants).
        final ItemStack tool = breaker.getMainHandStack();
        final boolean hoe = ItemUtils.isHoe(tool);
        final boolean axe = ItemUtils.isAxe(tool);
        if (!hoe && !axe) {
            return; // need a hoe or an axe in hand.
        }
        final boolean cocoa = "cocoa".equals(pathOf(state));
        if (axe && !cocoa) {
            return; // an axe only replants cocoa.
        }
        final Optional<String> seedPath = HerbalismManager.getGreenThumbReplantMaterial(blockId);
        if (seedPath.isEmpty()) {
            return; // not a replantable crop type.
        }
        if (!herbalism.rollGreenThumbReplant()) {
            return; // RNG failed and Green Terra isn't active.
        }
        final Optional<Item> seed = Materials.item(seedPath.get());
        if (seed.isEmpty()) {
            return; // the replant seed isn't an item in this registry.
        }
        final int seedSlot = findItemSlot(breaker.getInventory(), seed.get());
        if (seedSlot < 0) {
            return; // no replant seed to spend (legacy's hasItemIncludingOffHand).
        }
        final Optional<HerbalismManager.GreenThumbReplant> decision =
                herbalism.resolveGreenThumbReplant(blockId, mature, herbalism.isGreenTerraActive());
        if (decision.isEmpty()) {
            return; // crop can't replant (bizarre/unknown) — unreachable from the maturity path.
        }
        breaker.getInventory().removeStack(seedSlot, 1);
        scheduleReplant(world, pos, state, decision.get().finalAge());
        SoundManager.sendSound(mmoPlayer.getPlayer(), SoundType.ITEM_CONSUMED);
    }

    /**
     * Re-set a harvested crop at {@code finalAge} a second later (legacy {@code startReplantTask} →
     * {@code DelayedCropReplant}, {@code Misc.TICK_CONVERSION_FACTOR} ticks). The pre-break
     * {@link BlockState} is reused verbatim except for its age, so a cocoa pod's facing (and any
     * other property) is preserved for free — no {@code Directional} rebuild like legacy. The set
     * only lands if the spot is still air, so a block the player placed in the interim is never
     * overwritten (legacy's {@code blockIsAirOrExpectedCrop} guard).
     */
    private static void scheduleReplant(ServerWorld world, BlockPos pos, BlockState cropState,
            int finalAge) {
        final BlockState replant = BlockUtils.withAge(cropState, finalAge);
        McMMOMod.getScheduler().runLater(() -> {
            if (world.getBlockState(pos).isAir()) {
                world.setBlockState(pos, replant);
            }
        }, Misc.TICK_CONVERSION_FACTOR);
    }

    /**
     * First inventory slot holding {@code item}, or {@code -1} if none — matching legacy
     * {@code PlayerInventory#containsAtLeast(stack, 1)}, whose paired {@code removeItem} then consumes
     * one. Scans every slot (main, hotbar, off hand), covering legacy's
     * {@code hasItemIncludingOffHand}; mirrors {@code SuperAbilityListener#findItemSlot}.
     */
    private static int findItemSlot(PlayerInventory inventory, Item item) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            final ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && stack.isOf(item)) {
                return slot;
            }
        }
        return -1;
    }

    private static String pathOf(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).getPath();
    }

    private static void awardBlockXp(McMMOPlayer mmoPlayer, String blockId) {
        final BlockBreakXp.Reward reward = BlockBreakXp.resolve(blockId);
        if (reward != null) {
            mmoPlayer.beginXpGain(reward.skill(), reward.xp(), XPGainReason.PVE, XPGainSource.SELF);
        }
    }

    private static void awardMiningBonusDrops(McMMOPlayer mmoPlayer, ServerWorld world, BlockPos pos,
            BlockState state, @Nullable BlockEntity blockEntity, ServerPlayerEntity breaker,
            String blockId) {
        final MiningManager mining = mmoPlayer.getMiningManager();
        if (mining == null) {
            return;
        }
        final ItemStack tool = breaker.getMainHandStack();
        if (!mining.isBonusDropsEligible(blockId, BlockDrops.hasSilkTouch(world, tool))) {
            return;
        }
        final int rounds = mining.rollBonusDropCount();
        if (rounds > 0) {
            BlockDrops.dropBonusLoot(world, pos, state, blockEntity, breaker, tool, rounds);
        }
    }

    private static void awardWoodcuttingBonusDrops(McMMOPlayer mmoPlayer, ServerWorld world,
            BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
            ServerPlayerEntity breaker, String blockId) {
        final WoodcuttingManager woodcutting = mmoPlayer.getWoodcuttingManager();
        if (woodcutting == null) {
            return;
        }
        // Harvest Lumber / Clean Cuts duplicate the felled log regardless of Silk Touch, so (unlike
        // Mining) there is no enchantment gate here — the config toggle + rank + RNG live in the roll.
        final int rounds = woodcutting.rollHarvestLumberBonusDropCount(blockId);
        if (rounds > 0) {
            BlockDrops.dropBonusLoot(world, pos, state, blockEntity, breaker,
                    breaker.getMainHandStack(), rounds);
        }
    }

    private static void awardHerbalismBonusDrops(McMMOPlayer mmoPlayer, ServerWorld world,
            BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
            ServerPlayerEntity breaker, String blockId) {
        final HerbalismManager herbalism = mmoPlayer.getHerbalismManager();
        if (herbalism == null) {
            return;
        }
        // Herbalism double drops have no Silk Touch gate (plants don't drop via Silk Touch); the
        // config toggle + rank live in the eligibility check, the Green-Terra triple in the roll.
        if (!herbalism.isBonusDropsEligible(blockId)) {
            return;
        }
        final int rounds = herbalism.rollBonusDropCount();
        if (rounds > 0) {
            BlockDrops.dropBonusLoot(world, pos, state, blockEntity, breaker,
                    breaker.getMainHandStack(), rounds);
        }
    }

    private static void awardExcavationTreasures(McMMOPlayer mmoPlayer, ServerWorld world,
            BlockPos pos, String blockId) {
        final ExcavationManager excavation = mmoPlayer.getExcavationManager();
        if (excavation == null) {
            return;
        }
        final ExcavationManager.ExcavationRewards rewards = excavation.rollTreasureRewards(blockId);
        if (rewards.isEmpty()) {
            return;
        }
        // Build each MC-free ItemSpec into a live stack and scatter it at the block (an unknown
        // material is skipped, having already been logged by Materials).
        for (ItemSpec spec : rewards.treasures()) {
            ItemSpecBuilder.build(spec).ifPresent(stack -> Block.dropStack(world, pos, stack));
        }
        if (!rewards.experienceOrbs().isEmpty()) {
            final Vec3d center = Vec3d.ofCenter(pos);
            for (int amount : rewards.experienceOrbs()) {
                if (amount > 0) {
                    ExperienceOrbEntity.spawn(world, center, amount);
                }
            }
        }
        // Treasure XP is a bonus on top of the base block XP already awarded in awardBlockXp.
        if (rewards.treasureXp() > 0) {
            mmoPlayer.beginXpGain(PrimarySkillType.EXCAVATION, rewards.treasureXp(),
                    XPGainReason.PVE, XPGainSource.SELF);
        }
    }
}
