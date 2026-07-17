package com.gmail.nossr50.config.treasure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.treasure.FishingTreasure;
import com.gmail.nossr50.datatypes.treasure.Rarity;
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
    void loadsItemDropRatesPerTierAndRarity(@TempDir Path dataFolder) {
        final FishingTreasureConfig config = new FishingTreasureConfig(dataFolder);

        assertEquals(7.50, config.getItemDropRate(1, Rarity.COMMON));
        assertEquals(0.01, config.getItemDropRate(1, Rarity.MYTHIC));
        assertEquals(7.50, config.getItemDropRate(8, Rarity.EPIC));
    }
}
