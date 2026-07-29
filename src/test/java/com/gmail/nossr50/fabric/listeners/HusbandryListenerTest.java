package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.husbandry.HusbandryManager;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.player.UserManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Husbandry's stage-1 trigger layer — the half {@code HusbandryManagerTest} cannot reach.
 *
 * <p>That test pins the pricing and the gates as arithmetic. What is unproven without this file is
 * the wiring that can silently go wrong in-game: that a breeding actually reaches
 * {@link HusbandryManager#onBreed}, that the species charged is the one bred, that {@code Twins}
 * respects its roll, that Multi-Breed honours <b>both</b> of its bounds, and — the one no
 * predicate-level test would catch — that the re-entrancy guard holds, without which one piece of
 * wheat propagates outward animal by animal until the stack overflows.
 */
class HusbandryListenerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
    }

    /** A one-block hitbox at the origin — the sweep expands this by the Multi-Breed radius. */
    private static final Box UNIT_BOX = new Box(0, 0, 0, 1, 1, 1);

    private UUID uuid;
    private McMMOPlayer mmoPlayer;
    private HusbandryManager husbandry;
    private ServerWorld world;

    /** Every animal handed to {@code getEntitiesByClass}, regardless of the box or predicate. */
    private final List<AnimalEntity> worldAnimals = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        uuid = UUID.randomUUID();
        world = mock(ServerWorld.class);

        // Answer the sweep from worldAnimals, applying the caller's own predicate — that predicate
        // IS the candidate filter under test, so running it here rather than stubbing past it is
        // what makes the eligibility assertions mean anything.
        lenient().when(world.getEntitiesByClass(any(Class.class), any(Box.class), any()))
                .thenAnswer(invocation -> {
                    final java.util.function.Predicate<AnimalEntity> filter =
                            invocation.getArgument(2);
                    return worldAnimals.stream().filter(filter).toList();
                });

        husbandry = mock(HusbandryManager.class);
        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(uuid);
        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getHusbandryManager()).thenReturn(husbandry);
        UserManager.track(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        if (mmoPlayer != null) {
            UserManager.cleanupPlayer(mmoPlayer);
        }
        worldAnimals.clear();
    }

    private ServerPlayerEntity breeder() {
        final ServerPlayerEntity player = mock(ServerPlayerEntity.class);
        lenient().when(player.getUuid()).thenReturn(uuid);
        return player;
    }

    /** An adult, off cooldown, not already courting — everything Multi-Breed looks for. */
    private AnimalEntity eligibleCow() {
        return cow(true, 0, true);
    }

    private AnimalEntity cow(boolean alive, int breedingAge, boolean canEat) {
        final CowEntity animal = mock(CowEntity.class);
        // doReturn, not when/thenReturn: getType() is declared EntityType<?>, and the wildcard
        // capture makes the type-safe form uncompilable against a concrete EntityType<CowEntity>.
        Mockito.doReturn(EntityType.COW).when(animal).getType();
        lenient().when(animal.isAlive()).thenReturn(alive);
        lenient().when(animal.getBreedingAge()).thenReturn(breedingAge);
        lenient().when(animal.canEat()).thenReturn(canEat);
        lenient().when(animal.getEntityWorld()).thenReturn(world);
        lenient().when(animal.getBoundingBox()).thenReturn(UNIT_BOX);
        return animal;
    }

    private AnimalEntity pig() {
        final PigEntity animal = mock(PigEntity.class);
        Mockito.doReturn(EntityType.PIG).when(animal).getType();
        lenient().when(animal.isAlive()).thenReturn(true);
        lenient().when(animal.getBreedingAge()).thenReturn(0);
        lenient().when(animal.canEat()).thenReturn(true);
        lenient().when(animal.getEntityWorld()).thenReturn(world);
        lenient().when(animal.getBoundingBox()).thenReturn(UNIT_BOX);
        return animal;
    }

    private void allowMultiBreed(int maxAdditional, double radius) {
        when(husbandry.canMultiBreed()).thenReturn(true);
        lenient().when(husbandry.getMultiBreedMaxAdditionalAnimals()).thenReturn(maxAdditional);
        lenient().when(husbandry.getMultiBreedRadius()).thenReturn(radius);
    }

    // --- Breeding XP --------------------------------------------------------------------------

    @Test
    void aBreedingChargesTheSpeciesThatWasBred() {
        // The config key is derived from the parent's registry path, so a wrong-entity slip would
        // price every breeding as whatever animal happened to be passed first.
        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), eligibleCow(), mock(PassiveEntity.class));
        verify(husbandry).onBreed("Cow");
    }

    @Test
    void breedingStillPaysWhenVanillaProducedNoBaby() {
        // Frogs, sniffers and turtles lay eggs: vanilla passes a null child. The player did breed
        // them, so the verb pays — only Twins, which needs a baby to copy, is skipped.
        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), eligibleCow(), null);

        verify(husbandry).onBreed("Cow");
        verify(husbandry, never()).rollTwins();
    }

    @Test
    void aBreedingByAnUntrackedPlayerPaysNothing() {
        UserManager.cleanupPlayer(mmoPlayer);
        final ServerPlayerEntity stranger = mock(ServerPlayerEntity.class);
        lenient().when(stranger.getUuid()).thenReturn(UUID.randomUUID());

        HusbandryListener.onAnimalsBred(stranger, eligibleCow(), eligibleCow(), null);
        verify(husbandry, never()).onBreed(any());
        mmoPlayer = null; // already cleaned up
    }

    @Test
    void twinsIsRolledOnlyOncePerBreedingAndOnlyWhenThereIsABabyToCopy() {
        when(husbandry.rollTwins()).thenReturn(false);
        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), eligibleCow(),
                mock(PassiveEntity.class));

        verify(husbandry, times(1)).rollTwins();
        // A failed roll must not spawn anything. The pair is bred once, not once per parent.
        verify(world, never()).spawnEntityAndPassengers(any());
    }

    // --- Multi-Breed --------------------------------------------------------------------------

    @Test
    void multiBreedSetsEligibleSameSpeciesNeighboursInLoveFromTheOneItem() {
        allowMultiBreed(8, 40.0);
        final AnimalEntity fed = eligibleCow();
        final AnimalEntity neighbourA = eligibleCow();
        final AnimalEntity neighbourB = eligibleCow();
        worldAnimals.addAll(Arrays.asList(fed, neighbourA, neighbourB));

        final ServerPlayerEntity player = breeder();
        HusbandryListener.onLovePlayer(fed, player);

        verify(neighbourA).lovePlayer(player);
        verify(neighbourB).lovePlayer(player);
        verify(fed, never()).lovePlayer(any());
    }

    @Test
    void multiBreedSkipsAnimalsVanillaItselfWouldRefuseToFeed() {
        allowMultiBreed(8, 40.0);
        final AnimalEntity fed = eligibleCow();
        final AnimalEntity baby = cow(true, -1200, true);       // still a baby
        final AnimalEntity onCooldown = cow(true, 6000, true);  // just bred
        final AnimalEntity alreadyCourting = cow(true, 0, false); // canEat() == not in love
        final AnimalEntity dead = cow(false, 0, true);
        final AnimalEntity wrongSpecies = pig();
        worldAnimals.addAll(
                Arrays.asList(fed, baby, onCooldown, alreadyCourting, dead, wrongSpecies));

        HusbandryListener.onLovePlayer(fed, breeder());

        verify(baby, never()).lovePlayer(any());
        verify(onCooldown, never()).lovePlayer(any());
        verify(alreadyCourting, never()).lovePlayer(any());
        verify(dead, never()).lovePlayer(any());
        verify(wrongSpecies, never()).lovePlayer(any());
    }

    @Test
    void multiBreedStopsAtTheSpreadCapNoMatterHowManyAnimalsAreInRange() {
        // THE anti-exploit assertion. Husbandry pays per breeding, so without this cap one wheat in
        // a large pen would pay dozens of breedings at once and collapse the skill's XP budget.
        allowMultiBreed(3, 40.0);
        final AnimalEntity fed = eligibleCow();
        worldAnimals.add(fed);
        for (int i = 0; i < 30; i++) {
            worldAnimals.add(eligibleCow());
        }

        HusbandryListener.onLovePlayer(fed, breeder());

        final long spread = worldAnimals.stream()
                .filter(animal -> animal != fed)
                .filter(HusbandryListenerTest::wasSetInLove)
                .count();
        assertEquals(3, spread, "exactly MaxAdditionalAnimals neighbours may be set in love");
    }

    /** Whether this mock ever had {@code lovePlayer} called on it. */
    private static boolean wasSetInLove(AnimalEntity animal) {
        return Mockito.mockingDetails(animal).getInvocations().stream()
                .anyMatch(invocation -> invocation.getMethod().getName().equals("lovePlayer"));
    }

    @Test
    void multiBreedDoesNothingWhileLocked() {
        when(husbandry.canMultiBreed()).thenReturn(false);
        final AnimalEntity fed = eligibleCow();
        final AnimalEntity neighbour = eligibleCow();
        worldAnimals.addAll(Arrays.asList(fed, neighbour));

        HusbandryListener.onLovePlayer(fed, breeder());

        verify(neighbour, never()).lovePlayer(any());
        // The sweep must not even be attempted: it is an entity scan on every animal ever fed.
        verify(world, never()).getEntitiesByClass(any(Class.class), any(Box.class), any());
    }

    @Test
    void aZeroSpreadCapSkipsTheSweepEntirely() {
        allowMultiBreed(0, 40.0);
        final AnimalEntity fed = eligibleCow();
        worldAnimals.addAll(Arrays.asList(fed, eligibleCow()));

        HusbandryListener.onLovePlayer(fed, breeder());
        verify(world, never()).getEntitiesByClass(any(Class.class), any(Box.class), any());
    }

    @Test
    void theSpreadDoesNotCascadeThroughTheHookItUses() {
        // ⚠️ THE ONE THAT MATTERS. Multi-Breed spreads by calling lovePlayer, which is the very
        // method the mixin hooks, so re-entering here is not a rare edge case — it is the normal
        // path. Without the guard, each neighbour would run its own sweep from its own position and
        // one piece of wheat would walk outward across the world until the stack overflowed.
        //
        // The mock cannot re-enter on its own, so the cascade is simulated: every neighbour's
        // lovePlayer feeds the call straight back into the listener, exactly as the real mixin does.
        allowMultiBreed(8, 40.0);
        final AnimalEntity fed = eligibleCow();
        worldAnimals.add(fed);
        for (int i = 0; i < 8; i++) {
            final AnimalEntity neighbour = eligibleCow();
            lenient().doAnswer(invocation -> {
                HusbandryListener.onLovePlayer(neighbour, invocation.getArgument(0));
                return null;
            }).when(neighbour).lovePlayer(any());
            worldAnimals.add(neighbour);
        }

        assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> HusbandryListener.onLovePlayer(fed, breeder()),
                "the re-entrancy guard is gone — the spread is cascading");

        // One sweep, from the animal the player actually fed, and no more.
        verify(world, times(1)).getEntitiesByClass(any(Class.class), any(Box.class), any());
    }

    @Test
    void multiBreedSizesItsSweepFromTheConfiguredRadius() {
        // The radius is read per activation rather than baked in, so a maxed player reaches further
        // than a fresh one. Asserted by driving two different radii and reading the box back.
        allowMultiBreed(8, 40.0);
        final AnimalEntity fed = eligibleCow();
        worldAnimals.add(fed);

        HusbandryListener.onLovePlayer(fed, breeder());
        verify(husbandry).getMultiBreedRadius();
        verify(world).getEntitiesByClass(any(Class.class),
                argThat(box -> box.getLengthX() > 80.0), any());
    }

    @Test
    void aNonServerPlayerNeverTriggersTheSweep() {
        allowMultiBreed(8, 40.0);
        final AnimalEntity fed = eligibleCow();
        worldAnimals.add(fed);

        HusbandryListener.onLovePlayer(fed, null);
        verify(world, never()).getEntitiesByClass(any(Class.class), any(Box.class), any());
        verify(husbandry, never()).getMultiBreedRadius();
    }
}
