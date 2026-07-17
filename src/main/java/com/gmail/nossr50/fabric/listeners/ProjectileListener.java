package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.PlatformItem;
import com.gmail.nossr50.skills.archery.Archery;
import com.gmail.nossr50.skills.archery.ArcheryManager;
import com.gmail.nossr50.util.player.UserManager;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Both ends of Archery's <b>Arrow Retrieval</b>: the launch mark and the death drop. Replaces legacy
 * {@code EntityListener#onProjectileLaunch} (driven here by {@code fabric.mixin.ProjectileSpawnMixin},
 * since vanilla fires no launch event) and the {@code Archery.arrowRetrievalCheck(entity)} line of
 * legacy {@code EntityListener#onEntityDeath}.
 *
 * <p>The middle of the lifecycle — crediting a struck entity when a marked arrow hits it — belongs to
 * the damage pipeline and so lives on {@link EntityDamageListener}'s Archery arm, exactly where legacy
 * put it ({@code CombatUtils#processArcheryCombat}).
 *
 * <p><b>Deliberately not ported from the legacy launch handler</b>, because each would create state
 * that nothing reads — the "config that lies" failure mode this port keeps hitting:
 * <ul>
 *   <li>{@code METADATA_KEY_ARROW_DISTANCE} / {@code METADATA_KEY_BOW_FORCE} — both feed
 *       <em>per-hit</em> Archery XP multipliers, but this port pays combat XP per <em>kill</em>
 *       ({@link CombatListener}). Stamping them now would be write-only state; consuming them needs a
 *       per-hit-vs-per-kill XP-model decision, not an adapter.</li>
 *   <li>{@code METADATA_KEY_MULTI_SHOT_ARROW} — write-only <em>upstream too</em>: legacy sets it here
 *       and nothing anywhere reads it (see the §F note in CONVERSION_TODO).</li>
 * </ul>
 */
public final class ProjectileListener {

    /**
     * How long a launch mark survives if the arrow never strikes a living entity. Legacy's
     * {@code CombatUtils#delayArrowMetaCleanup} used {@code 20 * 120} ticks; kept verbatim (note it is
     * two minutes, not the one minute its comment claims). Without it every arrow ever fired would
     * leave an entry on the {@link MetadataStore} side-table until server stop.
     */
    private static final long MARK_CLEANUP_DELAY_TICKS = 20 * 120;

    private ProjectileListener() {
    }

    /** Register the death half of Arrow Retrieval. Called once from {@code McMMOMod#onInitialize}. */
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(ProjectileListener::onDeath);
    }

    /**
     * Launch half: roll Arrow Retrieval and mark the arrow if it wins. Driven from
     * {@code ProjectileSpawnMixin} for <em>every</em> projectile vanilla spawns, so this does the
     * narrowing legacy's handler did — a player-owned arrow, and nothing else.
     *
     * <p>{@code instanceof ArrowEntity} is legacy's {@code instanceof Arrow}, not a widening to
     * {@code PersistentProjectileEntity}: {@code SpectralArrowEntity} and {@code TridentEntity} are
     * <em>siblings</em> under it, mirroring Bukkit where {@code SpectralArrow}/{@code Trident}
     * implement {@code AbstractArrow} rather than {@code Arrow}. Neither was ever retrievable upstream.
     */
    public static void onProjectileSpawn(ProjectileEntity projectile, ServerWorld world) {
        if (!(projectile instanceof ArrowEntity arrow)) {
            return;
        }
        if (!(arrow.getOwner() instanceof ServerPlayerEntity shooter)) {
            return; // wild/dispenser arrow — legacy's `getShooter() instanceof Player` check.
        }
        final McMMOPlayer mmoPlayer = UserManager.getPlayer(shooter.getUuid());
        if (mmoPlayer == null) {
            return; // data not loaded (e.g. mid-join).
        }
        final ArcheryManager archery = mmoPlayer.getArcheryManager();
        if (archery == null) {
            return;
        }
        if (isInfinityShot(arrow) || hasPiercingInHands(shooter)) {
            return;
        }
        if (!archery.rollArrowRetrieval()) {
            return;
        }

        MetadataStore.setFlag(arrow, Archery.TRACKED_ARROW_KEY);
        final UUID arrowId = arrow.getUuid();
        McMMOMod.getScheduler().runLater(
                () -> MetadataStore.remove(arrowId, Archery.TRACKED_ARROW_KEY),
                MARK_CLEANUP_DELAY_TICKS);
    }

    /**
     * Whether this arrow was fired from an Infinity bow, in which case retrieving it would duplicate
     * ammo the shooter never spent.
     *
     * <p>Legacy reached the same conclusion by a longer route: a second handler
     * ({@code onEntityShootBow}) stamped {@code METADATA_KEY_INF_ARROW} from the bow, and the hit-side
     * check read it back. The firing weapon is recorded on the arrow itself in 1.21
     * ({@code PersistentProjectileEntity#getWeaponStack()}), so the marker is redundant — skipping the
     * mark at launch and never marking are indistinguishable to the hit side.
     *
     * <p>{@code getWeaponStack()} is genuinely nullable (vanilla's own {@code readCustomData} restores
     * it with {@code orElse(null)}), hence the guard — though on this path the arrow was just built by
     * {@code RangedWeaponItem#createArrowEntity}, which always records the weapon.
     */
    private static boolean isInfinityShot(ArrowEntity arrow) {
        final ItemStack weapon = arrow.getWeaponStack();
        if (weapon == null || weapon.isEmpty()) {
            return false;
        }
        return new PlatformItem(weapon).getEnchantmentLevel(Enchantments.INFINITY) > 0;
    }

    /**
     * Legacy's {@code ItemUtils.doesPlayerHaveEnchantmentInHands(player, PIERCING)}: a Piercing shot is
     * never tracked. Checks both hands, as legacy does, rather than the arrow's recorded weapon — the
     * looser check is the ported behaviour.
     */
    private static boolean hasPiercingInHands(ServerPlayerEntity shooter) {
        return hasPiercing(shooter.getMainHandStack()) || hasPiercing(shooter.getOffHandStack());
    }

    private static boolean hasPiercing(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && new PlatformItem(stack).getEnchantmentLevel(Enchantments.PIERCING) > 0;
    }

    /**
     * Death half: hand back every tracked arrow stuck in the entity that just died (legacy
     * {@code Archery.arrowRetrievalCheck}).
     *
     * <p>Registered separately from {@link CombatListener}'s kill hook on purpose, even though legacy
     * ran both from one {@code onEntityDeath}: that listener returns early unless a <em>player</em>
     * landed the killing blow, whereas the arrows are owed regardless of what finished the mob off.
     *
     * <p>PORT deviation (benign): legacy spawned {@code count} separate one-arrow item entities;
     * this drops a single stack of {@code count}. The player picks up the same arrows either way —
     * vanilla would have merged the stacks on the ground within a tick.
     */
    private static void onDeath(LivingEntity victim, DamageSource source) {
        final int arrowCount = Archery.arrowRetrievalCheck(victim.getUuid());
        if (arrowCount <= 0) {
            return;
        }
        if (!(victim.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        final ItemEntity drop = new ItemEntity(world, victim.getX(), victim.getY(), victim.getZ(),
                new ItemStack(Items.ARROW, arrowCount));
        drop.setToDefaultPickupDelay(); // Bukkit's World#dropItem behaviour.
        world.spawnEntity(drop);
    }
}
