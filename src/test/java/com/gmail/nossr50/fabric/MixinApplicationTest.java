package com.gmail.nossr50.fabric;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.fabric.mixin.BrewingStandBrewTimeAccessor;
import com.gmail.nossr50.fabric.mixin.HoeTillingActionsAccessor;
import com.gmail.nossr50.util.McTestRegistries;
import java.util.Arrays;
import java.util.List;
import net.minecraft.advancement.criterion.BredAnimalsCriterion;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.block.spawner.MobSpawnerLogic;
import net.minecraft.block.spawner.TrialSpawnerLogic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.BoggedEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.ArmadilloEntity;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.passive.SnowGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.HoeItem;
import net.minecraft.screen.slot.FurnaceOutputSlot;
import net.minecraft.world.explosion.Explosion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Proves mcMMO's mixins actually apply to their targets — that every injection point still resolves
 * against this Minecraft version.
 *
 * <p>This exists because a boot smoke-test <i>cannot</i> prove it for every mixin. Mixins apply
 * lazily, when their target class is first loaded, so a bad injection surfaces as a crash at the
 * moment of first use rather than at startup. {@link Explosion} is the case in point: nothing
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
        // Class-loading Explosion is what triggers mixin application: if either injection in
        // ExplosionDropsMixin (the destroyBlocks HEAD hook, or the onExploded drop-collector arg)
        // no longer matches, this throws rather than silently no-op'ing.
        assertDoesNotThrow(() -> Class.forName(Explosion.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        // ...and prove the mixin was really applied, rather than the class merely loading: the
        // @Unique flag ExplosionDropsMixin adds only exists on a transformed Explosion.
        final boolean hasMixinField = Arrays.stream(Explosion.class.getDeclaredFields())
                .anyMatch(field -> field.getName().contains("blastMiningHandled"));
        assertTrue(hasMixinField,
                "ExplosionDropsMixin did not apply to Explosion — its blast-mining drop "
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

    /**
     * ⚠️ The mixin whose failure is a <em>gameplay</em> failure, not a missing cosmetic.
     *
     * <p>{@code FireworkRocketEntityMixin} cancels the private {@code explode} for
     * mcMMO's own fireworks. That method deals {@code 5 + 2 × explosions} damage to everything within
     * five blocks, and mcMMO spawns its fireworks at the player's feet — so if this injection ever
     * stops binding, levelling up starts hurting the player instead of congratulating them, and the
     * fireworks still look exactly right while doing it.
     *
     * <p>With {@code defaultRequire=1} an unbound injector throws at class-load, so loading
     * {@code FireworkRocketEntity} is the whole test: a renamed or refactored {@code explode} fails
     * here rather than in someone's world.
     *
     * <p>&#9888;&#9888; {@code explode}'s PARAMETER LIST is not stable across bands — it has been both
     * {@code explode()} and {@code explode(ServerWorld)}. The {@code method} selector matches on name
     * and binds either way, but an {@code @Inject} handler must mirror the target's own parameters
     * exactly, so a mismatch is an {@code InvalidInjectionException} here. That is precisely what this
     * test caught on 2026-08-19, while {@code mixin-allow-audit.py} reported the injector {@code OK}.
     */
    @Test
    void fireworkRocketMixinApplies() {
        assertDoesNotThrow(() -> Class.forName(FireworkRocketEntity.class.getName(), true,
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
    void campfireMixinApplies() {
        // ⚠️ The headless boot proves NOTHING about this one. CampfireBlockEntity is registered
        // through a `CampfireBlockEntity::new` method reference, whose call site links lazily, so the
        // class is never loaded on a server that nobody plays on — the boot log contains the string
        // "campfire" zero times. Class-loading it here is the only thing that forces the mixin to
        // apply, which is also why this test exists rather than a note in the plan.
        assertDoesNotThrow(() -> Class.forName(CampfireBlockEntity.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        // A @ModifyArg that could not bind throws during application, so reaching this line at all is
        // most of the proof; the handler assertion is what survives someone deleting the injector.
        final boolean hasCampfireHook = Arrays.stream(CampfireBlockEntity.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("onCampfireCook"));
        assertTrue(hasCampfireHook,
                "CampfireCookMixin did not apply to CampfireBlockEntity — cooking on a campfire "
                        + "would silently pay no Cooking XP and never offer a Master Chef second "
                        + "helping in-game, while the furnace path kept working perfectly");
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
        // Class-loading is not the test here: bootstrap already loads the entity hierarchy, so the
        // target is transformed by now. An applied @Inject leaves its handler method on the target,
        // and its absence is the failure that matters — both diet sub-skills would silently do
        // nothing on every meal in-game.
        //
        // ⚠️ THE TARGET CLASS IS NOT FIXED ACROSS BANDS, and this assertion named the wrong one until
        // 2026-08-19. Where the consumption logic has been lifted onto the item-data components, the
        // seam is the component's own consume callback; where it has not, it is
        // LivingEntity#eatFood. FoodComponentMixin keeps its name because the HOOK is the same one,
        // not because the target class is — so assert against whichever class the mixin actually
        // names, and re-check this whenever that @Mixin target moves.
        //
        // 🔑 This is the third reflective mixin-application assertion found pointing at a class its
        // mixin no longer targets. Such an assertion COMPILES — it is reflection — so nothing but a
        // suite run catches it, and a red compileTestJava hides the suite entirely.
        final boolean hasConsumeHook = Arrays.stream(LivingEntity.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("onFoodConsumed"));
        assertTrue(hasConsumeHook,
                "FoodComponentMixin did not apply to its target — Farmer's Diet and Fisherman's "
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
    void husbandryShearMixinsApply() {
        // ⚠️⚠️ THE reason this test is per-seam rather than one assertion. Where vanilla routes shear
        // loot through a shared forEachShearedItem funnel, one hook on LivingEntity covers every
        // species at once and this test is a one-liner. THERE IS NO SUCH FUNNEL HERE: each species
        // drops inline by its own route, so the verb is split across three mixins and a miss in any
        // one of them is a silent, partial shortfall rather than a loud total one.

        // Half 1 — the once-per-shear window. Named per species for the same reason
        // bountifulHarvestDurabilitySaveAppliesToEveryShearableItNames is: ShearPayoutMixin lists
        // four classes explicitly, and a renamed or restructured `sheared` on any single one would
        // otherwise leave that animal paying nothing while the other three paid normally.
        for (Class<?> shearable : List.of(SheepEntity.class, MooshroomEntity.class,
                SnowGolemEntity.class, BoggedEntity.class)) {
            assertDoesNotThrow(() -> Class.forName(shearable.getName(), true,
                    MixinApplicationTest.class.getClassLoader()));
            final var methods = Arrays.stream(shearable.getDeclaredMethods())
                    .map(java.lang.reflect.Method::getName)
                    .toList();
            assertTrue(methods.stream().anyMatch(name -> name.contains("beginShear")),
                    "ShearPayoutMixin's HEAD injector did not apply to " + shearable.getSimpleName()
                            + " — shearing it would pay no XP and roll no Bountiful Harvest");
            // HEAD and TAIL are a pair. Losing TAIL is the worse half: the window would stay open
            // past the shear, and EntityShearDropMixin sits on Entity#dropStack — which is how most
            // of the game drops most of its items — so every later drop would double.
            assertTrue(methods.stream().anyMatch(name -> name.contains("endShear")),
                    "ShearPayoutMixin's TAIL injector did not apply to " + shearable.getSimpleName()
                            + " — the bonus window would outlive its shear and double unrelated drops");
        }

        // Half 2 — the per-item bonus, for the three species that do bottom out in dropStack.
        assertDoesNotThrow(() -> Class.forName(Entity.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));
        assertTrue(Arrays.stream(Entity.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().contains("doubleShearDrop")),
                "EntityShearDropMixin did not apply to Entity — Bountiful Harvest would be inert "
                        + "for sheep, snow golems and bogged while still paying their shear XP");

        // Half 3 — the one species that never reaches dropStack at all. MooshroomEntity converts
        // itself to a cow and builds ItemEntity instances directly in a fixed-count loop, so the
        // Entity hook above cannot see it and a separate seam is the only way it is covered.
        assertTrue(Arrays.stream(MooshroomEntity.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().contains("mooshroomBonusMushrooms")),
                "MooshroomShearDropsMixin did not apply to MooshroomEntity — Bountiful Harvest "
                        + "would be inert for the one shearable that bypasses the shared drop path");
    }

    @Test
    void husbandryBrushMixinApplies() {
        // 🔑 The dispenser exclusion for brushing IS this injection point, on this band. Where
        // vanilla exposes a forEachBrushedItem funnel, that funnel takes the brushing entity and
        // vanilla's armadillo-brushing dispenser (DispenserBehavior$5) passes null, so the exclusion
        // is a property of the signature and nothing here could break it. There is no funnel here:
        // brushScute() takes no arguments, so the gate is that ArmadilloBrushMixin hangs off
        // interactMob — which only a player reaches and the dispenser never enters.
        //
        // ⚠️ That makes this test load-bearing in a way its sibling on other bands is not: if the
        // hook ever drifted onto brushScute itself, an AFK brush farm would start paying and no unit
        // test could see it, because the listener's own gate (a real ServerPlayerEntity) would pass.
        assertDoesNotThrow(() -> Class.forName(ArmadilloEntity.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        final var methods = Arrays.stream(ArmadilloEntity.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();
        assertTrue(methods.stream().anyMatch(name -> name.contains("onBrushedItems")),
                "ArmadilloBrushMixin did not apply to ArmadilloEntity — brushing would pay nothing "
                        + "and Bountiful Harvest's second scute would never drop");
        assertTrue(methods.stream().anyMatch(name -> name.contains("saveBrushDurability")),
                "ArmadilloBrushMixin's durability save did not apply — worth 16 of a brush's 64, so "
                        + "a quarter of the tool per use silently stops being saved");
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
        // one of the most-built grinders in the game.
        //
        // 🔑🔑 AND WHY THERE ARE FOUR SEAMS HERE RATHER THAN ONE. Where the game exposes a
        // create(World, SpawnReason) factory, every spawn chain bottoms out in it and no subclass can
        // dodge it, so one hook covers the lot. THAT METHOD DOES NOT EXIST ON THIS BAND and nothing
        // single replaces it: spawners reach loadEntityWithPassengers, which carries no SpawnReason
        // parameter at all, and breeding reaches create(World). Only egg, dispenser and portal spawns
        // reach the 6-arg create.
        //
        // ⚠️⚠️ THE 6-ARG create DOES EXIST, so a mixin scoped to it BINDS — mixin-allow-audit reports
        // OK, the boot smoke-test passes, and spawner-farmed and bred mobs go silently unmarked. That
        // is strictly worse than the missing injection it replaced, because a missing injection is a
        // load-time failure and this is nothing at all. A per-seam assertion is the ONLY thing that
        // can see it, which is exactly why the four are named individually below.
        assertDoesNotThrow(() -> Class.forName(EntityType.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));
        assertTrue(Arrays.stream(EntityType.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().contains("stampSpawnOrigin")),
                "EntityTypeSpawnOriginMixin did not apply to EntityType — egg-placed, dispenser-"
                        + "spawned and portal-spawned mobs would go unmarked and count toward Hunter "
                        + "mastery");

        // The spawner half, and the whole reason this test grew: a mob spawner is the single most
        // farmed source of kills in the game, and its chain never touches EntityType#create here.
        assertDoesNotThrow(() -> Class.forName(MobSpawnerLogic.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));
        assertTrue(Arrays.stream(MobSpawnerLogic.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().contains("stampSpawnerOrigin")),
                "MobSpawnerOriginMixin did not apply to MobSpawnerLogic — every mob-spawner grinder "
                        + "in the world would count toward Hunter mastery while the gate looked "
                        + "present and every other origin stayed marked");

        assertDoesNotThrow(() -> Class.forName(TrialSpawnerLogic.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));
        assertTrue(Arrays.stream(TrialSpawnerLogic.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().contains("stampTrialSpawnerOrigin")),
                "TrialSpawnerOriginMixin did not apply to TrialSpawnerLogic — trial-chamber mobs "
                        + "would count toward Hunter mastery");

        // Breeding reaches create(World), not the 6-arg create, so it needs its own seam. AnimalEntity
        // is loaded by EntityType's static init during bootstrap, so class-loading proves nothing
        // here; the handler does.
        assertTrue(Arrays.stream(AnimalEntity.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().contains("stampBredOrigin")),
                "AnimalBreedOriginMixin did not apply to AnimalEntity — a bred animal would count "
                        + "toward Hunter mastery, which is the cheapest farm of the four");

        // MobEntity is loaded by EntityType's static init during bootstrap, so class-loading proves
        // nothing; the handler does. Losing this one leaves a narrower but very real hole: a zombie
        // spawner over water launders its origin into drowned that count.
        assertTrue(Arrays.stream(MobEntity.class.getDeclaredMethods())
                        .anyMatch(method -> method.getName().contains("carryOriginThroughConversion")),
                "MobConversionOriginMixin did not apply to MobEntity — a spawner mob would shed its "
                        + "marker the moment it converted, which is exactly how drowned farms work");
    }

    @Test
    void hoeTillingActionsAccessorApplies() {
        // GitHub #1's gate: SuperAbilityListener asks vanilla's own TILLING_ACTIONS table whether a
        // right-click is about to till, so it can suppress the hoe re-ready on a till without
        // breaking the readying gesture. Nothing during boot loads HoeItem, so class-loading it here
        // is what forces the accessor to apply.
        assertDoesNotThrow(() -> Class.forName(HoeItem.class.getName(), true,
                MixinApplicationTest.class.getClassLoader()));

        // An applied accessor mixin makes the target implement the interface. Without it the accessor
        // body throws AssertionError on first use, which on this path is a right-click on dirt — so
        // the failure would be a crash in the player's hand rather than anything visible at boot.
        assertTrue(HoeTillingActionsAccessor.class.isAssignableFrom(HoeItem.class),
                "HoeTillingActionsAccessor did not apply to HoeItem — mcMMO could not tell a till "
                        + "from a hoe-ready, so GitHub #1 (every till re-readies the hoe and burns "
                        + "Green Terra's cooldown by accident) would reopen");

        // The field name is the part that can drift silently: an @Accessor naming a field that no
        // longer exists fails at apply time, but only once something loads the class. Reading it here
        // proves the mapping still resolves and returns vanilla's real table rather than an empty one.
        assertTrue(HoeTillingActionsAccessor.getTillingActions().containsKey(Blocks.GRASS_BLOCK),
                "TILLING_ACTIONS no longer holds grass_block — the accessor resolved to the wrong "
                        + "field or an empty map, and every till would re-ready the hoe again");
    }

    @Test
    void husbandryMilkMixinAppliesToEveryMilkableSpecies() {
        // ⚠️ THERE IS NO MILKING FUNNEL, so CowMilkMixin names its targets explicitly and this test is
        // the only thing standing between that list and a silent shortfall.
        //
        // GoatEntity is why it exists. It extends AnimalEntity directly rather than CowEntity
        // and re-implements the entire bucket-for-milk-bucket branch inline in its own interactMob, so
        // the original @Mixin(CowEntity.class) paid ZERO for every goat ever milked — while
        // goats went on paying for breeding, raising and feeding, which is what made it invisible.
        //
        // 🔑 The roster was settled by binary-grepping the extracted 1.21.11 jar for MILK_BUCKET across
        // all 1040 entity classes, NOT from a species list and NOT from method names: javap shows a
        // method where it is DECLARED, which is not where it is reachable. That grep returns exactly
        // three — CowEntity (carrying cow and mooshroom), GoatEntity, and WanderingTraderEntity
        // (a trade offer, not a milking). Re-run it after a version bump; add any new hit here.
        for (Class<?> milkable : List.of(CowEntity.class, GoatEntity.class)) {
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
