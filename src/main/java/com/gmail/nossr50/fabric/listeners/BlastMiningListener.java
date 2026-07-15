package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.interactions.NotificationType;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.skills.mining.BlastMining;
import com.gmail.nossr50.skills.mining.MiningManager;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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
