package com.gmail.nossr50.util.experience;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.entity.boss.BossBar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pins the legacy-name -> vanilla-enum mapping in {@link ExperienceBarWrapper}: the bit of the
 * boss-bar port with real branching (Bukkit's {@code BarStyle.SEGMENTED_n} becomes vanilla
 * {@code NOTCHED_n}, {@code SOLID} becomes {@code PROGRESS}) and the fallbacks for a bad config value.
 *
 * <p>Runs under the {@code fabric-loader-junit} registry harness because touching
 * {@link BossBar.Color}/{@link BossBar.Style} loads the vanilla entity/boss classes.
 */
class ExperienceBarWrapperTest {

    @BeforeAll
    static void bootstrapRegistries() {
        com.gmail.nossr50.util.McTestRegistries.bootstrap();
    }

    @Test
    void mapsBukkitColorNamesDirectly() {
        assertEquals(BossBar.Color.YELLOW, ExperienceBarWrapper.mapColor("YELLOW"));
        assertEquals(BossBar.Color.PURPLE, ExperienceBarWrapper.mapColor("PURPLE"));
        assertEquals(BossBar.Color.GREEN, ExperienceBarWrapper.mapColor("green"), "case-insensitive");
    }

    @Test
    void unknownColorFallsBackToPink() {
        assertEquals(BossBar.Color.PINK, ExperienceBarWrapper.mapColor("chartreuse"));
    }

    @Test
    void mapsSegmentedStylesToNotched() {
        assertEquals(BossBar.Style.NOTCHED_6, ExperienceBarWrapper.mapStyle("SEGMENTED_6"));
        assertEquals(BossBar.Style.NOTCHED_10, ExperienceBarWrapper.mapStyle("SEGMENTED_10"));
        assertEquals(BossBar.Style.NOTCHED_12, ExperienceBarWrapper.mapStyle("SEGMENTED_12"));
        assertEquals(BossBar.Style.NOTCHED_20, ExperienceBarWrapper.mapStyle("SEGMENTED_20"));
    }

    @Test
    void mapsSolidToProgress() {
        assertEquals(BossBar.Style.PROGRESS, ExperienceBarWrapper.mapStyle("SOLID"));
    }

    @Test
    void unknownStyleFallsBackToNotched6() {
        assertEquals(BossBar.Style.NOTCHED_6, ExperienceBarWrapper.mapStyle("zigzag"));
    }
}
