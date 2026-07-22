package com.gmail.nossr50.fabric;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.fabric.mixin.BrewingStandBrewTimeAccessor;
import com.gmail.nossr50.util.McTestRegistries;
import java.util.Arrays;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.screen.slot.FurnaceOutputSlot;
import net.minecraft.world.explosion.ExplosionImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves mcMMO's mixins actually apply to their targets — that every injection point still resolves
 * against this Minecraft version.
 *
 * <p>This exists because a boot smoke-test <i>cannot</i> prove it for every mixin. Mixins apply
 * lazily, when their target class is first loaded, so a bad injection surfaces as a crash at the
 * moment of first use rather than at startup. {@link ExplosionImpl} is the case in point: nothing
 * loads it during server boot, so the first creeper in a live world would be the first thing to find
 * out. Loading the class here, under the same Knot classloader the mod runs on, forces Mixin to
 * apply and throw ({@code InvalidInjectionException}) if a target has drifted.
 *
 * <p>{@code TntExplodeMixin}'s target ({@code TntEntity}) does load during boot, so it is covered by
 * the smoke-test; the classes whose mixins are proven only here are the ones worth listing.
 *
 * <p>Note this test deliberately does <b>not</b> live in {@code com.gmail.nossr50.fabric.mixin}:
 * that package is {@code mcmmo.mixins.json}'s declared mixin package, so Mixin would try to treat
 * the test class itself as a mixin and fail to transform it.
 */
class MixinApplicationTest {

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    @Test
    void blastMiningExplosionMixinApplies() {
        // Class-loading ExplosionImpl is what triggers mixin application: if either injection in
        // ExplosionDropsMixin (the destroyBlocks HEAD hook, or the onExploded drop-collector arg)
        // no longer matches, this throws rather than silently no-op'ing.
        assertDoesNotThrow(() -> Class.forName(ExplosionImpl.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        // ...and prove the mixin was really applied, rather than the class merely loading: the
        // @Unique flag ExplosionDropsMixin adds only exists on a transformed ExplosionImpl.
        final boolean hasMixinField = Arrays.stream(ExplosionImpl.class.getDeclaredFields())
                .anyMatch(field -> field.getName().contains("blastMiningHandled"));
        assertTrue(hasMixinField,
                "ExplosionDropsMixin did not apply to ExplosionImpl — its blast-mining drop "
                        + "replacement would silently never run in-game");
    }

    @Test
    void projectileSpawnMixinApplies() {
        // ProjectileSpawnMixin injects into the four-argument ProjectileEntity#spawn static — the
        // funnel every projectile spawn goes through. It adds no field to assert on (it is a pure
        // @Inject), so class-loading is the whole test: with defaultRequire=1, a spawn signature that
        // has drifted fails the injection and throws here rather than silently costing Archery its
        // Arrow Retrieval marks in-game.
        assertDoesNotThrow(() -> Class.forName(ProjectileEntity.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));
    }

    @Test
    void bowShootMixinApplies() {
        // BowShootMixin injects at HEAD and RETURN of BowItem#onStoppedUsing to capture the bow's draw
        // force for Archery's force-scaled XP. It is a pure @Inject with no field to assert on, so
        // class-loading BowItem is the whole test: with defaultRequire=1, an onStoppedUsing signature
        // that has drifted fails the injection and throws here rather than silently costing every bow
        // shot its force multiplier in-game.
        assertDoesNotThrow(() -> Class.forName(BowItem.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));
    }

    @Test
    void blockPlaceMixinApplies() {
        // BlockPlaceMixin injects at RETURN of the inner BlockItem#place(ItemPlacementContext,
        // BlockState)Z to mark hand-placed blocks ineligible for gathering rewards (§A). It is a pure
        // @Inject with no field to assert on, so class-loading BlockItem is the whole test: with
        // defaultRequire=1, a place signature that has drifted fails the injection and throws here
        // rather than silently letting placed-block XP farming back in-game.
        assertDoesNotThrow(() -> Class.forName(BlockItem.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));
    }

    @Test
    void fishingBobberMixinsApply() {
        // Unlike the cases above, class-loading is NOT the test here: EntityType's static init already
        // loads FishingBobberEntity during McTestRegistries.bootstrap(), so by now the class is
        // transformed (or the failure has already surfaced as an error in @BeforeAll). What is worth
        // asserting is that the Master Angler @Redirect actually bound — an applied @Redirect leaves
        // its handler method on the transformed target.
        final boolean hasRedirect = Arrays.stream(FishingBobberEntity.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("masterAnglerWaitCountdown"));
        assertTrue(hasRedirect,
                "FishingWaitTimeMixin did not apply to FishingBobberEntity — Master Angler would "
                        + "silently never reduce the bite wait in-game");

        // The binding *count* is guarded in the mixin itself (allow = 1), because tickFishingLogic
        // makes three MathHelper#nextInt calls and a slice that fails to resolve is silently dropped
        // rather than raised — see FishingWaitTimeMixin's class doc for the mutation that proved it.

        // Same reasoning for the Shake @Inject on FishingBobberUseMixin: an applied @Inject leaves its
        // handler on the target, so its absence means reeling in a hooked mob would silently never
        // shake anything loose.
        final boolean hasShakeHook = Arrays.stream(FishingBobberEntity.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("onEntityHooked"));
        assertTrue(hasShakeHook,
                "FishingBobberUseMixin's Shake injector did not apply to FishingBobberEntity — the "
                        + "Shake sub-skill would silently never fire in-game");
    }

    @Test
    void brewingStandMixinsApply() {
        // Nothing during boot loads BrewingStandBlockEntity, so class-loading it here is what forces
        // both of its mixins to apply: the canCraft/craft/tick injections (mcMMO's brewing takeover
        // plus the Catalysis speed-up) and the brewTime accessor.
        assertDoesNotThrow(() -> Class.forName(BrewingStandBlockEntity.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        // An applied accessor mixin makes the target implement the interface — and without it,
        // AlchemyListener.applyCatalysis would ClassCastException on the first brewing-stand tick
        // rather than fail quietly.
        assertTrue(BrewingStandBrewTimeAccessor.class.isAssignableFrom(BrewingStandBlockEntity.class),
                "BrewingStandBrewTimeAccessor did not apply to BrewingStandBlockEntity — Catalysis "
                        + "could not read or shorten a brew timer in-game");

        // The tick hook is a pure @Inject with no field to assert on, but an applied @Inject leaves
        // its handler method on the transformed target.
        final boolean hasCatalysisHook = Arrays.stream(
                        BrewingStandBlockEntity.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("applyCatalysisBrewSpeed"));
        assertTrue(hasCatalysisHook,
                "BrewingStandBlockEntityMixin's Catalysis injector did not apply to "
                        + "BrewingStandBlockEntity — every brew would run at vanilla speed in-game");
    }

    @Test
    void furnaceMixinApplies() {
        // Three of the four Smelting hooks ride AbstractFurnaceBlockEntity#tick, and each is anchored
        // on a different call inside it, so they drift independently. The fourth sits on the private
        // static dropExperience. Class-loading forces application; the per-handler assertions below
        // are what prove each one actually bound.
        assertDoesNotThrow(() -> Class.forName(AbstractFurnaceBlockEntity.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        final var methods = Arrays.stream(AbstractFurnaceBlockEntity.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertTrue(methods.stream().anyMatch(name -> name.contains("onSmeltComplete")),
                "the craftRecipe-anchored injector did not apply — a finished smelt would award no "
                        + "Smelting XP in-game");
        assertTrue(methods.stream().anyMatch(name -> name.contains("onSecondSmelt")),
                "the setLastRecipe-anchored injector did not apply — Second Smelt would silently "
                        + "never grant its extra item in-game");
        assertTrue(methods.stream().anyMatch(name -> name.contains("applyFuelEfficiency")),
                "the getFuelTime modifier did not apply — Fuel Efficiency would silently leave every "
                        + "furnace at vanilla burn times in-game");
        assertTrue(methods.stream().anyMatch(name -> name.contains("boostVanillaXp")),
                "the dropExperience orb-size modifier did not apply — Understanding the Art would "
                        + "silently leave furnace XP at vanilla amounts in-game");
    }

    @Test
    void furnaceOutputSlotMixinApplies() {
        // The other half of Understanding the Art: nothing during boot loads FurnaceOutputSlot, so
        // class-loading it here is what forces its mixin to apply. Both handlers are asserted because
        // they are separate injections — losing the RETURN one alone would leak the multiplier onto
        // the next furnace extraction on the same thread.
        assertDoesNotThrow(() -> Class.forName(FurnaceOutputSlot.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        final var methods = Arrays.stream(FurnaceOutputSlot.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertTrue(methods.stream().anyMatch(name -> name.contains("beginFurnaceExtract")),
                "FurnaceOutputSlotMixin's HEAD injector did not apply — no extraction would ever "
                        + "carry an Understanding the Art multiplier in-game");
        assertTrue(methods.stream().anyMatch(name -> name.contains("endFurnaceExtract")),
                "FurnaceOutputSlotMixin's RETURN injector did not apply — the multiplier would "
                        + "leak past the extraction that set it");
    }
}
