package com.gmail.nossr50.fabric;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.subskills.taming.CallOfTheWildType;
import com.gmail.nossr50.platform.scheduler.ScheduledTask;
import com.gmail.nossr50.util.TrackedSummon;
import com.gmail.nossr50.util.TransientEntityTracker;
import com.gmail.nossr50.util.player.NotificationManager;
import com.gmail.nossr50.util.player.UserManager;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * The MC-typed {@link TrackedSummon}: a live Call-of-the-Wild animal plus its despawn schedule. Ported
 * from legacy {@code skills.taming.TrackedTamingEntity} (a {@code CancellableRunnable}), split so the
 * bookkeeping lives on the server-free {@link TransientEntityTracker} and only the entity handling is
 * here.
 *
 * <p>A finite-lifespan summon schedules a one-shot despawn on the {@link com.gmail.nossr50.platform.scheduler.TickScheduler}
 * ({@code lifespan * 20} ticks, legacy's {@code Misc.TICK_CONVERSION_FACTOR}); a lifespan of {@code 0}
 * (Summon_Length in the config) never expires. {@link #despawn(boolean)} is idempotent and cancels a
 * still-pending task, so logging out (which drives {@code cleanupPlayer}) can't leave a task firing
 * later on a discarded entity.
 *
 * <p>Deviation from legacy: despawn uses {@link net.minecraft.entity.Entity#discard()} (a silent
 * removal) rather than legacy's {@code setHealth(0)} + {@code remove()}, which fired death events and
 * dropped loot — a summoned pet should vanish, not die and drop meat/leather. The death sound and
 * {@code playCallOfTheWildEffect} particle are dropped with it (no particle adapter — the standing
 * Dodge/Rupture deferral).
 */
public final class CotwSummon implements TrackedSummon {

    private static final long TICKS_PER_SECOND = 20L;

    private final @NotNull LivingEntity entity;
    private final @NotNull CallOfTheWildType type;
    private final @NotNull UUID playerId;
    private final @NotNull TransientEntityTracker tracker;
    private ScheduledTask despawnTask;
    private boolean despawned = false;

    public CotwSummon(@NotNull LivingEntity entity, @NotNull CallOfTheWildType type,
            @NotNull UUID playerId, @NotNull TransientEntityTracker tracker, int lifespanSeconds) {
        this.entity = entity;
        this.type = type;
        this.playerId = playerId;
        this.tracker = tracker;
        if (lifespanSeconds > 0) {
            this.despawnTask = McMMOMod.getScheduler()
                    .runLater(() -> despawn(true), lifespanSeconds * TICKS_PER_SECOND);
        }
    }

    @Override
    public @NotNull CallOfTheWildType getCallOfTheWildType() {
        return type;
    }

    @Override
    public @NotNull UUID getEntityId() {
        return entity.getUuid();
    }

    @Override
    public boolean isValid() {
        return entity.isAlive() && !entity.isRemoved();
    }

    @Override
    public void despawn(boolean timeExpired) {
        if (despawned) {
            return;
        }
        despawned = true;

        if (despawnTask != null && !despawnTask.isCancelled()) {
            despawnTask.cancel();
        }
        if (isValid()) {
            entity.discard();
        }

        final McMMOPlayer owner = UserManager.getPlayer(playerId);
        if (owner != null) {
            NotificationManager.sendPlayerInformationChatOnly(owner,
                    timeExpired ? "Taming.Summon.COTW.TimeExpired" : "Taming.Summon.COTW.Removed",
                    type.getDisplayName());
        }

        tracker.removeSummon(playerId, this);
    }
}
