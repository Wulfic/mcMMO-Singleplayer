package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SubSkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.treasure.HylianTreasure;
import com.gmail.nossr50.datatypes.treasure.ItemSpec;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.BlockDrops;
import com.gmail.nossr50.platform.ItemSpecBuilder;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.skills.BlockBreakXp;
import com.gmail.nossr50.skills.excavation.ExcavationManager;
import com.gmail.nossr50.skills.herbalism.HerbalismManager;
import com.gmail.nossr50.skills.herbalism.MultiBlockPlantTraversal;
import com.gmail.nossr50.skills.mining.MiningManager;
import com.gmail.nossr50.skills.woodcutting.TreeFellerProcessor;
import com.gmail.nossr50.skills.woodcutting.WoodcuttingManager;
import com.gmail.nossr50.util.BlockUtils;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.MaterialMapStore;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.random.ProbabilityUtil;
import com.gmail.nossr50.util.skills.SkillUtils;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
 *
 * <p>Also registers the cancellable {@link PlayerBlockBreakEvents#BEFORE} for the one side effect that
 * must <em>replace</em> a block's drop rather than add to it — <b>Hylian Luck</b> (sword-breaking a
 * flower/bush/sapling/pot for treasure); see {@link #beforeBlockBroken}.
 */
public final class BlockBreakListener {

    /**
     * How long to wait before checking which blocks of a chorus tree actually came down. A chorus
     * tree collapses one layer per scheduled block tick, so a tall one takes many ticks to finish
     * falling; the {@code chorus_plant} tall-plant limit (22) is upstream's own estimate of the
     * tallest tree worth rewarding, so this leaves generous headroom over that.
     *
     * <p>PORT (upstream bug, fixed): legacy scheduled its equivalent check with <i>no</i> delay
     * (next tick) despite a comment reading "Large delay because the tree takes a while to break",
     * so every block that hadn't collapsed yet was silently skipped and tall chorus trees under-paid.
     */
    private static final long CHORUS_COLLAPSE_DELAY_TICKS = 40L;

    /** One block a multi-block plant break will take down, captured before the break removed it. */
    private record PlantSnapshot(BlockPos pos, BlockState state, String path) {}

    /** A multi-block plant break snapshotted in BEFORE, waiting to be rewarded in AFTER. */
    private record PlantBreakCapture(BlockPos origin, String originPath, List<PlantSnapshot> blocks) {}

    /**
     * The pending multi-block plant snapshot, handed from {@link #beforeBlockBroken} to
     * {@link #onBlockBroken}. Both seams run on the server thread and AFTER fires immediately after
     * BEFORE for the same break, so a plain field is enough — but it is cleared at the start of every
     * BEFORE and consumed unconditionally in AFTER so a cancelled break can never leak into the next.
     */
    private static @Nullable PlantBreakCapture pendingPlantBreak;

    private BlockBreakListener() {
    }

    /** Register the block-break hooks. Called once at mod load from {@code McMMOMod#onInitialize}. */
    public static void register() {
        // BEFORE runs while the block (and the rest of its plant) is still standing. Two things need
        // that: Hylian Luck, which must REPLACE a block's normal drop rather than supplement it — the
        // AFTER seam has already spawned that drop — and the multi-block plant snapshot, since vanilla
        // has begun removing the rest of the plant by the time AFTER fires.
        PlayerBlockBreakEvents.BEFORE.register(BlockBreakListener::beforeBlockBroken);
        PlayerBlockBreakEvents.AFTER.register(BlockBreakListener::onBlockBroken);
    }

    /**
     * The cancellable pre-break seam. Runs Hylian Luck (which may consume the block outright) and,
     * for a break that is going ahead, snapshots any multi-block plant that will come down with it —
     * see {@link #capturePlantBreak}.
     *
     * @return {@code false} to cancel the vanilla break, {@code true} to let it proceed
     */
    private static boolean beforeBlockBroken(World world, PlayerEntity player, BlockPos pos,
            BlockState state, @Nullable BlockEntity blockEntity) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)
                || !(world instanceof ServerWorld serverWorld)) {
            return true; // client-side prediction / non-server context: let vanilla handle it.
        }
        // Drop any snapshot from an earlier break that never reached AFTER (another listener cancelled
        // it, so onBlockBroken never consumed it). Both seams run on the server thread, so a capture
        // is only ever live between this call and the matching AFTER.
        pendingPlantBreak = null;

        if (!maybeProcessHylianLuck(serverWorld, serverPlayer, pos, state)) {
            return false; // Hylian consumed the block; nothing else to collect.
        }
        capturePlantBreak(serverWorld, pos, state);
        return true;
    }

    /**
     * Hylian Luck: breaking a flower, bush, sapling or flower pot with a sword has a chance to drop
     * rare treasure <em>in place of</em> the block's normal drop (legacy {@code processHylianLuck},
     * fired from {@code BlockListener#onBlockBreakHigher}). Legacy ran on a cancellable HIGHEST-priority
     * {@code BlockBreakEvent} — it set the block to air, spawned the treasure and cancelled the break —
     * so the faithful Fabric analogue is the cancellable {@link PlayerBlockBreakEvents#BEFORE} seam.
     *
     * @return {@code false} to cancel the vanilla break (Hylian consumed the block), {@code true} to
     *     let it break normally
     */
    private static boolean maybeProcessHylianLuck(ServerWorld serverWorld,
            ServerPlayerEntity serverPlayer, BlockPos pos, BlockState state) {
        // Cheapest gate first — Hylian triggers only on a sword break, and most breaks aren't swords.
        if (!ItemUtils.isSword(serverPlayer.getMainHandStack())) {
            return true;
        }
        if (serverPlayer.isCreative()) {
            return true; // legacy gates its whole block-break handler on non-creative.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(serverPlayer.getUuid());
        if (mmoPlayer == null) {
            return true; // data not loaded (e.g. mid-join).
        }
        final HerbalismManager herbalism = mmoPlayer.getHerbalismManager();
        if (herbalism == null || !herbalism.canUseHylianLuck()) {
            return true;
        }
        final String group = BlockUtils.getHylianTreasureGroup(state);
        if (group == null) {
            return true; // not a Hylian Luck source block.
        }

        final List<HylianTreasure> candidates = McMMOMod.getTreasureConfig().getHylianTreasures(group);
        final boolean mainRollWon = ProbabilityUtil.isSkillRNGSuccessful(
                SubSkillType.HERBALISM_HYLIAN_LUCK, mmoPlayer);
        final Optional<HylianTreasure> won = herbalism.rollHylianLuck(candidates, mainRollWon,
                chance -> ProbabilityUtil.isStaticSkillRNGSuccessful(PrimarySkillType.HERBALISM,
                        mmoPlayer, chance));

        if (won.isPresent()) {
            final Optional<ItemStack> built = ItemSpecBuilder.build(won.get().getDrop());
            if (built.isPresent()) {
                // Remove the block first (legacy order), then drop the treasure into the now-empty space.
                serverWorld.setBlockState(pos, Blocks.AIR.getDefaultState());
                Block.dropStack(serverWorld, pos, built.get());
                NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUBSKILL_MESSAGE,
                        "Herbalism.HylianLuck");
                return false; // treasure replaces the normal drop — cancel the vanilla break.
            }
            // Treasure material has no vanilla item (logged by Materials): fall through to a normal
            // break rather than consuming the block for nothing.
        }

        if ("Pots".equals(group)) {
            // Legacy: a sword-struck flower pot is consumed even without a treasure (set to air, break
            // cancelled so the pot's own drop is suppressed). Reachable whenever the main Hylian roll
            // fails — the common case at low Herbalism level.
            serverWorld.setBlockState(pos, Blocks.AIR.getDefaultState());
            return false;
        }

        return true; // no treasure, not a pot: break the block normally.
    }

    private static void onBlockBroken(World world, PlayerEntity player, BlockPos pos,
            BlockState state, @Nullable BlockEntity blockEntity) {
        // Consume the pre-break snapshot unconditionally, even on the paths that bail below, so it
        // can never be mistaken for a later break's.
        final PlantBreakCapture plantBreak = pendingPlantBreak;
        pendingPlantBreak = null;

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
        final boolean handPlaced = consumePlacedFlag(serverWorld, pos);

        final String blockId = Registries.BLOCK.getId(state.getBlock()).toString();

        // Multi-block plants (sugar cane, cactus, kelp, bamboo, chorus trees, tall grass, vines) take
        // their other blocks down with them, and legacy rewarded every one of those — so they get
        // their own handler rather than the single-block path below. Diverting is safe because none of
        // these blocks is a Mining/Woodcutting/Excavation XP source or a super-ability target: every
        // multi-block plant in MaterialMapStore appears only under experience.yml's Herbalism section.
        if (plantBreak != null && plantBreak.origin().equals(pos)) {
            final HerbalismManager herbalism = mmoPlayer.getHerbalismManager();
            if (herbalism != null) {
                processMultiBlockPlant(mmoPlayer, herbalism, serverWorld, serverPlayer, plantBreak,
                        handPlaced);
                return;
            }
        }

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
        if (isHerbalismAfkFarming(herbalism, breaker)) {
            return;
        }
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

    /**
     * Snapshot the multi-block plant the player is about to break, if it is one. Runs on the
     * <em>pre</em>-break seam because that is the only point at which the whole plant is guaranteed
     * readable: vanilla removes the rest of a broken plant on its own schedule, and the two schedules
     * differ. Sugar cane, cactus, kelp, bamboo and chorus all schedule a block tick and so are still
     * standing when AFTER fires, but a double plant's other half is replaced with air
     * <em>synchronously</em> during the origin's neighbour update ({@code TallPlantBlock}'s
     * {@code getStateForNeighborUpdate} returns {@code Blocks.AIR}, bytecode-verified), so a live read
     * in AFTER would find tall grass and large ferns already gone. Capturing the {@link BlockState}s
     * here also means the delayed chorus check can still roll their loot long after they fell.
     */
    private static void capturePlantBreak(ServerWorld world, BlockPos pos, BlockState state) {
        final String originPath = pathOf(state);
        final MaterialMapStore materials = McMMOMod.getMaterialMapStore();
        if (MultiBlockPlantTraversal.isOneBlockPlant(originPath, materials)) {
            return; // an ordinary block takes nothing down with it — the common case, kept cheap.
        }
        final List<MultiBlockPlantTraversal.PlantCoord> coords = MultiBlockPlantTraversal.collect(
                pos.getX(), pos.getY(), pos.getZ(), originPath, materials,
                (x, y, z) -> pathOf(world.getBlockState(new BlockPos(x, y, z))));

        final List<PlantSnapshot> blocks = new ArrayList<>(coords.size());
        for (MultiBlockPlantTraversal.PlantCoord coord : coords) {
            final BlockPos blockPos = new BlockPos(coord.x(), coord.y(), coord.z());
            blocks.add(new PlantSnapshot(blockPos, world.getBlockState(blockPos), coord.path()));
        }
        pendingPlantBreak = new PlantBreakCapture(pos, originPath, blocks);
    }

    /**
     * Reward every block of a broken multi-block plant — legacy
     * {@code processHerbalismOnBlocksBroken}. Blocks are split the way legacy split them: everything
     * is paid immediately except the non-origin parts of a chorus tree, whose collapse cascades over
     * the following ticks and so has to be re-checked later ({@link #scheduleChorusXpCheck}).
     *
     * <p>The origin is always paid immediately even when it <em>is</em> chorus: it is the block the
     * player just broke, so it is definitely gone and there is nothing to wait for — legacy did the
     * same, to keep the XP bar responsive. Legacy additionally routed a <em>hand-placed</em> chorus
     * origin into the delayed list, which this collapses away: both paths reward a placed block with
     * nothing and merely mark it natural again, so the outcome is identical.
     */
    private static void processMultiBlockPlant(McMMOPlayer mmoPlayer, HerbalismManager herbalism,
            ServerWorld world, ServerPlayerEntity breaker, PlantBreakCapture capture,
            boolean originHandPlaced) {
        if (isHerbalismAfkFarming(herbalism, breaker)) {
            return;
        }
        final List<PlantSnapshot> immediate = new ArrayList<>();
        final List<PlantSnapshot> delayedChorus = new ArrayList<>();
        for (PlantSnapshot snapshot : capture.blocks()) {
            if (snapshot.pos().equals(capture.origin())
                    || !MultiBlockPlantTraversal.isChorusTree(snapshot.path())) {
                immediate.add(snapshot);
            } else {
                delayedChorus.add(snapshot);
            }
        }
        awardPlantBlocks(mmoPlayer, herbalism, world, breaker, capture, immediate, originHandPlaced);
        if (!delayedChorus.isEmpty()) {
            scheduleChorusXpCheck(world, breaker, delayedChorus);
        }
    }

    /**
     * Pay XP and roll bonus drops for a set of broken plant blocks — legacy
     * {@code awardXPForPlantBlocks} fused with {@code checkDoubleDropsOnBrokenPlants} (the port has no
     * drop-event metadata seam, so the bonus loot is spawned here rather than marked on the block).
     *
     * <p>Per block, mirroring legacy: a hand-placed block pays nothing (and its tracker flag is
     * cleared, since the block is gone), and an ageable pays only once mature — unless it is a
     * "bizarre" ageable (cactus / kelp / sugar cane / bamboo) whose age says nothing about maturity.
     * Non-ageables always pay. The total is then capped by
     * {@link HerbalismManager#applyTallPlantXpCap} so an unnaturally tall plant can't be farmed for
     * unbounded XP.
     *
     * <p>PORT deviations, both narrow and both documented in CONVERSION_TODO §F:
     * <ul>
     *   <li>Legacy keyed the tall-plant cap off {@code brokenPlants.stream().findFirst()} — an
     *       <em>arbitrary</em> element of an unordered {@code HashSet}, so on a mixed-type plant (a
     *       cactus with a flower on top) whether the cap applied at all was down to hash order. This
     *       keys it off the block the player actually broke, which is what the cap plainly means.</li>
     *   <li>Legacy's hand-placed branch awarded bonus drops with <em>no RNG roll</em> for a mature
     *       non-bizarre ageable. That combination is unreachable for a multi-block plant (its only
     *       non-bizarre ageable is {@code chorus_flower}, which is always routed to the delayed path),
     *       so rather than propagate an obvious upstream slip, a placed block here simply pays
     *       nothing.</li>
     * </ul>
     */
    private static void awardPlantBlocks(McMMOPlayer mmoPlayer, HerbalismManager herbalism,
            ServerWorld world, ServerPlayerEntity breaker, PlantBreakCapture capture,
            List<PlantSnapshot> blocks, boolean originHandPlaced) {
        int xpToReward = 0;
        int firstBlockXp = 0;
        for (PlantSnapshot snapshot : blocks) {
            // The origin's flag was already read and cleared by the caller; re-reading it here would
            // see the cleared value and wrongly treat a placed block as natural.
            final boolean placed = snapshot.pos().equals(capture.origin())
                    ? originHandPlaced
                    : consumePlacedFlag(world, snapshot.pos());
            if (placed) {
                continue;
            }
            final BlockUtils.AgeableState ageable = BlockUtils.getAgeableState(snapshot.state());
            if (ageable != null && !herbalism.isBizarreAgeable(snapshot.path())
                    && !herbalism.isAgeableMature(snapshot.path(), ageable.age(), ageable.maxAge())) {
                continue; // an immature crop pays nothing.
            }
            final int blockXp = HerbalismManager.getExperienceFromPlant(
                    ConfigStringUtils.getMaterialConfigString(snapshot.path()));
            if (blockXp > 0) {
                if (firstBlockXp == 0) {
                    firstBlockXp = blockXp;
                }
                xpToReward += blockXp;
            }
            awardPlantBonusDrops(herbalism, world, breaker, snapshot);
        }
        if (xpToReward > 0) {
            mmoPlayer.beginXpGain(PrimarySkillType.HERBALISM,
                    herbalism.applyTallPlantXpCap(xpToReward, firstBlockXp, capture.originPath()),
                    XPGainReason.PVE, XPGainSource.SELF);
        }
    }

    /**
     * Re-check a chorus tree several ticks after the break and pay for the parts that actually came
     * down — legacy {@code awardXPForBlockSnapshots} / {@code DelayedHerbalismXPCheckTask}. A chorus
     * tree collapses gradually and the traversal deliberately over-collects (predicting exactly which
     * branches survive a root break would be far more expensive than looking afterwards), so a block
     * that is still standing when this runs is simply skipped.
     *
     * <p>Legacy checked maturity for nothing here — a chorus flower pays whatever age it died at (its
     * own source carries a "do we care about chorus flower age?" TODO) — and that is kept. The player
     * is re-resolved by UUID rather than captured, so a logout during the wait is a clean no-op.
     * There is no tall-plant cap on this path, matching legacy.
     */
    private static void scheduleChorusXpCheck(ServerWorld world, ServerPlayerEntity breaker,
            List<PlantSnapshot> chorusBlocks) {
        final UUID breakerId = breaker.getUuid();
        McMMOMod.getScheduler().runLater(() -> {
            final ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(breakerId);
            final McMMOPlayer owner = UserManager.getPlayer(breakerId);
            if (player == null || owner == null) {
                return; // logged out while the tree was falling.
            }
            final HerbalismManager herbalism = owner.getHerbalismManager();
            if (herbalism == null) {
                return;
            }
            int xpToReward = 0;
            for (PlantSnapshot snapshot : chorusBlocks) {
                if (!world.getBlockState(snapshot.pos()).isAir()) {
                    continue; // still standing: this part of the tree never came down.
                }
                if (consumePlacedFlag(world, snapshot.pos())) {
                    continue; // player-placed: no reward, but the location is natural again.
                }
                xpToReward += HerbalismManager.getExperienceFromPlant(
                        ConfigStringUtils.getMaterialConfigString(snapshot.path()));
                awardPlantBonusDrops(herbalism, world, player, snapshot);
            }
            if (xpToReward > 0) {
                owner.beginXpGain(PrimarySkillType.HERBALISM, xpToReward, XPGainReason.PVE,
                        XPGainSource.SELF);
            }
        }, CHORUS_COLLAPSE_DELAY_TICKS);
    }

    /**
     * Roll Herbalism bonus drops for one block of a broken multi-block plant, spawning them from the
     * captured pre-break {@link BlockState} (the block itself is gone, or about to be). Creative is
     * excluded exactly as on the single-block path — a creative break spawns no vanilla loot, so
     * bonus copies there would be a duplication bug.
     */
    private static void awardPlantBonusDrops(HerbalismManager herbalism, ServerWorld world,
            ServerPlayerEntity breaker, PlantSnapshot snapshot) {
        if (breaker.isCreative() || !herbalism.isBonusDropsEligible(snapshot.path())) {
            return;
        }
        final int rounds = herbalism.rollBonusDropCount();
        if (rounds > 0) {
            BlockDrops.dropBonusLoot(world, snapshot.pos(), snapshot.state(), null, breaker,
                    breaker.getMainHandStack(), rounds);
        }
    }

    /**
     * Whether Herbalism rewards are suppressed because the player is riding something — legacy
     * {@code processHerbalismBlockBreakEvent}'s opening {@code getHerbalismPreventAFK() &&
     * player.isInsideVehicle()} guard, aimed at minecart-parked sugar cane / crop farms.
     *
     * <p>PORT (narrower than legacy, deliberately): legacy applied this to <em>every</em> Herbalism
     * block break, but this port's single-block gathering XP runs through a path shared with Mining,
     * Woodcutting and Excavation, so the guard is applied to the two Herbalism-specific handlers —
     * multi-block plants and maturity-gated crops — which is where the AFK farms actually are.
     * Without it {@code Skills.Herbalism.Prevent_AFK_Leveling} (which ships <em>on</em>) was a config
     * knob the port read from disk and never consulted.
     */
    private static boolean isHerbalismAfkFarming(HerbalismManager herbalism,
            ServerPlayerEntity breaker) {
        return herbalism.isHerbalismAfkPrevented() && breaker.hasVehicle();
    }

    /**
     * Read a position's §A hand-placed flag and clear it in one go. Clearing is unconditional: the
     * block is gone by now, so the location is natural again and the tracker's memory is freed.
     */
    private static boolean consumePlacedFlag(ServerWorld world, BlockPos pos) {
        final boolean placed = BlockUtils.isRewardIneligible(world, pos);
        BlockUtils.markNatural(world, pos);
        return placed;
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
