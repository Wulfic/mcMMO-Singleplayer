package com.gmail.nossr50.fabric.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.util.McTestRegistries;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code Skills.Fishing.Override_Vanilla_Treasures} — the switch that decides whether mcMMO's
 * Treasure Hunter table is the only source of fished treasure.
 *
 * <p>The key shipped from the port's first commit, had a getter, had a ModMenu switch, and
 * <b>nothing read it</b> until the 2026-08-06 wiring audit. It is the third dead-switch shape found
 * in this codebase and the only one of the three that changes loot.
 *
 * <p>Both directions are asserted deliberately. A one-directional test here would be worthless: the
 * override's "off" behaviour is *do nothing*, which is also what a completely unwired switch does —
 * so proving only that the switch-off case leaves the catch alone proves nothing at all.
 */
class FishingTreasureOverrideTest {

    private GeneralConfig generalConfig;

    @BeforeAll
    static void bootstrapRegistries() {
        McTestRegistries.bootstrap();
    }

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        generalConfig = new GeneralConfig(dataFolder);
        McMMOMod.setGeneralConfig(generalConfig);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
    }

    private static List<ItemStack> caught(ItemStack... stacks) {
        return new ArrayList<>(List.of(stacks));
    }

    @Test
    void theShippedDefaultIsOn() {
        assertTrue(generalConfig.getFishingOverrideTreasures(),
                "config.yml ships Override_Vanilla_Treasures: true — the tests below describe the "
                        + "behaviour players get out of the box");
    }

    @Test
    void vanillaJunkAndTreasureBecomeSalmon() {
        final List<ItemStack> catch_ = caught(new ItemStack(Items.LEATHER_BOOTS));

        FishingListener.overrideVanillaTreasures(catch_);

        assertEquals(1, catch_.size());
        assertTrue(catch_.get(0).isOf(Items.SALMON), "vanilla junk is replaced, not merely dropped");
        assertEquals(1, catch_.get(0).getCount(), "legacy replaced with exactly one salmon");
    }

    /** Legacy's exemption list, verbatim: the four vanilla fish are never touched. */
    @Test
    void theFourVanillaFishAreLeftAlone() {
        final ItemStack cod = new ItemStack(Items.COD);
        final ItemStack salmon = new ItemStack(Items.SALMON);
        final ItemStack tropical = new ItemStack(Items.TROPICAL_FISH);
        final ItemStack puffer = new ItemStack(Items.PUFFERFISH);
        final List<ItemStack> catch_ = caught(cod, salmon, tropical, puffer);

        FishingListener.overrideVanillaTreasures(catch_);

        assertEquals(List.of(cod, salmon, tropical, puffer), catch_,
                "an all-fish catch is passed through by identity — no rebuild, no reordering");
    }

    /**
     * The mixed case is the one that catches a rebuild bug: replacing the collection wholesale must
     * preserve the fish that were already in it, in place.
     */
    @Test
    void aMixedCatchKeepsItsFishAndReplacesTheRest() {
        final ItemStack cod = new ItemStack(Items.COD);
        final List<ItemStack> catch_ = caught(cod, new ItemStack(Items.BOW), new ItemStack(Items.STICK));

        FishingListener.overrideVanillaTreasures(catch_);

        assertEquals(3, catch_.size(), "nothing is lost or duplicated");
        assertEquals(cod, catch_.get(0));
        assertTrue(catch_.get(1).isOf(Items.SALMON));
        assertTrue(catch_.get(2).isOf(Items.SALMON));
    }

    @Test
    void switchingItOffLeavesVanillaLootUntouched() {
        final GeneralConfig off = spy(generalConfig);
        when(off.getFishingOverrideTreasures()).thenReturn(false);
        McMMOMod.setGeneralConfig(off);

        final ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        final List<ItemStack> catch_ = caught(book);

        FishingListener.overrideVanillaTreasures(catch_);

        assertEquals(List.of(book), catch_,
                "with the override off, vanilla's own fishing treasure reaches the player");
    }

    /**
     * An empty stack must survive the rebuild as an empty stack. Vanilla's loot collection is the
     * live list {@code FishingBobberEntity#use} iterates; turning a placeholder into a salmon would
     * conjure an item out of nothing.
     */
    @Test
    void emptyStacksAreNotTurnedIntoFish() {
        final List<ItemStack> catch_ = caught(ItemStack.EMPTY);

        FishingListener.overrideVanillaTreasures(catch_);

        assertEquals(List.of(ItemStack.EMPTY), catch_);
    }
}
