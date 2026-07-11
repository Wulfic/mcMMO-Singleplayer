package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.datatypes.skills.ToolType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.BlockUtils;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.player.UserManager;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
 * <p>All callbacks return {@link ActionResult#PASS}: mcMMO observes the interaction, it never
 * cancels vanilla behaviour here. Handlers run only for a {@link ServerPlayerEntity} (client-side
 * callback fires resolve to {@code null} → pass), and only for {@link Hand#MAIN_HAND} so the dual
 * main/off-hand dispatch can't ready or fire an ability twice.
 *
 * <p><b>Deferred (as their bodies land):</b> the Herbalism Green Thumb / Shroom Thumb / berry-bush
 * right-click paths, Blast Mining remote detonation on right-click-air, and the Berserk insta-break on
 * left-click — all need their skill bodies + item/block adapters that are still stubbed.
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
        if (mmoPlayer == null || offhandBlocksActivation((ServerPlayerEntity) player)
                || !McMMOMod.getGeneralConfig().getAbilitiesEnabled()) {
            return ActionResult.PASS;
        }

        final BlockState state = world.getBlockState(hitResult.getBlockPos());
        if (BlockUtils.canActivateTools(state)) {
            if (BlockUtils.canActivateHerbalism(state)) {
                mmoPlayer.processAbilityActivation(PrimarySkillType.HERBALISM);
            }
            readyToolSkills(mmoPlayer);
        }
        return ActionResult.PASS;
    }

    /** Right-click the air → ready every tool skill (no target block to gate on). */
    private static ActionResult onUseItem(PlayerEntity player, World world, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        final McMMOPlayer mmoPlayer = resolve(player);
        if (mmoPlayer == null || offhandBlocksActivation((ServerPlayerEntity) player)
                || !McMMOMod.getGeneralConfig().getAbilitiesEnabled()) {
            return ActionResult.PASS;
        }

        mmoPlayer.processAbilityActivation(PrimarySkillType.HERBALISM);
        readyToolSkills(mmoPlayer);
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
        if (!BlockUtils.canActivateAbilities(state)) {
            return ActionResult.PASS;
        }

        final ItemStack held = ((ServerPlayerEntity) player).getMainHandStack();

        // Order matters: the prepared tool + matching item + block-affects-ability triple selects one
        // super ability. Singleplayer drops the legacy Permissions.* gates (always granted, Phase 6).
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
            // PORT: legacy also insta-broke the block once Berserk was active (BERSERK.blockCheck +
            // simulateBlockBreak → event.setInstaBreak). That's the Unarmed super-ability *body*, which
            // lands with the Berserk block-break body + the block-mutation adapter.
        }
        return ActionResult.PASS;
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
