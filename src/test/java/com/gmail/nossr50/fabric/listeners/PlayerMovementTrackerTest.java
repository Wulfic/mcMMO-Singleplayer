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
import com.gmail.nossr50.skills.agility.AgilityManager;
import com.gmail.nossr50.skills.agility.Medium;
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
import net.minecraft.util.PlayerInput;
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

    // --- Stealth: the sneak-travel gate ----------------------------------------------------------

    /** A player mid-sneak on dry ground with the forward key held — every gate satisfied. */
    private static ServerPlayerEntity sneakingPlayer() {
        final ServerPlayerEntity handle = player();
        lenient().when(handle.isSneaking()).thenReturn(true);
        lenient().when(handle.isOnGround()).thenReturn(true);
        lenient().when(handle.getPlayerInput())
                .thenReturn(new PlayerInput(true, false, false, false, false, true, false));
        return handle;
    }

    @Test
    void sneakingForwardOnDryGroundQualifies() {
        assertTrue(PlayerMovementTracker.qualifiesAsSneakTravel(sneakingPlayer()));
    }

    @Test
    void crouchSwimmingDoesNotQualify() {
        // The ruling this closes (2026-07-27): crouch-swimming is ~3 b/s against a 1.295 b/s
        // reference, so it would sit permanently at the speed clamp and make "hold shift in a water
        // current" the optimal farm — reopening the exact leak the Agility balance pass closed.
        final ServerPlayerEntity player = sneakingPlayer();
        lenient().when(player.isTouchingWater()).thenReturn(true);

        assertFalse(PlayerMovementTracker.qualifiesAsSneakTravel(player));
    }

    @Test
    void crouchGlidingDoesNotQualify() {
        final ServerPlayerEntity player = sneakingPlayer();
        lenient().when(player.isGliding()).thenReturn(true);
        lenient().when(player.isOnGround()).thenReturn(false);

        assertFalse(PlayerMovementTracker.qualifiesAsSneakTravel(player));
    }

    @Test
    void beingCarriedWhileCrouchedDoesNotQualify() {
        final ServerPlayerEntity player = sneakingPlayer();
        lenient().when(player.hasVehicle()).thenReturn(true);

        assertFalse(PlayerMovementTracker.qualifiesAsSneakTravel(player));
    }

    @Test
    void airborneSneakingDoesNotQualify() {
        final ServerPlayerEntity player = sneakingPlayer();
        lenient().when(player.isOnGround()).thenReturn(false);

        assertFalse(PlayerMovementTracker.qualifiesAsSneakTravel(player));
    }

    @Test
    void aStuckShiftKeyWithNoDirectionalInputDoesNotQualify() {
        // The whole point of the skill's anti-AFK design: sneak held down, being pushed along by a
        // water current or a piston loop, with nobody at the keyboard. Position moves; no movement
        // key is down. Note sneak() is true here — a held shift must not qualify as travel.
        final ServerPlayerEntity player = sneakingPlayer();
        lenient().when(player.getPlayerInput())
                .thenReturn(new PlayerInput(false, false, false, false, false, true, false));

        assertFalse(PlayerMovementTracker.qualifiesAsSneakTravel(player));
    }

    @Test
    void everyDirectionalKeyCountsAsMovement() {
        // Strafing and walking backwards while crouched are ordinary sneaking, not exploits.
        final boolean[][] directions = {
            {true, false, false, false}, {false, true, false, false},
            {false, false, true, false}, {false, false, false, true},
        };
        for (boolean[] d : directions) {
            final ServerPlayerEntity player = sneakingPlayer();
            lenient().when(player.getPlayerInput())
                    .thenReturn(new PlayerInput(d[0], d[1], d[2], d[3], false, true, false));

            assertTrue(PlayerMovementTracker.qualifiesAsSneakTravel(player));
        }
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
    void sneakTravelIsCreditedEvenThoughItHasNoAgilityMedium() {
        final UUID uuid = UUID.randomUUID();
        final ServerPlayerEntity player = sneakingPlayer();
        lenient().when(player.getUuid()).thenReturn(uuid);
        lenient().when(player.getEntityPos())
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
        lenient().when(player.getEntityPos()).thenReturn(new Vec3d(0, 64, 0));

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
        lenient().when(mmoPlayer.getAgilityManager()).thenReturn(mock(AgilityManager.class));

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
        lenient().when(handle.getEntityPos()).thenReturn(new Vec3d(0, 64, 0));
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            lenient().when(handle.getEquippedStack(slot)).thenReturn(ItemStack.EMPTY);
        }
        lenient().when(handle.getAttributeInstance(EntityAttributes.ARMOR))
                .thenReturn(new EntityAttributeInstance(EntityAttributes.ARMOR, instance -> { }));
        return handle;
    }

    /** As {@link #trackedPlayer} but carrying an Unarmored manager instead of a Stealth one. */
    private static McMMOPlayer trackedUnarmoredPlayer(UUID uuid, UnarmoredManager unarmored) {
        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(uuid);

        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getUnarmoredManager()).thenReturn(unarmored);
        lenient().when(mmoPlayer.getAgilityManager()).thenReturn(mock(AgilityManager.class));

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
            assertEquals(1, player.getAttributeInstance(EntityAttributes.ARMOR).getModifiers().size(),
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
    void ironSkinSurvivesAMissingAgilityManager() {
        final UUID uuid = UUID.randomUUID();
        final ServerPlayerEntity player = unarmoredPlayerWithArmourAttribute(uuid);
        final UnarmoredManager unarmored = mock(UnarmoredManager.class);
        when(unarmored.getSkinArmorPoints(true)).thenReturn(20.0);

        final McMMOPlayer mmoPlayer = trackedUnarmoredPlayer(uuid, unarmored);
        when(mmoPlayer.getAgilityManager()).thenReturn(null);
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
