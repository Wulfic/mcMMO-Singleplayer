package com.gmail.nossr50.fabric.listeners;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.platform.SkillAttributeService;
import com.gmail.nossr50.skills.agility.AgilityManager;
import com.gmail.nossr50.skills.agility.Medium;
import com.gmail.nossr50.util.player.UserManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The per-tick movement sampler behind Agility's Land, Water and Air domains (F1).
 *
 * <p>Everything ported in Pass 1 hangs off a discrete event — a block broken, an entity damaged, an
 * item used. Movement is not an event; it is a continuous state, and nothing in the codebase sampled
 * it before this. One {@code END_SERVER_TICK} sweep measures how far each online player moved,
 * decides which medium they moved through, and hands that to
 * {@link AgilityManager#onMovementTick}.
 *
 * <p><b>This class deliberately does not compute XP.</b> It owns only the platform-y guards below;
 * the speed clamp that turns distance into credited seconds is MC-free arithmetic in
 * {@link com.gmail.nossr50.skills.agility.MovementXpSettings}, where it can be unit-tested. Getting
 * that split wrong is how the most important formula in the skill ends up buried in a tick handler
 * that no test can reach.
 *
 * <p><b>Anti-AFK / anti-exploit is load-bearing here, not a nicety.</b> A bubble elevator, a
 * soul-sand column, flowing water, a minecart loop and a firework circuit all move a player who is
 * not at the keyboard. Three guards cover all of them:
 * <ul>
 *   <li><b>No vehicles.</b> Boats, horses and minecarts move the player; the player is not moving.</li>
 *   <li><b>No teleport-scale deltas.</b> Anything past {@link #TELEPORT_DELTA} in a single tick is a
 *       teleport, a portal or a dimension change, not travel — skipped, and the baseline is reset so
 *       the <em>next</em> tick doesn't bill the whole jump either.</li>
 *   <li><b>Real movement required.</b> Sprinting into a wall, or a rubber-banded key, produces a
 *       delta of roughly zero and pays roughly zero.</li>
 * </ul>
 * Even if one of those leaks, the speed clamp caps what it can be worth per second — the guards and
 * the clamp are independent defences on purpose.
 *
 * <p>Cost: this runs 20×/s per online player. Singleplayer means one player, but it is written as if
 * it were not — no per-tick config parsing (the manager snapshots its tuning once) and no per-tick
 * allocation beyond one {@link Vec3d} per player.
 */
public final class PlayerMovementTracker {

    private PlayerMovementTracker() {
    }

    /**
     * Per-tick distance past which movement is treated as a teleport rather than travel.
     *
     * <p>Ten blocks per tick is 200 blocks/second — far above anything reachable by sprinting,
     * swimming or even a rocket-boosted dive, and far below a typical teleport. Being generous here
     * is safe because the speed clamp already caps the payout of any single tick; the guard exists to
     * stop a dimension change from registering as a lifetime of travel, not to police speed.
     */
    private static final double TELEPORT_DELTA = 10.0;

    /**
     * Movement below this is treated as standing still. Floating-point position jitter and the
     * sub-pixel drift of a player pressed against a wall both land under it.
     */
    private static final double MIN_DELTA = 1.0E-4;

    /** Last tick's position per player. Not a session field, so it can be reset independently. */
    private static final Map<UUID, Vec3d> LAST_POSITIONS = new HashMap<>();

    /** Ticks since each player's last Solar Wings repair, so the trickle is rate-limited. */
    private static final Map<UUID, Integer> SOLAR_WINGS_TICKS = new HashMap<>();

    /**
     * Register the sweep and its teardown. Called once at mod load from
     * {@link com.gmail.nossr50.fabric.McMMOMod#onInitialize}.
     */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlayerMovementTracker::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onQuit(handler.player));
    }

    /** Drop all per-player movement state (server stop). */
    public static void clear() {
        LAST_POSITIONS.clear();
        SOLAR_WINGS_TICKS.clear();
    }

    private static void onQuit(@NotNull ServerPlayerEntity player) {
        LAST_POSITIONS.remove(player.getUuid());
        SOLAR_WINGS_TICKS.remove(player.getUuid());
        // Belt-and-braces: the modifiers are temporary and never persisted, but leaving nothing
        // behind on a player who is no longer online makes the invariant trivially checkable.
        SkillAttributeService.clearAll(player);
    }

    private static void onServerTick(@NotNull MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            try {
                tickPlayer(player);
            } catch (Exception e) {
                // One bad tick must never take the server tick loop down with it, and a movement
                // skill failing silently forever is worse than a log line.
                com.gmail.nossr50.fabric.McMMOMod.LOGGER.error(
                        "Agility movement tick failed for {}", player.getName().getString(), e);
            }
        }
    }

    private static void tickPlayer(@NotNull ServerPlayerEntity player) {
        final UUID uuid = player.getUuid();
        final Vec3d current = player.getEntityPos();
        final Vec3d previous = LAST_POSITIONS.put(uuid, current);

        final McMMOPlayer mmoPlayer = UserManager.getPlayer(uuid);
        if (mmoPlayer == null) {
            return; // Data not loaded yet (mid-join) — nothing to credit and nothing to buff.
        }
        final AgilityManager agility = mmoPlayer.getAgilityManager();
        if (agility == null) {
            return;
        }

        final Medium medium = classifyMedium(player);
        // Re-derive the speed buffs from live state every tick rather than tracking whether they are
        // applied. Respawning and leaving the End both build a NEW ServerPlayerEntity, silently
        // dropping every modifier on the old one, so cached "already applied" state goes wrong on the
        // first death while re-deriving self-heals on the next tick.
        applyFleetFooted(player, agility, medium);
        applyLeadLungs(player, agility);
        applySolarWings(player, agility);

        if (medium == null || previous == null || player.hasVehicle()) {
            // No qualifying medium, no baseline to measure against, or being carried by something
            // else. In the vehicle case the baseline was still refreshed above, so stepping out of a
            // boat does not bill the whole ride.
            return;
        }

        // Horizontal only, for every medium. The reference speeds are horizontal figures, and it also
        // means a player cannot bill a vertical elytra dive (or a fall) as travel.
        final double dx = current.x - previous.x;
        final double dz = current.z - previous.z;
        final double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance < MIN_DELTA || distance > TELEPORT_DELTA) {
            return;
        }
        agility.onMovementTick(medium, distance);
    }

    /**
     * Which medium this tick counts as, or {@code null} when the player is not doing anything that
     * earns Agility XP (walking, standing, falling).
     *
     * <p>Exactly one medium per tick, checked most-specialised first — a player can be gliding
     * <em>and</em> touching water, or sprinting <em>and</em> swimming, and paying both would double
     * the rate for a single tick of travel.
     *
     * <p>Public because Second Wind dispatches its body on the same classification: "which Agility
     * domain is this player in right now" must have exactly one answer, and two implementations of
     * that question would eventually disagree.
     */
    public static @Nullable Medium classifyMedium(@NotNull ServerPlayerEntity player) {
        if (player.hasVehicle()) {
            return null;
        }
        if (player.isGliding()) {
            return Medium.AIR;
        }
        if (player.isTouchingWater()) {
            return Medium.WATER;
        }
        if (player.isSprinting()) {
            return Medium.LAND;
        }
        return null;
    }

    /**
     * Apply or clear the Fleet Footed speed buff for the medium the player is in right now.
     *
     * <p>Both media are set every tick — including to {@code 0} — so the buff is removed the instant
     * the medium ends rather than lingering until something notices. The air body is not here: elytra
     * flight is velocity-driven with no attribute behind it, so it lives in
     * {@link com.gmail.nossr50.fabric.mixin.LivingEntityGlideMixin}.
     */
    private static void applyFleetFooted(@NotNull ServerPlayerEntity player,
            @NotNull AgilityManager agility, @Nullable Medium medium) {
        SkillAttributeService.set(player, SkillAttributeService.Managed.AGILITY_FLEET_FOOTED_LAND,
                medium == Medium.LAND ? agility.getFleetFootedBonus(Medium.LAND) : 0.0);
        SkillAttributeService.set(player, SkillAttributeService.Managed.AGILITY_FLEET_FOOTED_WATER,
                medium == Medium.WATER ? agility.getFleetFootedBonus(Medium.WATER) : 0.0);
    }

    /**
     * Top up the player's air while submerged (Lead Lungs).
     *
     * <p>A per-tick top-up rather than a mixin on vanilla's air decrement: it is simpler, it stacks
     * sanely with Respiration (which reduces how often air is spent rather than how much), and if it
     * ever misbehaves the failure mode is "breath is slightly wrong", not "the drowning code is
     * broken". Clamped to the vanilla maximum so it can top up but never overfill.
     */
    private static void applyLeadLungs(@NotNull ServerPlayerEntity player,
            @NotNull AgilityManager agility) {
        if (!player.isSubmergedInWater()) {
            return;
        }
        final int topUp = agility.consumeLeadLungsAirTopUp();
        if (topUp <= 0) {
            return;
        }
        final int maxAir = player.getMaxAir();
        final int air = player.getAir();
        if (air < maxAir) {
            player.setAir(Math.min(maxAir, air + topUp));
        }
    }

    /**
     * Slowly repair a worn, damaged elytra in daylight (Solar Wings).
     *
     * <p>Rate-limited hard, and it must stay that way: elytra durability is one of the few real
     * resource pressures in late-game Minecraft, and a generous repair rate deletes it outright.
     * Repairs faster on the ground than in flight, so it is a reason to land rather than a reason to
     * never land.
     */
    private static void applySolarWings(@NotNull ServerPlayerEntity player,
            @NotNull AgilityManager agility) {
        final UUID uuid = player.getUuid();
        if (!agility.canSolarWings()) {
            SOLAR_WINGS_TICKS.remove(uuid);
            return;
        }
        final ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA) || !chest.isDamaged()) {
            SOLAR_WINGS_TICKS.remove(uuid);
            return;
        }
        if (!player.getEntityWorld().isDay()
                || !player.getEntityWorld().isSkyVisibleAllowingSea(player.getBlockPos())) {
            return; // Keep the counter: stepping through a tunnel shouldn't reset the progress.
        }

        final int elapsed = SOLAR_WINGS_TICKS.merge(uuid, 1, Integer::sum);
        if (elapsed < agility.getSolarWingsIntervalTicks()) {
            return;
        }
        SOLAR_WINGS_TICKS.put(uuid, 0);

        final int repair = agility.getSolarWingsRepairAmount(player.isOnGround());
        if (repair > 0) {
            chest.setDamage(Math.max(0, chest.getDamage() - repair));
        }
    }
}
