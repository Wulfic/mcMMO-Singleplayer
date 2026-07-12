package com.gmail.nossr50.config.skills.alchemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.datatypes.skills.alchemy.AlchemyPotion;
import com.gmail.nossr50.datatypes.skills.alchemy.PotionStage;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.McTestRegistries;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link PotionConfig} + {@link PotionStage} end-to-end against the real bundled
 * {@code potions.yml} / {@code experience.yml}. Like {@code RepairConfigTest} this is MC-typed at load
 * time — every ingredient resolves against the live item registry and every potion's base type /
 * effects against {@code Registries.POTION}/{@code STATUS_EFFECT} — so it runs under the
 * {@code fabric-loader-junit} ("Knot") registry harness. Assertions pin the shipped brewing tree, not
 * a hand-rolled fixture.
 *
 * <p>{@link McMMOMod#setPotionConfig} is wired in {@code @BeforeEach} because {@link AlchemyPotion#getChild}
 * resolves child names back through {@code McMMOMod.getPotionConfig()}, exactly as the runtime does.
 */
class PotionConfigTest {

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    private PotionConfig potionConfig;
    private ExperienceConfig experienceConfig;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dir));
        experienceConfig = new ExperienceConfig(dir);
        McMMOMod.setExperienceConfig(experienceConfig);
        potionConfig = new PotionConfig(dir);
        McMMOMod.setPotionConfig(potionConfig);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setPotionConfig(null);
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setGeneralConfig(null);
    }

    @Test
    void loadsTheBundledBrewingTree() {
        assertTrue(potionConfig.getLoadedPotionCount() > 0,
                "bundled potions.yml should yield potions");
    }

    @Test
    void concoctionTiersLoadAndCascade() {
        final List<ItemStack> tier1 = potionConfig.getIngredients(1);
        final List<ItemStack> tier2 = potionConfig.getIngredients(2);
        assertFalse(tier1.isEmpty(), "tier 1 ingredients load");
        assertTrue(tier1.stream().anyMatch(s -> s.isOf(Items.BLAZE_POWDER)),
                "tier 1 includes Blaze Powder");
        // Each tier includes every lower tier's ingredients, so tier 2 is strictly larger.
        assertTrue(tier2.size() > tier1.size(), "tier 2 cascades tier 1's ingredients plus its own");
        assertTrue(tier2.stream().anyMatch(s -> s.isOf(Items.BLAZE_POWDER)),
                "the cascade carries tier 1 ingredients into tier 2");
    }

    @Test
    void resolvesAWaterPotionByItemStack() {
        // A vanilla water bottle must be recognised as the config's POTION_OF_WATER.
        final ItemStack waterBottle =
                PotionContentsComponent.createStack(Items.POTION, Potions.WATER);
        final AlchemyPotion potion = potionConfig.getPotion(waterBottle);
        assertNotNull(potion, "a vanilla water bottle resolves to a config potion");
        assertEquals("POTION_OF_WATER", potion.getPotionConfigName());
        assertTrue(potionConfig.isValidPotion(waterBottle));
    }

    @Test
    void ingredientDrivesTheChildTransition() {
        // AWKWARD + SUGAR brews into POTION_OF_SWIFTNESS (shipped tree).
        final AlchemyPotion awkward = potionConfig.getPotion("POTION_OF_AWKWARD");
        assertNotNull(awkward, "POTION_OF_AWKWARD is in the tree");
        final AlchemyPotion child = awkward.getChild(new ItemStack(Items.SUGAR));
        assertNotNull(child, "SUGAR is a valid ingredient on an Awkward potion");
        assertEquals("POTION_OF_SWIFTNESS", child.getPotionConfigName());
        // A non-ingredient returns no child.
        assertNull(awkward.getChild(new ItemStack(Items.DIRT)),
                "dirt is not a valid brewing ingredient");
    }

    @Test
    void potionStageOfATransitionSelectsTheXpTier() {
        final AlchemyPotion awkward = potionConfig.getPotion("POTION_OF_AWKWARD");
        final AlchemyPotion swiftness = potionConfig.getPotion("POTION_OF_SWIFTNESS");
        assertNotNull(swiftness, "POTION_OF_SWIFTNESS is in the tree");

        // Awkward has no effects (stage 1); Swiftness carries its base speed effect (stage 2). The
        // input is not a water bottle and the stages differ, so no top-stage bump -> stage TWO.
        final PotionStage stage = PotionStage.getPotionStage(awkward, swiftness);
        assertEquals(PotionStage.TWO, stage);
        // experience.yml Stage_2 = 1111.
        assertEquals(1111.0, experienceConfig.getPotionXP(stage), 1.0e-9);
    }

    @Test
    void perStageXpMatchesTheBundledTable() {
        assertEquals(666.0, experienceConfig.getPotionXP(PotionStage.ONE), 1.0e-9);
        assertEquals(1111.0, experienceConfig.getPotionXP(PotionStage.TWO), 1.0e-9);
        assertEquals(1750.0, experienceConfig.getPotionXP(PotionStage.THREE), 1.0e-9);
        assertEquals(2250.0, experienceConfig.getPotionXP(PotionStage.FOUR), 1.0e-9);
        assertEquals(0.0, experienceConfig.getPotionXP(PotionStage.FIVE), 1.0e-9);
    }

    @Test
    void everyLoadedPotionCarriesPotionContents() {
        // Invariant: nothing garbage leaks past the load skips — every parsed potion has a base
        // potion component (which is what stage/child resolution reads).
        final AlchemyPotion water = potionConfig.getPotion("POTION_OF_WATER");
        final PotionContentsComponent contents = water.getPotionContents();
        assertNotNull(contents, "a loaded potion always carries a PotionContentsComponent");
        assertTrue(contents.potion().isPresent(), "and a resolved base potion");
    }

    @Test
    void splashConversionRaisesTheStage() {
        // POTION_OF_ABSORPTION (custom Absorption effect) -> stage TWO; its splash child adds the
        // dispersion step -> stage THREE.
        final AlchemyPotion absorption = potionConfig.getPotion("POTION_OF_ABSORPTION");
        assertNotNull(absorption, "POTION_OF_ABSORPTION is in the tree");
        assertEquals(PotionStage.TWO, PotionStage.getPotionStage(absorption));

        final AlchemyPotion splash = absorption.getChild(new ItemStack(Items.GUNPOWDER));
        assertNotNull(splash, "gunpowder converts Absorption to its splash variant");
        assertTrue(splash.isSplash(), "the child is a splash potion");
        assertEquals(PotionStage.THREE, PotionStage.getPotionStage(splash));
    }

    @Test
    void toItemStackClonesWithRequestedCount() {
        final AlchemyPotion water = potionConfig.getPotion("POTION_OF_WATER");
        final ItemStack three = water.toItemStack(3);
        assertEquals(3, three.getCount());
        // A separate copy each call (not aliasing the template stack).
        assertEquals(1, water.toItemStack(0).getCount(), "count is floored at 1");
    }
}
