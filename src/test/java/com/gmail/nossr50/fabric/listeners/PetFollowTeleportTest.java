package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PetFollowTeleport} — GitHub #2, tamed pets following their owner through a long same-world
 * teleport.
 *
 * <p>Runs under the {@code fabric-loader-junit} registry harness because mocking a
 * {@link ServerPlayerEntity} or a {@link WolfEntity} loads the entity class hierarchy.
 *
 * <p><b>What is deliberately not asserted:</b> that vanilla's {@code tryTeleportToOwner()} finds a
 * good spot. That is vanilla's search over real block state and re-testing it here would test
 * Mockito. What is asserted is that it is <em>asked first</em>, that the fallback fires only when it
 * came up empty, and that the fallback is refused over an airborne owner.
 */
class PetFollowTeleportTest {

    @BeforeAll
    static void bootstrapRegistries() {
        com.gmail.nossr50.util.McTestRegistries.bootstrap();
    }

    @AfterEach
    void resetGlobals() {
        McMMOMod.setGeneralConfig(null);
        PlayerMovementTracker.clear();
    }

    // --- "was that a teleport?" -----------------------------------------------------------------

    @Test
    void ordinaryMovementIsNotATeleport() {
        assertFalse(PetFollowTeleport.isTeleport(new Vec3(0, 64, 0), new Vec3(0.4, 64, 0)));
    }

    @Test
    void aLongHorizontalJumpIsATeleport() {
        assertTrue(PetFollowTeleport.isTeleport(new Vec3(0, 64, 0), new Vec3(5000, 64, 0)));
    }

    @Test
    void aPurelyVerticalJumpIsATeleport() {
        // The movement tracker measures horizontal distance only, because it is billing horizontal
        // travel. Reusing that measurement here would make `/tp ~ ~200 ~` invisible and leave the pets
        // behind, so the check is three-dimensional even though the threshold is shared.
        assertTrue(PetFollowTeleport.isTeleport(new Vec3(0, 64, 0), new Vec3(0, 264, 0)));
    }

    @Test
    void theThresholdIsExclusiveAtExactlyTheLimit() {
        final double limit = PlayerMovementTracker.TELEPORT_DELTA;
        assertFalse(PetFollowTeleport.isTeleport(new Vec3(0, 64, 0), new Vec3(limit, 64, 0)));
        assertTrue(PetFollowTeleport.isTeleport(new Vec3(0, 64, 0), new Vec3(limit + 0.1, 64, 0)));
    }

    // --- who counts as a follower ----------------------------------------------------------------

    @Test
    void anOwnedTamedStandingPetFollows() {
        final ServerPlayer owner = player(UUID.randomUUID());
        assertTrue(PetFollowTeleport.isFollower(pet(owner), owner));
    }

    @Test
    void aSittingPetIsLeftWhereItWasTold() {
        // The single most important refusal in the feature. "Sit" is an explicit order to stay put,
        // and a pet posted as a guard must not be dragged across the world by its owner's ender pearl.
        // cannotFollowOwner() is vanilla's own predicate and also covers leashed and ridden pets, so
        // this pins that we ask it rather than re-listing those conditions ourselves.
        final ServerPlayer owner = player(UUID.randomUUID());
        final Wolf wolf = pet(owner);
        when(wolf.unableToMoveToOwner()).thenReturn(true);

        assertFalse(PetFollowTeleport.isFollower(wolf, owner));
    }

    @Test
    void someoneElsesPetDoesNotFollow() {
        final ServerPlayer owner = player(UUID.randomUUID());
        final Wolf wolf = pet(owner);
        when(wolf.isOwnedBy(owner)).thenReturn(false);

        assertFalse(PetFollowTeleport.isFollower(wolf, owner));
    }

    @Test
    void anUntamedWolfDoesNotFollow() {
        final ServerPlayer owner = player(UUID.randomUUID());
        final Wolf wolf = pet(owner);
        when(wolf.isTame()).thenReturn(false);

        assertFalse(PetFollowTeleport.isFollower(wolf, owner));
    }

    @Test
    void aDeadPetDoesNotFollow() {
        final ServerPlayer owner = player(UUID.randomUUID());
        final Wolf wolf = pet(owner);
        when(wolf.isAlive()).thenReturn(false);

        assertFalse(PetFollowTeleport.isFollower(wolf, owner));
    }

    // --- vanilla's placement first, then the bounded fallback ------------------------------------

    @Test
    void vanillaPlacementIsTriedFirstAndIsEnoughOnItsOwn() {
        final ServerPlayer owner = player(UUID.randomUUID());
        final Wolf wolf = pet(owner);
        // Vanilla found a spot: after its attempt it no longer wants to teleport.
        when(wolf.shouldTryTeleportToOwner()).thenReturn(false);
        worldContaining(owner, wolf);

        assertEquals(1, PetFollowTeleport.bringPetsFrom(owner, new Vec3(0, 64, 0), 32.0));

        verify(wolf).tryToTeleportToOwner();
        verify(wolf, never()).teleportTo(any(), anyDouble(), anyDouble(), anyDouble(), anySet(),
                anyFloat(), anyFloat(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void aStrandedPetIsPlacedOnAnOwnerStandingOnTheGround() {
        // tryTeleportNear tries ten spots within ±3 blocks and takes only a WALKABLE node with room
        // for the hitbox. On a ledge or in an alcove it can find none — and nothing ever retries,
        // which is the very bug this feature exists to fix. So the fallback is load-bearing.
        final ServerPlayer owner = player(UUID.randomUUID());
        final Wolf wolf = pet(owner);
        when(wolf.shouldTryTeleportToOwner()).thenReturn(true);
        when(wolf.teleportTo(any(), anyDouble(), anyDouble(), anyDouble(), anySet(), anyFloat(),
                anyFloat(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(true);
        worldContaining(owner, wolf);

        assertEquals(1, PetFollowTeleport.bringPetsFrom(owner, new Vec3(0, 64, 0), 32.0));

        verify(wolf).teleportTo(any(), anyDouble(), anyDouble(), anyDouble(), anySet(), anyFloat(),
                anyFloat(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void aStrandedPetIsNotDroppedOnAnAirborneOwner() {
        // ⚠️ The failure this refusal exists to prevent is strictly worse than the bug being fixed:
        // dropping a wolf out of an elytra flight kills it, whereas leaving it behind is exactly what
        // vanilla already does. Vanilla's own attempt still happens — only the fallback is refused.
        final ServerPlayer owner = player(UUID.randomUUID());
        lenient().when(owner.onGround()).thenReturn(false);
        lenient().when(owner.isInWater()).thenReturn(false);
        final Wolf wolf = pet(owner);
        when(wolf.shouldTryTeleportToOwner()).thenReturn(true);
        worldContaining(owner, wolf);

        assertEquals(0, PetFollowTeleport.bringPetsFrom(owner, new Vec3(0, 64, 0), 32.0));

        verify(wolf).tryToTeleportToOwner();
        verify(wolf, never()).teleportTo(any(), anyDouble(), anyDouble(), anyDouble(), anySet(),
                anyFloat(), anyFloat(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    // --- the search box is drawn where the player LEFT, not where they arrived --------------------

    @Test
    void theSearchBoxIsCentredOnTheDeparturePointAndSizedByTheRadius() {
        // The whole point of the feature: by the time we run, the player is somewhere else entirely.
        // A box drawn around their current position would find nothing at all — the feature would
        // compile, boot clean and never move a single pet.
        final ServerPlayer owner = player(UUID.randomUUID());
        final ServerLevel world = worldContaining(owner);
        final Vec3 departure = new Vec3(100, 64, -200);

        PetFollowTeleport.bringPetsFrom(owner, departure, 16.0);

        final org.mockito.ArgumentCaptor<AABB> box = org.mockito.ArgumentCaptor.forClass(AABB.class);
        verify(world).getEntitiesOfClass(any(), box.capture(), any());
        assertEquals(departure.x, box.getValue().getCenter().x, 1.0E-6);
        assertEquals(departure.z, box.getValue().getCenter().z, 1.0E-6);
        assertEquals(32.0, box.getValue().getXsize(), 1.0E-6, "a 16-block radius is a 32-wide box");
    }

    // --- the real tick path ----------------------------------------------------------------------

    /**
     * The ordering trap, this feature's copy of it — and the one assertion that proves the whole thing
     * is wired to something.
     *
     * <p>Drives the genuine {@link PlayerMovementTracker#tickPlayer} with <b>no {@code McMMOPlayer}
     * tracked at all</b>, which is the state a player is in mid-join and after a failed profile load.
     * The dispatch sits above that early return on purpose; written below it this test goes red while
     * every unit test above still passes.
     */
    @Test
    void petsFollowOnTheRealSweepWithNoProfileLoaded() {
        final UUID uuid = UUID.randomUUID();
        final ServerPlayer owner = player(uuid);
        when(owner.position()).thenReturn(new Vec3(0, 64, 0), new Vec3(5000, 64, 0));
        final Wolf wolf = pet(owner);
        when(wolf.shouldTryTeleportToOwner()).thenReturn(false);
        worldContaining(owner, wolf);

        PlayerMovementTracker.tickPlayer(owner); // baseline only — no previous position yet
        PlayerMovementTracker.tickPlayer(owner); // the jump

        verify(wolf).tryToTeleportToOwner();
    }

    @Test
    void walkingAroundNeverTouchesThePets() {
        final UUID uuid = UUID.randomUUID();
        final ServerPlayer owner = player(uuid);
        when(owner.position()).thenReturn(new Vec3(0, 64, 0), new Vec3(0.3, 64, 0));
        final Wolf wolf = pet(owner);
        final ServerLevel world = worldContaining(owner, wolf);

        PlayerMovementTracker.tickPlayer(owner);
        PlayerMovementTracker.tickPlayer(owner);

        // Not merely "the pet was not moved" — the entity sweep must not run at all, because this
        // path is 20 ticks a second forever.
        verify(world, never()).getEntitiesOfClass(any(), any(), any());
    }

    @Test
    void aCrossWorldMoveIsLeftAlone() {
        // Explicitly out of scope per the issue, and it is not merely unhandled: the coordinates
        // either side of a nether portal are both real and 8× apart, so a box drawn at the old
        // position in the new world is a box somewhere plausible and wrong.
        final UUID uuid = UUID.randomUUID();
        final ServerPlayer owner = player(uuid);
        when(owner.position()).thenReturn(new Vec3(0, 64, 0), new Vec3(5000, 64, 0));
        final Wolf wolf = pet(owner);
        final ServerLevel overworld = worldContaining(owner, wolf);
        when(overworld.dimension()).thenReturn(Level.OVERWORLD, Level.NETHER);

        PlayerMovementTracker.tickPlayer(owner);
        PlayerMovementTracker.tickPlayer(owner);

        verify(wolf, never()).tryToTeleportToOwner();
    }

    @Test
    void theConfigSwitchTurnsTheWholeThingOff() {
        final GeneralConfig config = mock(GeneralConfig.class);
        when(config.arePetsFollowingTeleports()).thenReturn(false);
        McMMOMod.setGeneralConfig(config);

        final UUID uuid = UUID.randomUUID();
        final ServerPlayer owner = player(uuid);
        when(owner.position()).thenReturn(new Vec3(0, 64, 0), new Vec3(5000, 64, 0));
        final Wolf wolf = pet(owner);
        worldContaining(owner, wolf);

        PlayerMovementTracker.tickPlayer(owner);
        PlayerMovementTracker.tickPlayer(owner);

        verify(wolf, never()).tryToTeleportToOwner();
    }

    // --- the radius default, written down in four places ------------------------------------------

    @Test
    void everyPlaceThePetRadiusDefaultIsWrittenDownAgrees(@TempDir java.nio.file.Path dataFolder) {
        // ⚠️ GitHub #12 MOVED THIS NUMBER, AND IT IS SPELLED OUT FOUR TIMES: the bundled config.yml,
        // GeneralConfig's getDouble fallback, DEFAULT_RADIUS here (used when no config is loaded at
        // all) and the ConfigRetunes newDefault that carries it onto an existing file. Nothing makes
        // them agree and every disagreement is SILENT — the retune one worst of all, because it would
        // strand returning players on a value the code calls the default while every other test
        // passed.
        //
        // 🔑 This exact drift already happened once: Stealth's ModMenu "reset to default" offered
        // 30.0 long after the YAML had been halved to 15.0, and nothing noticed because each side was
        // self-consistent. Agreement between sources needs its own assertion.
        final double shipped = new GeneralConfig(dataFolder).getPetFollowTeleportRadius();

        assertEquals(128.0D, shipped, 1.0E-9, "the value the bundled config.yml actually ships");
        assertEquals(shipped, PetFollowTeleport.DEFAULT_RADIUS, 1.0E-9,
                "the no-config fallback must be the shipped value, not a stale copy of it");

        final List<com.gmail.nossr50.config.ConfigRetunes.Retune> retunes =
                com.gmail.nossr50.config.ConfigRetunes.forFile("config.yml").stream()
                        .filter(r -> r.path().equals("Skills.Taming.Pets_Follow_Teleport_Radius"))
                        .toList();
        assertEquals(1, retunes.size(), "exactly one retune moves this key");
        assertEquals(shipped, ((Number) retunes.get(0).newDefault()).doubleValue(), 1.0E-9,
                "the retune must carry existing configs to the value the mod now ships");
        assertEquals(32.0D, ((Number) retunes.get(0).oldDefault()).doubleValue(), 1.0E-9,
                "…from the value it used to ship, or files still holding 32 are never migrated");
    }

    @Test
    void theRadiusActuallySizesTheSearchBox() {
        // The reference point for the constants above: they would be four agreeing numbers that
        // nothing reads if the sweep ignored the configured radius. 96 is deliberately none of the
        // values named anywhere else.
        final ServerPlayer owner = player(UUID.randomUUID());
        final ServerLevel world = worldContaining(owner);

        PetFollowTeleport.bringPetsFrom(owner, new Vec3(0, 64, 0), 96.0);

        final org.mockito.ArgumentCaptor<AABB> box = org.mockito.ArgumentCaptor.forClass(AABB.class);
        verify(world).getEntitiesOfClass(any(), box.capture(), any());
        assertEquals(192.0, box.getValue().getXsize(), 1.0E-6,
                "a 96-block radius must be a 192-wide box — the radius is not ignored or halved");
    }

    // --- fixtures --------------------------------------------------------------------------------

    /** A player standing on the ground in the overworld. */
    private static ServerPlayer player(UUID uuid) {
        final ServerPlayer handle = mock(ServerPlayer.class);
        lenient().when(handle.getUUID()).thenReturn(uuid);
        lenient().when(handle.getName()).thenReturn(Component.literal("TestPlayer"));
        lenient().when(handle.position()).thenReturn(new Vec3(0, 64, 0));
        lenient().when(handle.onGround()).thenReturn(true);
        lenient().when(handle.isInWater()).thenReturn(false);
        return handle;
    }

    /** A tamed, alive, standing wolf owned by {@code owner}. */
    private static Wolf pet(ServerPlayer owner) {
        final Wolf wolf = mock(Wolf.class);
        lenient().when(wolf.isAlive()).thenReturn(true);
        lenient().when(wolf.isTame()).thenReturn(true);
        lenient().when(wolf.isOwnedBy(owner)).thenReturn(true);
        lenient().when(wolf.unableToMoveToOwner()).thenReturn(false);
        lenient().when(wolf.shouldTryTeleportToOwner()).thenReturn(false);
        // doReturn, not when/thenReturn: getType() is declared EntityType<?>, whose capture no
        // concrete EntityType can be assigned to through the generic thenReturn signature.
        // Stubbed for the "left behind" log lines, which name the pet.
        lenient().doReturn(EntityType.WOLF).when(wolf).getType();
        return wolf;
    }

    /**
     * Put {@code owner} in an overworld whose entity sweep returns whichever of {@code pets} pass the
     * predicate the production code hands it — so the eligibility filter is exercised for real rather
     * than assumed.
     */
    @SuppressWarnings("unchecked")
    private static ServerLevel worldContaining(ServerPlayer owner, Wolf... pets) {
        final ServerLevel world = mock(ServerLevel.class);
        final ResourceKey<Level> overworld = Level.OVERWORLD;
        lenient().when(world.dimension()).thenReturn(overworld);
        lenient().when(world.getEntitiesOfClass(any(), any(), any())).thenAnswer(invocation -> {
            final Predicate<Object> filter = invocation.getArgument(2, Predicate.class);
            return List.of(pets).stream().filter(filter).toList();
        });
        lenient().when(owner.level()).thenReturn(world);
        return world;
    }
}
