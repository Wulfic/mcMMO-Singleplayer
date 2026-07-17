package com.gmail.nossr50.util;

import com.gmail.nossr50.datatypes.skills.subskills.taming.CallOfTheWildType;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

/**
 * A live Call-of-the-Wild summon, seen through a server-free lens so {@link TransientEntityTracker} can
 * do its per-player bookkeeping (counting against the cap, cleanup) without touching a
 * {@code LivingEntity}. The MC-typed implementation ({@code fabric.CotwSummon}) wraps the real entity;
 * unit tests supply a fake.
 *
 * <p>This is the same "collapse a scheduled-runnable tracker onto a testable handle" substitution Arrow
 * Retrieval made (its {@code TrackedEntity} became an {@code int} on the {@code MetadataStore}) — here
 * legacy's {@code TrackedTamingEntity extends CancellableRunnable} is split into this MC-free contract
 * plus its MC-typed impl, which owns the despawn task.
 */
public interface TrackedSummon {

    /** Which animal this summon is, for per-type cap counting. */
    @NotNull CallOfTheWildType getCallOfTheWildType();

    /** The summoned entity's UUID, the tracker's key for death / anti-XP lookups. */
    @NotNull UUID getEntityId();

    /** Whether the summon is still alive and in the world (legacy's {@code LivingEntity#isValid}). */
    boolean isValid();

    /**
     * Remove the summon from the world and tell its owner. Cancels any pending despawn task.
     *
     * @param timeExpired {@code true} if the summon's lifespan ran out (the "Time is up" message),
     *                    {@code false} if it was cleared some other way (the "vanished" message)
     */
    void despawn(boolean timeExpired);
}
