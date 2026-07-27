package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.gmail.nossr50.skills.agility.Medium;
import net.minecraft.server.network.ServerPlayerEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link PlayerMovementTracker#classifyMedium} — the single answer to "which Agility domain is this
 * player in right now."
 *
 * <p>Worth pinning because three separate consumers read it and they must never disagree: movement
 * XP, the Fleet Footed speed modifier, and the Second Wind dispatch. A change here silently retunes
 * all three at once, which is exactly the kind of thing a boot test cannot catch.
 *
 * <p>Runs under the {@code fabric-loader-junit} registry harness because mocking a
 * {@link ServerPlayerEntity} loads the entity class hierarchy.
 */
class PlayerMovementTrackerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        com.gmail.nossr50.util.McTestRegistries.bootstrap();
    }

    /**
     * A player in no qualifying state at all: on foot, dry, walking. Each test turns on only the
     * flags it is about, so a default that flips upstream surfaces here rather than in play.
     */
    private static ServerPlayerEntity player() {
        final ServerPlayerEntity handle = mock(ServerPlayerEntity.class);
        lenient().when(handle.hasVehicle()).thenReturn(false);
        lenient().when(handle.isSneaking()).thenReturn(false);
        lenient().when(handle.isGliding()).thenReturn(false);
        lenient().when(handle.isTouchingWater()).thenReturn(false);
        lenient().when(handle.isSprinting()).thenReturn(false);
        return handle;
    }

    // --- the three qualifying media -------------------------------------------------------------

    @Test
    void sprintingOnLandIsTheLandMedium() {
        final ServerPlayerEntity player = player();
        lenient().when(player.isSprinting()).thenReturn(true);

        assertSame(Medium.LAND, PlayerMovementTracker.classifyMedium(player));
    }

    @Test
    void beingInWaterIsTheWaterMedium() {
        final ServerPlayerEntity player = player();
        lenient().when(player.isTouchingWater()).thenReturn(true);

        assertSame(Medium.WATER, PlayerMovementTracker.classifyMedium(player));
    }

    @Test
    void glidingIsTheAirMedium() {
        final ServerPlayerEntity player = player();
        lenient().when(player.isGliding()).thenReturn(true);

        assertSame(Medium.AIR, PlayerMovementTracker.classifyMedium(player));
    }

    // --- walking is not a medium ----------------------------------------------------------------

    @Test
    void walkingPaysNothingAtAll() {
        // Deliberate, not an oversight: simply existing in the world must never level the skill, so
        // ordinary walking has no medium and therefore no XP, no speed buff and no Second Wind.
        assertNull(PlayerMovementTracker.classifyMedium(player()));
    }

    // --- exactly one medium per tick ------------------------------------------------------------

    @Test
    void glidingIntoWaterPaysOnceAsAir() {
        final ServerPlayerEntity player = player();
        lenient().when(player.isGliding()).thenReturn(true);
        lenient().when(player.isTouchingWater()).thenReturn(true);
        lenient().when(player.isSprinting()).thenReturn(true);

        // All three states are live at once; without a fixed priority this tick would pay three times.
        assertSame(Medium.AIR, PlayerMovementTracker.classifyMedium(player));
    }

    @Test
    void sprintSwimmingPaysOnceAsWater() {
        final ServerPlayerEntity player = player();
        lenient().when(player.isTouchingWater()).thenReturn(true);
        lenient().when(player.isSprinting()).thenReturn(true);

        assertSame(Medium.WATER, PlayerMovementTracker.classifyMedium(player));
    }

    // --- the guards -----------------------------------------------------------------------------

    @Test
    void beingCarriedByAVehicleIsNotTravel() {
        final ServerPlayerEntity player = player();
        lenient().when(player.hasVehicle()).thenReturn(true);
        lenient().when(player.isTouchingWater()).thenReturn(true);

        // A boat on water hits isTouchingWater; the boat is moving, the player is not.
        assertNull(PlayerMovementTracker.classifyMedium(player));
    }

    @Test
    void crouchingPaysNothingInEveryMedium() {
        // Sneaking is Stealth's sensor, and one movement state must not feed two skills' XP. On land
        // this was already true by accident (you cannot sneak and sprint at once) but in water it was
        // not: holding shift to sink is still isTouchingWater, so crouch-swimming used to pay.
        for (Medium medium : Medium.values()) {
            final ServerPlayerEntity player = player();
            lenient().when(player.isSneaking()).thenReturn(true);
            switch (medium) {
                case LAND -> lenient().when(player.isSprinting()).thenReturn(true);
                case WATER -> lenient().when(player.isTouchingWater()).thenReturn(true);
                case AIR -> lenient().when(player.isGliding()).thenReturn(true);
            }

            assertNull(PlayerMovementTracker.classifyMedium(player),
                    "crouching must pay nothing in " + medium);
        }
    }
}
