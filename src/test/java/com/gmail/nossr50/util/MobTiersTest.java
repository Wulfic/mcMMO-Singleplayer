package com.gmail.nossr50.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.skills.hunter.HunterManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.Registries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/**
 * D-HU5's tier resolution, driven against the <b>real vanilla entity types</b> rather than against
 * numbers typed into the test.
 *
 * <p>That is the whole value of this file. {@code HunterManagerTest} pins the rule at its boundaries
 * with synthetic inputs, which proves the arithmetic and nothing about whether the rule places actual
 * creatures where the plan says it should. Here a chicken is a real chicken: if Mojang retunes a mob,
 * or a future refactor reads the stats from somewhere subtly different, these assertions move.
 */
class MobTiersTest {

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
    }

    /**
     * Cleared on BOTH sides. {@code McMMOMod}'s config holders are process-wide statics on a JVM
     * JUnit reuses across classes, so a config left behind by another test would decide whether the
     * "derivation alone gets this wrong" assertions below hold — by execution order alone.
     */
    @BeforeEach
    void clearConfigBefore() {
        McMMOMod.setAdvancedConfig(null);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setAdvancedConfig(null);
    }

    /** The shipped {@code advanced.yml}, overrides and all. */
    private void loadShippedConfig(Path dataFolder) {
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));
    }

    // --- the plan's worked examples --------------------------------------------------------------

    @Test
    void theShippedTiersMatchThePlansWorkedExamples(@TempDir Path dataFolder) {
        loadShippedConfig(dataFolder);

        // plans/new-skills/hunter.md, D-HU5's table. If one of these ever has to change, the plan
        // changes with it -- that is the point of naming them here rather than asserting a count.
        assertEquals(1, MobTiers.tierOf(EntityType.CHICKEN));
        assertEquals(1, MobTiers.tierOf(EntityType.COW));
        assertEquals(1, MobTiers.tierOf(EntityType.SHEEP));
        assertEquals(1, MobTiers.tierOf(EntityType.RABBIT));

        assertEquals(2, MobTiers.tierOf(EntityType.ZOMBIE));
        assertEquals(2, MobTiers.tierOf(EntityType.SKELETON));
        assertEquals(2, MobTiers.tierOf(EntityType.CREEPER));
        assertEquals(2, MobTiers.tierOf(EntityType.SPIDER));

        assertEquals(3, MobTiers.tierOf(EntityType.BLAZE));
        assertEquals(3, MobTiers.tierOf(EntityType.WITHER_SKELETON));
        assertEquals(3, MobTiers.tierOf(EntityType.GUARDIAN));
        assertEquals(3, MobTiers.tierOf(EntityType.RAVAGER));

        assertEquals(4, MobTiers.tierOf(EntityType.WITHER));
        assertEquals(4, MobTiers.tierOf(EntityType.ENDER_DRAGON));
        assertEquals(4, MobTiers.tierOf(EntityType.WARDEN));
    }

    @Test
    void theTwoShippedOverridesAreBothLoadBearingAndTheTableIsActuallyConsulted(
            @TempDir Path dataFolder) {
        // ⚠️ THE test in this file, and it asserts BOTH halves on purpose. Without the second half a
        // broken override lookup is invisible; without the first half the entries could be deleted
        // from advanced.yml and nothing would notice, because a test that only sees the shipped
        // config cannot tell "the override worked" from "the rule already got it right".

        // Derivation alone gets both of these wrong, and for the same reason: their danger is not in
        // their attributes. A ghast has 10 health and no ATTACK_DAMAGE attribute at all; a wither
        // skeleton's ATTACK_DAMAGE is the inherited default 2.0, identical to a plain skeleton's.
        assertEquals(2, MobTiers.tierOf(EntityType.GHAST),
                "the derived rule is expected to under-rate the ghast -- that is why it is overridden");
        assertEquals(2, MobTiers.tierOf(EntityType.WITHER_SKELETON),
                "the derived rule is expected to under-rate the wither skeleton");

        loadShippedConfig(dataFolder);

        assertEquals(3, MobTiers.tierOf(EntityType.GHAST));
        assertEquals(3, MobTiers.tierOf(EntityType.WITHER_SKELETON));
    }

    @Test
    void theWitchNeedsNoOverrideBecauseTheRuleAlreadyPlacesItCorrectly() {
        // The plan named the witch as a likely override ("26 HP and barely fights back") alongside
        // the ghast. It is not one: 26 health is below the T3 line, so the rule gets it right for
        // free. Pinned so nobody "completes" the override table by adding it.
        assertEquals(2, MobTiers.tierOf(EntityType.WITCH));
    }

    // --- the reads themselves --------------------------------------------------------------------

    @Test
    void theTierIsReadFromTheSpeciesNotFromTheIndividualThatDied() {
        // ⚠️ The single most important property in this class, and the easiest to lose in a refactor:
        // it would read far more naturally to ask the victim for getMaxHealth(). This zombie is a
        // 500-health boss by every live measure and is still a tier-2 zombie, because tier is a fact
        // about the species that a player can learn -- not about the individual's gear, its rolled
        // health, or the world difficulty it spawned on.
        final ZombieEntity buffed = mock(ZombieEntity.class);
        Mockito.doReturn(EntityType.ZOMBIE).when(buffed).getType();
        lenient().when(buffed.getMaxHealth()).thenReturn(500.0F);

        assertEquals(2, MobTiers.tierOf(buffed));
    }

    @Test
    void theEnderDragonHasNoAttackDamageAttributeAtAllAndStillResolves() {
        // Pins the has() guard in tierOf. The dragon genuinely has no ATTACK_DAMAGE entry -- its
        // 200 health is the only signal available, and reading a missing attribute instead of
        // testing for it is the kind of thing that throws once, in a live world, on a boss fight.
        assertTrue(DefaultAttributeRegistry.hasDefinitionFor(EntityType.ENDER_DRAGON));
        assertEquals(4, MobTiers.tierOf(EntityType.ENDER_DRAGON));
    }

    @Test
    void anIronGolemIsPromotedOutOfTheTrivialTierAndAHorseIsNot() {
        // The non-hostile heavyweight rule, asserted OFF its reference point as well as on it. The
        // iron golem is the only vanilla mob it catches (100 health); a horse is the nearest miss at
        // 53, and a rule written as ">= 50" would silently sweep every horse, donkey, mule and llama
        // in the game into T2.
        assertEquals(2, MobTiers.tierOf(EntityType.IRON_GOLEM));
        assertEquals(1, MobTiers.tierOf(EntityType.HORSE));
        assertEquals(1, MobTiers.tierOf(EntityType.LLAMA));
    }

    @Test
    void aGoldFarmPiglinIsPricedAsACommonHostileAndABlazeIsNot() {
        // The reason DANGEROUS_ATTACK_DAMAGE is 6.0 and not 5.0. A zombified piglin has 5.0 attack
        // damage, and a gold farm is the most-built grinder in the game against which no spawn origin
        // helps -- nether-wastes piglins are legitimately NATURAL. At a 5.0 threshold that farm would
        // pay 800 a kill instead of 300. The blaze (6.0) is what the rule exists to catch, and its
        // farm is spawner-fed, which stage 1 already closes.
        assertEquals(2, MobTiers.tierOf(EntityType.ZOMBIFIED_PIGLIN));
        assertEquals(2, MobTiers.tierOf(EntityType.PIGLIN));
        assertEquals(3, MobTiers.tierOf(EntityType.BLAZE));
    }

    @Test
    void configKeysUseTheHousePerEntityFormNotTheNamespacedId() {
        // Deliberately NOT the "minecraft:wither_skeleton" the kills map uses. This table is hand
        // written in advanced.yml next to experience.yml's Combat.Multiplier, which has used this
        // form for a decade -- and a namespaced key would need quoting in YAML to survive the colon.
        assertEquals("Wither_Skeleton", MobTiers.configKeyOf(EntityType.WITHER_SKELETON));
        assertEquals("Zombie", MobTiers.configKeyOf(EntityType.ZOMBIE));
    }

    // --- completeness ----------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void everyLivingEntityTypeInTheGameResolvesToARealTier(@TempDir Path dataFolder) {
        loadShippedConfig(dataFolder);

        final List<String> broken = new ArrayList<>();
        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            if (!DefaultAttributeRegistry.hasDefinitionFor(type)) {
                continue; // not a living entity: boats, arrows, item frames.
            }
            final int tier = MobTiers.tierOf((EntityType<? extends LivingEntity>) type);
            if (tier < HunterManager.MIN_TIER || tier > HunterManager.MAX_TIER) {
                broken.add(Registries.ENTITY_TYPE.getId(type) + " -> " + tier);
            }
        }

        // The failure this guards is Husbandry's, twice: a species absent from a hand-maintained
        // table resolving to 0 and paying nothing forever, silently. A derived tier cannot do that,
        // and this walks the live registry to prove it -- including any mob a future Minecraft adds.
        assertTrue(broken.isEmpty(), () -> "types outside the tier range: " + broken);
    }

    @Test
    void aTypeWithNoAttributeDefinitionFallsToTheLowestTierItsHostilityAllows() {
        // Fail LOW, never high. Hunter XP is the axis a mob farm attacks, so an unknown creature must
        // be worth a chicken rather than a warden. (No vanilla living type reaches this branch; a
        // mod's could, if it registers an entity without default attributes.)
        assertEquals(1, HunterManager.deriveTier(false, 0.0D, 0.0D));
        assertEquals(2, HunterManager.deriveTier(true, 0.0D, 0.0D));
    }
}
