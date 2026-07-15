package com.gmail.nossr50.runnables.skills;

import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import com.gmail.nossr50.util.CancellableRunnable;
import com.gmail.nossr50.util.LogUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Swords Rupture: the bleed damage-over-time applied by {@code SwordsManager#processRupture} (Phase
 * §C). Runs every tick on the {@link com.gmail.nossr50.platform.scheduler.TickScheduler}, dealing
 * {@code pureTickDamage} twice a second until it expires or the target dies.
 *
 * <p>Rupture damage is "pure" (see {@code advanced.yml}): it is written straight to the target's
 * health rather than dealt through the damage pipeline, so armor/absorption do not reduce it and it
 * causes no knockback, hurt animation, or invulnerability frames. It also can never land the killing
 * blow — {@link #calculateAdjustedTickDamage()} clamps the tick to leave 0.01 health.
 *
 * <p>Only one rupture runs per target: the active task is parked on {@link MetadataStore} under
 * {@link #RUPTURE_KEY} (replacing legacy's {@code RuptureTaskMeta}, a wrapper that existed only
 * because Bukkit metadata needed a {@code MetadataValue} — the store takes any object). A fresh hit
 * on an already-bleeding target calls {@link #refreshRupture()} instead of stacking a second task.
 *
 * <p>Kept MC-free (it holds a {@link PlatformLivingEntity}, not a vanilla {@code LivingEntity}) so
 * the timer/expiry/clamping logic is unit-testable, matching the other ported {@code runnables/}.
 *
 * <p>DROPPED from legacy: {@code McMMOEntityDamageByRuptureEvent} (a K5 plugin-veto event; there are
 * no other listeners in singleplayer, so the tick damage is applied directly), {@code
 * MobHealthbarUtils} (a multiplayer feature, cut in Phase 1.5), and {@code
 * ParticleEffectUtils.playBleedEffect} (deferred with the rest of the particle surface — no
 * adapter yet, same as Acrobatics' dodge effect).
 *
 * <p>Legacy's {@code ruptureSource} field goes with them: it fed only the dropped event, the debug
 * messages, and an {@code equals}/{@code hashCode} pair used for nothing (the marker, not equality,
 * is what stops a target stacking two bleeds). Rupture cannot land a killing blow, so there is no
 * kill to attribute back to the player either.
 *
 * <p><b>DEVIATION — CONVERSION_TODO §F upstream bug #4.</b> Legacy ran two counters: {@code
 * ruptureTick} (reset by {@code refreshRupture}) and a {@code totalTicks} failsafe bounded by
 * {@code totalTickCeiling = min(expireTick, 200)}. Because that ceiling is by construction {@code <=
 * expireTick} and both counters advance together, {@code totalTicks >= ceiling} always fired first
 * and legacy's {@code endRupture()} was <b>unreachable in every configuration</b>. Two live effects:
 * the rupture marker was never removed on natural expiry, so any mob that survived a full bleed
 * became permanently rupture-immune (every later hit took the refresh path on a dead task); and
 * {@code refreshRupture} could not actually extend a bleed, since it reset {@code ruptureTick} but
 * not {@code totalTicks}. This port keeps the intent — a single tick counter, still capped at
 * {@link #MAX_RUPTURE_TICKS} — and guarantees every exit path runs {@link #endRupture()}.
 */
public class RuptureTask extends CancellableRunnable {

    /** Ticks between damage applications — 10 ticks = twice a second, as {@code advanced.yml} says. */
    public static final int DAMAGE_TICK_INTERVAL = 10;

    /**
     * Absolute ceiling on a single rupture, from legacy's failsafe ("ensure Rupture always exits and
     * does not run forever"). A configured duration longer than this is truncated.
     */
    public static final int MAX_RUPTURE_TICKS = 200;

    /** {@link MetadataStore} key holding the {@link RuptureTask} currently bleeding a target. */
    public static final String RUPTURE_KEY = "mcmmo:rupture";

    private final @NotNull PlatformLivingEntity targetEntity;
    private final double pureTickDamage;
    private final int expireTicks;

    private int ruptureTick;
    private int damageTickTracker;

    /**
     * @param targetEntity the bleeding target
     * @param pureTickDamage health removed per damage tick
     * @param durationTicks configured bleed length, truncated to {@link #MAX_RUPTURE_TICKS}
     */
    public RuptureTask(@NotNull PlatformLivingEntity targetEntity, double pureTickDamage,
            int durationTicks) {
        this.targetEntity = targetEntity;
        this.pureTickDamage = pureTickDamage;
        this.expireTicks = Math.min(durationTicks, MAX_RUPTURE_TICKS);
        this.ruptureTick = 0;
        this.damageTickTracker = 0;
    }

    @Override
    public void run() {
        // Target died or was removed (commonly: the player finished it off mid-bleed).
        if (!targetEntity.isValid()) {
            endRupture();
            return;
        }

        ruptureTick++;
        damageTickTracker++;

        if (ruptureTick >= expireTicks) {
            applyRupture(); // legacy applies a final tick as the bleed expires
            endRupture();
            return;
        }

        if (damageTickTracker >= DAMAGE_TICK_INTERVAL) {
            damageTickTracker = 0;
            applyRupture();
        }
    }

    /**
     * Restart the bleed on a target that is already rupturing — legacy's answer to a second hit
     * landing mid-bleed (rupture refreshes rather than stacking). The next damage tick lands
     * immediately.
     */
    public void refreshRupture() {
        damageTickTracker = DAMAGE_TICK_INTERVAL;
        ruptureTick = 0;
    }

    /**
     * Apply one tick of pure bleed damage.
     *
     * @return {@code true} if the bleed could not be applied and should stop trying
     */
    private boolean applyRupture() {
        final double healthBeforeRuptureIsApplied = targetEntity.getHealth();
        if (healthBeforeRuptureIsApplied <= 0.01) {
            return false;
        }

        final double damage = calculateAdjustedTickDamage();
        if (damage <= 0 || healthBeforeRuptureIsApplied - damage <= 0) {
            return true;
        }

        final double damagedHealth = healthBeforeRuptureIsApplied - damage;
        if (damagedHealth > targetEntity.getMaxHealth()) {
            // Something else is mutating this entity's health in a way we cannot reason about;
            // refuse to write rather than heal the target by "damaging" it.
            LogUtils.debug("RuptureTask: target " + targetEntity.getUniqueId() + " has an illegal"
                    + " health state (health " + healthBeforeRuptureIsApplied + " exceeds max health "
                    + targetEntity.getMaxHealth() + "). Cancelling Rupture.");
            return true;
        }

        targetEntity.setHealth((float) damagedHealth);
        return false;
    }

    /** Clamp the tick so a bleed always leaves the target alive (legacy never lets Rupture kill). */
    private double calculateAdjustedTickDamage() {
        if (targetEntity.getHealth() > pureTickDamage) {
            return pureTickDamage;
        }
        return Math.max(targetEntity.getHealth() - 0.01, 0);
    }

    /** Stop bleeding and release the target's marker so it can be ruptured again later. */
    private void endRupture() {
        MetadataStore.remove(targetEntity.getUniqueId(), RUPTURE_KEY);
        cancel();
    }
}
