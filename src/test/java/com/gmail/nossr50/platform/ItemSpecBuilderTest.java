package com.gmail.nossr50.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.config.treasure.FishingTreasureConfig;
import com.gmail.nossr50.datatypes.treasure.ItemSpec;
import com.gmail.nossr50.datatypes.treasure.ShakeTreasure;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link ItemSpecBuilder}'s potion arm — the point where the MC-free {@code PotionData}
 * strings a treasure config carries finally meet {@code Registries.POTION}. Runs under the
 * {@code fabric-loader-junit} ("Knot") registry harness because it resolves real items and potions.
 *
 * <p>The end-to-end case ({@link #everyShippedShakePotionResolves()}) is the one that matters: it
 * walks the real bundled {@code fishing_treasures.yml} and proves all four shipped potion drops build
 * into live stacks, which is the claim the config's own plain-JUnit test cannot make.
 */
class ItemSpecBuilderTest {

    @BeforeAll
    static void bootstrapRegistries() {
        com.gmail.nossr50.util.McTestRegistries.bootstrap();
    }

    private static PotionContentsComponent contentsOf(ItemStack stack) {
        return stack.get(DataComponentTypes.POTION_CONTENTS);
    }

    @Test
    void buildsAPlainItemWithNoPotionComponent() {
        final Optional<ItemStack> built = ItemSpecBuilder.build(new ItemSpec("heart_of_the_sea", 1));

        assertTrue(built.isPresent());
        assertSame(Items.HEART_OF_THE_SEA, built.get().getItem());
        assertNull(contentsOf(built.get()), "a non-potion spec must not gain potion contents");
    }

    @Test
    void appliesTheBasePotionType() {
        final Optional<ItemStack> built = ItemSpecBuilder.build(new ItemSpec("potion", 1, null,
                List.of(), new ItemSpec.PotionSpec("POISON", false, false)));

        assertTrue(built.isPresent());
        assertSame(Items.POTION, built.get().getItem());
        final PotionContentsComponent contents = contentsOf(built.get());
        assertNotNull(contents, "a potion spec must set POTION_CONTENTS");
        assertTrue(contents.matches(Potions.POISON));
    }

    @Test
    void translatesLegacyBukkitPotionNames() {
        // The shipped config still uses the pre-1.20.5 Bukkit spellings; PotionUtil maps them.
        assertTrue(contentsOf(build("splash_potion", "INSTANT_HEAL", false, false))
                .matches(Potions.HEALING), "INSTANT_HEAL must resolve to healing");
        assertTrue(contentsOf(build("splash_potion", "SPEED", false, false))
                .matches(Potions.SWIFTNESS), "SPEED must resolve to swiftness");
        assertTrue(contentsOf(build("splash_potion", "FIRE_RESISTANCE", false, false))
                .matches(Potions.FIRE_RESISTANCE));
    }

    @Test
    void upgradedAndExtendedSelectTheVariantEntries() {
        // In modern MC these are not flags but distinct registry entries, so the flags must move the
        // lookup onto strong_/long_ ids rather than being silently dropped.
        assertTrue(contentsOf(build("potion", "POISON", true, false)).matches(Potions.STRONG_POISON));
        assertTrue(contentsOf(build("potion", "POISON", false, true)).matches(Potions.LONG_POISON));
        assertTrue(contentsOf(build("potion", "INSTANT_HEAL", true, false))
                .matches(Potions.STRONG_HEALING), "the legacy-name mapping must survive the prefix");
    }

    @Test
    void fallsBackToTheBaseWhenNoVariantExists() {
        // Healing has no long_ variant; legacy resolveVariant fell back to the base rather than
        // rejecting the treasure, so an operator asking for one gets a plain healing potion.
        assertTrue(contentsOf(build("potion", "INSTANT_HEAL", false, true)).matches(Potions.HEALING));
    }

    @Test
    void unresolvablePotionTypeYieldsNothing() {
        // The faithful analogue of legacy rejecting the treasure at config load. Also the mutation
        // lever: returning a water bottle instead would pass every other case here.
        assertTrue(ItemSpecBuilder.build(new ItemSpec("potion", 1, null, List.of(),
                new ItemSpec.PotionSpec("NOT_A_REAL_POTION", false, false))).isEmpty());
    }

    @Test
    void unknownMaterialStillYieldsNothing() {
        assertTrue(ItemSpecBuilder.build(new ItemSpec("definitely_not_an_item", 1)).isEmpty());
    }

    @Test
    void everyShippedShakePotionResolves(@TempDir Path dataFolder) {
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        int potionDrops = 0;
        for (String entity : List.of("cave_spider", "witch")) {
            for (ShakeTreasure treasure : config.getShakeTreasures(entity)) {
                final ItemSpec spec = treasure.getDrop();
                if (spec.getPotion() == null) {
                    continue;
                }
                potionDrops++;
                final Optional<ItemStack> built = ItemSpecBuilder.build(spec);
                assertTrue(built.isPresent(),
                        entity + "'s " + spec.getPotion().potionType() + " potion must build");
                assertNotNull(contentsOf(built.get()), "built potion must carry its contents");
                assertFalse(built.get().isEmpty());
            }
        }
        assertEquals(4, potionDrops,
                "the shipped config has 4 potion shake drops (cave spider 1, witch 3)");
    }

    private static ItemStack build(String materialId, String potionType, boolean upgraded,
            boolean extended) {
        final Optional<ItemStack> built = ItemSpecBuilder.build(new ItemSpec(materialId, 1, null,
                List.of(), new ItemSpec.PotionSpec(potionType, upgraded, extended)));
        assertTrue(built.isPresent(), potionType + " must resolve");
        return built.get();
    }
}
