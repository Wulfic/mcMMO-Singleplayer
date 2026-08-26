package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.subskills.taming.PetCombatMode;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.platform.SkillAttributeService;
import com.gmail.nossr50.skills.taming.TamingManager;
import com.gmail.nossr50.util.McTestRegistries;
import com.gmail.nossr50.util.player.UserManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link PetCombatSweep} — what an aggressive pet picks a fight with, and how far any pet will chase
 * what it already has.
 *
 * <p>Drives the real {@code tick} body throughout. The two halves fail in different directions and
 * both are silent, so both get positive <em>and</em> negative cases: an over-eager candidate filter
 * makes pets suicide into things, and a missing one makes the feature look like it does nothing.
 */
class PetCombatSweepTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
    }

    private static final double BASE_FOLLOW_RANGE = 16.0D;

    private GeneralConfig config;
    private ServerLevel world;
    private ServerPlayer player;
    private UUID playerUuid;
    private McMMOPlayer mmoPlayer;
    private TamingManager tamingManager;

    /** Everything the world's box queries will hand back, by the class the caller asked for. */
    private final List<Wolf> pets = new ArrayList<>();
    private final List<Mob> mobs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        config = mock(GeneralConfig.class);
        lenient().when(config.isPetCombatModeEnabled()).thenReturn(true);
        lenient().when(config.getPetSweepIntervalTicks()).thenReturn(20);
        lenient().when(config.getPetAggressiveRadius()).thenReturn(32.0D);
        lenient().when(config.getPetEngageRange()).thenReturn(32.0D);
        McMMOMod.setGeneralConfig(config);

        world = mock(ServerLevel.class);
        player = mock(ServerPlayer.class);
        playerUuid = UUID.randomUUID();
        lenient().when(player.getUUID()).thenReturn(playerUuid);
        lenient().when(player.getName()).thenReturn(Component.literal("tester"));
        lenient().when(player.level()).thenReturn(world);
        lenient().when(player.getBoundingBox()).thenReturn(new AABB(-0.3, 0, -0.3, 0.3, 1.8, 0.3));
        // age 0 % 20 == 0, so every test is on a sweep tick unless it says otherwise.
        player.tickCount = 0;
        // Everything is 100 blocks² away unless a test says otherwise; overridden per mob below.
        lenient().when(player.distanceToSqr(any(Entity.class))).thenReturn(100.0D);

        wireWorldQueries();
        trackProfile(PetCombatMode.AGGRESSIVE);
    }

    @AfterEach
    void tearDown() {
        if (mmoPlayer != null) {
            UserManager.cleanupPlayer(mmoPlayer);
            mmoPlayer = null;
        }
        McMMOMod.setGeneralConfig(null);
        pets.clear();
        mobs.clear();
    }

    // --- the aggressive candidate set -----------------------------------------------------------

    @Test
    void anAggressivePetWithNoTargetAttacksTheNearestHostile() {
        final Wolf pet = idlePet();
        final Zombie zombie = hostile(Zombie.class, 25.0D);
        final Zombie distant = hostile(Zombie.class, 900.0D);

        PetCombatSweep.tick(player);

        verify(pet).setTarget(zombie);
        verify(pet, never()).setTarget(distant);
    }

    /**
     * ⚠️⚠️ THE row this class exists for. {@code HostileEntity} — the obvious candidate test, and
     * the one {@code CombatUtils.categoryOf} uses — silently omits slime, magma cube, ghast, phantom
     * and hoglin, every one of which implements {@code Monster} without extending it. A pack that
     * simply never reacted to a slime would produce no error anywhere, and no other test in this
     * build looks.
     */
    @Test
    void aSlimeIsAValidTargetEvenThoughItIsNotAHostileEntity() {
        final Wolf pet = idlePet();
        final Slime slime = hostile(Slime.class, 25.0D);

        assertTrue(slime instanceof Enemy, "precondition: a slime is a Monster");

        PetCombatSweep.tick(player);

        verify(pet).setTarget(slime);
    }

    /** The same trap, second species — magma cubes are the other common one. */
    @Test
    void aMagmaCubeIsAValidTarget() {
        final Wolf pet = idlePet();
        final MagmaCube magmaCube = hostile(MagmaCube.class, 25.0D);

        PetCombatSweep.tick(player);

        verify(pet).setTarget(magmaCube);
    }

    /**
     * The warden is a {@code Monster}, so nothing else filters it. It is unkillable by a wolf pack
     * and the noise summons it onto the player who never chose the fight.
     */
    @Test
    void aWardenIsNeverTargeted() {
        final Wolf pet = idlePet();
        hostile(Warden.class, 4.0D);

        PetCombatSweep.tick(player);

        verify(pet, never()).setTarget(any());
    }

    /**
     * Creeper and ghast are refused by vanilla's own {@code canAttackWithOwner}, which this
     * delegates to rather than re-listing. The mock says "no" for them, which is what the real
     * method does (bytecode-verified).
     */
    @Test
    void aCreeperIsNeverTargeted() {
        final Wolf pet = idlePet();
        final Creeper creeper = hostile(Creeper.class, 4.0D);
        lenient().when(pet.wantsToAttack(creeper, player)).thenReturn(false);

        PetCombatSweep.tick(player);

        verify(pet, never()).setTarget(any());
    }

    @Test
    void aGhastIsNeverTargeted() {
        final Wolf pet = idlePet();
        final Ghast ghast = hostile(Ghast.class, 4.0D);
        lenient().when(pet.wantsToAttack(ghast, player)).thenReturn(false);

        PetCombatSweep.tick(player);

        verify(pet, never()).setTarget(any());
    }

    /** A cow is not a Monster. Aggressive mode must not turn the pack loose on livestock. */
    @Test
    void aPassiveAnimalIsNeverTargeted() {
        final Wolf pet = idlePet();
        final Cow cow = mock(Cow.class);
        lenient().when(cow.isAlive()).thenReturn(true);
        lenient().when(player.distanceToSqr(cow)).thenReturn(4.0D);
        mobs.add(cow);

        PetCombatSweep.tick(player);

        verify(pet, never()).setTarget(any());
    }

    // --- the passive negative -------------------------------------------------------------------

    /**
     * ⚠️⚠️ Without this, the whole feature is indistinguishable from "pets are always aggressive
     * now".
     *
     * <p>Note what is asserted: <b>the sweep picked nothing</b>, not "the pet has no target". The
     * latter would pass for free and would stay green with the entire feature deleted — and it would
     * also be the wrong assertion under ruling R-6, which says passive never clears a target a pet
     * already has.
     */
    @Test
    void aPassivePetPicksNoFightOfItsOwn() {
        trackProfile(PetCombatMode.PASSIVE);
        final Wolf pet = idlePet();
        hostile(Zombie.class, 4.0D);

        PetCombatSweep.tick(player);

        verify(pet, never()).setTarget(any());
    }

    /** R-6: passive gates acquisition only. A fight already under way is never called off. */
    @Test
    void aPassivePetKeepsTheTargetItAlreadyHas() {
        trackProfile(PetCombatMode.PASSIVE);
        final Zombie engaged = mock(Zombie.class);
        lenient().when(engaged.isAlive()).thenReturn(true);
        final Wolf pet = pet(engaged);

        PetCombatSweep.tick(player);

        verify(pet, never()).setTarget(any());
        verify(pet, never()).setTarget(null);
    }

    /** An unloaded profile fails closed to passive rather than turning the pack loose mid-join. */
    @Test
    void anUnloadedProfileDoesNotAcquireTargets() {
        UserManager.cleanupPlayer(mmoPlayer);
        mmoPlayer = null;
        final Wolf pet = idlePet();
        hostile(Zombie.class, 4.0D);

        PetCombatSweep.tick(player);

        verify(pet, never()).setTarget(any());
    }

    // --- the reach fix (applies in BOTH stances) ------------------------------------------------

    /**
     * ⚠️⚠️ The reported bug. A pet with a live target gets its follow range raised to the configured
     * engage range — otherwise it stands next to you holding a target it can never path to, which is
     * exactly what "my pets ignore what I shoot" looks like from inside the game.
     */
    @Test
    void aPetWithATargetIsBoostedToTheEngageRange() {
        final Zombie shot = mock(Zombie.class);
        lenient().when(shot.isAlive()).thenReturn(true);
        final Wolf pet = pet(shot);

        PetCombatSweep.tick(player);

        // 32 configured - 16 base = 16 added.
        assertEquals(16.0D, SkillAttributeService.appliedValue(pet,
                SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE), 1.0e-9);
    }

    /**
     * ⚠️ And it applies in PASSIVE, which is the stance the bug was reported in. A pet you sicced by
     * shooting something is a passive-mode pet.
     */
    @Test
    void theReachBoostAppliesInPassiveModeToo() {
        trackProfile(PetCombatMode.PASSIVE);
        final Zombie shot = mock(Zombie.class);
        lenient().when(shot.isAlive()).thenReturn(true);
        final Wolf pet = pet(shot);

        PetCombatSweep.tick(player);

        assertTrue(SkillAttributeService.isApplied(pet,
                SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE),
                "the reach fix is not a mode feature — it is why pets ignore what you shoot");
    }

    /**
     * ⚠️⚠️ The other half, and the one a leak hides in. A modifier that outlives its condition is a
     * permanent buff; re-deriving every sweep is what makes a missed removal self-heal.
     */
    @Test
    void theBoostIsRemovedWhenTheTargetIsGone() {
        final Zombie shot = mock(Zombie.class);
        lenient().when(shot.isAlive()).thenReturn(true);
        final Wolf pet = pet(shot);

        PetCombatSweep.tick(player);
        assertTrue(SkillAttributeService.isApplied(pet,
                SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE), "precondition: boosted");

        // The target dies and the goal clears it.
        when(pet.getTarget()).thenReturn(null);
        PetCombatSweep.tick(player);

        assertTrue(!SkillAttributeService.isApplied(pet,
                SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE),
                "a boost that outlives its target is a permanent buff on the pet");
    }

    /**
     * A dead target is not a live one — the boost must not survive on a corpse.
     *
     * <p>⚠️ The pet is boosted FIRST, on a living target, and only then does the target die. An
     * earlier draft of this test started from a corpse and asserted the boost was absent — which it
     * trivially was, because nothing had ever applied it. That version stayed green when the removal
     * was deleted outright, and it was caught only because the mutation run produced <em>one</em>
     * failure where two were predicted. A test that asserts a state it never entered proves nothing.
     */
    @Test
    void aDeadTargetDoesNotKeepTheBoostAlive() {
        final Zombie victim = mock(Zombie.class);
        when(victim.isAlive()).thenReturn(true);
        final Wolf pet = pet(victim);

        PetCombatSweep.tick(player);
        assertTrue(SkillAttributeService.isApplied(pet,
                SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE),
                "precondition: a pet chasing a living target is boosted");

        // It dies, but the goal has not cleared the reference yet — the pet still points at a corpse.
        when(victim.isAlive()).thenReturn(false);
        PetCombatSweep.tick(player);

        assertTrue(!SkillAttributeService.isApplied(pet,
                SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE),
                "the boost outlived its target and is now a permanent buff on the pet");
    }

    /**
     * The same vacuity trap on the sitting branch: boost the pet first, then sit it.
     *
     * <p>{@link #aSittingPetIsNeitherBoostedNorGivenATarget} asserts a pet that was never boosted
     * stays unboosted, which is true for free. This one proves the sitting branch actually takes an
     * existing boost back off.
     */
    @Test
    void sittingAPetTakesItsExistingBoostBackOff() {
        final Zombie target = mock(Zombie.class);
        lenient().when(target.isAlive()).thenReturn(true);
        final Wolf pet = pet(target);

        PetCombatSweep.tick(player);
        assertTrue(SkillAttributeService.isApplied(pet,
                SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE), "precondition: boosted");

        when(pet.isOrderedToSit()).thenReturn(true);
        PetCombatSweep.tick(player);

        assertTrue(!SkillAttributeService.isApplied(pet,
                SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE),
                "a pet told to sit kept its chase boost");
    }

    /**
     * And on the below-natural-range branch: a config edit that lowers the engage range must take
     * an already-applied boost off, not merely decline to add a new one.
     */
    @Test
    void loweringTheEngageRangeBelowNaturalRemovesAnExistingBoost() {
        final Zombie target = mock(Zombie.class);
        lenient().when(target.isAlive()).thenReturn(true);
        final Wolf pet = pet(target);

        PetCombatSweep.tick(player);
        assertTrue(SkillAttributeService.isApplied(pet,
                SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE), "precondition: boosted");

        when(config.getPetEngageRange()).thenReturn(BASE_FOLLOW_RANGE);
        PetCombatSweep.tick(player);

        assertTrue(!SkillAttributeService.isApplied(pet,
                SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE));
    }

    /**
     * ⚠️ The modifier must be TEMPORARY. A persistent one is written to the save file, where no
     * later code fix can reach it — the difference between a bug and an unrecoverable save.
     */
    @Test
    void theBoostIsTemporaryAndNeverPersistent() {
        final Zombie shot = mock(Zombie.class);
        lenient().when(shot.isAlive()).thenReturn(true);
        final Wolf pet = pet(shot);

        PetCombatSweep.tick(player);

        final AttributeInstance instance =
                pet.getAttribute(Attributes.FOLLOW_RANGE);
        assertTrue(instance.getModifiers().stream().anyMatch(m -> m.amount() == 16.0D),
                "precondition: the modifier is applied");
        assertTrue(instance.getPermanentModifiers().stream().noneMatch(
                        m -> m.is(SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE.id())),
                "the engage-range boost reached the PERSISTENT map and will be written to the save");
    }

    /** A sitting pet is under an explicit order to stay: no fight, no chase, no boost. */
    @Test
    void aSittingPetIsNeitherBoostedNorGivenATarget() {
        final Zombie shot = mock(Zombie.class);
        lenient().when(shot.isAlive()).thenReturn(true);
        final Wolf pet = pet(shot);
        when(pet.isOrderedToSit()).thenReturn(true);
        hostile(Zombie.class, 4.0D);

        PetCombatSweep.tick(player);

        verify(pet, never()).setTarget(any());
        assertTrue(!SkillAttributeService.isApplied(pet,
                SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE));
    }

    /** An engage range at or below a pet's natural one must not apply a zero or negative "boost". */
    @Test
    void anEngageRangeBelowTheNaturalOneAppliesNothing() {
        when(config.getPetEngageRange()).thenReturn(BASE_FOLLOW_RANGE);
        final Zombie shot = mock(Zombie.class);
        lenient().when(shot.isAlive()).thenReturn(true);
        final Wolf pet = pet(shot);

        PetCombatSweep.tick(player);

        assertTrue(!SkillAttributeService.isApplied(pet,
                SkillAttributeService.Managed.TAMING_PET_ENGAGE_RANGE));
    }

    // --- gating ---------------------------------------------------------------------------------

    @Test
    void theSweepDoesNothingWhenTheFeatureIsDisabled() {
        when(config.isPetCombatModeEnabled()).thenReturn(false);
        final Wolf pet = idlePet();
        hostile(Zombie.class, 4.0D);

        PetCombatSweep.tick(player);

        verify(pet, never()).setTarget(any());
    }

    /**
     * The throttle has to actually throttle. Without it the two box queries run 20×/s per player,
     * and nothing in the build would report it — the feature would simply work, expensively.
     */
    @Test
    void theSweepIsSkippedOnATickThatIsNotOnTheInterval() {
        player.tickCount = 7; // 7 % 20 != 0
        final Wolf pet = idlePet();
        hostile(Zombie.class, 4.0D);

        PetCombatSweep.tick(player);

        verify(pet, never()).setTarget(any());
    }

    @Test
    void aPetSomebodyElseOwnsIsUntouched() {
        final Wolf stranger = mock(Wolf.class);
        lenient().when(stranger.isTame()).thenReturn(true);
        lenient().when(stranger.isOwnedBy(any())).thenReturn(false);
        // NOT added to `pets` — the world query's own predicate excludes it. Driving the real
        // predicate is the point: this asserts the filter is asked, not that the list was short.
        hostile(Zombie.class, 4.0D);

        PetCombatSweep.tick(player);

        verify(stranger, never()).setTarget(any());
    }

    // --- fixture --------------------------------------------------------------------------------

    /**
     * Makes {@code world.getEntitiesByClass} answer from {@link #pets} / {@link #mobs}, applying the
     * caller's real predicate.
     *
     * <p>⚠️ The predicate is applied rather than ignored, deliberately: it is where the
     * {@code Monster} test, the warden exclusion and the ownership test live, and a fixture that
     * handed back the raw list would make every filtering assertion here vacuous.
     */
    @SuppressWarnings("unchecked")
    private void wireWorldQueries() {
        lenient().when(world.getEntitiesOfClass(any(Class.class), any(AABB.class), any()))
                .thenAnswer(invocation -> {
                    final Class<?> type = invocation.getArgument(0);
                    final Predicate<Object> filter = invocation.getArgument(2);
                    final List<Object> source =
                            type == Wolf.class ? List.copyOf(pets) : List.copyOf(mobs);
                    final List<Object> matched = new ArrayList<>();
                    for (Object candidate : source) {
                        if (type.isInstance(candidate) && filter.test(candidate)) {
                            matched.add(candidate);
                        }
                    }
                    return matched;
                });
    }

    /** A tamed, owned, standing pet with no target and a real FOLLOW_RANGE attribute instance. */
    private Wolf idlePet() {
        return pet(null);
    }

    /** A tamed, owned, standing pet currently targeting {@code target} (or nothing). */
    private Wolf pet(LivingEntity target) {
        final Wolf pet = mock(Wolf.class);
        lenient().when(pet.isTame()).thenReturn(true);
        lenient().when(pet.isOwnedBy(player)).thenReturn(true);
        lenient().when(pet.isOwnedBy(any())).thenReturn(true);
        lenient().when(pet.isOrderedToSit()).thenReturn(false);
        lenient().when(pet.getTarget()).thenReturn(target);
        // Vanilla permits by default; the creeper/ghast cases override it, matching what the real
        // canAttackWithOwner does.
        lenient().when(pet.wantsToAttack(any(), any())).thenReturn(true);

        // A REAL attribute instance, not a stub: the boost's value is derived from getBaseValue()
        // and asserted through SkillAttributeService, so a mock returning 0 would make every reach
        // assertion here agree with itself and with nothing else.
        final AttributeInstance followRange =
                new AttributeInstance(Attributes.FOLLOW_RANGE, ignored -> { });
        followRange.setBaseValue(BASE_FOLLOW_RANGE);
        lenient().when(pet.getAttribute(Attributes.FOLLOW_RANGE))
                .thenReturn(followRange);

        pets.add(pet);
        return pet;
    }

    /** A live hostile of {@code type}, {@code squaredDistance} from the player. */
    private <T extends Mob> T hostile(Class<T> type, double squaredDistance) {
        final T mob = mock(type);
        lenient().when(mob.isAlive()).thenReturn(true);
        lenient().when(player.distanceToSqr(mob)).thenReturn(squaredDistance);
        mobs.add(mob);
        return mob;
    }

    /** Put a profile in {@link UserManager} whose Taming manager reports {@code mode}. */
    private void trackProfile(PetCombatMode mode) {
        if (mmoPlayer != null) {
            UserManager.cleanupPlayer(mmoPlayer);
        }
        tamingManager = mock(TamingManager.class);
        lenient().when(tamingManager.getPetCombatMode()).thenReturn(mode);

        final PlatformPlayer platformPlayer = mock(PlatformPlayer.class);
        lenient().when(platformPlayer.getUniqueId()).thenReturn(playerUuid);
        mmoPlayer = mock(McMMOPlayer.class);
        lenient().when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        lenient().when(mmoPlayer.getTamingManager()).thenReturn(tamingManager);
        UserManager.track(mmoPlayer);
    }
}
