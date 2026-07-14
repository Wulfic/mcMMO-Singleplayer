package com.gmail.nossr50.skills.alchemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.config.skills.alchemy.PotionConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.alchemy.AlchemyPotion;
import com.gmail.nossr50.datatypes.skills.alchemy.PotionStage;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.McTestRegistries;
import java.nio.file.Path;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.collection.DefaultedList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link AlchemyPotionBrewer} — the K7 brew-resolution + inventory-mutation core — against
 * the real bundled {@code potions.yml}. Like {@code PotionConfigTest} this resolves potions/ingredients
 * against the live registries, so it runs under the {@code fabric-loader-junit} ("Knot") harness.
 *
 * <p>Uses the shipped {@code AWKWARD + SUGAR → SWIFTNESS} transition (a stage-2 brew), the same
 * recipe {@code PotionConfigTest} pins, and drives it through a hand-built 5-slot brewing-stand
 * inventory (bottles 0–2, ingredient 3, fuel 4) exactly as the vanilla {@code craft} mixin would.
 */
class AlchemyPotionBrewerTest {

    @BeforeAll
    static void bootstrap() {
        McTestRegistries.bootstrap();
    }

    private PotionConfig potionConfig;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dir));
        McMMOMod.setExperienceConfig(new ExperienceConfig(dir));
        potionConfig = new PotionConfig(dir);
        McMMOMod.setPotionConfig(potionConfig);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setPotionConfig(null);
        McMMOMod.setExperienceConfig(null);
        McMMOMod.setGeneralConfig(null);
    }

    /** A 5-slot brewing stand: an Awkward potion in bottle slot 0 and {@code ingredient} in slot 3. */
    private DefaultedList<ItemStack> awkwardStandWith(ItemStack ingredient) {
        final AlchemyPotion awkward = potionConfig.getPotion("POTION_OF_AWKWARD");
        assertNotNull(awkward, "POTION_OF_AWKWARD is in the bundled tree");
        final DefaultedList<ItemStack> slots = DefaultedList.ofSize(5, ItemStack.EMPTY);
        slots.set(0, awkward.toItemStack(1));
        slots.set(AlchemyPotionBrewer.INGREDIENT_SLOT, ingredient);
        return slots;
    }

    @Test
    void isValidBrewRecognisesAConfiguredTransition() {
        assertTrue(AlchemyPotionBrewer.isValidBrew(awkwardStandWith(new ItemStack(Items.SUGAR))),
                "Awkward + Sugar is a shipped brew");
    }

    @Test
    void isValidBrewRejectsANonIngredient() {
        assertFalse(AlchemyPotionBrewer.isValidBrew(awkwardStandWith(new ItemStack(Items.DIRT))),
                "dirt is not a brewing ingredient");
    }

    @Test
    void isValidBrewRejectsAnEmptyIngredientSlot() {
        assertFalse(AlchemyPotionBrewer.isValidBrew(awkwardStandWith(ItemStack.EMPTY)),
                "no ingredient → not a brew");
    }

    @Test
    void finishBrewingTransformsTheBottleAndConsumesTheIngredient() {
        final DefaultedList<ItemStack> slots = awkwardStandWith(new ItemStack(Items.SUGAR));

        AlchemyPotionBrewer.finishBrewing(slots, null); // unattended brew → no XP, still completes.

        final AlchemyPotion brewed = potionConfig.getPotion(slots.get(0));
        assertNotNull(brewed, "the brewed bottle is still a recognised potion");
        assertEquals("POTION_OF_SWIFTNESS", brewed.getPotionConfigName(),
                "Awkward brewed into Swiftness");
        assertTrue(slots.get(AlchemyPotionBrewer.INGREDIENT_SLOT).isEmpty(),
                "the single sugar was consumed");
    }

    @Test
    void finishBrewingDecrementsAStackedIngredient() {
        final DefaultedList<ItemStack> slots = awkwardStandWith(new ItemStack(Items.SUGAR, 2));

        AlchemyPotionBrewer.finishBrewing(slots, null);

        final ItemStack ingredient = slots.get(AlchemyPotionBrewer.INGREDIENT_SLOT);
        assertTrue(ingredient.isOf(Items.SUGAR), "the ingredient stack survives");
        assertEquals(1, ingredient.getCount(), "exactly one sugar consumed");
    }

    @Test
    void finishBrewingLeavesTheStandUntouchedForANonIngredient() {
        final DefaultedList<ItemStack> slots = awkwardStandWith(new ItemStack(Items.DIRT));

        AlchemyPotionBrewer.finishBrewing(slots, null);

        assertEquals("POTION_OF_AWKWARD", potionConfig.getPotion(slots.get(0)).getPotionConfigName(),
                "the potion is unchanged when the ingredient is not a valid brew");
        assertTrue(slots.get(AlchemyPotionBrewer.INGREDIENT_SLOT).isOf(Items.DIRT),
                "the non-ingredient is not consumed");
    }

    @Test
    void finishBrewingAwardsTheStageXpToTheBrewer() {
        final DefaultedList<ItemStack> slots = awkwardStandWith(new ItemStack(Items.SUGAR));

        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);
        final AlchemyManager alchemyManager = mock(AlchemyManager.class);
        when(mmoPlayer.getAlchemyManager()).thenReturn(alchemyManager);

        AlchemyPotionBrewer.finishBrewing(slots, mmoPlayer);

        // Awkward → Swiftness is a stage-2 brew (see PotionConfigTest); one bottle → amount 1.
        verify(alchemyManager).handlePotionBrewSuccesses(PotionStage.TWO, 1);
    }

    @Test
    void finishBrewingAwardsNoXpForANonBrew() {
        final DefaultedList<ItemStack> slots = awkwardStandWith(new ItemStack(Items.DIRT));

        final McMMOPlayer mmoPlayer = mock(McMMOPlayer.class);

        AlchemyPotionBrewer.finishBrewing(slots, mmoPlayer);

        verifyNoInteractions(mmoPlayer);
    }
}
