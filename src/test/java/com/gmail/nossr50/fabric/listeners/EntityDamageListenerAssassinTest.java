package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.stealth.StealthManager;
import com.gmail.nossr50.util.player.UserManager;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Stealth <b>Assassin</b>'s wiring into the combat seam — the half that
 * {@code StealthManagerTest} cannot reach.
 *
 * <p>That test already pins the {@code assassinReady} truth table and the multiplier's clamp. What
 * is unproven without this file is everything the listener owns: that the backstab is actually
 * applied to the damage, that the "hasn't been hit recently" window is measured against a clock that
 * only moves forwards, and that the same gates which keep Smash off a projectile keep Assassin off
 * one too. Those are exactly the pieces that can be deleted while every MC-free test stays green.
 */
class EntityDamageListenerAssassinTest {

    @BeforeAll
    static void bootstrapRegistries() {
        com.gmail.nossr50.util.McTestRegistries.bootstrap();
    }

    private static final int NOW = 10_000;

    private UUID uuid;
    private McMMOPlayer mmoPlayer;

    @AfterEach
    void tearDown() {
        if (mmoPlayer != null) {
            UserManager.cleanupPlayer(mmoPlayer);
        }
        EntityDamageListener.clear();
    }

    /** A crouched attacker mid-swing, with the server clock parked at {@link #NOW}. */
    private ServerPlayer attacker() {
        uuid = UUID.randomUUID();

        final MinecraftServer server = mock(MinecraftServer.class);
        lenient().when(server.getTickCount()).thenReturn(NOW);
        final ServerLevel world = mock(ServerLevel.class);
        lenient().when(world.getServer()).thenReturn(server);

        final ServerPlayer player = mock(ServerPlayer.class);
        lenient().when(player.getUUID()).thenReturn(uuid);
        lenient().when(player.isShiftKeyDown()).thenReturn(true);
        lenient().when(player.level()).thenReturn(world);
        return player;
    }

    /** Register a mock profile whose Stealth manager answers however the test needs. */
    private StealthManager trackStealth(boolean ready, double multiplier) {
        final StealthManager stealth = mock(StealthManager.class);
        lenient().when(stealth.assassinReady(anyBoolean(), anyLong())).thenReturn(ready);
        lenient().when(stealth.getAssassinDamageMultiplier()).thenReturn(multiplier);

        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(uuid);
        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getStealthManager()).thenReturn(stealth);
        UserManager.track(mmoPlayer);
        return stealth;
    }

    /** A direct melee source: the attacker is both the responsible entity and the direct one. */
    private static DamageSource melee(ServerPlayer attacker) {
        final DamageSource source = mock(DamageSource.class);
        lenient().when(source.getEntity()).thenReturn(attacker);
        lenient().when(source.getDirectEntity()).thenReturn(attacker);
        lenient().when(source.is(DamageTypes.THORNS)).thenReturn(false);
        return source;
    }

    // --- the payload ----------------------------------------------------------------------------

    @Test
    void aReadyBackstabMultipliesTheDamage() {
        final ServerPlayer attacker = attacker();
        trackStealth(true, 2.0);

        assertEquals(20F,
                EntityDamageListener.applyAssassin(mock(LivingEntity.class), melee(attacker), 10F),
                1.0E-4);
    }

    @Test
    void anUnreadyBackstabLeavesTheDamageAlone() {
        final ServerPlayer attacker = attacker();
        trackStealth(false, 2.0);

        assertEquals(10F,
                EntityDamageListener.applyAssassin(mock(LivingEntity.class), melee(attacker), 10F),
                1.0E-4);
    }

    @Test
    void aWalkingAttackerIsNotBackstabbing() {
        final ServerPlayer attacker = attacker();
        when(attacker.isShiftKeyDown()).thenReturn(false);
        trackStealth(true, 2.0);

        assertEquals(10F,
                EntityDamageListener.applyAssassin(mock(LivingEntity.class), melee(attacker), 10F),
                1.0E-4);
    }

    @Test
    void anArmourStandCannotBeBackstabbed() {
        // Same carve-out as Smash: an armour stand is a punching bag, not a victim, and letting it
        // proc combat sub-skills is how a damage multiplier gets measured and then abused.
        final ServerPlayer attacker = attacker();
        trackStealth(true, 2.0);

        assertEquals(10F, EntityDamageListener.applyAssassin(
                mock(ArmorStand.class), melee(attacker), 10F), 1.0E-4);
    }

    @Test
    void aProjectileIsNotABackstab() {
        // The direct source is the arrow, not the player — so a sneaking archer does not get to
        // multiply every shot. Mirrors the weapon arm's and Smash's own test.
        final ServerPlayer attacker = attacker();
        trackStealth(true, 2.0);

        final DamageSource source = melee(attacker);
        when(source.getDirectEntity()).thenReturn(mock(LivingEntity.class));

        assertEquals(10F,
                EntityDamageListener.applyAssassin(mock(LivingEntity.class), source, 10F), 1.0E-4);
    }

    @Test
    void thornsIsNotASwing() {
        final ServerPlayer attacker = attacker();
        trackStealth(true, 2.0);

        final DamageSource source = melee(attacker);
        when(source.is(DamageTypes.THORNS)).thenReturn(true);

        assertEquals(10F,
                EntityDamageListener.applyAssassin(mock(LivingEntity.class), source, 10F), 1.0E-4);
    }

    // --- the recency window (D-S3) ---------------------------------------------------------------

    @Test
    void aPlayerWhoHasNeverBeenHitIsMaximallyStealthy() {
        // Not zero — a fresh session must read as "hasn't been hit in ages", or Assassin would be
        // off until the player took their first hit, which is exactly backwards.
        assertEquals(Long.MAX_VALUE, EntityDamageListener.ticksSinceDamageTaken(attacker()));
    }

    @Test
    void theWindowIsMeasuredFromTheStampedTick() {
        final ServerPlayer player = attacker();
        EntityDamageListener.recordDamageTaken(player); // stamped at NOW

        assertEquals(0L, EntityDamageListener.ticksSinceDamageTaken(player));

        // Advance the server clock; the window must open by exactly that much.
        when(player.level().getServer().getTickCount()).thenReturn(NOW + 137);
        assertEquals(137L, EntityDamageListener.ticksSinceDamageTaken(player));
    }

    @Test
    void aClockThatRanBackwardsCannotInvertTheWindow() {
        // getTicks() is an int and wraps after ~3.4 years of uptime. A negative window would read as
        // "hit in the future" and silently disable the sub-skill rather than mistiming it once.
        final ServerPlayer player = attacker();
        EntityDamageListener.recordDamageTaken(player);
        when(player.level().getServer().getTickCount()).thenReturn(NOW - 500);

        assertEquals(0L, EntityDamageListener.ticksSinceDamageTaken(player));
    }

    @Test
    void forgettingAPlayerRestoresTheNeverBeenHitState() {
        final ServerPlayer player = attacker();
        EntityDamageListener.recordDamageTaken(player);
        assertTrue(EntityDamageListener.ticksSinceDamageTaken(player) < Long.MAX_VALUE);

        EntityDamageListener.forgetPlayer(player.getUUID());

        assertEquals(Long.MAX_VALUE, EntityDamageListener.ticksSinceDamageTaken(player));
    }
}
