package com.gmail.nossr50.config.treasure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.treasure.FishingTreasure;
import com.gmail.nossr50.datatypes.treasure.Rarity;
import com.gmail.nossr50.datatypes.treasure.ShakeTreasure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
            assertNotNull(config.fishingRewards.get(rarity), rarity + " bucket must exist");
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
    void enchantedBookRewardIsDeferred(@TempDir Path dataFolder) {
        // The bundled config ships a LEGENDARY ENCHANTED_BOOK, but book rewards need the dynamic
        // enchant registry + K3 enchant-write, so they are skipped by name until that adapter lands.
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);
        assertFalse(anyBucketHas(config, "enchanted_book"),
                "enchanted_book reward should be deferred, not loaded into any bucket");
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

    @Test
    void potionAndInventoryShakeEntriesAreSkipped(@TempDir Path dataFolder) {
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        // CAVE_SPIDER ships a POTION|0|POISON entry; ItemSpec carries no potion base type yet.
        assertTrue(config.getShakeTreasures("cave_spider").stream()
                        .noneMatch(t -> t.getDrop().getMaterialId().contains("potion")),
                "potion shake drops must be deferred, not loaded as a bogus material");
        // WITCH is mostly splash potions — its non-potion drops must still load.
        assertFalse(config.getShakeTreasures("witch").isEmpty(),
                "the witch's non-potion drops must survive the potion skip");
        assertTrue(config.getShakeTreasures("witch").stream()
                .noneMatch(t -> t.getDrop().getMaterialId().contains("potion")));

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
}
