package com.gmail.nossr50.skills.archery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gmail.nossr50.config.experience.ExperienceConfig;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.MetadataStore;
import com.gmail.nossr50.skills.archery.Archery.FiredFrom;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies Archery's fired-from distance XP multiplier against the real bundled
 * {@code experience.yml} ({@code Experience_Values.Archery.Distance_Multiplier: 0.025}):
 * {@code 1 + min(distance, 50) * 0.025}.
 */
class ArcheryTest {

    private static final String OVERWORLD = "minecraft:overworld";
    /** The shipped Experience_Values.Archery.Distance_Multiplier. */
    private static final double PER_BLOCK = 0.025;

    @BeforeEach
    void loadConfig(@TempDir Path dir) {
        McMMOMod.setExperienceConfig(new ExperienceConfig(dir));
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setExperienceConfig(null);
        MetadataStore.clearAll(); // the launch marks below live on the static side-table.
    }

    /** Stand in for the launch stamp {@code ProjectileListener} sets on every player-fired arrow. */
    private static UUID arrowFiredFrom(String worldKey, double x, double y, double z) {
        final UUID arrowId = UUID.randomUUID();
        Archery.markFiredFrom(arrowId, new FiredFrom(worldKey, x, y, z));
        return arrowId;
    }

    @Test
    void distanceScalesTheMultiplierPerBlock() {
        // Fired from the origin, struck 20 blocks east: 1 + 20 * 0.025 = 1.5.
        final UUID arrow = arrowFiredFrom(OVERWORLD, 0, 0, 0);
        assertEquals(1 + (20 * PER_BLOCK),
                Archery.distanceXpBonusMultiplier(arrow, OVERWORLD, 20, 0, 0), 1e-9);
    }

    @Test
    void distanceIsMeasuredInThreeDimensions() {
        // A 3-4-5 triangle in the XZ plane, 12 up: sqrt(9 + 16 + 144) = 13.
        final UUID arrow = arrowFiredFrom(OVERWORLD, 0, 0, 0);
        assertEquals(1 + (13 * PER_BLOCK),
                Archery.distanceXpBonusMultiplier(arrow, OVERWORLD, 3, 12, 4), 1e-9);
    }

    @Test
    void aPointBlankShotEarnsNoBonus() {
        final UUID arrow = arrowFiredFrom(OVERWORLD, 10, 64, 10);
        assertEquals(1.0, Archery.distanceXpBonusMultiplier(arrow, OVERWORLD, 10, 64, 10), 1e-9);
    }

    @Test
    void theBonusIsCappedAtFiftyBlocks() {
        // Legacy's Math.min(distance, 50): a 200-block shot pays the same as a 50-block one.
        final UUID far = arrowFiredFrom(OVERWORLD, 0, 0, 0);
        final UUID atCap = arrowFiredFrom(OVERWORLD, 0, 0, 0);
        assertEquals(1 + (50 * PER_BLOCK),
                Archery.distanceXpBonusMultiplier(far, OVERWORLD, 200, 0, 0), 1e-9);
        assertEquals(Archery.distanceXpBonusMultiplier(atCap, OVERWORLD, 50, 0, 0),
                Archery.distanceXpBonusMultiplier(far, OVERWORLD, 200, 0, 0), 1e-9);
    }

    @Test
    void anUnmarkedArrowPaysTheFlatDefault() {
        // Legacy's "hacky fix" default: an arrow that never passed the launch hook (spawned and
        // adopted by another mod), or whose mark has aged out, multiplies by 1 rather than failing.
        assertEquals(1.0,
                Archery.distanceXpBonusMultiplier(UUID.randomUUID(), OVERWORLD, 500, 0, 0), 1e-9);
    }

    @Test
    void aCrossWorldHitPaysTheFlatDefault() {
        // Coordinates in different worlds are not comparable, so legacy bails to 1 rather than
        // measuring a nonsense distance (which here would be a big one, and thus a big bonus).
        final UUID arrow = arrowFiredFrom("minecraft:the_nether", 0, 0, 0);
        assertEquals(1.0, Archery.distanceXpBonusMultiplier(arrow, OVERWORLD, 40, 0, 0), 1e-9);
    }
}
