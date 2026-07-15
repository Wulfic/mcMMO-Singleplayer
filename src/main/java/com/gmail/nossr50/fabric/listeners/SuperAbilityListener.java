package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.SuperAbilityType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.Materials;
import com.gmail.nossr50.skills.herbalism.Herbalism;
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
 * <p><b>Deferred (as their bodies land):</b> the Herbalism Green Thumb / Shroom Thumb / berry-bush
 * right-click paths and Woodcutting's Leaf Blower insta-break — both need skill bodies that are
 * still stubbed.
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

        final BlockState state = world.getBlockState(hitResult.getBlockPos());

        // Blast Mining's "don't blow yourself up" guard: with the detonator (flint & steel, by
        // default) in hand, right-clicking a TNT block you're standing next to would light it the
        // vanilla way. Refusing the interaction is legacy's event.setCancelled(true). This runs
        // before the activation chain because legacy checks it in an earlier (LOWEST-priority)
        // handler whose cancel suppresses the activation handler entirely.
        if (state.isOf(Blocks.TNT) && mmoPlayer.getMiningManager().canDetonate()) {
            return ActionResult.FAIL;
        }

        if (offhandBlocksActivation((ServerPlayerEntity) player)
                || !McMMOMod.getGeneralConfig().getAbilitiesEnabled()) {
            return ActionResult.PASS;
        }

        if (BlockUtils.canActivateTools(state)) {
            if (BlockUtils.canActivateHerbalism(state)) {
                mmoPlayer.processAbilityActivation(PrimarySkillType.HERBALISM);
            }
            readyToolSkills(mmoPlayer);
        }
        return ActionResult.PASS;
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
                    instaBroke = instaBreak(mmoPlayer, serverPlayer, pos, state);
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
                instaBroke = instaBreak(mmoPlayer, serverPlayer, pos, state);
            }
        }
        // PORT: legacy's third branch is Woodcutting's Leaf Blower (insta-break the non-wood parts of
        // a tree). It lands with the Leaf Blower body, which is still deferred (§D Woodcutting).
        return instaBroke;
    }

    /**
     * Berserk's insta-break: while Berserk is active, a bare-fisted strike on a block it affects
     * (dirt/gravel/sand, snow, glass) destroys it outright instead of mining it down. Ports legacy's
     * {@code event.setInstaBreak(true)}, which handed the break back to vanilla as a normal player
     * break — so this uses {@code tryBreakBlock} rather than {@code World#breakBlock}, keeping the
     * drops, the block-break event, and therefore mcMMO's own XP/treasure processing
     * ({@code BlockBreakListener}) intact, exactly as the real {@code BlockBreakEvent} did upstream.
     *
     * @return whether the block was actually broken
     */
    private static boolean instaBreak(McMMOPlayer mmoPlayer, ServerPlayerEntity serverPlayer,
            BlockPos pos, BlockState state) {
        // PORT (K5): legacy gated this on EventUtils.simulateBlockBreak(block, player) — a fake
        // BlockBreakEvent asking other plugins whether the break was allowed. No plugins exist in
        // singleplayer, so the check collapses to "always allowed"; tryBreakBlock still enforces
        // vanilla's own rules (adventure mode, protected spawn) and reports the outcome.
        if (!serverPlayer.interactionManager.tryBreakBlock(pos)) {
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
}
