package com.gmail.nossr50.config.treasure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.treasure.EnchantmentTreasure;
import com.gmail.nossr50.datatypes.treasure.FishingTreasure;
import com.gmail.nossr50.datatypes.treasure.FishingTreasureBook;
import com.gmail.nossr50.datatypes.treasure.ItemSpec;
import com.gmail.nossr50.datatypes.treasure.Rarity;
import com.gmail.nossr50.datatypes.treasure.ShakeTreasure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link FishingTreasureConfig} against the real bundled {@code fishing_treasures.yml}. The
 * config is MC-free (materials kept as {@code ItemSpec} blueprints, resolved at spawn time), so this
 * runs in plain JUnit with no registry harness — matching {@link TreasureConfigTest}.
 */
class FishingTreasureConfigTest {

    private static FishingTreasure findByMaterial(List<FishingTreasure> list, String id) {
        return list.stream().filter(t -> t.getDrop().getMaterialId().equals(id)).findFirst()
                .orElse(null);
    }

    /** The configured level for an enchantment path in a band, or {@code -1} when it isn't there. */
    private static int levelOf(List<EnchantmentTreasure> band, String enchantmentId) {
        return band.stream().filter(t -> t.enchantmentId().equals(enchantmentId))
                .mapToInt(EnchantmentTreasure::level).findFirst().orElse(-1);
    }

    private static boolean anyBucketHas(FishingTreasureConfig config, String id) {
        return config.fishingRewards.values().stream()
                .flatMap(List::stream)
                .anyMatch(t -> t.getDrop().getMaterialId().equals(id));
    }

    @Test
    void writesDefaultToDiskWhenMissing(@TempDir Path dataFolder) {
        new FishingTreasureConfig(dataFolder);
        assertTrue(Files.exists(dataFolder.resolve("fishing_treasures.yml")));
    }

    @Test
    void everyRarityBucketIsInitialised(@TempDir Path dataFolder) {
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);
        for (Rarity rarity : Rarity.values()) {
            assertNotNull(config.fishingRewards.get(rarity), rarity + " reward bucket must exist");
            assertNotNull(config.fishingEnchantments.get(rarity),
                    rarity + " enchantment bucket must exist");
        }
    }

    @Test
    void loadsFishingRewardsBucketedByRarity(@TempDir Path dataFolder) {
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        // leather_boots is a COMMON reward; netherite_sword is MYTHIC.
        assertNotNull(findByMaterial(config.fishingRewards.get(Rarity.COMMON), "leather_boots"),
                "leather_boots should be a COMMON fishing reward");
        assertNotNull(findByMaterial(config.fishingRewards.get(Rarity.MYTHIC), "netherite_sword"),
                "netherite_sword should be a MYTHIC fishing reward");
        // netherite_scrap is a LEGENDARY reward.
        assertNotNull(findByMaterial(config.fishingRewards.get(Rarity.LEGENDARY), "netherite_scrap"),
                "netherite_scrap should be a LEGENDARY fishing reward");
    }

    @Test
    void parsesRewardFieldsIntoItemSpec(@TempDir Path dataFolder) {
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        // LAPIS_LAZULI: COMMON, 200 XP, a stack of 20.
        final FishingTreasure lapis = findByMaterial(config.fishingRewards.get(Rarity.COMMON),
                "lapis_lazuli");
        assertNotNull(lapis, "lapis_lazuli should be a COMMON fishing reward");
        assertEquals(20, lapis.getDrop().getAmount());
        assertEquals(200, lapis.getXp());
        // Fishing rewards carry no drop chance/level of their own (Item_Drop_Rates decides).
        assertEquals(0, lapis.getDropLevel());
        assertEquals(0.0, lapis.getDropChance());
    }

    @Test
    void loadsTheShippedEnchantedBookAsATreasureBook(@TempDir Path dataFolder) {
        // The bundled config ships a LEGENDARY ENCHANTED_BOOK with both enchantment filters commented
        // out, so it loads as a book that may roll anything.
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        final FishingTreasure book = findByMaterial(config.fishingRewards.get(Rarity.LEGENDARY),
                "enchanted_book");
        assertInstanceOf(FishingTreasureBook.class, book,
                "an ENCHANTED_BOOK reward must load as a FishingTreasureBook, not a plain treasure");
        assertEquals(400, book.getXp());
        assertEquals(1, book.getDrop().getAmount(), "a book reward is always a single book");
        assertTrue(((FishingTreasureBook) book).getWhitelistedEnchantmentIds().isEmpty());
        assertTrue(((FishingTreasureBook) book).getBlacklistedEnchantmentIds().isEmpty());
    }

    @Test
    void parsesEnchantedBookFiltersAsRegistryPaths(@TempDir Path dataFolder) throws IOException {
        // Both filters ship commented out, so a hand-written config is the only way to cover them.
        // Amount and Lore are deliberately set to values the loader must ignore for a book (legacy
        // builds it as `new ItemStack(material, 1)` and applies only the custom name).
        Files.writeString(dataFolder.resolve("fishing_treasures.yml"), """
                Fishing:
                    ENCHANTED_BOOK:
                        Amount: 4
                        XP: 250
                        Rarity: EPIC
                        Lore:
                            - '&7ignored for books'
                        Enchantments_Whitelist:
                            - Fortune
                            - Silk_Touch
                        Enchantments_Blacklist:
                            - Vanishing_Curse
                """);

        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);
        final FishingTreasureBook book = (FishingTreasureBook) findByMaterial(
                config.fishingRewards.get(Rarity.EPIC), "enchanted_book");

        assertNotNull(book, "the hand-written ENCHANTED_BOOK must load into its configured rarity");
        assertEquals(Set.of("fortune", "silk_touch"), book.getWhitelistedEnchantmentIds(),
                "filter names must lower-case to registry paths, as the Magic Hunter table does");
        assertEquals(Set.of("vanishing_curse"), book.getBlacklistedEnchantmentIds());
        assertEquals(250, book.getXp());
        assertEquals(1, book.getDrop().getAmount(), "a configured Amount is ignored for books");
        assertTrue(book.getDrop().getLore().isEmpty(), "configured Lore is ignored for books");
    }

    @Test
    void loadsShakeDropsKeyedByEntityRegistryPath(@TempDir Path dataFolder) {
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        // ZOMBIE: a 2% zombie head and 98% rotten flesh, in that config order (the order the
        // cumulative drop-chance walk in FishingManager#rollShakeTreasure depends on).
        final List<ShakeTreasure> zombie = config.getShakeTreasures("zombie");
        assertEquals(2, zombie.size());
        assertEquals("zombie_head", zombie.get(0).getDrop().getMaterialId());
        assertEquals(2.0, zombie.get(0).getDropChance());
        assertEquals("rotten_flesh", zombie.get(1).getDrop().getMaterialId());
        assertEquals(98.0, zombie.get(1).getDropChance());

        // Amount is carried through: a sheep sheds 3 wool, a wither skeleton 2 coal.
        assertEquals(3, config.getShakeTreasures("sheep").get(0).getDrop().getAmount());

        assertTrue(config.getShakeTreasures("bat").isEmpty(),
                "an entity with no Shake section must yield an empty list, not null");
    }

    @Test
    void renamedEntitySectionsAreAliasedToTheirRegistryPaths(@TempDir Path dataFolder) {
        // The shipped config still uses the pre-rename Bukkit names for these three, which is why
        // upstream (which iterates live EntityType.values()) never loads them at all. We alias them.
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        assertFalse(config.getShakeTreasures("mooshroom").isEmpty(),
                "MUSHROOM_COW must alias to mooshroom");
        assertFalse(config.getShakeTreasures("zombified_piglin").isEmpty(),
                "PIG_ZOMBIE must alias to zombified_piglin");
        assertFalse(config.getShakeTreasures("snow_golem").isEmpty(),
                "SNOWMAN must alias to snow_golem");

        // The raw section names are not registry paths, so nothing may be filed under them.
        assertTrue(config.getShakeTreasures("mushroom_cow").isEmpty());
        assertTrue(config.getShakeTreasures("pig_zombie").isEmpty());
        assertTrue(config.getShakeTreasures("snowman").isEmpty());
    }

    /** The {@link ItemSpec.PotionSpec} of the one drop in {@code entity} whose material is {@code id}. */
    private static ItemSpec.PotionSpec potionOf(FishingTreasureConfig config, String entity,
            String id) {
        return config.getShakeTreasures(entity).stream()
                .map(ShakeTreasure::getDrop)
                .filter(spec -> spec.getMaterialId().equals(id))
                .map(ItemSpec::getPotion)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }

    @Test
    void loadsPotionShakeDropsWithTheirBaseType(@TempDir Path dataFolder) {
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        // CAVE_SPIDER ships POTION|0|POISON — the material head is the item, PotionData is the type.
        final ItemSpec.PotionSpec poison = potionOf(config, "cave_spider", "potion");
        assertNotNull(poison, "the cave spider's poison potion must load");
        assertEquals("POISON", poison.potionType());
        assertFalse(poison.upgraded(), "no Upgraded flag is set in the shipped config");
        assertFalse(poison.extended(), "no Extended flag is set in the shipped config");

        // WITCH ships three SPLASH_POTION entries; all three must land on the splash_potion item.
        final List<ShakeTreasure> witch = config.getShakeTreasures("witch");
        final Set<String> witchPotionTypes = witch.stream()
                .map(ShakeTreasure::getDrop)
                .filter(spec -> spec.getPotion() != null)
                .map(spec -> spec.getMaterialId() + "/" + spec.getPotion().potionType())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("splash_potion/INSTANT_HEAL", "splash_potion/FIRE_RESISTANCE",
                "splash_potion/SPEED"), witchPotionTypes);
    }

    @Test
    void nonPotionEntriesCarryNoPotionData(@TempDir Path dataFolder) {
        // Guards the isPotionEntry gate in both directions: reading PotionData for every entry would
        // silently turn ordinary drops into water bottles at spawn time.
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        assertNull(potionOf(config, "witch", "glass_bottle"),
                "a non-potion shake drop must carry no potion base type");
        assertTrue(config.fishingRewards.values().stream().flatMap(List::stream)
                        .allMatch(t -> t.getDrop().getPotion() == null),
                "the shipped Fishing section has no potion rewards");
    }

    @Test
    void inventoryShakeEntryIsSkipped(@TempDir Path dataFolder) {
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        // PLAYER.INVENTORY is legacy's magic-BEDROCK inventory steal — unreachable in singleplayer.
        assertTrue(config.getShakeTreasures("player").stream()
                        .noneMatch(t -> t.getDrop().getMaterialId().equals("inventory")),
                "the INVENTORY steal entry must not load as a material");
    }

    @Test
    void loadsItemDropRatesPerTierAndRarity(@TempDir Path dataFolder) {
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        assertEquals(7.50, config.getItemDropRate(1, Rarity.COMMON));
        assertEquals(0.01, config.getItemDropRate(1, Rarity.MYTHIC));
        assertEquals(7.50, config.getItemDropRate(8, Rarity.EPIC));
    }

    @Test
    void loadsMagicHunterEnchantmentsBucketedByRarity(@TempDir Path dataFolder) {
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        // Enchantments_Rarity.COMMON ships EFFICIENCY: 1; LEGENDARY ships SHARPNESS: 5.
        assertEquals(1, levelOf(config.getEnchantmentTreasures(Rarity.COMMON), "efficiency"));
        assertEquals(5, levelOf(config.getEnchantmentTreasures(Rarity.LEGENDARY), "sharpness"));
        // A multi-word key must lower-case to the vanilla registry path, not stay upper-snake.
        assertEquals(4, levelOf(config.getEnchantmentTreasures(Rarity.EPIC), "bane_of_arthropods"));
        assertEquals(-1, levelOf(config.getEnchantmentTreasures(Rarity.EPIC), "BANE_OF_ARTHROPODS"),
                "entries must be keyed by registry path, never by the config's Bukkit spelling");

        // COMMON is the widest low band; MYTHIC carries the top-level entries.
        assertEquals(14, config.getEnchantmentTreasures(Rarity.COMMON).size());
        assertEquals(1, levelOf(config.getEnchantmentTreasures(Rarity.MYTHIC), "infinity"));
    }

    @Test
    void everyShippedEnchantmentNameLooksLikeARegistryPath(@TempDir Path dataFolder) {
        // The port resolves these against the dynamic enchantment registry at drop time, so a name
        // that isn't a legal lower-case path can never resolve. Guards the toLowerCase mapping.
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        for (Rarity rarity : Rarity.values()) {
            for (EnchantmentTreasure treasure : config.getEnchantmentTreasures(rarity)) {
                assertTrue(treasure.enchantmentId().matches("[a-z0-9_.\\-/:]+"),
                        rarity + " enchantment '" + treasure.enchantmentId()
                                + "' is not a legal registry path");
                assertTrue(treasure.level() > 0, "levels must be positive");
            }
        }
    }

    @Test
    void loadsEnchantmentDropRatesPerTierAndRarity(@TempDir Path dataFolder) {
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        // Enchantment_Drop_Rates is a table of its own — deliberately not Item_Drop_Rates.
        assertEquals(5.00, config.getEnchantmentDropRate(1, Rarity.COMMON));
        assertEquals(0.01, config.getEnchantmentDropRate(1, Rarity.MYTHIC));
        assertEquals(10.0, config.getEnchantmentDropRate(8, Rarity.UNCOMMON));
        assertNotEquals(config.getItemDropRate(1, Rarity.COMMON),
                config.getEnchantmentDropRate(1, Rarity.COMMON),
                "the item and enchant curves must not be read from the same section");
    }
}
