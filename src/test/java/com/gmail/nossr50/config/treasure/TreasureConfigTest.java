package com.gmail.nossr50.config.treasure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.treasure.ExcavationTreasure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link TreasureConfig} against the real bundled {@code treasures.yml}. The config
 * service is intentionally un-wired, so {@code McMMOMod.isRetroModeEnabled()} returns its null-safe
 * default (Standard scaling) — the Standard {@code Level_Requirement} values are asserted here.
 */
class TreasureConfigTest {

    private static ExcavationTreasure findByMaterial(List<ExcavationTreasure> list, String id) {
        return list.stream().filter(t -> t.getDrop().getMaterialId().equals(id)).findFirst()
                .orElse(null);
    }

    @Test
    void writesDefaultToDiskWhenMissing(@TempDir Path dataFolder) {
        new TreasureConfig(dataFolder);
        assertTrue(Files.exists(dataFolder.resolve("treasures.yml")));
    }

    @Test
    void loadsExcavationTreasuresIndexedBySourceBlock(@TempDir Path dataFolder) {
        final TreasureConfig config = new TreasureConfig(dataFolder);

        assertTrue(config.excavationMap.containsKey("Mud"), "Mud is a treasure source block");
        assertTrue(config.excavationMap.containsKey("Dirt"), "Dirt is a treasure source block");
        assertTrue(config.excavationMap.getOrDefault("Bedrock", List.of()).isEmpty(),
                "Bedrock yields no treasures");
    }

    @Test
    void parsesTreasureFieldsIntoItemSpecBlueprint(@TempDir Path dataFolder) {
        final TreasureConfig config = new TreasureConfig(dataFolder);

        // HEART_OF_THE_SEA drops only from Mud, Standard level 90, 0.01% chance, 9999 XP.
        final ExcavationTreasure heart = findByMaterial(config.excavationMap.get("Mud"),
                "heart_of_the_sea");
        assertNotNull(heart, "Mud should drop a heart_of_the_sea treasure");
        assertEquals(1, heart.getDrop().getAmount());
        assertEquals(9999, heart.getXp());
        assertEquals(0.01, heart.getDropChance());
        assertEquals(90, heart.getDropLevel(), "Standard-mode level requirement");
        assertNull(heart.getDrop().getCustomName(), "no Custom_Name in the default entry");
        assertTrue(heart.getDrop().getLore().isEmpty(), "no Lore in the default entry");

        // STICK drops a stack of 2.
        final ExcavationTreasure stick = findByMaterial(config.excavationMap.get("Mud"), "stick");
        assertNotNull(stick, "Mud should drop a stick treasure");
        assertEquals(2, stick.getDrop().getAmount());
    }

    @Test
    void hylianTreasuresAreDeferred(@TempDir Path dataFolder) {
        // PORT Phase 10: Hylian Luck loading needs a block-tag adapter; the map stays empty for now.
        final TreasureConfig config = new TreasureConfig(dataFolder);
        assertTrue(config.hylianMap.isEmpty());
    }
}
