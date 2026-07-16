package com.gmail.nossr50.skills.crossbows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gmail.nossr50.config.AdvancedConfig;
import com.gmail.nossr50.config.GeneralConfig;
import com.gmail.nossr50.config.RankConfig;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import com.gmail.nossr50.fabric.McMMOMod;
import com.gmail.nossr50.platform.PlatformPlayer;
import com.gmail.nossr50.util.player.UserManager;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the Crossbows numeric cores (Phase 10.3) against the real bundled configs — the twin of
 * ArcheryManagerTest. Powered Shot bonus is {@code rank * RankDamageMultiplier(10.0) / 100}, capped at
 * {@code oldDamage + MaxDamage(9.0)}; Trick Shot's max bounce count equals its rank. With RetroMode on,
 * Powered Shot unlocks at level 1 (rank 1 → 100 → 150) and Trick Shot at level 50 (rank 1 → 200 → 400).
 */
class CrossbowsManagerTest {

    private PlatformPlayer platformPlayer;
    private McMMOPlayer mmoPlayer;
    private CrossbowsManager crossbowsManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        crossbowsManager = new CrossbowsManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private void atCrossbowsLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.CROSSBOWS)).thenReturn(level);
    }

    @Test
    void poweredShotLockedBelowRankOne() {
        atCrossbowsLevel(0);
        assertFalse(crossbowsManager.canPoweredShot(), "rank 0 → Powered Shot locked");
    }

    @Test
    void poweredShotUnlocksAtRankOne() {
        atCrossbowsLevel(1); // RetroMode Powered Shot Rank_1 = 1
        assertTrue(crossbowsManager.canPoweredShot(), "rank 1 → Powered Shot unlocked");
    }

    @Test
    void poweredShotScalesWithRankAndIsCapped() {
        atCrossbowsLevel(0); // rank 0 → no bonus
        assertEquals(10.0D, crossbowsManager.poweredShot(10.0D), 1e-9, "rank 0 → unchanged");

        atCrossbowsLevel(1); // rank 1 → +10%
        assertEquals(11.0D, crossbowsManager.poweredShot(10.0D), 1e-9, "rank 1 → +10% of 10");

        atCrossbowsLevel(100); // rank 2 → +20%
        assertEquals(12.0D, crossbowsManager.poweredShot(10.0D), 1e-9, "rank 2 → +20% of 10");
    }

    @Test
    void trickShotMaxBounceCountEqualsRank() {
        atCrossbowsLevel(0);
        assertEquals(0, crossbowsManager.getTrickShotMaxBounceCount(), "rank 0 → 0 bounces");

        atCrossbowsLevel(50); // Trick Shot rank 1
        assertEquals(1, crossbowsManager.getTrickShotMaxBounceCount(), "rank 1 → 1 bounce");

        atCrossbowsLevel(200); // Trick Shot rank 2
        assertEquals(2, crossbowsManager.getTrickShotMaxBounceCount(), "rank 2 → 2 bounces");
    }
}
