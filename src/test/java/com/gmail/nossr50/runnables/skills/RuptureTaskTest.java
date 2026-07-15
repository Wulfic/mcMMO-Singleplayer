package com.gmail.nossr50.runnables.skills;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.PlatformLivingEntity;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves the Swords Rupture bleed loop ({@link RuptureTask}).
 *
 * <p>Rupture is "pure" damage written straight to health, dealt every
 * {@link RuptureTask#DAMAGE_TICK_INTERVAL} ticks (twice a second) for the configured duration.
 *
 * <p>{@link #expiryReleasesTheTargetsMarker()} and {@link #refreshExtendsTheBleed()} pin the fix for
 * CONVERSION_TODO §F upstream bug #4: legacy's {@code totalTicks} failsafe shadowed its own
 * {@code endRupture()}, so on expiry the task cancelled without releasing the marker (leaving the
 * target permanently rupture-immune) and {@code refreshRupture} could not extend a bleed.
 */
class RuptureTaskTest {

    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final int DURATION_TICKS = 100; // advanced.yml Against_Mobs: 5s

    private PlatformLivingEntity target;

    @BeforeEach
    void setUp() {
        target = mock(PlatformLivingEntity.class);
        when(target.getUniqueId()).thenReturn(TARGET_ID);
        when(target.isValid()).thenReturn(true);
        when(target.getHealth()).thenReturn(20.0F);
        when(target.getMaxHealth()).thenReturn(20.0F);
    }

    @AfterEach
    void tearDown() {
        MetadataStore.clearAll();
    }

    /** Build a task and park it on the store the way {@code SwordsManager#processRupture} does. */
    private RuptureTask markedRupture(double tickDamage) {
        final RuptureTask task = new RuptureTask(target, tickDamage, DURATION_TICKS);
        MetadataStore.set(TARGET_ID, RuptureTask.RUPTURE_KEY, task);
        return task;
    }

    private static void runTicks(RuptureTask task, int ticks) {
        for (int i = 0; i < ticks; i++) {
            task.run();
        }
    }

    private RuptureTask activeRupture() {
        return MetadataStore.get(TARGET_ID, RuptureTask.RUPTURE_KEY, RuptureTask.class);
    }

    @Test
    void damageLandsOnlyEveryTenthTick() {
        final RuptureTask task = markedRupture(1.0D);

        runTicks(task, RuptureTask.DAMAGE_TICK_INTERVAL - 1);
        verify(target, never()).setHealth(anyFloat());

        task.run(); // 10th tick
        verify(target).setHealth(19.0F);
    }

    @Test
    void expiryReleasesTheTargetsMarker() {
        final RuptureTask task = markedRupture(1.0D);

        runTicks(task, DURATION_TICKS);

        assertTrue(task.isCancelled(), "bleed should stop once its duration elapses");
        assertNull(activeRupture(),
                "expired bleed must release its marker, or the target can never rupture again");
    }

    @Test
    void refreshExtendsTheBleed() {
        final RuptureTask task = markedRupture(1.0D);

        runTicks(task, DURATION_TICKS - 1);
        task.refreshRupture();
        // Legacy's absolute failsafe would already have killed the task by this point.
        runTicks(task, DURATION_TICKS - 1);

        assertFalse(task.isCancelled(), "a refreshed bleed runs a full fresh duration");
        assertTrue(activeRupture() == task, "a refreshed bleed keeps its marker");
    }

    @Test
    void bleedNeverLandsTheKillingBlow() {
        when(target.getHealth()).thenReturn(0.5F); // less than one tick of damage
        final RuptureTask task = markedRupture(1.0D);

        runTicks(task, RuptureTask.DAMAGE_TICK_INTERVAL);

        // Damage is clamped to health - 0.01 so the target survives on a sliver.
        verify(target).setHealth(0.01F);
    }

    @Test
    void deadTargetEndsTheBleedAndReleasesTheMarker() {
        final RuptureTask task = markedRupture(1.0D);
        when(target.isValid()).thenReturn(false); // player finished it off mid-bleed

        task.run();

        assertTrue(task.isCancelled(), "a bleed on a dead target must stop");
        assertNull(activeRupture(), "a dead target's marker must not outlive it");
        verify(target, never()).setHealth(anyFloat());
    }

    @Test
    void refusesToWriteHealthWhenTargetStateIsIllegal() {
        // Health above max means something else is mutating this entity; writing health - damage
        // would "damage" it to a value above its maximum.
        when(target.getHealth()).thenReturn(30.0F);
        when(target.getMaxHealth()).thenReturn(20.0F);
        final RuptureTask task = markedRupture(1.0D);

        runTicks(task, RuptureTask.DAMAGE_TICK_INTERVAL);

        verify(target, never()).setHealth(anyFloat());
    }

    @Test
    void durationIsTruncatedToTheFailsafeCeiling() {
        // A config asking for longer than the ceiling still exits at MAX_RUPTURE_TICKS.
        final RuptureTask task = new RuptureTask(target, 1.0D, RuptureTask.MAX_RUPTURE_TICKS * 2);
        MetadataStore.set(TARGET_ID, RuptureTask.RUPTURE_KEY, task);

        runTicks(task, RuptureTask.MAX_RUPTURE_TICKS);

        assertTrue(task.isCancelled(), "bleed must not outlive the failsafe ceiling");
        assertNull(activeRupture(), "the ceiling exit must release the marker too");
    }
}
