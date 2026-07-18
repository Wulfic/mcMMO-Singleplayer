package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.skills.herbalism.Herbalism;
import com.gmail.nossr50.skills.herbalism.HerbalismManager;
import com.gmail.nossr50.skills.unarmed.Unarmed;
import com.gmail.nossr50.util.BlockUtils;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.LogUtils;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.sounds.SoundManager;
import com.gmail.nossr50.util.sounds.SoundType;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;

/**
 * The K6 super-ability activation trigger: the interaction listener that readies a super-ability tool
 * and then fires the ability. Ports the activation slice of legacy {@code PlayerListener} (right-click
 * → {@code processAbilityActivation}) and {@code BlockListener#onBlockDamage} (left-click → {@code
 * checkAbilityActivation}); the pure decision bodies live MC-free on
 * {@link McMMOPlayer#processAbilityActivation}/{@link McMMOPlayer#checkAbilityActivation}, so this
 * listener only owns the MC-typed gating (block classification, held-item type, the off-hand rule).
 *
 * <p>Two-step flow, exactly as upstream:
 * <ol>
 *   <li><b>Ready</b> — a right-click (on a tool-activatable block, or in the air) flips the matching
 *       {@link ToolType} into preparation mode and schedules the {@code ToolLowerTask} window.</li>
 *   <li><b>Activate</b> — a left-click (block damage) with the prepared tool on a block the ability
 *       affects flips the super-ability mode on and schedules the {@code AbilityDisableTask}.</li>
 * </ol>
 *
 * <p>Callbacks return {@link ActionResult#PASS} — mcMMO observes the interaction rather than
 * replacing it — with one exception: Berserk's insta-break has already destroyed the block, so that
 * strike returns {@link ActionResult#SUCCESS} to stop vanilla starting a mining cycle on it.
 * Handlers run only for a {@link ServerPlayerEntity} (client-side callback fires resolve to
 * {@code null} → pass), and only for {@link Hand#MAIN_HAND} so the dual main/off-hand dispatch can't
 * ready or fire an ability twice.
 *
 * <p>Blast Mining hangs off the same two right-click paths (its detonation glue lives in
 * {@link BlastMiningListener}): right-clicking thin air detonates the TNT you're aiming at, and
 * right-clicking a TNT block with the detonator in hand is refused so you can't light one at your
 * feet. ⚠️ Legacy's right-click-block arm was <b>unreachable</b> — its {@code else if} hangs off
 * {@code if (!onlyActivateWhenSneaking || isSneaking())}, so it needs the player to <i>not</i> be
 * sneaking while {@code canDetonate()} requires that they are (and with the default config the
 * {@code if} is simply always true). Ported to the reachable form upstream clearly intended; the
 * "else → remoteDetonation" half of that arm is intentionally dropped, as a ray-cast from a
 * right-click-<i>block</i> can only ever re-find the block just clicked, which the TNT arm has
 * already excluded — it could never detonate anything.
 *
 * <p>Taming's <b>Call of the Wild</b> also rides the left-click (attack-block) path: a sneaking strike
 * with a summoning item spawns a pet (see {@code onAttackBlock}). Only the left-click-<i>block</i> form
 * is wired; Fabric has no left-click-<i>air</i> callback, so summoning while looking at open sky is the
 * one deferred gesture (a mixin on the swing/action packet would be needed for it).
 *
 * <p>The Herbalism right-click-block interactions ride {@code onUseBlock} too, porting the trailing
 * arm of legacy {@code PlayerListener}'s {@code RIGHT_CLICK_BLOCK} case (see
 * {@link #processHerbalismInteraction}): <b>Green Thumb</b> (wheat seeds mossify a block), <b>Shroom
 * Thumb</b> (a mushroom turns dirt/grass to mycelium), and <b>berry-bush harvest</b> (a delayed XP
 * award). Unlike the tool-skill activation above, these are <i>not</i> gated on
 * {@code getAbilitiesEnabled()} — legacy runs them in a separate block — but they do sit behind the
 * shared off-hand rule (legacy's {@code break} at the top of the case skips the whole arm).
 * ⚠️ In-game verification pending (they can't be exercised headless — the standing §G debt).
 */
public final class SuperAbilityListener {

    private SuperAbilityListener() {
    }

    /** Register the interaction hooks. Called once at mod load from {@code McMMOMod#onInitialize}. */
    public static void register() {
        UseBlockCallback.EVENT.register(SuperAbilityListener::onUseBlock);
        UseItemCallback.EVENT.register(SuperAbilityListener::onUseItem);
        AttackBlockCallback.EVENT.register(SuperAbilityListener::onAttackBlock);
    }

    /** Right-click a block → ready the tool for whichever skill the block can activate. */
    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand,
            BlockHitResult hitResult) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        final McMMOPlayer mmoPlayer = resolve(player);
        if (mmoPlayer == null) {
            return ActionResult.PASS;
        }

        final BlockPos pos = hitResult.getBlockPos();
        final BlockState state = world.getBlockState(pos);
        final ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

        // Blast Mining's "don't blow yourself up" guard: with the detonator (flint & steel, by
        // default) in hand, right-clicking a TNT block you're standing next to would light it the
        // vanilla way. Refusing the interaction is legacy's event.setCancelled(true). This runs
        // before the activation chain because legacy checks it in an earlier (LOWEST-priority)
        // handler whose cancel suppresses the activation handler entirely.
        if (state.isOf(Blocks.TNT) && mmoPlayer.getMiningManager().canDetonate()) {
            return ActionResult.FAIL;
        }

        // The off-hand rule gates the whole RIGHT_CLICK_BLOCK arm — both the tool-skill activation
        // below and the Herbalism interactions after it (legacy's break at the top of the case).
        if (offhandBlocksActivation(serverPlayer)) {
            return ActionResult.PASS;
        }

        // Tool-skill activation: legacy nests this inside the abilities-enabled gate, so — unlike the
        // Herbalism interactions below — it doesn't run when abilities are disabled.
        if (McMMOMod.getGeneralConfig().getAbilitiesEnabled() && BlockUtils.canActivateTools(state)) {
            if (BlockUtils.canActivateHerbalism(state)) {
                mmoPlayer.processAbilityActivation(PrimarySkillType.HERBALISM);
            }
            readyToolSkills(mmoPlayer);
        }

        // Herbalism right-click interactions (Green Thumb / Shroom Thumb / berry bush) — legacy runs
        // these in a separate block, outside the abilities-enabled gate.
        return processHerbalismInteraction(mmoPlayer, serverPlayer, world, pos, state);
    }

    /**
     * The trailing Herbalism arm of legacy {@code PlayerListener}'s {@code RIGHT_CLICK_BLOCK} case —
     * a single if / else-if / else selecting at most one of Green Thumb, Shroom Thumb, or a berry-bush
     * harvest for this right-click, in legacy's order.
     *
     * <p>PORT: legacy's leading {@code BONE_MEAL} branch (which reset the {@code UserBlockTracker}
     * "eligible" flag on a bone-mealed crop) is dropped — the K9 {@code PlacedBlockTracker} only ever
     * marks a block placed through {@code BlockItem#place}, never through bone meal, so there is no
     * over-marking to walk back (the conservative-tracking collapse). A player-planted crop is instead
     * maturity-gated on harvest, not placed-flag-gated (see {@code BlockBreakListener}).
     *
     * @return {@link ActionResult#FAIL} for a Shroom Thumb conversion (legacy's
     *     {@code event.setCancelled(true)}, so the held mushroom isn't also placed); otherwise
     *     {@link ActionResult#PASS} — Green Thumb consumes a seed but doesn't cancel the click (wheat
     *     seeds don't place on a mossify-able block anyway) and a berry-bush click must reach vanilla
     *     to actually reap the bush.
     */
    private static ActionResult processHerbalismInteraction(McMMOPlayer mmoPlayer,
            ServerPlayerEntity serverPlayer, World world, BlockPos pos, BlockState state) {
        final HerbalismManager herbalism = mmoPlayer.getHerbalismManager();
        final ItemStack mainHand = serverPlayer.getMainHandStack();

        if (canGreenThumbBlock(herbalism, mainHand, state)) {
            processGreenThumbBlock(mmoPlayer, serverPlayer, world, pos, state);
            return ActionResult.PASS;
        }
        if (canUseShroomThumb(herbalism, serverPlayer, mainHand, state)) {
            processShroomThumb(mmoPlayer, serverPlayer, world, pos, state);
            return ActionResult.FAIL;
        }
        maybeHarvestBerryBush(mmoPlayer, world, pos, state);
        return ActionResult.PASS;
    }

    /**
     * Green Thumb block-conversion gate: the rank/enable half on the manager
     * ({@link HerbalismManager#canGreenThumbBlock()}) plus the MC-typed half here — a non-empty
     * {@code wheat_seeds} main hand on a mossify-able block (legacy {@code canGreenThumbBlock}).
     */
    private static boolean canGreenThumbBlock(HerbalismManager herbalism, ItemStack mainHand,
            BlockState state) {
        return herbalism.canGreenThumbBlock()
                && !mainHand.isEmpty()
                && mainHand.isOf(Items.WHEAT_SEEDS)
                && BlockUtils.canMakeMossy(state);
    }

    /**
     * Shroom Thumb gate: the rank/enable half on the manager
     * ({@link HerbalismManager#canUseShroomThumb()}) plus the MC-typed half here — a mushroom in the
     * main hand, a shroomy-able block, and one brown + one red mushroom somewhere in the pack (legacy
     * {@code canUseShroomThumb}, whose {@code inventory.contains(..)} checks the whole inventory).
     */
    private static boolean canUseShroomThumb(HerbalismManager herbalism, ServerPlayerEntity player,
            ItemStack mainHand, BlockState state) {
        if (!BlockUtils.canMakeShroomy(state) || !herbalism.canUseShroomThumb()) {
            return false;
        }
        if (!mainHand.isOf(Items.BROWN_MUSHROOM) && !mainHand.isOf(Items.RED_MUSHROOM)) {
            return false;
        }
        final PlayerInventory inventory = player.getInventory();
        return findItemSlot(inventory, Items.BROWN_MUSHROOM) >= 0
                && findItemSlot(inventory, Items.RED_MUSHROOM) >= 0;
    }

    /**
     * Green Thumb: spend one wheat seed and, on a successful roll, mossify the block. Legacy consumes
     * the seed <i>before</i> the roll ({@code setAmount(amount - 1)} then {@code processGreenThumbBlocks}),
     * so a failed Green Thumb still costs the seed. The conversion target reuses the shared Green
     * Terra / Green Thumb table ({@link Herbalism#greenTerraConversionTarget}).
     */
    private static void processGreenThumbBlock(McMMOPlayer mmoPlayer, ServerPlayerEntity serverPlayer,
            World world, BlockPos pos, BlockState state) {
        serverPlayer.getMainHandStack().decrement(1);
        if (!mmoPlayer.getHerbalismManager().rollGreenThumbBlockSuccess()) {
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.SUBSKILL_MESSAGE_FAILED, "Herbalism.Ability.GTh.Fail");
            return;
        }
        convertBlock(mmoPlayer, world, pos, state,
                Herbalism.greenTerraConversionTarget(blockPath(state)));
    }

    /**
     * Shroom Thumb: spend one brown + one red mushroom and, on a successful roll, turn the block to
     * mycelium. {@link #canUseShroomThumb} has already proven both mushrooms are present; legacy removes
     * them <i>before</i> the roll, so a failed Shroom Thumb still costs the pair.
     */
    private static void processShroomThumb(McMMOPlayer mmoPlayer, ServerPlayerEntity serverPlayer,
            World world, BlockPos pos, BlockState state) {
        final PlayerInventory inventory = serverPlayer.getInventory();
        final int brownSlot = findItemSlot(inventory, Items.BROWN_MUSHROOM);
        final int redSlot = findItemSlot(inventory, Items.RED_MUSHROOM);
        if (brownSlot < 0 || redSlot < 0) {
            return; // defensive: the gate already proved both present (brown/red are distinct slots).
        }
        inventory.removeStack(brownSlot, 1);
        inventory.removeStack(redSlot, 1);
        if (!mmoPlayer.getHerbalismManager().rollShroomThumbSuccess()) {
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.SUBSKILL_MESSAGE_FAILED, "Herbalism.Ability.ShroomThumb.Fail");
            return;
        }
        convertBlock(mmoPlayer, world, pos, state,
                Herbalism.shroomThumbConversionTarget(blockPath(state)));
    }

    /**
     * The shared Green Thumb / Shroom Thumb block swap: resolve the conversion-target path to a live
     * block and set it. Mirrors {@link #processGreenTerraConversion}'s resolve-then-set shape, so a
     * block that is whitelisted but has no specific target (or a target absent from this registry) is
     * a safe no-op.
     */
    private static void convertBlock(McMMOPlayer mmoPlayer, World world, BlockPos pos,
            BlockState state, Optional<String> targetPath) {
        if (targetPath.isEmpty()) {
            return; // whitelisted but with no conversion target for this specific block.
        }
        final Optional<Block> targetBlock = Materials.block(targetPath.get());
        if (targetBlock.isEmpty()) {
            LogUtils.debug(McMMOMod.LOGGER, "Herbalism conversion target '" + targetPath.get()
                    + "' is not a block in this registry; skipping conversion of " + blockPath(state));
            return;
        }
        world.setBlockState(pos, targetBlock.get().getDefaultState());
    }

    /**
     * Berry-bush harvest XP, porting legacy {@code processBerryBushHarvesting} + its {@code CheckBushAge}
     * runnable. A ripe sweet berry bush (age 2 or 3) is worth XP, but only if the right-click actually
     * reaps it — a successful harvest resets the bush to age 1, so the reward is scheduled a tick later
     * and paid only when the bush has dropped to age &le; 1. This runs on the {@code UseBlockCallback}
     * (before vanilla reaps the bush), which is why the re-read a tick later sees the reset age.
     */
    private static void maybeHarvestBerryBush(McMMOPlayer mmoPlayer, World world, BlockPos pos,
            BlockState state) {
        if (!state.isOf(Blocks.SWEET_BERRY_BUSH)) {
            return;
        }
        final BlockUtils.AgeableState age = BlockUtils.getAgeableState(state);
        if (age == null) {
            return; // no age property (unreachable for a sweet berry bush, but stay defensive).
        }
        final int reward = mmoPlayer.getHerbalismManager()
                .getBerryBushXpReward(blockPath(state), age.age());
        if (reward <= 0) {
            return; // not ripe enough to pay (age < 2).
        }
        McMMOMod.getScheduler().runLater(() -> {
            final BlockState now = world.getBlockState(pos);
            if (!now.isOf(Blocks.SWEET_BERRY_BUSH)) {
                return;
            }
            final BlockUtils.AgeableState nowAge = BlockUtils.getAgeableState(now);
            if (nowAge != null && nowAge.age() <= 1) {
                mmoPlayer.beginXpGain(PrimarySkillType.HERBALISM, reward, XPGainReason.PVE,
                        XPGainSource.SELF);
            }
        }, 1);
    }

    /** Right-click the air → ready every tool skill, and fire Blast Mining's remote detonation. */
    private static ActionResult onUseItem(PlayerEntity player, World world, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        final McMMOPlayer mmoPlayer = resolve(player);
        if (mmoPlayer == null || offhandBlocksActivation((ServerPlayerEntity) player)) {
            return ActionResult.PASS;
        }

        if (McMMOMod.getGeneralConfig().getAbilitiesEnabled()) {
            mmoPlayer.processAbilityActivation(PrimarySkillType.HERBALISM);
            readyToolSkills(mmoPlayer);
        }

        // Blast Mining: aiming at distant TNT and right-clicking thin air detonates it. Legacy runs
        // this after the activation chain and outside the abilities-enabled gate, as here.
        if (mmoPlayer.getMiningManager().canDetonate()) {
            BlastMiningListener.remoteDetonation(mmoPlayer, (ServerPlayerEntity) player);
        }
        return ActionResult.PASS;
    }

    /** Left-click (break) a block with a prepared tool on an eligible block → fire the super ability. */
    private static ActionResult onAttackBlock(PlayerEntity player, World world, Hand hand,
            BlockPos pos, Direction direction) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        final McMMOPlayer mmoPlayer = resolve(player);
        if (mmoPlayer == null) {
            return ActionResult.PASS;
        }

        final BlockState state = world.getBlockState(pos);
        final ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        final ItemStack held = serverPlayer.getMainHandStack();

        // Taming Call of the Wild: a sneaking left-click while holding a summoning item (bones for a
        // wolf, cod for a cat, an apple for a horse) summons the pet. Legacy fired this from the
        // LEFT_CLICK arm of PlayerInteractEvent gated on isSneaking(). Only the left-click-BLOCK form is
        // wired — Fabric exposes no left-click-air callback — which still covers summoning while looking
        // at the ground or a wall (the common case; left-click-air is deferred, see the class note).
        // Returning FAIL consumes the click so it doesn't also begin breaking the block.
        if (serverPlayer.isSneaking()
                && McMMOMod.getCallOfTheWild().isCOTWItem(itemPath(held))) {
            CallOfTheWildHandler.processCallOfTheWild(mmoPlayer, serverPlayer);
            return ActionResult.FAIL;
        }

        boolean instaBroke = false;

        // Legacy splits this strike across two BlockDamageEvent handlers: activation at NORMAL
        // priority (onBlockDamage), then the ability *effects* at HIGHEST (onBlockDamageHigher).
        // That order is load-bearing — the strike that activates Green Terra also converts the block
        // it was activated on — so activation must stay ahead of the effects below.
        if (BlockUtils.canActivateAbilities(state)) {
            // Order matters: the prepared tool + matching item + block-affects-ability triple selects
            // one super ability. Singleplayer drops the legacy Permissions.* gates (Phase 6).
            if (mmoPlayer.getToolPreparationMode(ToolType.HOE) && ItemUtils.isHoe(held)
                    && (BlockUtils.affectedByGreenTerra(state) || BlockUtils.canMakeMossy(state))) {
                mmoPlayer.checkAbilityActivation(PrimarySkillType.HERBALISM);
            } else if (mmoPlayer.getToolPreparationMode(ToolType.AXE) && ItemUtils.isAxe(held)
                    && BlockUtils.hasWoodcuttingXP(state)) {
                mmoPlayer.checkAbilityActivation(PrimarySkillType.WOODCUTTING);
            } else if (mmoPlayer.getToolPreparationMode(ToolType.PICKAXE) && ItemUtils.isPickaxe(held)
                    && BlockUtils.affectedBySuperBreaker(state)) {
                mmoPlayer.checkAbilityActivation(PrimarySkillType.MINING);
            } else if (mmoPlayer.getToolPreparationMode(ToolType.SHOVEL) && ItemUtils.isShovel(held)
                    && BlockUtils.affectedByGigaDrillBreaker(state)) {
                mmoPlayer.checkAbilityActivation(PrimarySkillType.EXCAVATION);
            } else if (mmoPlayer.getToolPreparationMode(ToolType.FISTS) && held.isEmpty()
                    && (BlockUtils.affectedByGigaDrillBreaker(state)
                            || McMMOMod.getMaterialMapStore().isGlass(blockPath(state))
                            || state.isOf(Blocks.SNOW)
                            || BlockUtils.affectedByBlockCracker(state))) {
                mmoPlayer.checkAbilityActivation(PrimarySkillType.UNARMED);

                // The strike that activates Berserk also insta-breaks, exactly as legacy.
                if (mmoPlayer.getAbilityMode(SuperAbilityType.BERSERK)
                        && BlockUtils.affectedByBerserk(state)) {
                    instaBroke = berserkInstaBreak(mmoPlayer, serverPlayer, pos, state);
                }
            }
        }

        // Super-ability effects (legacy onBlockDamageHigher). That handler has no
        // canActivateAbilities gate and doesn't else-if against the activation branches above, so
        // these run on every strike an already-active ability makes on an eligible block.
        instaBroke = processAbilityEffects(mmoPlayer, serverPlayer, world, pos, state, held,
                instaBroke);

        // Cancelling the attack is how the port spells legacy's event.setInstaBreak(true): the block
        // is already gone, so vanilla must not also start a mining-progress cycle on it.
        return instaBroke ? ActionResult.SUCCESS : ActionResult.PASS;
    }

    /**
     * The already-active super-ability effects, porting legacy {@code BlockListener#onBlockDamageHigher}
     * — including its if/else-if shape, so at most one ability effect fires per strike.
     *
     * @param instaBroke whether the activation phase above already broke this block (legacy's
     *                   {@code event.getInstaBreak()} read)
     * @return {@code instaBroke}, updated if this phase broke the block
     */
    private static boolean processAbilityEffects(McMMOPlayer mmoPlayer,
            ServerPlayerEntity serverPlayer, World world, BlockPos pos, BlockState state,
            ItemStack held, boolean instaBroke) {
        if (mmoPlayer.getHerbalismManager().isGreenTerraActive() && BlockUtils.canMakeMossy(state)) {
            processGreenTerraConversion(mmoPlayer, serverPlayer, world, pos, state);
        } else if (mmoPlayer.getAbilityMode(SuperAbilityType.BERSERK)
                && (held.isEmpty() || McMMOMod.getGeneralConfig().getUnarmedItemsAsUnarmed())) {
            // These two branches can't contend for the same block: Block Cracker's whitelist holds
            // brick/tile blocks, while affectedByBerserk covers Excavation-XP blocks, snow and glass.
            if (mmoPlayer.getUnarmedManager().canUseBlockCracker()
                    && BlockUtils.affectedByBlockCracker(state)) {
                processBlockCracker(mmoPlayer, world, pos, state);
            } else if (!instaBroke && BlockUtils.affectedByBerserk(state)) {
                instaBroke = berserkInstaBreak(mmoPlayer, serverPlayer, pos, state);
            }
        } else if (mmoPlayer.getWoodcuttingManager().canUseLeafBlower() && ItemUtils.isAxe(held)
                && BlockUtils.isNonWoodPartOfTree(state)) {
            // Leaf Blower: an axe pops the non-wood parts of a tree (leaves, mushroom caps, warts)
            // outright rather than chewing through them. Unlike the two branches above this is a
            // plain sub-skill, not a super ability — no ability mode is consulted, only the rank.
            instaBroke = instaBreak(serverPlayer, pos);
            if (instaBroke) {
                SoundManager.sendSound(mmoPlayer.getPlayer(), SoundType.POP);
            }
        }
        return instaBroke;
    }

    /**
     * Destroy a block on the player's behalf — the port of legacy's {@code event.setInstaBreak(true)},
     * which handed the break back to vanilla as a normal player break. Hence {@code tryBreakBlock}
     * rather than {@code World#breakBlock}: it keeps the drops, the block-break event, and therefore
     * mcMMO's own XP/treasure processing ({@code BlockBreakListener}) intact, exactly as the real
     * {@code BlockBreakEvent} did upstream.
     *
     * @return whether the block was actually broken
     */
    private static boolean instaBreak(ServerPlayerEntity serverPlayer, BlockPos pos) {
        // PORT (K5): legacy gated this on EventUtils.simulateBlockBreak(block, player) — a fake
        // BlockBreakEvent asking other plugins whether the break was allowed. No plugins exist in
        // singleplayer, so the check collapses to "always allowed"; tryBreakBlock still enforces
        // vanilla's own rules (adventure mode, protected spawn) and reports the outcome.
        return serverPlayer.interactionManager.tryBreakBlock(pos);
    }

    /**
     * Berserk's insta-break: while Berserk is active, a bare-fisted strike on a block it affects
     * (dirt/gravel/sand, snow, glass) destroys it outright instead of mining it down. Berserk is the
     * one insta-break that picks its sound from the block — glass shatters, everything else pops.
     *
     * @return whether the block was actually broken
     */
    private static boolean berserkInstaBreak(McMMOPlayer mmoPlayer, ServerPlayerEntity serverPlayer,
            BlockPos pos, BlockState state) {
        if (!instaBreak(serverPlayer, pos)) {
            return false;
        }

        if (blockPath(state).contains("glass")) {
            SoundManager.worldSendSound(mmoPlayer.getPlayer(), SoundType.GLASS);
        } else {
            SoundManager.sendSound(mmoPlayer.getPlayer(), SoundType.POP);
        }
        return true;
    }

    /**
     * The Block Cracker sub-skill: while Berserk is active, striking an intact brick/tile block has a
     * chance to crack it in place. Ports legacy {@code UnarmedManager#blockCrackerCheck}, split as
     * usual — config + RNG gate on {@link com.gmail.nossr50.skills.unarmed.UnarmedManager#rollBlockCracker},
     * conversion table in {@link Unarmed#blockCrackerConversionTarget}, live swap here.
     */
    private static void processBlockCracker(McMMOPlayer mmoPlayer, World world, BlockPos pos,
            BlockState state) {
        if (!mmoPlayer.getUnarmedManager().rollBlockCracker()) {
            return;
        }

        final Optional<String> targetPath = Unarmed.blockCrackerConversionTarget(blockPath(state));
        if (targetPath.isEmpty()) {
            return; // block-cracker-whitelisted but with no cracked variant: nothing to become.
        }
        final Optional<Block> targetBlock = Materials.block(targetPath.get());
        if (targetBlock.isEmpty()) {
            LogUtils.debug(McMMOMod.LOGGER, "Block Cracker target '" + targetPath.get()
                    + "' is not a block in this registry; skipping crack of " + blockPath(state));
            return;
        }

        world.setBlockState(pos, targetBlock.get().getDefaultState());
    }

    /**
     * The Green Terra super-ability effect: while Green Terra is active, striking a mossify-able
     * block converts it (cobblestone → mossy cobblestone, dirt → grass, …) at the cost of one wheat
     * seed. Ports legacy {@code HerbalismManager#processGreenTerraBlockConversion}: the MC-free
     * "what does this block become" decision lives on {@link Herbalism#greenTerraConversionTarget},
     * so this glue only owns the inventory read/consume and the live block swap.
     *
     * <p>Singleplayer drops the legacy {@code Permissions.greenThumbBlock} gate (always granted,
     * Phase 6). Legacy's {@code blockState.update(true)} force-flag is implicit here:
     * {@link World#setBlockState(BlockPos, BlockState)} already notifies neighbours, which is what
     * re-connects a converted {@code cobblestone_wall}.
     *
     * <p>The Green Terra active + {@code canMakeMossy} gate lives on the caller, matching legacy's
     * own branch condition in {@code onBlockDamageHigher}.
     */
    private static void processGreenTerraConversion(McMMOPlayer mmoPlayer,
            ServerPlayerEntity serverPlayer, World world, BlockPos pos, BlockState state) {
        final Optional<String> targetPath = Herbalism.greenTerraConversionTarget(blockPath(state));
        if (targetPath.isEmpty()) {
            return; // mossify-whitelisted but with no conversion target: nothing to become.
        }
        final Optional<Block> targetBlock = Materials.block(targetPath.get());
        if (targetBlock.isEmpty()) {
            LogUtils.debug(McMMOMod.LOGGER, "Green Terra conversion target '" + targetPath.get()
                    + "' is not a block in this registry; skipping conversion of " + blockPath(state));
            return;
        }

        final Optional<Item> seed = Materials.item(Herbalism.GREEN_TERRA_SEED);
        if (seed.isEmpty()) {
            LogUtils.debug(McMMOMod.LOGGER, "Green Terra seed item '" + Herbalism.GREEN_TERRA_SEED
                    + "' is not an item in this registry; skipping conversion.");
            return;
        }
        final int seedSlot = findItemSlot(serverPlayer.getInventory(), seed.get());
        if (seedSlot < 0) {
            NotificationManager.sendPlayerInformation(mmoPlayer,
                    NotificationType.REQUIREMENTS_NOT_MET, "Herbalism.Ability.GTe.NeedMore");
            return;
        }

        serverPlayer.getInventory().removeStack(seedSlot, 1);
        world.setBlockState(pos, targetBlock.get().getDefaultState());
    }

    /**
     * First inventory slot holding {@code item}, or {@code -1} if none — matching legacy
     * {@code PlayerInventory#containsAtLeast(stack, 1)}, whose paired {@code removeItem} then
     * consumes one. Mirrors {@code RepairSalvageListener#findMaterialSlot}.
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

    /** Ready the six shared tool skills (Herbalism is gated separately by its caller). */
    private static void readyToolSkills(McMMOPlayer mmoPlayer) {
        mmoPlayer.processAbilityActivation(PrimarySkillType.AXES);
        mmoPlayer.processAbilityActivation(PrimarySkillType.EXCAVATION);
        mmoPlayer.processAbilityActivation(PrimarySkillType.MINING);
        mmoPlayer.processAbilityActivation(PrimarySkillType.SWORDS);
        mmoPlayer.processAbilityActivation(PrimarySkillType.UNARMED);
        mmoPlayer.processAbilityActivation(PrimarySkillType.WOODCUTTING);
    }

    /**
     * Legacy off-hand rule: holding an item in the off hand suppresses activation unless the player is
     * mounted or sneaking (so shield-raising / off-hand food use doesn't fire abilities).
     */
    private static boolean offhandBlocksActivation(ServerPlayerEntity player) {
        return !player.getOffHandStack().isEmpty() && !player.hasVehicle() && !player.isSneaking();
    }

    private static McMMOPlayer resolve(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return null; // client-side callback: ignore.
        }
        return UserManager.getPlayer(serverPlayer.getUuid());
    }

    private static String blockPath(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).getPath();
    }

    private static String itemPath(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).getPath();
    }
}
