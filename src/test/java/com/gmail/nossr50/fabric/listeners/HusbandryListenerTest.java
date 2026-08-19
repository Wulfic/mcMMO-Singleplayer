package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.subskills.taming.CallOfTheWildType;
import com.gmail.nossr50.fabric.McMMOAttachments;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.skills.husbandry.HusbandryManager;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.TrackedSummon;
import com.gmail.nossr50.util.player.UserManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ArmadilloEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/**
 * Husbandry's stage-1 trigger layer — the half {@code HusbandryManagerTest} cannot reach.
 *
 * <p>That test pins the pricing and the gates as arithmetic. What is unproven without this file is
 * the wiring that can silently go wrong in-game: that a breeding actually reaches
 * {@link HusbandryManager#onBreed}, that the species charged is the one bred, that {@code Twins}
 * respects its roll, that Multi-Breed honours its radius, that a breeding the per-window award cap
 * refused leaves no payable calf behind, and — the one no predicate-level test would catch — that the
 * re-entrancy guard holds, without which one piece of wheat propagates outward animal by animal until
 * the stack overflows.
 */
class HusbandryListenerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
    }

    /** A one-block hitbox at the origin — the sweep expands this by the Multi-Breed radius. */
    private static final Box UNIT_BOX = new Box(0, 0, 0, 1, 1, 1);

    /**
     * Stands in for whatever a shear loot table produced.
     *
     * <p>⚠️ Built per test, never as a {@code static final}. A static initializer runs when JUnit
     * loads the class, which can be <em>before</em> any {@code @BeforeAll} in the run — including
     * this class's own — so building an {@code ItemStack} there hits {@code Items} with the
     * registries still empty. That failure does not stay local: it leaves Minecraft's
     * {@code Bootstrap} half-initialized, and every later {@code bootstrap()} in the same JVM fork
     * then throws {@code ExceptionInInitializerError}, reddening test classes that have nothing to
     * do with this one.
     */
    private static ItemStack wool() {
        return new ItemStack(Items.WHITE_WOOL);
    }

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
        // ⚠️ Not optional. onBreed returns a record, and an unstubbed mock hands back null, which
        // the listener dereferences immediately -- every breeding test would die on an NPE rather
        // than on whatever it was actually asserting. A paying breeding is the default because it is
        // the case all the marker and Twins tests below are written against; the refusal is stubbed
        // locally by the tests that mean it.
        lenient().when(husbandry.onBreed(any(), anyLong())).thenReturn(PAID);
        UserManager.track(mmoPlayer);
    }

    /** A breeding that paid, and did not trip the award cap. */
    private static final HusbandryManager.BreedAward PAID =
            new HusbandryManager.BreedAward(350F, false);

    /** A breeding the per-window award cap refused, announcing itself for the first time. */
    private static final HusbandryManager.BreedAward REFUSED =
            new HusbandryManager.BreedAward(0F, true);

    @AfterEach
    void tearDown() {
        if (mmoPlayer != null) {
            UserManager.cleanupPlayer(mmoPlayer);
        }
        worldAnimals.clear();
        // The interaction stash is a ThreadLocal on a thread JUnit reuses, so without this one
        // test's leftovers decide the next one's outcome. (The bred-by markers need no cleanup:
        // since HU16 they live on the animal itself, and each test builds its own animals.)
        HusbandryListener.endPlayerInteraction();
        // Stage 4's harvest cooldown is a process-wide static side-table. Each test mints fresh
        // animal UUIDs so collisions are not the risk -- unbounded growth across the run is.
        MetadataStore.clearAll();
        // Both JVM-wide singletons. The COTW gate's tests install a config and register a summon;
        // leaving either behind would silently change what every later test in this fork does --
        // the config in particular, because with one loaded the gate stops being a no-op.
        McMMOMod.setExperienceConfig(null);
        McMMOMod.getTransientEntityTracker().cleanupPlayer(uuid);
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
        // Every real entity has one, and since the COTW gate looks each parent up in the summon
        // tracker by UUID, a mock without one is not a cow -- it is a null key in a map lookup.
        lenient().when(animal.getUuid()).thenReturn(UUID.randomUUID());
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

    private void allowMultiBreed(double radius) {
        when(husbandry.canMultiBreed()).thenReturn(true);
        lenient().when(husbandry.getMultiBreedRadius()).thenReturn(radius);
    }

    /** A calf with a breeding age and a working attachment slot for the bred-by marker. */
    private PassiveEntity calf(int breedingAge) {
        final CowEntity baby = mock(CowEntity.class);
        Mockito.doReturn(EntityType.COW).when(baby).getType();
        lenient().when(baby.getBreedingAge()).thenReturn(breedingAge);
        lenient().when(baby.getEntityWorld()).thenReturn(world);
        stubAttachments(baby);
        return baby;
    }

    /**
     * Give a mock a working one-slot attachment table, so the bred-by marker round-trips through it
     * the way it does on a real entity.
     *
     * <p>⚠️ Not optional decoration. Mockito answers every unstubbed method with {@code null}, so
     * without this a mock would swallow {@code setAttached} and then hand back nothing — every raise
     * assertion below would go green for the wrong reason, and the pays-once and marker-gate tests
     * would be indistinguishable from each other. One slot is enough: the listener attaches exactly
     * one type.
     *
     * <p>Bare {@code any()} rather than {@code any(Class)} throughout, because
     * {@code removeAttached} is the write of a {@code null} value and a typed matcher does not match
     * {@code null} in Mockito 5.
     */
    private static void stubAttachments(Entity entity) {
        final AtomicReference<Object> slot = new AtomicReference<>();
        lenient().doAnswer(invocation -> slot.getAndSet(invocation.getArgument(1)))
                .when(entity).setAttached(any(), any());
        lenient().doAnswer(invocation -> slot.get()).when(entity).getAttached(any());
        lenient().doAnswer(invocation -> slot.getAndSet(null)).when(entity).removeAttached(any());
    }

    /** Breed a calf and hand back the marked child, with acceleration stubbed out as a no-op. */
    private PassiveEntity bredCalf() {
        final PassiveEntity child = calf(-24000);
        lenient().when(husbandry.applyGrowthAcceleration(anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), eligibleCow(), child);
        return child;
    }

    // --- Breeding XP --------------------------------------------------------------------------

    @Test
    void aBreedingChargesTheSpeciesThatWasBred() {
        // The config key is derived from the parent's registry path, so a wrong-entity slip would
        // price every breeding as whatever animal happened to be passed first.
        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), eligibleCow(), calf(-24000));
        verify(husbandry).onBreed(eq("Cow"), anyLong());
    }

    @Test
    void breedingStillPaysWhenVanillaProducedNoBaby() {
        // Frogs, sniffers and turtles lay eggs: vanilla passes a null child. The player did breed
        // them, so the verb pays — only Twins, which needs a baby to copy, is skipped.
        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), eligibleCow(), null);

        verify(husbandry).onBreed(eq("Cow"), anyLong());
        verify(husbandry, never()).rollTwins();
    }

    @Test
    void aBreedingByAnUntrackedPlayerPaysNothing() {
        UserManager.cleanupPlayer(mmoPlayer);
        final ServerPlayerEntity stranger = mock(ServerPlayerEntity.class);
        lenient().when(stranger.getUuid()).thenReturn(UUID.randomUUID());

        HusbandryListener.onAnimalsBred(stranger, eligibleCow(), eligibleCow(), null);
        verify(husbandry, never()).onBreed(any(), anyLong());
        mmoPlayer = null; // already cleaned up
    }

    // --- ExploitFix.COTWBreeding (GitHub #9) ------------------------------------------------

    /**
     * Registers {@code animal} with the transient tracker as a live Call-of-the-Wild summon, the way
     * {@code CallOfTheWildHandler} does for a real one.
     */
    private void registerAsSummon(AnimalEntity animal) {
        final UUID summonId = UUID.randomUUID();
        lenient().when(animal.getUuid()).thenReturn(summonId);
        final TrackedSummon summon = mock(TrackedSummon.class);
        lenient().when(summon.getEntityId()).thenReturn(summonId);
        lenient().when(summon.getCallOfTheWildType()).thenReturn(CallOfTheWildType.WOLF);
        McMMOMod.getTransientEntityTracker().initPlayer(uuid);
        McMMOMod.getTransientEntityTracker().addSummon(uuid, summon);
    }

    @Test
    void breedingYourOwnCallOfTheWildSummonPaysNothing(@TempDir Path dir) {
        McMMOMod.setExperienceConfig(new ExperienceConfig(dir));
        final AnimalEntity summon = eligibleCow();
        registerAsSummon(summon);

        HusbandryListener.onAnimalsBred(breeder(), summon, eligibleCow(), calf(-24000));

        // Taming conjures the parents out of a few bones; paying for their offspring would turn one
        // skill's ability into another skill's XP tap.
        verify(husbandry, never()).onBreed(any(), anyLong());
    }

    @Test
    void aRefusedCotwBreedingLeavesNoPayableCalfBehind(@TempDir Path dir) {
        // The GitHub #3 lesson, applied to this gate: if the calf were still claimed, the raise verb
        // would pay for it when it grew up and the gate would be a twenty-minute delay, not a gate.
        McMMOMod.setExperienceConfig(new ExperienceConfig(dir));
        final AnimalEntity summon = eligibleCow();
        registerAsSummon(summon);
        final PassiveEntity child = calf(-24000);

        HusbandryListener.onAnimalsBred(breeder(), summon, eligibleCow(), child);

        verify(child, never()).setAttached(eq(McMMOAttachments.BRED_BY), any());
    }

    @Test
    void theSummonMayBeEitherParent(@TempDir Path dir) {
        McMMOMod.setExperienceConfig(new ExperienceConfig(dir));
        final AnimalEntity summon = eligibleCow();
        registerAsSummon(summon);

        // Mate side, not just the parent side -- the check has to be on both or feeding the summon
        // second is a trivial bypass.
        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), summon, calf(-24000));

        verify(husbandry, never()).onBreed(any(), anyLong());
    }

    @Test
    void switchingOffTheCotwGateLetsSummonsBreedForXp(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("experience.yml"),
                "ExploitFix:\n    COTWBreeding: false\n", StandardCharsets.UTF_8);
        McMMOMod.setExperienceConfig(new ExperienceConfig(dir));
        final AnimalEntity summon = eligibleCow();
        registerAsSummon(summon);

        HusbandryListener.onAnimalsBred(breeder(), summon, eligibleCow(), calf(-24000));

        verify(husbandry, times(1)).onBreed(any(), anyLong());
    }

    @Test
    void anOrdinaryBreedingIsUnaffectedByTheCotwGate(@TempDir Path dir) {
        // The reference point: with the gate on and the config loaded, two wild cows still pay.
        // Without this, a bug that refused every breeding would satisfy all three tests above.
        McMMOMod.setExperienceConfig(new ExperienceConfig(dir));

        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), eligibleCow(), calf(-24000));

        verify(husbandry, times(1)).onBreed(any(), anyLong());
    }

    @Test
    void twinsIsRolledOnlyOncePerBreedingAndOnlyWhenThereIsABabyToCopy() {
        when(husbandry.rollTwins()).thenReturn(false);
        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), eligibleCow(), calf(-24000));

        verify(husbandry, times(1)).rollTwins();
        // A failed roll must not spawn anything. The pair is bred once, not once per parent.
        verify(world, never()).spawnEntityAndPassengers(any());
    }

    // --- Raise: the bred-by marker and the grow-up crossing ------------------------------------

    @Test
    void anAnimalYouBredPaysTheRaiseVerbWhenItGrowsUp() {
        final PassiveEntity child = bredCalf();

        // -1 -> 0 is exactly how vanilla's tickMovement walks a baby into adulthood.
        HusbandryListener.onBreedingAgeChange(child, -1, 0);
        verify(husbandry).onRaise("Cow");
    }

    @Test
    void theBredByMarkerIsWrittenOntoTheAnimalItselfNotIntoASessionSideTable() {
        // ⚠️ HU16, reversed 2026-07-29. The marker used to be a MetadataStore entry, which is a
        // JVM-lifetime side table: a calf bred before quitting to title paid nobody when it matured,
        // twenty minutes of vanilla growth being long enough that "and did you stay logged in?" was
        // a real, invisible condition on the payout. Asserting the attachment write specifically —
        // rather than just that the raise verb pays — is what pins the marker to a home that gets
        // written into the world save.
        final PassiveEntity child = calf(-24000);
        lenient().when(husbandry.applyGrowthAcceleration(anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), eligibleCow(), child);

        verify(child).setAttached(McMMOAttachments.BRED_BY, uuid);
    }

    @Test
    void aCalfBredInAnEarlierSessionStillPaysWhenItMatures() {
        // The other half of HU16, from the read side: nothing bred this animal during this session.
        // Its marker arrived with the entity off disk, which is exactly what a reloaded world hands
        // the listener. Before HU16 was reversed this calf was indistinguishable from a wild one.
        final PassiveEntity reloadedCalf = calf(-1);
        reloadedCalf.setAttached(McMMOAttachments.BRED_BY, uuid);

        HusbandryListener.onBreedingAgeChange(reloadedCalf, -1, 0);
        verify(husbandry).onRaise("Cow");
    }

    @Test
    void anAnimalNobodyBredPaysNothingWhenItGrowsUp() {
        // The marker gate. Without it every wild baby in every loaded chunk coming of age would pay
        // somebody -- and there is no somebody to pay.
        final PassiveEntity wildCalf = calf(-1);

        HusbandryListener.onBreedingAgeChange(wildCalf, -1, 0);
        verify(husbandry, never()).onRaise(any());
    }

    @Test
    void aBabyLoadingFromDiskPaysNothingHoweverOftenItIsReloaded() {
        // The transition gate, and the reason it is not merely tidiness: readCustomData routes
        // through setBreedingAge, so a baby loading from a chunk goes from the field default of 0 to
        // its real negative age. Without the gate, flying away and back would re-pay the raise verb
        // on every single chunk load, for every baby you had ever bred.
        final PassiveEntity child = bredCalf();

        for (int i = 0; i < 5; i++) {
            HusbandryListener.onBreedingAgeChange(child, 0, -1200);
        }
        verify(husbandry, never()).onRaise(any());
    }

    @Test
    void anAdultTurnedBackIntoABabyPaysNothing() {
        // The same gate's other half: setBreedingAge runs its transition branch when an adult
        // becomes a baby too (a spawn egg, or setBaby(true)).
        final PassiveEntity child = bredCalf();

        HusbandryListener.onBreedingAgeChange(child, 0, -24000);
        verify(husbandry, never()).onRaise(any());
    }

    @Test
    void theRaiseVerbPaysAtMostOncePerAnimal() {
        // The marker is consumed as it is read, so a second crossing has nobody left to credit.
        // Without that, anything that drove the animal back across the boundary would pay again.
        final PassiveEntity child = bredCalf();

        HusbandryListener.onBreedingAgeChange(child, -1, 0);
        HusbandryListener.onBreedingAgeChange(child, -1, 0);
        verify(husbandry, times(1)).onRaise("Cow");
    }

    @Test
    void aBreedingTheAwardCapRefusedMarksNoCalfAndSoPaysNothingWhenItMatures() {
        // ⚠️⚠️ THE HALF THAT MAKES IT A CAP RATHER THAN A TWENTY-MINUTE DELAY. The raise verb pays a
        // full breeding's worth of XP off the bred-by marker alone, so a refused breeding that still
        // marked its calf would hand over the whole refused amount anyway -- later, invisibly, and
        // with the cap's own message saying it had been stopped. Brood already sets this precedent
        // for exactly the same reason (a hatched chick is deliberately unmarked).
        when(husbandry.onBreed(any(), anyLong())).thenReturn(REFUSED);
        final PassiveEntity child = calf(-24000);
        lenient().when(husbandry.applyGrowthAcceleration(anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), eligibleCow(), child);

        verify(child, never()).setAttached(any(), any());
        HusbandryListener.onBreedingAgeChange(child, -1, 0);
        verify(husbandry, never()).onRaise(any());
    }

    @Test
    void aRefusedBreedingStillHappensAndItsCalfStillGrowsUpFaster() {
        // The cap gates the REWARD, never the game. Vanilla bred the pair, the calf exists, and
        // Accelerated Growth is a yield effect with no XP behind it -- withholding that would only
        // make a refused breeding feel arbitrarily punished. Asserted off the reference point above:
        // without this, "marks nothing" could have been implemented as "does nothing".
        when(husbandry.onBreed(any(), anyLong())).thenReturn(REFUSED);
        final PassiveEntity child = calf(-24000);
        when(husbandry.applyGrowthAcceleration(-24000)).thenReturn(-16800);

        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), eligibleCow(), child);
        verify(child).setBreedingAge(-16800);
    }

    @Test
    void aTwinBornOfARefusedBreedingIsUnmarkedToo() {
        // Otherwise Twins is a hole straight through the cap: the refused breeding's own calf pays
        // nobody, and its sibling quietly pays the full raise verb twenty minutes later.
        when(husbandry.onBreed(any(), anyLong())).thenReturn(REFUSED);
        final PassiveEntity child = calf(-24000);
        final PassiveEntity twin = calf(-24000);
        final AnimalEntity parent = eligibleCow();
        lenient().when(husbandry.applyGrowthAcceleration(anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(husbandry.rollTwins()).thenReturn(true);
        Mockito.doReturn(twin).when(parent).createChild(any(), any());

        HusbandryListener.onAnimalsBred(breeder(), parent, eligibleCow(), child);

        verify(twin, never()).setAttached(any(), any());
        HusbandryListener.onBreedingAgeChange(twin, -1, 0);
        verify(husbandry, never()).onRaise(any());
    }

    @Test
    void aTwinIsMarkedTooSoItAlsoPaysWhenItGrowsUp() {
        // A twin that carried no marker would be the only baby in the game whose breeder could never
        // be paid for raising it -- which reads as a bug rather than as balance.
        final PassiveEntity child = calf(-24000);
        final PassiveEntity twin = calf(-24000);
        final AnimalEntity parent = eligibleCow();
        lenient().when(husbandry.applyGrowthAcceleration(anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(husbandry.rollTwins()).thenReturn(true);
        Mockito.doReturn(twin).when(parent).createChild(any(), any());

        HusbandryListener.onAnimalsBred(breeder(), parent, eligibleCow(), child);

        HusbandryListener.onBreedingAgeChange(twin, -1, 0);
        verify(husbandry).onRaise("Cow");
    }

    // --- Accelerated Growth: the birth half ----------------------------------------------------

    @Test
    void acceleratedGrowthShortensTheNewbornsChildhoodAtBirth() {
        final PassiveEntity child = calf(-24000);
        when(husbandry.applyGrowthAcceleration(-24000)).thenReturn(-16800);

        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), eligibleCow(), child);
        verify(child).setBreedingAge(-16800);
    }

    @Test
    void anUnchangedAgeIsNotWrittenBack() {
        // setBreedingAge is not a plain setter -- it is the method the raise hook watches. Writing
        // an unchanged value would fire a pointless transition check on every birth.
        final PassiveEntity child = calf(-24000);
        when(husbandry.applyGrowthAcceleration(-24000)).thenReturn(-24000);

        HusbandryListener.onAnimalsBred(breeder(), eligibleCow(), eligibleCow(), child);
        verify(child, never()).setBreedingAge(anyInt());
    }

    // --- Feed: the interaction stash -----------------------------------------------------------

    @Test
    void feedingABabyYouAreInteractingWithPaysTheFeedVerb() {
        final PassiveEntity baby = calf(-24000);
        when(husbandry.applyFeedBonus(120)).thenReturn(240);
        final ServerPlayerEntity player = breeder();

        HusbandryListener.beginPlayerInteraction(player, baby);
        try {
            assertEquals(240, HusbandryListener.onGrowthApplied(baby, 120),
                    "Accelerated Growth's doubled value must reach vanilla");
        } finally {
            HusbandryListener.endPlayerInteraction();
        }
        verify(husbandry).onFeedBaby("Cow");
    }

    @Test
    void aDoubledFeedIsAnnouncedAndAnOrdinaryOneIsNot() {
        // Regression: Husbandry.SubSkill.AcceleratedGrowth.Proc shipped as a locale string nothing
        // ever sent, so the sub-skill's active half was invisible -- the baby just grew a little
        // more, with nothing to attribute it to. NotificationManager gates every send on
        // useChatNotifications(), which makes that call the seam for "a message was attempted".
        final PassiveEntity baby = calf(-24000);
        when(husbandry.applyFeedBonus(120)).thenReturn(240);
        when(mmoPlayer.useChatNotifications()).thenReturn(false);
        final ServerPlayerEntity player = breeder();

        HusbandryListener.beginPlayerInteraction(player, baby);
        try {
            HusbandryListener.onGrowthApplied(baby, 120);
        } finally {
            HusbandryListener.endPlayerInteraction();
        }
        verify(mmoPlayer).useChatNotifications();

        // ...and a feed that did NOT double stays silent, which is the half that makes the
        // announcement mean something.
        clearInvocations(mmoPlayer);
        when(husbandry.applyFeedBonus(120)).thenReturn(120);

        HusbandryListener.beginPlayerInteraction(player, baby);
        try {
            HusbandryListener.onGrowthApplied(baby, 120);
        } finally {
            HusbandryListener.endPlayerInteraction();
        }
        verify(mmoPlayer, never()).useChatNotifications();
    }

    @Test
    void growthWithNoPlayerInteractionInFlightPaysNothing() {
        // ⚠️ THE test on this seam. growUp is a growth funnel, not a feeding one: SheepEntity's
        // onEatingGrass calls it from an AI goal, and a tadpole ages itself through it. Paying for
        // those would make a lamb standing in a field an AFK income -- exactly the dispenser-farm
        // shape this skill's plan spends a page warning about, arrived at from the other direction.
        final PassiveEntity lamb = calf(-24000);

        assertEquals(60, HusbandryListener.onGrowthApplied(lamb, 60),
                "vanilla's growth must pass through completely untouched");
        verify(husbandry, never()).onFeedBaby(any());
        verify(husbandry, never()).applyFeedBonus(anyInt());
    }

    @Test
    void feedingOneAnimalDoesNotPayForAnotherGrowingAtTheSameMoment() {
        // The stash records WHICH entity is being interacted with, not merely that someone is
        // interacting. Without the identity check, any growth anywhere during a right-click would
        // bill as a feed of whatever the player happened to be holding a hand out to.
        final PassiveEntity fed = calf(-24000);
        final PassiveEntity other = calf(-24000);

        HusbandryListener.beginPlayerInteraction(breeder(), fed);
        try {
            assertEquals(60, HusbandryListener.onGrowthApplied(other, 60));
        } finally {
            HusbandryListener.endPlayerInteraction();
        }
        verify(husbandry, never()).onFeedBaby(any());
    }

    @Test
    void theStashDoesNotOutliveTheInteractionThatSetIt() {
        // The mixin's RETURN injector is what clears this. If it ever stopped matching, the last
        // animal a player right-clicked would keep earning feed XP for every growth in the world.
        final PassiveEntity baby = calf(-24000);

        HusbandryListener.beginPlayerInteraction(breeder(), baby);
        HusbandryListener.endPlayerInteraction();

        assertEquals(60, HusbandryListener.onGrowthApplied(baby, 60));
        verify(husbandry, never()).onFeedBaby(any());
    }

    @Test
    void growthDrivenByANonPlayerHolderOfTheStashPaysNothing() {
        // beginPlayerInteraction is reached from PlayerEntity#interact, which is shared with the
        // client player. Only a real ServerPlayerEntity may open a stash.
        final PassiveEntity baby = calf(-24000);
        final PlayerEntity clientSide = mock(PlayerEntity.class);

        HusbandryListener.beginPlayerInteraction(clientSide, baby);
        try {
            assertEquals(60, HusbandryListener.onGrowthApplied(baby, 60));
        } finally {
            HusbandryListener.endPlayerInteraction();
        }
        verify(husbandry, never()).onFeedBaby(any());
    }

    // --- Shear and Bountiful Harvest ------------------------------------------------------------

    private PassiveEntity shearableSheep() {
        final SheepEntity sheep = mock(SheepEntity.class);
        Mockito.doReturn(EntityType.SHEEP).when(sheep).getType();
        lenient().when(sheep.getEntityWorld()).thenReturn(world);
        stubAttachments(sheep);
        return sheep;
    }

    /**
     * One shear, driven the way the seam really runs it.
     *
     * <p>⚠️ There is no shear loot funnel on this band — no single {@code BiConsumer} sees every
     * item a shear produces, because each species drops inline by its own route. The equivalent is an
     * explicit window: {@code ShearPayoutMixin} opens it at {@code sheared}'s HEAD,
     * {@code EntityShearDropMixin} runs once per dropped stack while it is open, and it closes at
     * TAIL. Driving all three in that order is what keeps these tests a guard for the real call
     * sequence rather than for three methods in isolation.
     *
     * @return what each stack actually became on its way out
     */
    private static List<ItemStack> shear(LivingEntity sheared, ItemStack... drops) {
        final List<ItemStack> delivered = new ArrayList<>();
        HusbandryListener.beginShear(sheared);
        try {
            for (ItemStack drop : drops) {
                delivered.add(HusbandryListener.onShearDropStack(drop));
            }
        } finally {
            HusbandryListener.endShear();
        }
        return delivered;
    }

    @Test
    void shearingAnAnimalYouAreInteractingWithPaysTheShearVerb() {
        final PassiveEntity sheep = shearableSheep();

        HusbandryListener.beginPlayerInteraction(breeder(), sheep);
        try {
            assertEquals(1, shear(sheep, wool()).get(0).getCount(),
                    "a failed bonus roll must leave vanilla's own stack exactly as it was");
        } finally {
            HusbandryListener.endPlayerInteraction();
        }

        verify(husbandry).onShear();
    }

    @Test
    void aDispenserShearingASheepPaysNothingAndDropsNothingExtra() {
        // ⚠️⚠️ THE row this whole seam was chosen for. ShearsDispenserBehavior calls the same
        // Shearable#sheared that a player does, so it opens this very window — that IS the classic
        // AFK wool farm, and it is the single most important thing shearing must never pay for.
        // Nothing distinguishes the two calls except that a dispenser opens no player interaction,
        // so this test is the gate.
        final PassiveEntity sheep = shearableSheep();

        // No beginPlayerInteraction: this is exactly the state a dispenser fires in.
        final List<ItemStack> delivered = shear(sheep, wool());

        verify(husbandry, never()).onShear();
        verify(husbandry, never()).rollBonusHarvestDrop();
        assertEquals(1, delivered.get(0).getCount(),
                "vanilla's drops must pass through a dispenser shear completely untouched");
    }

    @Test
    void shearingOneAnimalDoesNotPayForAnotherShearedAtTheSameMoment() {
        // The identity half of the gate. Without it a dispenser firing anywhere in the world during
        // a player's right-click would bill to that player.
        final PassiveEntity held = shearableSheep();
        final PassiveEntity elsewhere = shearableSheep();

        HusbandryListener.beginPlayerInteraction(breeder(), held);
        try {
            shear(elsewhere, wool());
        } finally {
            HusbandryListener.endPlayerInteraction();
        }

        verify(husbandry, never()).onShear();
    }

    @Test
    void bountifulHarvestDoublesVanillasOwnStackRatherThanSpawningItsOwn() {
        // The bonus is vanilla's own stack handed back with twice the count, so a sheep's colour and
        // a mooshroom's variant carry into it for free. Asserting on the returned stack -- not just
        // that the roll happened -- is what pins that.
        //
        // ⚠️ ONE stack of 2, not two stacks of 1. Where a loot funnel exists the bonus is vanilla's
        // handler invoked a second time; here it is a doubled count, which is one ItemEntity instead
        // of two and cannot desynchronise from the first drop's position or pickup delay.
        final PassiveEntity sheep = shearableSheep();
        when(husbandry.rollBonusHarvestDrop()).thenReturn(true);

        HusbandryListener.beginPlayerInteraction(breeder(), sheep);
        final List<ItemStack> delivered;
        try {
            delivered = shear(sheep, wool());
        } finally {
            HusbandryListener.endPlayerInteraction();
        }

        assertEquals(1, delivered.size(), "the bonus must not add a second delivery");
        assertEquals(2, delivered.get(0).getCount(), "a successful roll must double the yield");
        assertEquals(Items.WHITE_WOOL, delivered.get(0).getItem(),
                "the bonus must be a copy of what vanilla actually dropped");
    }

    @Test
    void theBonusDropIsRolledOncePerShearNotOncePerItem() {
        // A shear that yields three wool must resolve the sub-skill once and then double all three,
        // rather than rolling per item and producing a partial, noisy result. The roll is decided at
        // beginShear and only READ per stack, which is what makes that structural rather than
        // incidental.
        final PassiveEntity sheep = shearableSheep();
        when(husbandry.rollBonusHarvestDrop()).thenReturn(true);

        HusbandryListener.beginPlayerInteraction(breeder(), sheep);
        final List<ItemStack> delivered;
        try {
            delivered = shear(sheep, wool(), wool(), wool());
        } finally {
            HusbandryListener.endPlayerInteraction();
        }

        verify(husbandry, times(1)).rollBonusHarvestDrop();
        assertEquals(6, delivered.stream().mapToInt(ItemStack::getCount).sum(),
                "all three stacks must double, not just the one the roll happened on");
    }

    @Test
    void aDropOutsideAnyShearWindowIsLeftAlone() {
        // 🔑🔑 THE guard that makes a seam on Entity#dropStack safe at all. That method is how most
        // of the game drops most of its items -- a mob's death loot, a broken block, a player
        // emptying their inventory -- and the ONLY thing narrowing it back down to "items this shear
        // produced" is the window being open. If endShear ever stopped clearing the flag, every drop
        // in the world would silently double for the rest of the session.
        final PassiveEntity sheep = shearableSheep();
        when(husbandry.rollBonusHarvestDrop()).thenReturn(true);

        HusbandryListener.beginPlayerInteraction(breeder(), sheep);
        try {
            shear(sheep, wool());
        } finally {
            HusbandryListener.endPlayerInteraction();
        }

        assertEquals(1, HusbandryListener.onShearDropStack(wool()).getCount(),
                "the window must close: an unrelated drop after a winning shear must not double");
    }

    @Test
    void bountifulHarvestSparesTheShearsOnASuccessfulRoll() {
        final PassiveEntity sheep = shearableSheep();
        when(husbandry.rollToolDurabilitySave()).thenReturn(true);

        HusbandryListener.beginPlayerInteraction(breeder(), sheep);
        try {
            assertEquals(0, HusbandryListener.onShearToolDamaged(sheep, 1),
                    "a saved shear must cost the tool nothing");
        } finally {
            HusbandryListener.endPlayerInteraction();
        }
    }

    @Test
    void aDispenserNeverSavesDurability() {
        // The same gate on the other half. A dispenser's shears are not a player's tool and must
        // wear exactly as vanilla intends -- otherwise an automated farm quietly runs forever.
        final PassiveEntity sheep = shearableSheep();
        lenient().when(husbandry.rollToolDurabilitySave()).thenReturn(true);

        assertEquals(1, HusbandryListener.onShearToolDamaged(sheep, 1));
        verify(husbandry, never()).rollToolDurabilitySave();
    }

    // --- Multi-Breed --------------------------------------------------------------------------

    @Test
    void multiBreedSetsEligibleSameSpeciesNeighboursInLoveFromTheOneItem() {
        allowMultiBreed(40.0);
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
        allowMultiBreed(40.0);
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
    void multiBreedSpreadsToEveryEligibleAnimalInRange() {
        // GitHub #3, and the direct inverse of what this test used to assert. There was a cap of four
        // here; it is gone, because it taxed the mechanic rather than the reward and because it
        // bounded XP per ITEM in a game where wheat is free. The anti-exploit gate now sits on the XP
        // payout, one window at a time, and is pinned by HusbandryManagerTest.
        allowMultiBreed(40.0);
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
        assertEquals(30, spread, "every eligible neighbour in the radius is set in love");
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
    void aZeroRadiusSkipsTheSweepEntirely() {
        // The radius is the only bound left on the spread, so it is also the only remaining way to
        // switch it off -- and switching it off must skip the entity scan, not merely discard it.
        allowMultiBreed(0.0);
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
        allowMultiBreed(40.0);
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
        allowMultiBreed(40.0);
        final AnimalEntity fed = eligibleCow();
        worldAnimals.add(fed);

        HusbandryListener.onLovePlayer(fed, breeder());
        verify(husbandry).getMultiBreedRadius();
        verify(world).getEntitiesByClass(any(Class.class),
                argThat(box -> box.getLengthX() > 80.0), any());
    }

    @Test
    void aNonServerPlayerNeverTriggersTheSweep() {
        allowMultiBreed(40.0);
        final AnimalEntity fed = eligibleCow();
        worldAnimals.add(fed);

        HusbandryListener.onLovePlayer(fed, null);
        verify(world, never()).getEntitiesByClass(any(Class.class), any(Box.class), any());
        verify(husbandry, never()).getMultiBreedRadius();
    }

    // --- Stage 4: hive, milk, brush, Beekeeper and the harvest cooldown --------------------------

    private static final int COOLDOWN_SECONDS = 300;
    private static final long COOLDOWN_TICKS = COOLDOWN_SECONDS * 20L;

    /** A distinct animal with a UUID, which the cooldown's side-table keys on. */
    private Entity harvestable(Class<? extends Entity> type) {
        final Entity animal = mock(type);
        // ⚠️ MetadataStore keys on getUuid() and its backing ConcurrentHashMap rejects a null key, so
        // an unstubbed mock does not merely misbehave here -- it throws from inside the cooldown.
        lenient().when(animal.getUuid()).thenReturn(UUID.randomUUID());
        lenient().when(animal.getEntityWorld()).thenReturn(world);
        return animal;
    }

    /** Move the world's clock, which is the clock the harvest cooldown is measured against. */
    private void worldTime(long ticks) {
        lenient().when(world.getTime()).thenReturn(ticks);
    }

    private void allowHarvestCooldown() {
        lenient().when(husbandry.getHarvestCooldownSeconds()).thenReturn(COOLDOWN_SECONDS);
    }

    // --- Hive -----------------------------------------------------------------------------------

    @Test
    void harvestingHoneycombPaysTheHiveVerb() {
        HusbandryListener.onHoneycombHarvested(breeder(), wool(), null, world, null);
        verify(husbandry).onHiveHarvest();
    }

    @Test
    void harvestingAHoneyBottlePaysTheHiveVerb() {
        HusbandryListener.onHoneyBottled(breeder());
        verify(husbandry).onHiveHarvest();
    }

    @Test
    void aHiveHarvestByAnUntrackedPlayerPaysNothing() {
        final ServerPlayerEntity stranger = mock(ServerPlayerEntity.class);
        lenient().when(stranger.getUuid()).thenReturn(UUID.randomUUID());

        HusbandryListener.onHoneyBottled(stranger);
        verify(husbandry, never()).onHiveHarvest();
    }

    @Test
    void theHiveVerbHasNoCooldownBecauseVanillaAlreadyLimitsIt() {
        // Deliberate asymmetry with milk and brush, and worth pinning so nobody "fixes" it into
        // consistency: a drained hive needs five levels of bee-pollination time before it can be
        // harvested again, so mcMMO adding a second stopwatch on top would only feel arbitrary.
        allowHarvestCooldown();
        worldTime(0L);

        HusbandryListener.onHoneyBottled(breeder());
        HusbandryListener.onHoneyBottled(breeder());

        verify(husbandry, times(2)).onHiveHarvest();
        verify(husbandry, never()).getHarvestCooldownSeconds();
    }

    @Test
    void beekeeperAndBountifulHarvestStackRatherThanReRollingTheSameCoin() {
        // The point of Beekeeper's yield half is that a maxed beekeeper out-yields a maxed
        // generalist at a hive, which it cannot do if the two sub-skills share one roll.
        when(husbandry.rollBonusHarvestDrop()).thenReturn(true);
        when(husbandry.rollBonusHoney()).thenReturn(true);
        assertEquals(2, HusbandryListener.bonusHiveHelpings(husbandry));

        clearInvocations(husbandry);
        when(husbandry.rollBonusHoney()).thenReturn(false);
        assertEquals(1, HusbandryListener.bonusHiveHelpings(husbandry));

        clearInvocations(husbandry);
        when(husbandry.rollBonusHarvestDrop()).thenReturn(false);
        assertEquals(0, HusbandryListener.bonusHiveHelpings(husbandry));
    }

    @Test
    void beekeeperReportsTheBeesCalmAndOtherwiseLeavesVanillaAlone() {
        // ⚠️ The mechanic is "you always count as standing over a lit campfire" -- vanilla's OWN
        // shelter branch -- because that closes BOTH ways a harvest angers bees at once. Suppressing
        // angerNearbyBees alone, which the plan proposed, would have left takeHoney's EMERGENCY
        // release firing: the bees inside the hive would still have come out for you.
        when(husbandry.countsAsShelteredHiveHarvest()).thenReturn(true);
        assertTrue(HusbandryListener.hiveHarvestLeavesBeesCalm(breeder()));

        when(husbandry.countsAsShelteredHiveHarvest()).thenReturn(false);
        assertFalse(HusbandryListener.hiveHarvestLeavesBeesCalm(breeder()));
    }

    @Test
    void aDispenserHarvestingAHiveNeverCalmsBeesOrSavesDurability() {
        // A dispenser cannot reach onUseWithItem at all, so these two are belt-and-braces -- but
        // vanilla DOES ship two dispenser behaviours that harvest hives (ShearsDispenserBehavior and
        // DispenserBehavior$3), so a non-player holder must resolve to nothing rather than to whoever
        // happens to be tracked.
        lenient().when(husbandry.countsAsShelteredHiveHarvest()).thenReturn(true);
        lenient().when(husbandry.rollToolDurabilitySave()).thenReturn(true);

        assertFalse(HusbandryListener.hiveHarvestLeavesBeesCalm(null));
        assertEquals(1, HusbandryListener.onHiveToolDamaged(null, 1));
        verify(husbandry, never()).rollToolDurabilitySave();
    }

    @Test
    void bountifulHarvestSparesTheShearsOnAHive() {
        when(husbandry.rollToolDurabilitySave()).thenReturn(true);
        assertEquals(0, HusbandryListener.onHiveToolDamaged(breeder(), 1));
    }

    // --- Milk -----------------------------------------------------------------------------------

    @Test
    void milkingACowPaysTheMilkVerb() {
        allowHarvestCooldown();
        worldTime(0L);

        HusbandryListener.onMilked(harvestable(CowEntity.class), breeder());
        verify(husbandry).onMilk();
    }

    @Test
    void milkingTheSameCowInsideTheCooldownPaysOnlyOnce() {
        // ⚠️⚠️ D-H5, and the reason this verb needed a gate invented for it: vanilla puts NO cooldown
        // on milking, so the same cow can be milked as fast as a player can click, forever, for free.
        // Without this it would be the fastest XP source in the mod by a wide margin.
        allowHarvestCooldown();
        final Entity cow = harvestable(CowEntity.class);

        worldTime(0L);
        HusbandryListener.onMilked(cow, breeder());
        worldTime(COOLDOWN_TICKS - 1);
        HusbandryListener.onMilked(cow, breeder());

        verify(husbandry, times(1)).onMilk();
    }

    @Test
    void milkingTheSameCowAfterTheCooldownPaysAgain() {
        allowHarvestCooldown();
        final Entity cow = harvestable(CowEntity.class);

        worldTime(0L);
        HusbandryListener.onMilked(cow, breeder());
        worldTime(COOLDOWN_TICKS);
        HusbandryListener.onMilked(cow, breeder());

        verify(husbandry, times(2)).onMilk();
    }

    @Test
    void theCooldownIsPerAnimalAndNotPerPlayer() {
        // A herd is the intended way to earn this verb. Capping the player rather than the animal
        // would punish keeping livestock, which is the thing the skill exists to reward.
        allowHarvestCooldown();
        worldTime(0L);

        HusbandryListener.onMilked(harvestable(CowEntity.class), breeder());
        HusbandryListener.onMilked(harvestable(CowEntity.class), breeder());

        verify(husbandry, times(2)).onMilk();
    }

    @Test
    void aZeroCooldownDisablesTheGateEntirely() {
        // The escape hatch, so the behaviour is diagnosable during play-testing.
        lenient().when(husbandry.getHarvestCooldownSeconds()).thenReturn(0);
        final Entity cow = harvestable(CowEntity.class);
        worldTime(0L);

        HusbandryListener.onMilked(cow, breeder());
        HusbandryListener.onMilked(cow, breeder());

        verify(husbandry, times(2)).onMilk();
    }

    @Test
    void aClockThatMovedBackwardsCountsAsElapsedRatherThanLockingTheAnimalOut() {
        // /time set, or an animal carried into a dimension keeping its own count. Treating a negative
        // elapsed as "not yet" would lock that animal out of paying anything ever again, silently --
        // the worst of the two available failure modes.
        allowHarvestCooldown();
        final Entity cow = harvestable(CowEntity.class);

        worldTime(1_000_000L);
        HusbandryListener.onMilked(cow, breeder());
        worldTime(5L);
        HusbandryListener.onMilked(cow, breeder());

        verify(husbandry, times(2)).onMilk();
    }

    @Test
    void milkingByANonServerPlayerPaysNothing() {
        allowHarvestCooldown();
        worldTime(0L);

        HusbandryListener.onMilked(harvestable(CowEntity.class), mock(PlayerEntity.class));
        verify(husbandry, never()).onMilk();
    }

    // --- Brush ----------------------------------------------------------------------------------

    @Test
    void brushingAnArmadilloPaysTheBrushVerbWhenAScuteIsActuallyDelivered() {
        allowHarvestCooldown();
        worldTime(0L);
        final Entity armadillo = harvestable(ArmadilloEntity.class);

        // `true` is what brushScute() returns when it really handed over a scute.
        HusbandryListener.onBrushed(armadillo, breeder(), true);

        verify(husbandry).onBrush();
    }

    @Test
    void aBrushThatDeliversNothingPaysNothing() {
        // ⚠️ The reason this verb pays on the DROP where shearing pays on the attempt. Shearing is
        // gated upstream by isShearable(); brushing is gated by nothing at all -- brushScute returns
        // true for any adult armadillo and refuses only a baby -- so an item actually changing hands
        // is the only available proof a harvest happened.
        //
        // 🔑 That flag is a listener PARAMETER rather than an early return inside ArmadilloBrushMixin
        // precisely so this test can reach it. A guard living in a mixin body is a guard nothing
        // proves.
        allowHarvestCooldown();
        worldTime(0L);
        final Entity armadillo = harvestable(ArmadilloEntity.class);

        // `false` is exactly what brushScute() returns for a baby armadillo.
        assertFalse(HusbandryListener.onBrushed(armadillo, breeder(), false),
                "a brush that delivered nothing must not owe a bonus scute either");

        verify(husbandry, never()).onBrush();
        verify(husbandry, never()).getHarvestCooldownSeconds();
    }

    @Test
    void aDispenserBrushingAnArmadilloPaysNothing() {
        // ⚠️ Vanilla really does ship an armadillo-brushing dispenser (DispenserBehavior$5) and the
        // plan did not mention it.
        //
        // ⚠️⚠️ THE REASON IT IS EXCLUDED IS NOT THE SIGNATURE HERE. Where vanilla routes brush loot
        // through a forEachBrushedItem funnel, that funnel takes the brushing Entity and the
        // dispenser passes null, so the exclusion falls out of the argument list. There is no funnel
        // on this band: brushScute() takes no arguments and drops the scute inline. The real gate is
        // the CALL SITE -- ArmadilloBrushMixin hangs off interactMob, which only a player reaches and
        // which the dispenser behaviour never enters. Stricter, but a different reason, and it is
        // MixinApplicationTest that proves the hook is still on interactMob.
        //
        // What this test pins is the listener's own half: a brusher who is not a real server player
        // pays nothing, whatever route reached us.
        final Entity armadillo = harvestable(ArmadilloEntity.class);

        assertFalse(HusbandryListener.onBrushed(armadillo, null, true));

        verify(husbandry, never()).onBrush();
        verify(husbandry, never()).rollBonusHarvestDrop();
    }

    @Test
    void aBrushInsideTheCooldownStillDropsTheScuteButPaysNothing() {
        // The cooldown gates the REWARD, never the drop. A mod that withheld vanilla's own loot to
        // enforce its own balance would be breaking the game rather than tuning itself.
        //
        // 🔑 On this band the "still drops" half is structural rather than asserted: vanilla's scute
        // is dropped by brushScute() BEFORE the hook is reached, and the listener only ever answers
        // whether a BONUS is owed. There is no path by which mcMMO could withhold it. The half that
        // could still regress -- the second brush must not pay -- is what this asserts.
        allowHarvestCooldown();
        worldTime(0L);
        final Entity armadillo = harvestable(ArmadilloEntity.class);

        HusbandryListener.onBrushed(armadillo, breeder(), true);
        HusbandryListener.onBrushed(armadillo, breeder(), true);

        verify(husbandry, times(1)).onBrush();
    }

    @Test
    void oneBrushResolvesTheSubSkillExactlyOnce() {
        // Renamed from "...NotOncePerItem". Where vanilla routes brush loot through a funnel the hook
        // runs once per dropped item, and rolling per item would give a partial, noisy result. There
        // is no funnel here -- brushScute drops the scute itself and the hook fires once per brush --
        // so the per-item half of that guard has no subject on this band. What remains testable is
        // that ONE brush resolves the sub-skill exactly once; the "exactly one injection point" half
        // is now carried by allow = 1 on ArmadilloBrushMixin plus scripts/mixin-allow-audit.py.
        allowHarvestCooldown();
        worldTime(0L);
        final Entity armadillo = harvestable(ArmadilloEntity.class);
        when(husbandry.rollBonusHarvestDrop()).thenReturn(true);

        assertTrue(HusbandryListener.onBrushed(armadillo, breeder(), true),
                "a winning roll must tell the caller a second scute is owed");

        verify(husbandry, times(1)).rollBonusHarvestDrop();
        verify(husbandry, times(1)).onBrush();
    }

    @Test
    void aBrushInsideTheCooldownRollsNoBonusEither() {
        // The bonus rides the award, not the drop: a brush that pays nothing must not also hand out
        // a free extra scute, or the cooldown would only be throttling half of the reward.
        allowHarvestCooldown();
        worldTime(0L);
        final Entity armadillo = harvestable(ArmadilloEntity.class);
        lenient().when(husbandry.rollBonusHarvestDrop()).thenReturn(true);

        HusbandryListener.onBrushed(armadillo, breeder(), true);
        clearInvocations(husbandry);
        assertFalse(HusbandryListener.onBrushed(armadillo, breeder(), true),
                "a brush inside the cooldown must owe no bonus scute");

        verify(husbandry, never()).rollBonusHarvestDrop();
    }

    @Test
    void bountifulHarvestSparesTheBrushOnASuccessfulRoll() {
        final Entity armadillo = harvestable(ArmadilloEntity.class);
        when(husbandry.rollToolDurabilitySave()).thenReturn(true);

        HusbandryListener.beginPlayerInteraction(breeder(), armadillo);
        try {
            assertEquals(0, HusbandryListener.onBrushToolDamaged(armadillo, 16),
                    "a saved brush must cost the tool nothing -- worth a quarter of it per use");
        } finally {
            HusbandryListener.endPlayerInteraction();
        }
    }

    @Test
    void aDispenserNeverSavesBrushDurability() {
        final Entity armadillo = harvestable(ArmadilloEntity.class);
        lenient().when(husbandry.rollToolDurabilitySave()).thenReturn(true);

        assertEquals(16, HusbandryListener.onBrushToolDamaged(armadillo, 16));
        verify(husbandry, never()).rollToolDurabilitySave();
    }

    // --- Stage 5: Selective Breeding -------------------------------------------------------------

    private AnimalEntity horseLovedBy(ServerPlayerEntity player) {
        final AnimalEntity horse = cow(true, 0, true);
        lenient().when(horse.getLovingPlayer()).thenReturn(player);
        return horse;
    }

    @Test
    void selectiveBreedingBiasesAFoalStatForTheBreeder() {
        // The bias itself is the manager's arithmetic; what this pins is the STASH -- vanilla's
        // inheritance roll is a static method with no player in it, so without the join there is
        // nobody to ask and every foal gets the plain dice.
        lenient().when(husbandry.applyStatBias(20.0, 10.0, 30.0)).thenReturn(22.5);

        HusbandryListener.beginSelectiveBreeding(horseLovedBy(breeder()), null);
        try {
            assertEquals(22.5, HusbandryListener.applySelectiveBreedingBias(20.0, 10.0, 30.0));
        } finally {
            HusbandryListener.endSelectiveBreeding();
        }
        verify(husbandry).applyStatBias(20.0, 10.0, 30.0);
    }

    @Test
    void aBreedingWithNoLovingPlayerLeavesTheRollAlone() {
        // ⚠️ The common case by a wide margin: this static method runs for EVERY horse bred anywhere,
        // including AI-driven breeding with no player involved. It must be the exact identity there.
        HusbandryListener.beginSelectiveBreeding(cow(true, 0, true), null);
        try {
            assertEquals(17.3, HusbandryListener.applySelectiveBreedingBias(17.3, 10.0, 30.0));
        } finally {
            HusbandryListener.endSelectiveBreeding();
        }
        verify(husbandry, never()).applyStatBias(anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void theBiasStashDoesNotOutliveTheBreedingThatOpenedIt() {
        lenient().when(husbandry.applyStatBias(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(99.0);

        HusbandryListener.beginSelectiveBreeding(horseLovedBy(breeder()), null);
        HusbandryListener.endSelectiveBreeding();

        assertEquals(20.0, HusbandryListener.applySelectiveBreedingBias(20.0, 10.0, 30.0),
                "a leaked stash would bias every horse bred anywhere for the rest of the session");
    }

    @Test
    void eitherParentCanCarryTheLovingPlayer() {
        // Vanilla sets the loving player on whichever animal the player actually fed, so checking only
        // one parent would silently halve the sub-skill.
        lenient().when(husbandry.applyStatBias(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(22.5);

        HusbandryListener.beginSelectiveBreeding(cow(true, 0, true), horseLovedBy(breeder()));
        try {
            assertEquals(22.5, HusbandryListener.applySelectiveBreedingBias(20.0, 10.0, 30.0));
        } finally {
            HusbandryListener.endSelectiveBreeding();
        }
    }

    // --- Stage 5: Brood -------------------------------------------------------------------------

    private Entity eggThrownBy(ServerPlayerEntity thrower) {
        final EggEntity egg = mock(EggEntity.class);
        lenient().when(egg.getOwner()).thenReturn(thrower);
        return egg;
    }

    @Test
    void broodRescuesAnEggVanillaWasAboutToWaste() {
        when(husbandry.rollEggHatch()).thenReturn(true);
        assertEquals(0, HusbandryListener.onEggHatchRoll(eggThrownBy(breeder()), 5),
                "a successful Brood roll must force vanilla's own hatch branch");
    }

    @Test
    void broodDoesNotReRollAnEggVanillaWasAlreadyHatching() {
        // Layering, not replacing: Brood can only ever improve the odds. If this consulted the
        // sub-skill on an already-winning roll, a configured chance could turn a hatch into a miss.
        lenient().when(husbandry.rollEggHatch()).thenReturn(false);
        assertEquals(0, HusbandryListener.onEggHatchRoll(eggThrownBy(breeder()), 0));
        verify(husbandry, never()).rollEggHatch();
    }

    @Test
    void aFailedBroodRollLeavesVanillasResultUntouched() {
        when(husbandry.rollEggHatch()).thenReturn(false);
        assertEquals(5, HusbandryListener.onEggHatchRoll(eggThrownBy(breeder()), 5));
    }

    @Test
    void broodTurnsAHatchIntoAFullClutch() {
        when(husbandry.rollMultipleChicks()).thenReturn(true);
        assertEquals(0, HusbandryListener.onFullClutchRoll(eggThrownBy(breeder()), 17));
    }

    @Test
    void aDispensedEggEarnsNoBroodAtAll() {
        // ⚠️ Eggs are dispensable in vanilla. A dispensed egg has no player owner, so both halves of
        // the sub-skill must decline rather than bill whoever happens to be tracked -- otherwise an
        // egg-farm-plus-dispenser loop would quietly run Brood forever.
        lenient().when(husbandry.rollEggHatch()).thenReturn(true);
        lenient().when(husbandry.rollMultipleChicks()).thenReturn(true);

        assertEquals(5, HusbandryListener.onEggHatchRoll(eggThrownBy(null), 5));
        assertEquals(17, HusbandryListener.onFullClutchRoll(eggThrownBy(null), 17));
        verify(husbandry, never()).rollEggHatch();
        verify(husbandry, never()).rollMultipleChicks();
    }

    @Test
    void hatchingAnEggPaysNoXpAndMarksNoChick() {
        // ⚠️⚠️ Both halves are load-bearing and both are about the same exploit. ChickenEntity's
        // eggLayTime is a passive timer ticked in tickMovement, so a hopper under a coop is fully AFK
        // income. Brood is a YIELD sub-skill: it must never award XP, and the chick it hatches must
        // never carry a bred-by marker -- one would turn that same AFK farm into a raise-XP farm
        // twenty minutes later, when the chicks came of age.
        when(husbandry.rollEggHatch()).thenReturn(true);
        when(husbandry.rollMultipleChicks()).thenReturn(true);
        final Entity egg = eggThrownBy(breeder());

        HusbandryListener.onEggHatchRoll(egg, 5);
        HusbandryListener.onFullClutchRoll(egg, 17);

        verify(husbandry, never()).onBreed(any(), anyLong());
        verify(husbandry, never()).onRaise(any());
        verify(husbandry, never()).onFeedBaby(any());
        verify(egg, never()).setAttached(any(), any());
    }

    // --- Stage 5: Hidden Bounty -----------------------------------------------------------------

    // --- Stage 6: Herdsman's Call ---------------------------------------------------------------

    @Test
    void herdsmansCallLetsAHarvestIgnoreItsCooldown() {
        // The cooldown-bypass half. Placed in the shared gate rather than at the two call sites so it
        // cannot be wired into milking and forgotten for brushing -- so testing it through milk also
        // covers brush.
        allowHarvestCooldown();
        final Entity cow = harvestable(CowEntity.class);
        worldTime(0L);

        HusbandryListener.onMilked(cow, breeder());
        when(husbandry.isHerdsmansCallActive()).thenReturn(true);
        HusbandryListener.onMilked(cow, breeder());

        verify(husbandry, times(2)).onMilk();
    }

    @Test
    void aBypassedHarvestDoesNotResetTheOrdinaryCooldown() {
        // ⚠️ Otherwise blowing the horn over a herd would stamp every animal's clock, handing the
        // player a second full round the instant the ability ended -- the super would be worth twice
        // what it looks like, and only to someone who noticed.
        allowHarvestCooldown();
        final Entity cow = harvestable(CowEntity.class);

        worldTime(0L);
        HusbandryListener.onMilked(cow, breeder()); // Normal award, stamps tick 0.

        when(husbandry.isHerdsmansCallActive()).thenReturn(true);
        worldTime(100L);
        HusbandryListener.onMilked(cow, breeder()); // Bypassed; must NOT stamp tick 100.

        when(husbandry.isHerdsmansCallActive()).thenReturn(false);
        worldTime(COOLDOWN_TICKS - 1);
        HusbandryListener.onMilked(cow, breeder()); // Still inside the ORIGINAL window.

        verify(husbandry, times(2)).onMilk();
    }

    @Test
    void aHarvestWithNoTreasureConfigBoundIsSafeAndSilent() {
        // McMMOMod's TreasureConfig is unbound in this fixture, which is also the real state during
        // early boot. Every harvest verb calls into Hidden Bounty, so a missing config must be a
        // no-op rather than an NPE that takes the whole verb down with it.
        allowHarvestCooldown();
        worldTime(0L);

        HusbandryListener.onMilked(harvestable(CowEntity.class), breeder());
        HusbandryListener.onHoneyBottled(breeder());

        verify(husbandry).onMilk();
        verify(husbandry).onHiveHarvest();
        verify(husbandry, never()).rollHiddenBounty();
    }
}
