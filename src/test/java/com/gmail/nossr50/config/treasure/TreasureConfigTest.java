package com.gmail.nossr50.config.treasure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gmail.nossr50.datatypes.treasure.ExcavationTreasure;
import com.gmail.nossr50.datatypes.treasure.HylianTreasure;
import com.gmail.nossr50.datatypes.treasure.Treasure;
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

    private static boolean containsMaterial(List<? extends Treasure> list, String id) {
        return list.stream().anyMatch(t -> t.getDrop().getMaterialId().equals(id));
    }

    private static HylianTreasure findHylian(List<HylianTreasure> list, String id) {
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
    void loadsHylianTreasuresIndexedByDropsFromGroup(@TempDir Path dataFolder) {
        final TreasureConfig config = new TreasureConfig(dataFolder);

        // The three groups the bundled treasures.yml defines.
        assertTrue(config.hylianMap.containsKey("Bushes"), "Bushes is a Hylian group");
        assertTrue(config.hylianMap.containsKey("Flowers"), "Flowers is a Hylian group");
        assertTrue(config.hylianMap.containsKey("Pots"), "Pots is a Hylian group");

        // Bushes drop seeds/cocoa; Flowers drop food; Pots drop valuables (verbatim from treasures.yml).
        assertTrue(containsMaterial(config.getHylianTreasures("Bushes"), "melon_seeds"));
        assertTrue(containsMaterial(config.getHylianTreasures("Bushes"), "pumpkin_seeds"));
        assertTrue(containsMaterial(config.getHylianTreasures("Bushes"), "cocoa_beans"));
        assertTrue(containsMaterial(config.getHylianTreasures("Flowers"), "carrot"));
        assertTrue(containsMaterial(config.getHylianTreasures("Flowers"), "apple"));
        assertTrue(containsMaterial(config.getHylianTreasures("Pots"), "emerald"));
        assertTrue(containsMaterial(config.getHylianTreasures("Pots"), "copper_nugget"));
    }

    @Test
    void parsesHylianTreasureFields(@TempDir Path dataFolder) {
        final TreasureConfig config = new TreasureConfig(dataFolder);

        // COPPER_NUGGET from Pots: 1×, 5 XP, 100% chance, Standard level 0.
        final HylianTreasure copper = findHylian(config.getHylianTreasures("Pots"), "copper_nugget");
        assertNotNull(copper, "Pots should drop a copper_nugget treasure");
        assertEquals(1, copper.getDrop().getAmount());
        assertEquals(5, copper.getXp());
        assertEquals(100.0, copper.getDropChance());
        assertEquals(0, copper.getDropLevel(), "Standard-mode level requirement");

        // MELON_SEEDS from Bushes gives no XP.
        final HylianTreasure melon = findHylian(config.getHylianTreasures("Bushes"), "melon_seeds");
        assertNotNull(melon, "Bushes should drop a melon_seeds treasure");
        assertEquals(0, melon.getXp());
    }

    @Test
    void getHylianTreasuresReturnsEmptyForUnknownGroup(@TempDir Path dataFolder) {
        final TreasureConfig config = new TreasureConfig(dataFolder);
        assertTrue(config.getHylianTreasures("Nonexistent").isEmpty());
    }
}
