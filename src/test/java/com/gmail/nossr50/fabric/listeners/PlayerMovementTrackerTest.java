package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.platform.SkillAttributeService;
import com.gmail.nossr50.skills.movement.MovementManager;
import com.gmail.nossr50.skills.movement.Medium;
import com.gmail.nossr50.skills.stealth.StealthManager;
import com.gmail.nossr50.skills.unarmored.UnarmoredManager;
import com.gmail.nossr50.util.player.UserManager;
import java.util.UUID;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
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
        // Both the anti-AFK "input observed" log line and SkillAttributeService's missing-attribute
        // warning name the player, and an unstubbed getName() NPEs inside the tracker rather than in
        // the assertion — so it is stubbed here for every test rather than per-test.
        lenient().when(handle.getName()).thenReturn(Text.literal("TestPlayer"));
        lenient().when(handle.hasVehicle()).thenReturn(false);
        lenient().when(handle.isSneaking()).thenReturn(false);
        lenient().when(handle.isFallFlying()).thenReturn(false);
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
        lenient().when(player.isFallFlying()).thenReturn(true);

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
        lenient().when(player.isFallFlying()).thenReturn(true);
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
                case AIR -> lenient().when(player.isFallFlying()).thenReturn(true);
            }

            assertNull(PlayerMovementTracker.classifyMedium(player),
                    "crouching must pay nothing in " + medium);
        }
    }

    // --- Stealth: the sneak-travel gate ----------------------------------------------------------

    /**
     * A per-tick horizontal delta that is unambiguously a player walking while crouched — vanilla
     * crouch speed is about {@code 0.065} blocks a tick.
     */
    private static final double WALKING = 0.065;

    /**
     * A per-tick delta far below anything a player produces on foot: the jitter-macro case the
     * anti-AFK gate exists to refuse.
     */
    private static final double JITTER = 0.001;

    /** A player mid-sneak on dry ground — every positional gate satisfied. */
    private static ServerPlayerEntity sneakingPlayer() {
        final ServerPlayerEntity handle = player();
        lenient().when(handle.isSneaking()).thenReturn(true);
        lenient().when(handle.isOnGround()).thenReturn(true);
        return handle;
    }

    @Test
    void sneakingForwardOnDryGroundQualifies() {
        assertTrue(PlayerMovementTracker.qualifiesAsSneakTravel(sneakingPlayer(), WALKING));
    }

    @Test
    void crouchSwimmingDoesNotQualify() {
        // The ruling this closes (2026-07-27): crouch-swimming is ~3 b/s against a 1.295 b/s
        // reference, so it would sit permanently at the speed clamp and make "hold shift in a water
        // current" the optimal farm — reopening the exact leak the Agility balance pass closed.
        final ServerPlayerEntity player = sneakingPlayer();
        lenient().when(player.isTouchingWater()).thenReturn(true);

        assertFalse(PlayerMovementTracker.qualifiesAsSneakTravel(player, WALKING));
    }

    @Test
    void crouchGlidingDoesNotQualify() {
        final ServerPlayerEntity player = sneakingPlayer();
        lenient().when(player.isFallFlying()).thenReturn(true);
        lenient().when(player.isOnGround()).thenReturn(false);

        assertFalse(PlayerMovementTracker.qualifiesAsSneakTravel(player, WALKING));
    }

    @Test
    void beingCarriedWhileCrouchedDoesNotQualify() {
        final ServerPlayerEntity player = sneakingPlayer();
        lenient().when(player.hasVehicle()).thenReturn(true);

        assertFalse(PlayerMovementTracker.qualifiesAsSneakTravel(player, WALKING));
    }

    @Test
    void airborneSneakingDoesNotQualify() {
        final ServerPlayerEntity player = sneakingPlayer();
        lenient().when(player.isOnGround()).thenReturn(false);

        assertFalse(PlayerMovementTracker.qualifiesAsSneakTravel(player, WALKING));
    }

    @Test
    void holdingShiftThroughAFallPaysStealthNothing() {
        // GitHub #4 reported Graceful Roll "never procs" and said Stealth XP fired "instead",
        // implying Stealth was claiming the landing and short-circuiting Agility. It is not: the two
        // are independent, and the ground requirement means a player who holds shift for the whole
        // descent earns exactly zero Stealth XP for it. (What #4 actually was: Roll's odds were gated
        // on Agility's three-skill mean instead of on Parkour — see RollProbabilityTest.)
        //
        // Distinct from airborneSneakingDoesNotQualify above, which pins the gate; this pins the
        // claim in the bug report, so it is not silently deleted as a duplicate.
        final ServerPlayerEntity falling = sneakingPlayer();
        lenient().when(falling.isOnGround()).thenReturn(false);

        assertFalse(PlayerMovementTracker.qualifiesAsSneakTravel(falling, WALKING),
                "no Stealth credit while airborne, however long the fall");
        // ...and the landing tick itself is ordinary sneak-travel, worth one tick like any other.
        assertTrue(PlayerMovementTracker.qualifiesAsSneakTravel(sneakingPlayer(), WALKING),
                "touchdown is not special-cased either way");
    }

    @Test
    void aStuckShiftKeyWithNoRealTravelDoesNotQualify() {
        // The whole point of the skill's anti-AFK design: sneak held down with nobody at the
        // keyboard. Every positional gate is satisfied and the player is technically "moving" — a
        // jitter macro nudging them a fraction of a block a tick to keep the travel flag true. That
        // is not travel, and it must not pay.
        //
        // ⚠️ This is the test that has to exist on THIS version. Where the server can see the
        // client's movement keys the gate asks the stricter question directly; here it cannot — the
        // input packet is not sent on foot — so displacement is the signal, and this pins the floor
        // under it. Delete this and the band's Stealth gate is unguarded, not simplified.
        assertFalse(PlayerMovementTracker.qualifiesAsSneakTravel(sneakingPlayer(), JITTER));
    }

    @Test
    void crouchWalkingSpeedQualifies() {
        // The other side of the same threshold: ordinary crouched travel must pay. Strafing and
        // walking backwards cover the same ground per tick as walking forwards, so one delta covers
        // all four directions -- direction is not observable on this version and is not the question.
        assertTrue(PlayerMovementTracker.qualifiesAsSneakTravel(sneakingPlayer(), WALKING));
    }

    @Test
    void theTravelFloorSitsWellUnderCrouchSpeed() {
        // Guards the constant against being tuned up into ordinary play. Genuine travel dips below
        // full crouch speed routinely -- a stair, a corner, the tick a jump lands -- so a third of
        // crouch speed must still qualify. A floor that fails this starves the skill on rough ground,
        // and it would do it silently.
        assertTrue(PlayerMovementTracker.qualifiesAsSneakTravel(sneakingPlayer(), WALKING / 3.0),
                "a third of crouch speed is still travel; the floor is too high");
    }

    // --- Stealth: the dispatch must survive the Agility early-return -----------------------------

    /**
     * The ordering trap, pinned.
     *
     * <p>{@link PlayerMovementTracker#classifyMedium} returns {@code null} for every sneaking player,
     * and {@code tickPlayer} returns early on exactly that — so a Stealth dispatch written below that
     * guard is dead code which compiles, boots clean and passes every test above. This drives the
     * real {@code tickPlayer} twice (the first tick only establishes a position baseline) and asserts
     * the payout actually happened.
     */
    @Test
    void sneakTravelIsCreditedEvenThoughItHasNoTravelMedium() {
        final UUID uuid = UUID.randomUUID();
        final ServerPlayerEntity player = sneakingPlayer();
        lenient().when(player.getUuid()).thenReturn(uuid);
        lenient().when(player.getPos())
                .thenReturn(new Vec3d(0, 64, 0), new Vec3d(0.05, 64, 0));

        final StealthManager stealth = mock(StealthManager.class);
        final McMMOPlayer mmoPlayer = trackedPlayer(uuid, stealth);
        try {
            // Guard the premise: if this ever stops being null the test below proves nothing.
            assertNull(PlayerMovementTracker.classifyMedium(player));

            PlayerMovementTracker.tickPlayer(player); // baseline only — no previous position yet
            PlayerMovementTracker.tickPlayer(player);

            verify(stealth).onSneakTick(anyDouble());
        } finally {
            UserManager.cleanupPlayer(mmoPlayer);
            PlayerMovementTracker.clear();
        }
    }

    /** A standing-still sneaker pays nothing, on the same real code path. */
    @Test
    void sneakingWithoutMovingIsCreditedNothing() {
        final UUID uuid = UUID.randomUUID();
        final ServerPlayerEntity player = sneakingPlayer();
        lenient().when(player.getUuid()).thenReturn(uuid);
        lenient().when(player.getPos()).thenReturn(new Vec3d(0, 64, 0));

        final StealthManager stealth = mock(StealthManager.class);
        final McMMOPlayer mmoPlayer = trackedPlayer(uuid, stealth);
        try {
            PlayerMovementTracker.tickPlayer(player);
            PlayerMovementTracker.tickPlayer(player);

            verify(stealth, never()).onSneakTick(anyDouble());
        } finally {
            UserManager.cleanupPlayer(mmoPlayer);
            PlayerMovementTracker.clear();
        }
    }

    /** Register a mock {@link McMMOPlayer} with {@link UserManager} so {@code tickPlayer} finds it. */
    private static McMMOPlayer trackedPlayer(UUID uuid, StealthManager stealth) {
        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(uuid);

        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getStealthManager()).thenReturn(stealth);
        lenient().when(mmoPlayer.getMovementManager()).thenReturn(mock(MovementManager.class));

        UserManager.track(mmoPlayer);
        return mmoPlayer;
    }

    // --- Unarmored: Iron Skin's managed armour modifier ------------------------------------------

    /**
     * A player whose {@code ARMOR} attribute is real rather than mocked.
     *
     * <p>{@link SkillAttributeService} is only worth testing against the genuine
     * {@link EntityAttributeInstance} — the whole contract it offers (re-applying replaces in place,
     * an amount of zero removes rather than zeroes) lives in vanilla's modifier map, and a mock of
     * that map would just be a restatement of the assertions.
     */
    private static ServerPlayerEntity unarmoredPlayerWithArmourAttribute(UUID uuid) {
        final ServerPlayerEntity handle = player();
        lenient().when(handle.getUuid()).thenReturn(uuid);
        lenient().when(handle.getPos()).thenReturn(new Vec3d(0, 64, 0));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            lenient().when(handle.getEquippedStack(slot)).thenReturn(ItemStack.EMPTY);
        }
        lenient().when(handle.getAttributeInstance(EntityAttributes.GENERIC_ARMOR))
                .thenReturn(new EntityAttributeInstance(EntityAttributes.GENERIC_ARMOR, instance -> { }));
        return handle;
    }

    /** As {@link #trackedPlayer} but carrying an Unarmored manager instead of a Stealth one. */
    private static McMMOPlayer trackedUnarmoredPlayer(UUID uuid, UnarmoredManager unarmored) {
        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(uuid);

        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getUnarmoredManager()).thenReturn(unarmored);
        lenient().when(mmoPlayer.getMovementManager()).thenReturn(mock(MovementManager.class));

        UserManager.track(mmoPlayer);
        return mmoPlayer;
    }

    @Test
    void ironSkinIsAppliedToABarePlayerOnTheRealSweep() {
        final UUID uuid = UUID.randomUUID();
        final ServerPlayerEntity player = unarmoredPlayerWithArmourAttribute(uuid);
        final UnarmoredManager unarmored = mock(UnarmoredManager.class);
        when(unarmored.getSkinArmorPoints(true)).thenReturn(15.0);

        final McMMOPlayer mmoPlayer = trackedUnarmoredPlayer(uuid, unarmored);
        try {
            PlayerMovementTracker.tickPlayer(player);

            assertEquals(15.0, SkillAttributeService.appliedValue(player,
                    SkillAttributeService.Managed.UNARMORED_IRON_SKIN), 1.0E-6);
        } finally {
            UserManager.cleanupPlayer(mmoPlayer);
            PlayerMovementTracker.clear();
        }
    }

    @Test
    void equippingOnePieceStripsTheSkinOnTheNextTick() {
        // D-U3, and the bug this whole per-tick re-derivation exists to make impossible: a modifier
        // that outlives its condition is permanent, stacking free armour. The manager is asked
        // `false` here, and answers 0 — so what is really pinned is that the tracker re-reads live
        // equipment state rather than remembering last tick's answer.
        final UUID uuid = UUID.randomUUID();
        final ServerPlayerEntity player = unarmoredPlayerWithArmourAttribute(uuid);
        final UnarmoredManager unarmored = mock(UnarmoredManager.class);
        when(unarmored.getSkinArmorPoints(true)).thenReturn(20.0);
        when(unarmored.getSkinArmorPoints(false)).thenReturn(0.0);

        final McMMOPlayer mmoPlayer = trackedUnarmoredPlayer(uuid, unarmored);
        try {
            PlayerMovementTracker.tickPlayer(player);
            assertTrue(SkillAttributeService.isApplied(player,
                    SkillAttributeService.Managed.UNARMORED_IRON_SKIN));

            when(player.getEquippedStack(EquipmentSlot.HEAD))
                    .thenReturn(new ItemStack(Items.LEATHER_HELMET));
            PlayerMovementTracker.tickPlayer(player);

            // Removed outright, not left attached at zero — the two are indistinguishable to a
            // player but not to whoever debugs this next.
            assertFalse(SkillAttributeService.isApplied(player,
                    SkillAttributeService.Managed.UNARMORED_IRON_SKIN));
        } finally {
            UserManager.cleanupPlayer(mmoPlayer);
            PlayerMovementTracker.clear();
        }
    }

    @Test
    void crossingATierUpdatesTheModifierRatherThanStackingASecond() {
        final UUID uuid = UUID.randomUUID();
        final ServerPlayerEntity player = unarmoredPlayerWithArmourAttribute(uuid);
        final UnarmoredManager unarmored = mock(UnarmoredManager.class);
        when(unarmored.getSkinArmorPoints(true)).thenReturn(7.0, 7.0, 11.0);

        final McMMOPlayer mmoPlayer = trackedUnarmoredPlayer(uuid, unarmored);
        try {
            PlayerMovementTracker.tickPlayer(player);
            PlayerMovementTracker.tickPlayer(player); // idempotent re-apply, the 20-per-second case
            PlayerMovementTracker.tickPlayer(player); // level-up across the gold breakpoint

            assertEquals(11.0, SkillAttributeService.appliedValue(player,
                    SkillAttributeService.Managed.UNARMORED_IRON_SKIN), 1.0E-6);
            assertEquals(1, player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).getModifiers().size(),
                    "a per-tick caller must never accumulate modifiers");
        } finally {
            UserManager.cleanupPlayer(mmoPlayer);
            PlayerMovementTracker.clear();
        }
    }

    /**
     * The ordering trap, Unarmored's copy of it.
     *
     * <p>{@code tickPlayer} returns early when the <em>Agility</em> manager is missing. Iron Skin has
     * nothing to do with Agility, so a dispatch written below that guard would make a player's armour
     * depend on an unrelated skill having loaded — silently, and only for players in that state.
     */
    @Test
    void ironSkinSurvivesAMissingMovementManager() {
        final UUID uuid = UUID.randomUUID();
        final ServerPlayerEntity player = unarmoredPlayerWithArmourAttribute(uuid);
        final UnarmoredManager unarmored = mock(UnarmoredManager.class);
        when(unarmored.getSkinArmorPoints(true)).thenReturn(20.0);

        final McMMOPlayer mmoPlayer = trackedUnarmoredPlayer(uuid, unarmored);
        when(mmoPlayer.getMovementManager()).thenReturn(null);
        try {
            PlayerMovementTracker.tickPlayer(player);

            assertEquals(20.0, SkillAttributeService.appliedValue(player,
                    SkillAttributeService.Managed.UNARMORED_IRON_SKIN), 1.0E-6);
        } finally {
            UserManager.cleanupPlayer(mmoPlayer);
            PlayerMovementTracker.clear();
        }
    }
}
