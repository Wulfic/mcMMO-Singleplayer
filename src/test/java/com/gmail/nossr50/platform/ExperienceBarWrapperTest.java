package com.gmail.nossr50.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.BossEvent;
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
        assertEquals(BossEvent.BossBarColor.YELLOW, ExperienceBarWrapper.mapColor("YELLOW"));
        assertEquals(BossEvent.BossBarColor.PURPLE, ExperienceBarWrapper.mapColor("PURPLE"));
        assertEquals(BossEvent.BossBarColor.GREEN, ExperienceBarWrapper.mapColor("green"), "case-insensitive");
    }

    @Test
    void unknownColorFallsBackToPink() {
        assertEquals(BossEvent.BossBarColor.PINK, ExperienceBarWrapper.mapColor("chartreuse"));
    }

    @Test
    void mapsSegmentedStylesToNotched() {
        assertEquals(BossEvent.BossBarOverlay.NOTCHED_6, ExperienceBarWrapper.mapStyle("SEGMENTED_6"));
        assertEquals(BossEvent.BossBarOverlay.NOTCHED_10, ExperienceBarWrapper.mapStyle("SEGMENTED_10"));
        assertEquals(BossEvent.BossBarOverlay.NOTCHED_12, ExperienceBarWrapper.mapStyle("SEGMENTED_12"));
        assertEquals(BossEvent.BossBarOverlay.NOTCHED_20, ExperienceBarWrapper.mapStyle("SEGMENTED_20"));
    }

    @Test
    void mapsSolidToProgress() {
        assertEquals(BossEvent.BossBarOverlay.PROGRESS, ExperienceBarWrapper.mapStyle("SOLID"));
    }

    @Test
    void unknownStyleFallsBackToNotched6() {
        assertEquals(BossEvent.BossBarOverlay.NOTCHED_6, ExperienceBarWrapper.mapStyle("zigzag"));
    }

    /**
     * The early-game boost's only visual cue: legacy painted the bar yellow while the boost applied,
     * overriding the skill's configured colour. Both directions, because "returns the configured
     * colour" is what an unwired override would also do.
     */
    @Test
    void theEarlyGameBoostOverridesTheConfiguredBarColor() {
        assertEquals(BossEvent.BossBarColor.YELLOW, ExperienceBarWrapper.resolveColor(true, "BLUE"),
                "while boosted the bar is yellow whatever the skill's colour is set to");
        assertEquals(BossEvent.BossBarColor.BLUE, ExperienceBarWrapper.resolveColor(false, "BLUE"),
                "otherwise the configured colour wins");
    }
}
