package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.experience.XPGainReason;
import com.gmail.nossr50.datatypes.experience.XPGainSource;
import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.skills.mining.BlastMining;
import com.gmail.nossr50.skills.mining.MiningManager;
import com.gmail.nossr50.util.BlockUtils;
import com.gmail.nossr50.util.ItemUtils;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.text.ConfigStringUtils;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The MC-typed glue for Blast Mining: remote detonation, and the two explosion-time hooks that make
 * an mcMMO-detonated blast behave differently from a vanilla one (Bigger Bombs' radius, Demolitions
 * Expertise' self-damage reduction, and — see {@code fabric.mixin.ExplosionDropsMixin} — the ore
 * yield). Ports the Blast Mining slices of legacy {@code PlayerListener#onPlayerInteract} and
 * {@code EntityListener#onExplosionPrime}/{@code #onEnitityExplode}; the numbers all live MC-free on
 * {@link MiningManager}.
 *
 * <p><b>How an mcMMO blast is recognised.</b> Legacy stamped its remotely-detonated TNT with the
 * {@code mcMMO: tracked_tnt} Bukkit metadata (holding the detonating player's name) and every
 * explosion handler re-read it, so a hand-lit TNT never got Blast Mining treatment. This port keeps
 * that design on the transient {@link MetadataStore}, storing the player's {@link UUID} instead of
 * their name (singleplayer has no name→player lookup, and the UUID is stable across renames).
 * A TNT entity's vanilla {@code owner} is deliberately <em>not</em> used for this: a player-lit TNT
 * also has an owner, so it would misclassify ordinary TNT as a Blast Mining charge.
 */
public final class BlastMiningListener {

    private BlastMiningListener() {
    }

    /** Marks a TNT entity as mcMMO-detonated; the value is the detonating player's {@link UUID}. */
    private static final String TRACKED_TNT_KEY = "mcmmo:tracked_tnt";

    /**
     * Blast Mining's remote detonation: a sneaking right-click with a pickaxe (or the configured
     * detonator item) primes the TNT the player is aiming at, from up to
     * {@link BlastMining#MAXIMUM_REMOTE_DETONATION_DISTANCE} blocks away. Ports legacy
     * {@code MiningManager#remoteDetonation}.
     *
     * <p>Gate order is upstream's and is load-bearing: the cooldown is checked <em>before</em> the
     * aim, so a player who tries to detonate too early is told they're too tired even if they aren't
     * looking at TNT.
     *
     * @param mmoPlayer the detonating player (already known to pass {@code canDetonate()})
     */
    public static void remoteDetonation(@NotNull McMMOPlayer mmoPlayer,
            @NotNull ServerPlayerEntity serverPlayer) {
        final MiningManager miningManager = mmoPlayer.getMiningManager();
        if (!miningManager.blastMiningCooldownOver()) {
            return;
        }

        final BlockPos targetPos = targetBlock(serverPlayer);
        if (targetPos == null
                || !serverPlayer.getEntityWorld().getBlockState(targetPos).isOf(Blocks.TNT)) {
            return;
        }
        // PORT (K5): legacy also required EventUtils.simulateBlockBreak(targetBlock, player) — a
        // fake BlockBreakEvent asking other plugins whether removing the TNT was allowed. There are
        // no plugins in singleplayer, so the check collapses to "always allowed".

        final ServerWorld world = (ServerWorld) serverPlayer.getEntityWorld();
        final Vec3d spawnPos = Vec3d.ofBottomCenter(targetPos);
        final TntEntity tnt = new TntEntity(world, spawnPos.getX(), spawnPos.getY(),
                spawnPos.getZ(), serverPlayer);
        MetadataStore.set(tnt, TRACKED_TNT_KEY, serverPlayer.getUuid());
        tnt.setFuse(0);
        world.spawnEntity(tnt);

        NotificationManager.sendPlayerInformation(mmoPlayer, NotificationType.SUPER_ABILITY,
                "Mining.Blast.Boom");

        // Remove the TNT block itself: it has become the primed entity above. Legacy's
        // targetBlock.setType(Material.AIR) — a silent replace, no drop, no break particles.
        world.setBlockState(targetPos, Blocks.AIR.getDefaultState());

        miningManager.startBlastMiningCooldown();
    }

    /**
     * The block the player is aiming at, or {@code null} if they are aiming at nothing solid within
     * range. Legacy used {@code player.getTargetBlock(BlockUtils.getTransparentBlocks(), 100)},
     * whose transparent-block set exists to make the ray pass <i>through</i> air/foliage; vanilla's
     * own ray-cast already skips non-colliding blocks, so the set has no analogue here.
     */
    private static @Nullable BlockPos targetBlock(@NotNull ServerPlayerEntity serverPlayer) {
        final HitResult hit = serverPlayer.raycast(
                BlastMining.MAXIMUM_REMOTE_DETONATION_DISTANCE, 1.0F, false);
        return hit.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) hit).getBlockPos() : null;
    }

    /**
     * Bigger Bombs: widen an mcMMO-detonated blast by the player's rank modifier. Called from
     * {@code fabric.mixin.TntExplodeMixin} with the power {@link TntEntity} is about to explode with;
     * ports legacy {@code EntityListener#onExplosionPrime}'s {@code event.setRadius(biggerBombs(...))}.
     * A vanilla (untracked) TNT, or a detonator who hasn't unlocked the sub-skill, gets {@code power}
     * back unchanged.
     *
     * @param tnt the exploding TNT entity
     * @param power the explosion power vanilla would use
     * @return the power to explode with
     */
    public static float applyBiggerBombs(@NotNull TntEntity tnt, float power) {
        final MiningManager miningManager = detonatorMiningManager(tnt);
        if (miningManager == null || !miningManager.canUseBiggerBombs()) {
            return power;
        }
        return miningManager.biggerBombs(power);
    }

    /**
     * Blast Mining's ore yield: replace what an mcMMO-detonated blast would have dropped with
     * mcMMO's own richer payout, and award the Mining XP for every ore destroyed. Ports legacy
     * {@code MiningManager#blastMiningDropProcessing}, driven from
     * {@code fabric.mixin.ExplosionDropsMixin} at the head of {@code ExplosionImpl#destroyBlocks} —
     * the analogue of the {@code EntityExplodeEvent}, which likewise fired with the doomed blocks
     * still standing.
     *
     * <p>Legacy split the blast list in two and paid them out differently, as here: <b>ores</b>
     * (blocks that carry Mining XP) drop their real loot once per successful yield round, plus the
     * Bigger-Bombs-independent bonus copies, and pay XP; <b>everything else</b> is debris with a
     * flat 10% chance to drop its own block item. Blocks that can't legitimately be obtained
     * (spawner, budding amethyst, infested) are skipped entirely — which also makes legacy's
     * separate {@code BLAST_MINING_BLACKLIST} on the ore spawn redundant, so it isn't ported.
     *
     * @param explosion the explosion about to destroy {@code blocks}
     * @param blocks the positions the explosion will destroy
     * @return whether mcMMO handled the drops, i.e. whether vanilla's own must now be suppressed
     *         (legacy's {@code event.setYield(0F)})
     */
    public static boolean processBlastDrops(@NotNull Explosion explosion,
            @NotNull List<BlockPos> blocks) {
        final MiningManager miningManager = detonatorMiningManager(explosion.getEntity());
        if (miningManager == null || !miningManager.canUseBlastMining()) {
            return false; // not an mcMMO charge (or the detonator lost the sub-skill): vanilla rules.
        }

        // Bukkit handed mcMMO a "yield" — the fraction of destroyed blocks that drop. Vanilla spells
        // the same number as the explosion-decay loot function's 1/radius survival chance, so that
        // is what the ore yield is boosted from. A zero-power blast drops nothing to boost.
        final float yield = explosion.getPower() <= 0 ? 0F : 1.0F / explosion.getPower();
        if (yield == 0) {
            return false;
        }

        final ServerWorld world = explosion.getWorld();
        final ServerPlayerEntity detonator = world.getServer()
                .getPlayerManager().getPlayer(detonatorUuid(explosion.getEntity()));
        if (detonator == null) {
            return false; // detonator logged out mid-fuse: leave the blast vanilla.
        }
        final ItemStack tool = detonator.getMainHandStack();
        final float oreYield = miningManager.blastMiningOreYield(yield);

        int xp = 0;
        for (BlockPos pos : blocks) {
            // PORT: legacy also skipped blocks the UserBlockTracker knows were player-placed (an
            // anti-farm measure). That tracker is still unported (CONVERSION_TODO §A), so a blast on
            // placed ores currently pays out. Revisit when it lands.
            final BlockState state = world.getBlockState(pos);
            if (miningManager.isDropIllegal(blockPath(state))) {
                continue;
            }

            if (isOre(state)) {
                xp += miningXp(state);
                dropOre(world, pos, state, detonator, tool, miningManager, oreYield);
            } else {
                dropDebris(world, pos, state, miningManager);
            }
        }

        miningManager.applyXpGain(xp, XPGainReason.PVE, XPGainSource.SELF);
        return true;
    }

    /**
     * Whether the blast should treat this block as an ore: it must both carry Mining XP and be a
     * registry-classified ore. Legacy additionally excluded {@code Container} block states — a
     * guard against an ore-classified block holding an inventory, which no vanilla block does, so
     * it has no analogue to port.
     */
    private static boolean isOre(BlockState state) {
        return miningXp(state) != 0 && BlockUtils.isOre(state);
    }

    /** The Mining XP {@code experience.yml} awards for breaking this block (0 if it awards none). */
    private static int miningXp(BlockState state) {
        return McMMOMod.getExperienceConfig().getXp(PrimarySkillType.MINING,
                ConfigStringUtils.getMaterialConfigString(blockPath(state)));
    }

    /**
     * Spawn an ore's blast payout: its real loot once per successful yield round (rolled with the
     * detonator's pickaxe, so Fortune applies exactly as when mining it by hand), plus the bonus
     * copies Blast Mining's rank grants. An empty-handed / non-pickaxe detonator gets the plain
     * block item instead, matching legacy.
     */
    private static void dropOre(ServerWorld world, BlockPos pos, BlockState state,
            ServerPlayerEntity detonator, ItemStack tool, MiningManager miningManager,
            float oreYield) {
        final int rounds = miningManager.rollOreDropRounds(oreYield);
        for (int round = 0; round < rounds; round++) {
            spawnOreDrops(world, pos, state, detonator, tool);

            final int bonusRounds = miningManager.rollBonusOreRounds();
            for (int bonus = 0; bonus < bonusRounds; bonus++) {
                spawnOreDrops(world, pos, state, detonator, tool);
            }
        }
    }

    private static void spawnOreDrops(ServerWorld world, BlockPos pos, BlockState state,
            ServerPlayerEntity detonator, ItemStack tool) {
        if (!ItemUtils.isPickaxe(tool)) {
            Block.dropStack(world, pos, new ItemStack(state.getBlock()));
            return;
        }
        for (ItemStack drop : Block.getDroppedStacks(state, world, pos,
                world.getBlockEntity(pos), detonator, tool)) {
            if (!drop.isEmpty()) {
                Block.dropStack(world, pos, drop);
            }
        }
    }

    /**
     * Spawn a non-ore's debris: a flat 10% chance at the block's own item (legacy spawns the block
     * itself, not its loot — so blasted stone yields stone, not cobblestone). Blocks with no item
     * form (fire, portals, …) drop nothing.
     */
    private static void dropDebris(ServerWorld world, BlockPos pos, BlockState state,
            MiningManager miningManager) {
        if (state.getBlock().asItem() == Items.AIR || !miningManager.rollDebrisDrop()) {
            return;
        }
        Block.dropStack(world, pos, new ItemStack(state.getBlock()));
    }

    private static String blockPath(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).getPath();
    }

    /**
     * The {@link MiningManager} of the player who remotely detonated {@code entity}, or {@code null}
     * unless this really is an mcMMO-tracked TNT whose detonator is still loaded.
     */
    public static @Nullable MiningManager detonatorMiningManager(@Nullable Entity entity) {
        final UUID detonator = detonatorUuid(entity);
        if (detonator == null) {
            return null;
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(detonator);
        return mmoPlayer == null ? null : mmoPlayer.getMiningManager();
    }

    /**
     * The {@link UUID} of the player who remotely detonated {@code entity}, or {@code null} if it is
     * not an mcMMO-tracked TNT (a vanilla-lit TNT, a creeper, a bed, …).
     */
    public static @Nullable UUID detonatorUuid(@Nullable Entity entity) {
        if (!(entity instanceof TntEntity)) {
            return null;
        }
        return MetadataStore.get(entity, TRACKED_TNT_KEY, UUID.class);
    }
}
