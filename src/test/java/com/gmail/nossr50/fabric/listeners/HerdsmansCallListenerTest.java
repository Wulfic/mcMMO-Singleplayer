package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The three tool-free super abilities share one {@code UseItemCallback}, so their trigger items are a
 * config-level invariant rather than three independent settings.
 *
 * <p>Run against the <b>real bundled {@code config.yml}</b>, because the shipped values <em>are</em> the
 * invariant: mocking the getters would prove they compile while a copy-paste in the YAML shipped two
 * abilities on one item.
 */
class HerdsmansCallListenerTest {

    private GeneralConfig config;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        config = new GeneralConfig(dataFolder);
        McMMOMod.setGeneralConfig(config);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
    }

    @Test
    void theThreeToolFreeActivesShipOnThreeDifferentItems() {
        // ⚠️⚠️ THE row this test exists for. Second Wind, Smoke Bomb and Herdsman's Call all listen on
        // the same use-item event. A shared item activates whichever registered first and prints
        // another's refusal message, which reads as a broken ability rather than as a config collision
        // — and nothing else in the build would catch it.
        final Set<String> items = Set.of(config.getSecondWindItem(), config.getSmokeBombItem(),
                config.getHerdsmansCallItem());
        assertEquals(3, items.size(),
                "the three actives must ship on three distinct items: " + items);
    }

    @Test
    void theHerdsmansCallItemIsTheShippedGoatHorn() {
        assertEquals("GOAT_HORN", config.getHerdsmansCallItem());
    }

    @Test
    void theTriggerItemIsNotABreedingItem() {
        // Feeding animals is Husbandry's core loop, and a breeding item as the trigger would overload
        // the one click a player makes hundreds of times an hour.
        final String trigger = config.getHerdsmansCallItem();
        for (String breedingItem : Set.of("WHEAT", "CARROT", "SEEDS", "WHEAT_SEEDS", "GOLDEN_CARROT",
                "HAY_BLOCK", "SWEET_BERRIES", "TORCHFLOWER_SEEDS")) {
            assertNotEquals(breedingItem, trigger);
        }
    }
}
