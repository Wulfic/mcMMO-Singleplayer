package com.gmail.nossr50.fabric.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.McTestRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Call of the Wild's "sic your pets on it", driven through the real damage seam
 * ({@code EntityDamageListener#onModifyAppliedDamage}).
 *
 * <h2>Two gaps, one cause</h2>
 * The sic used to be a block inside {@code applyProjectileAttackBonus}, and it inherited two of that
 * method's early returns as bugs. Both are faithful ports of legacy behaviour and both are wrong for
 * what was reported:
 *
 * <ul>
 *   <li><b>A thrown trident sicced nothing</b>, because the trident arm returns to Impale before the
 *       sic block was reached.</li>
 *   <li><b>Only arrows and tridents reached the code at all</b>, because that method opens on
 *       {@code instanceof PersistentProjectileEntity} — correctly, for its own arithmetic. A
 *       snowball, an egg or a fired firework was invisible to it.</li>
 * </ul>
 *
 * <p>Fixing it by reordering would have closed the first and left the shape that caused it, so the
 * sic is now its own method asking its own question. These tests pin both directions: everything the
 * player throws sics, and the two exclusions that must survive still do.
 */
class SicPetsOnRangedHitTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
    }

    private ServerPlayer shooter;
    private Wolf pet;
    private final List<Wolf> wolves = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Both configs resolve to shipped defaults; the sic itself reads neither, but the damage
        // seam it rides does, and a null config there would take a different path.
        McMMOMod.setGeneralConfig(mock(GeneralConfig.class));
        McMMOMod.setExperienceConfig(mock(ExperienceConfig.class));

        final ServerLevel world = mock(ServerLevel.class);
        shooter = mock(ServerPlayer.class);
        lenient().when(shooter.getUuid()).thenReturn(UUID.randomUUID());
        lenient().when(shooter.getEntityWorld()).thenReturn(world);
        lenient().when(shooter.getBoundingBox()).thenReturn(new AABB(-0.3, 0, -0.3, 0.3, 1.8, 0.3));

        pet = mock(Wolf.class);
        lenient().when(pet.isTamed()).thenReturn(true);
        lenient().when(pet.isOwner(shooter)).thenReturn(true);
        lenient().when(pet.isOwner(any())).thenReturn(true);
        lenient().when(pet.isSitting()).thenReturn(false);
        wolves.add(pet);

        // The real predicate is applied, so the ownership/sitting gates in attackTarget are exercised
        // rather than bypassed.
        lenient().when(world.getEntitiesByClass(any(Class.class), any(AABB.class), any()))
                .thenAnswer(invocation -> {
                    final Predicate<Object> filter = invocation.getArgument(2);
                    final List<Object> matched = new ArrayList<>();
                    for (Wolf wolf : wolves) {
                        if (filter.test(wolf)) {
                            matched.add(wolf);
                        }
                    }
                    return matched;
                });
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setExperienceConfig(null);
        wolves.clear();
    }

    // --- the two gaps ---------------------------------------------------------------------------

    /**
     * ⚠️⚠️ Gap one. A trident is unambiguously a ranged weapon, and throwing one used to leave the
     * pack standing there — the Impale arm returned first.
     */
    @Test
    void aThrownTridentSicsThePack() {
        final Zombie target = zombie();

        EntityDamageListener.onModifyAppliedDamage(target, thrownBy(ThrownTrident.class), 10F);

        verify(pet).setTarget(target);
    }

    /**
     * ⚠️⚠️ Gap two. Nothing but arrows and tridents reached the sic at all, so a snowball — a
     * player hitting a mob from a distance by any reasonable reading — did nothing.
     */
    @Test
    void aSnowballSicsThePack() {
        final Zombie target = zombie();

        EntityDamageListener.onModifyAppliedDamage(target, thrownBy(Snowball.class), 1F);

        verify(pet).setTarget(target);
    }

    @Test
    void aThrownEggSicsThePack() {
        final Zombie target = zombie();

        EntityDamageListener.onModifyAppliedDamage(target, thrownBy(ThrownEgg.class), 1F);

        verify(pet).setTarget(target);
    }

    @Test
    void aFiredFireworkSicsThePack() {
        final Zombie target = zombie();

        EntityDamageListener.onModifyAppliedDamage(target, thrownBy(FireworkRocketEntity.class), 6F);

        verify(pet).setTarget(target);
    }

    /** The case that always worked, kept so the fix cannot be a regression dressed as a feature. */
    @Test
    void anArrowStillSicsThePack() {
        final Zombie target = zombie();

        EntityDamageListener.onModifyAppliedDamage(target, thrownBy(Arrow.class), 6F);

        verify(pet).setTarget(target);
    }

    /**
     * ⚠️ Gap three, and it was found by a mutation run rather than by reading.
     *
     * <p>No profile is tracked anywhere in this class, and every test above passes — because the sic
     * asks only "did this player hit that mob with something they threw?". In the old shape it sat
     * <em>below</em> {@code applyProjectileAttackBonus}'s {@code UserManager.getPlayer} early return,
     * so a shot fired during the window before a profile loads sicced nothing. That is not a rank
     * gate and never was: legacy gated this on a permission which is always granted in singleplayer.
     *
     * <p>Stated as its own test because the difference was documented but untested, and an
     * undocumented-and-untested behaviour change is indistinguishable from an accident. Reinstating
     * the old shape turns this red.
     */
    @Test
    void aShotFiredBeforeTheProfileLoadsStillSicsThePack() {
        final Zombie target = zombie();
        // UserManager holds nothing for this shooter — see the class fixture.

        EntityDamageListener.onModifyAppliedDamage(target, thrownBy(Arrow.class), 6F);

        verify(pet).setTarget(target);
    }

    // --- what must still be excluded ------------------------------------------------------------

    /**
     * Sending the pack at a creeper is sending the pack to be blown up next to its owner. Vanilla's
     * own {@code canAttackWithOwner} makes the same refusal.
     */
    @Test
    void aCreeperIsNeverSicced() {
        final Creeper creeper = mock(Creeper.class);
        lenient().when(creeper.getUuid()).thenReturn(UUID.randomUUID());

        EntityDamageListener.onModifyAppliedDamage(creeper, thrownBy(Arrow.class), 6F);

        verify(pet, never()).setTarget(any());
    }

    /** An armour stand is not a fight — the target-dummy guard has to stay ahead of the sic. */
    @Test
    void anArmourStandIsNeverSicced() {
        final ArmorStand dummy = mock(ArmorStand.class);
        lenient().when(dummy.getUuid()).thenReturn(UUID.randomUUID());
        lenient().when(McMMOMod.getExperienceConfig().isArmorStandInteractionPrevented())
                .thenReturn(true);

        EntityDamageListener.onModifyAppliedDamage(dummy, thrownBy(Arrow.class), 6F);

        verify(pet, never()).setTarget(any());
    }

    /** A skeleton's arrow has an owner, but not one whose pets these are. */
    @Test
    void aProjectileFiredByAMobSicsNothing() {
        final Zombie target = zombie();
        final Arrow arrow = mock(Arrow.class);
        lenient().when(arrow.getOwner()).thenReturn(mock(Zombie.class));

        EntityDamageListener.onModifyAppliedDamage(target, sourceOf(arrow), 6F);

        verify(pet, never()).setTarget(any());
    }

    /** A dispenser-fired arrow has no owner at all. */
    @Test
    void anOwnerlessProjectileSicsNothing() {
        final Zombie target = zombie();
        final Arrow arrow = mock(Arrow.class);
        lenient().when(arrow.getOwner()).thenReturn(null);

        EntityDamageListener.onModifyAppliedDamage(target, sourceOf(arrow), 6F);

        verify(pet, never()).setTarget(any());
    }

    /** A melee swing is not a ranged hit; the melee path has its own sic and this must not double it. */
    @Test
    void aMeleeHitDoesNotReachTheRangedSic() {
        final Zombie target = zombie();
        final DamageSource melee = mock(DamageSource.class);
        lenient().when(melee.getAttacker()).thenReturn(shooter);
        lenient().when(melee.getSource()).thenReturn(shooter);
        lenient().when(melee.isOf(any())).thenReturn(false);
        lenient().when(melee.isIn(any())).thenReturn(false);

        EntityDamageListener.onModifyAppliedDamage(target, melee, 6F);

        verify(pet, never()).setTarget(any());
    }

    /** A sitting pet stays sitting — "sit" is an explicit order and a bow shot does not override it. */
    @Test
    void aSittingPetIsNotSicced() {
        lenient().when(pet.isSitting()).thenReturn(true);
        final Zombie target = zombie();

        EntityDamageListener.onModifyAppliedDamage(target, thrownBy(Arrow.class), 6F);

        verify(pet, never()).setTarget(any());
    }

    // --- fixture --------------------------------------------------------------------------------

    private static Zombie zombie() {
        final Zombie zombie = mock(Zombie.class);
        lenient().when(zombie.getUuid()).thenReturn(UUID.randomUUID());
        return zombie;
    }

    /** A damage source whose direct damager is a projectile of {@code type}, thrown by the shooter. */
    private <T extends Projectile> DamageSource thrownBy(Class<T> type) {
        final T projectile = mock(type);
        lenient().when(projectile.getOwner()).thenReturn(shooter);
        return sourceOf(projectile);
    }

    private DamageSource sourceOf(Entity projectile) {
        final DamageSource source = mock(DamageSource.class);
        lenient().when(source.getAttacker()).thenReturn(shooter);
        lenient().when(source.getSource()).thenReturn(projectile);
        lenient().when(source.isOf(any())).thenReturn(false);
        lenient().when(source.isIn(any())).thenReturn(false);
        return source;
    }
}
