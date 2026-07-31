package com.gmail.nossr50.fabric;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.fabric.mixin.BrewingStandBrewTimeAccessor;
import com.gmail.nossr50.util.McTestRegistries;
import java.util.Arrays;
import java.util.List;
import net.minecraft.advancement.criterion.BredAnimalsCriterion;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.BoggedEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractCowEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.SnowGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
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

        // Same again for the Treasure Hunter vanilla-XP boost, which rides a @ModifyArg on the
        // ExperienceOrbEntity constructor inside use()'s loot loop. It is capped at allow = 1 because
        // that constructor is invoked exactly once there today — an unconstrained injector would bind
        // to any future orb spawn added to the method.
        final boolean hasVanillaXpHook = Arrays.stream(FishingBobberEntity.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("boostVanillaFishingXp"));
        assertTrue(hasVanillaXpHook,
                "FishingBobberUseMixin's vanilla-XP injector did not apply to FishingBobberEntity — "
                        + "Treasure Hunter would silently leave every catch at vanilla XP in-game");
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
    void livingEntityDamageMixinApplies() {
        // LivingEntity is loaded long before this test runs, so class-loading proves nothing here.
        // What matters is the pre-armour injector: it is the *second* injection on this mixin, added
        // for Unarmored, and it is the one whose absence is invisible. Losing it does not crash and
        // does not stop Unarmored earning — onModifyAppliedDamage falls back to the post-armour
        // amount — so the skill would simply level at a third rate at the diamond tier, which is
        // exactly the symptom the injector exists to prevent and is indistinguishable from a tuning
        // problem in play-testing.
        final boolean hasPreArmorHook = Arrays.stream(LivingEntity.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("capturePreArmorDamage"));
        assertTrue(hasPreArmorHook,
                "LivingEntityDamageMixin's applyArmorToDamage injector did not apply to "
                        + "LivingEntity — Unarmored XP would silently be metered on post-armour "
                        + "damage, so Iron Skin would throttle the skill that grants it");
    }

    @Test
    void foodComponentMixinApplies() {
        // Class-loading is not the test here: Items' static init builds food components during
        // McTestRegistries.bootstrap(), so FoodComponent is already transformed by now. An applied
        // @Inject leaves its handler method on the target, and its absence is the failure that
        // matters — both diet sub-skills would silently do nothing on every meal in-game.
        final boolean hasConsumeHook = Arrays.stream(FoodComponent.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("onFoodConsumed"));
        assertTrue(hasConsumeHook,
                "FoodComponentMixin did not apply to FoodComponent — Farmer's Diet and Fisherman's "
                        + "Diet would silently never restore their extra hunger in-game");
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

    @Test
    void husbandryBreedMixinApplies() {
        // Nothing during boot loads BredAnimalsCriterion, so class-loading it here is what forces the
        // mixin to apply. Both halves matter: the target is disambiguated by a full descriptor
        // (BredAnimalsCriterion inherits a two-arg trigger from AbstractCriterion), so a drifted
        // signature fails the injection here rather than leaving every breeding unpaid in-game.
        assertDoesNotThrow(() -> Class.forName(BredAnimalsCriterion.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        final boolean hasBredHook = Arrays.stream(BredAnimalsCriterion.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("onAnimalsBred"));
        assertTrue(hasBredHook,
                "BredAnimalsCriterionMixin did not apply to BredAnimalsCriterion — Husbandry would "
                        + "silently pay nothing for any breeding in-game, and Twins would never fire");
    }

    @Test
    void husbandryMultiBreedMixinApplies() {
        // AnimalEntity is loaded by EntityType's static init during McTestRegistries.bootstrap(), so
        // class-loading proves nothing; the handler's presence on the transformed target does.
        //
        // Worth stating why the target is lovePlayer and not interactMob: AbstractHorseEntity,
        // CamelEntity, LlamaEntity and PandaEntity all override interactMob and call lovePlayer
        // themselves, so an interactMob hook would leave Multi-Breed dead on four species — horses
        // among them — while passing every test that used a cow.
        final boolean hasLoveHook = Arrays.stream(AnimalEntity.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("onLovePlayer"));
        assertTrue(hasLoveHook,
                "AnimalLovePlayerMixin did not apply to AnimalEntity — Multi-Breed would silently "
                        + "never spread love beyond the one animal a player fed");
    }

    @Test
    void husbandryGrowthMixinApplies() {
        assertDoesNotThrow(() -> Class.forName(PassiveEntity.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        final var methods = Arrays.stream(PassiveEntity.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();

        // The raise verb. Worth stating why the target is setBreedingAge and not onGrowUp, because
        // the plan named onGrowUp and it looks like the obvious choice: HoglinEntity#onGrowUp and
        // GoatEntity#onGrowUp do not call super, so an injection there would have paid exactly zero
        // raise XP for goats and hoglins — both priced — while passing every test written with a cow.
        assertTrue(methods.stream().anyMatch(name -> name.contains("onBreedingAgeChange")),
                "PassiveEntityGrowthMixin's setBreedingAge injector did not apply — no animal would "
                        + "ever pay the raise verb in-game");

        // The feed verb plus Accelerated Growth's double-feed roll. A @ModifyVariable that stopped
        // matching would leave feeding a baby worth nothing and the sub-skill's active half inert.
        assertTrue(methods.stream().anyMatch(name -> name.contains("onGrowthApplied")),
                "PassiveEntityGrowthMixin's growUp injector did not apply — feeding a baby would "
                        + "pay nothing and Accelerated Growth would never double a feed");
    }

    @Test
    void playerInteractMixinApplies() {
        assertDoesNotThrow(() -> Class.forName(PlayerEntity.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        final var methods = Arrays.stream(PlayerEntity.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();

        // HEAD and RETURN are a pair and both are load-bearing. Losing HEAD makes every feed
        // unattributable, so the verb pays nothing; losing RETURN leaves the stash set after the
        // interaction, so the last animal right-clicked would earn feed XP for growth it had no part
        // in — including a lamb eating grass, which is the AFK farm this seam exists to prevent.
        assertTrue(methods.stream().anyMatch(name -> name.contains("beginInteraction")),
                "PlayerEntityInteractMixin's HEAD injector did not apply — Husbandry's feed verb "
                        + "would have no player to credit and would pay nothing");
        assertTrue(methods.stream().anyMatch(name -> name.contains("endInteraction")),
                "PlayerEntityInteractMixin's RETURN injector did not apply — the interaction stash "
                        + "would outlive its interaction and pay for AI-driven growth");
    }

    @Test
    void husbandryShearDropMixinApplies() {
        assertDoesNotThrow(() -> Class.forName(LivingEntity.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        // The shear verb and Bountiful Harvest's bonus drop, both on the shared loot funnel rather
        // than on four per-species interactMob hooks. Worth stating why: 1.21.11 has a FIFTH
        // Shearable (CopperGolemEntity) that the plan's species list never had, and hand-maintained
        // lists are how the previous four seam misses in this skill happened. forEachShearedItem is
        // also what excludes the copper golem for free — it is the one shearable that rolls no loot
        // table, and it can be re-flowered and re-sheared indefinitely.
        final boolean hasShearHook = Arrays.stream(LivingEntity.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("onShearedItems"));
        assertTrue(hasShearHook,
                "LivingEntityShearDropsMixin did not apply to LivingEntity — shearing would pay "
                        + "nothing for every species at once and Bountiful Harvest would be inert");
    }

    @Test
    void hunterTrophyLootMixinApplies() {
        assertDoesNotThrow(() -> Class.forName(LivingEntity.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        // Trophy Hunter's second roll of the creature's own loot table. The injection names the
        // 3-argument dropLoot by full descriptor, so a drift in either overload's signature — or in
        // the pair's relationship, which is the entire reason the re-roll cannot recurse — fails the
        // injection under defaultRequire=1 rather than silently costing the sub-skill its payout.
        final boolean hasTrophyHook = Arrays.stream(LivingEntity.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("trophyHunterBonusRoll"));
        assertTrue(hasTrophyHook,
                "LivingEntityTrophyHunterMixin did not apply to LivingEntity — Trophy Hunter would "
                        + "never roll a bonus trophy and nothing in-game would say so");
    }

    @Test
    void bountifulHarvestDurabilitySaveAppliesToEveryShearableItNames() {
        // ⚠️ THE point of this test. Unlike the XP hook above, the durability save cannot ride a
        // shared funnel — vanilla damages the shears back inside each species' own interactMob —
        // so ShearableInteractMixin names four classes explicitly. Asserting each one individually
        // is what turns "the species list drifted" from a silent shortfall into a red test: a
        // renamed or restructured interactMob on any single species would otherwise leave that
        // animal's shear quietly wearing the tool while the other three did not.
        for (Class<?> shearable : List.of(SheepEntity.class, MooshroomEntity.class,
                SnowGolemEntity.class, BoggedEntity.class)) {
            assertDoesNotThrow(() -> Class.forName(shearable.getName(), true,
                    MixinApplicationTest.class.getClassLoader()));
            final boolean hasSave = Arrays.stream(shearable.getDeclaredMethods())
                    .anyMatch(method -> method.getName().contains("saveShearDurability"));
            assertTrue(hasSave, "ShearableInteractMixin did not apply to " + shearable.getSimpleName()
                    + " — Bountiful Harvest would save no durability when shearing it");
        }
    }

    @Test
    void mobOriginMixinsApply() {
        // Hunter's D-HU1 anti-farm gate. Both halves are pure @Injects, so the handler's presence on
        // the transformed target is the assertion.
        //
        // ⚠️ Worth stating why the target is EntityType#create and not MobEntity#initialize, because
        // initialize is what the plan implied and what both spawner logics visibly call:
        // CaveSpiderEntity#initialize is a bare `return entityData` with no super call, so an
        // injection there would have missed every cave spider — and a mineshaft cave-spider spawner is
        // one of the most-built grinders in the game. create(World, SpawnReason) is the factory all
        // four verified spawn chains bottom out in, on a class with no vanilla subclasses.
        assertDoesNotThrow(() -> Class.forName(EntityType.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));
        assertTrue(Arrays.stream(EntityType.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().contains("stampSpawnOrigin")),
                "EntityTypeSpawnOriginMixin did not apply to EntityType — no mob would ever be "
                        + "marked, so every spawner, bred, egg-placed and portal-spawned mob would "
                        + "count toward Hunter mastery and the whole anti-farm gate would be absent "
                        + "while looking present");

        // MobEntity is loaded by EntityType's static init during bootstrap, so class-loading proves
        // nothing; the handler does. Losing this one leaves a narrower but very real hole: a zombie
        // spawner over water launders its origin into drowned that count.
        assertTrue(Arrays.stream(MobEntity.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().contains("carryOriginThroughConversion")),
                "MobConversionOriginMixin did not apply to MobEntity — a spawner mob would shed its "
                        + "marker the moment it converted, which is exactly how drowned farms work");
    }

    @Test
    void husbandryMilkMixinAppliesToEveryMilkableSpecies() {
        // ⚠️ THERE IS NO MILKING FUNNEL, so CowMilkMixin names its targets explicitly and this test is
        // the only thing standing between that list and a silent shortfall.
        //
        // GoatEntity is why it exists. It extends AnimalEntity directly rather than AbstractCowEntity
        // and re-implements the entire bucket-for-milk-bucket branch inline in its own interactMob, so
        // the original @Mixin(AbstractCowEntity.class) paid ZERO for every goat ever milked — while
        // goats went on paying for breeding, raising and feeding, which is what made it invisible.
        //
        // 🔑 The roster was settled by binary-grepping the extracted 1.21.11 jar for MILK_BUCKET across
        // all 1040 entity classes, NOT from a species list and NOT from method names: javap shows a
        // method where it is DECLARED, which is not where it is reachable. That grep returns exactly
        // three — AbstractCowEntity (carrying cow and mooshroom), GoatEntity, and WanderingTraderEntity
        // (a trade offer, not a milking). Re-run it after a version bump; add any new hit here.
        for (Class<?> milkable : List.of(AbstractCowEntity.class, GoatEntity.class)) {
            assertDoesNotThrow(() -> Class.forName(milkable.getName(), true,
                    MixinApplicationTest.class.getClassLoader()));
            final boolean hasMilkHook = Arrays.stream(milkable.getDeclaredMethods())
                    .anyMatch(method -> method.getName().contains("onMilked"));
            assertTrue(hasMilkHook, "CowMilkMixin did not apply to " + milkable.getSimpleName()
                    + " — milking one would pay no Husbandry XP, roll no Bountiful Harvest bonus and "
                    + "no Hidden Bounty, and skip the D-H5 harvest cooldown entirely");
        }
    }
}
