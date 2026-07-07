package com.gmail.nossr50.skills.archery;

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
 * Proves the Archery Skill Shot path (Phase 10.3) end-to-end against the real bundled configs.
 *
 * <p>With RetroMode on (the bundled default), {@code Skill Shot} unlocks at level 1 and its ranks
 * climb 1 → 100 → 150 → … → 500 (rank 10) → 1000 (rank 20) in {@code skillranks.yml}. The damage
 * bonus is {@code rank * RankDamageMultiplier(10.0) / 100}, capped so the total never exceeds
 * {@code oldDamage + MaxDamage(9.0)}. {@link ArcheryManager#skillShot(double)} always evaluates the
 * bonus (the singleplayer activation check is unconditionally true), so the numbers are
 * deterministic.
 */
class ArcheryManagerTest {

    private PlatformPlayer platformPlayer;
    private McMMOPlayer mmoPlayer;
    private ArcheryManager archeryManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000a5"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        archeryManager = new ArcheryManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private void atArcheryLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.ARCHERY)).thenReturn(level);
    }

    @Test
    void skillShotAndRetrievalLockedBelowRankOne() {
        atArcheryLevel(0);
        assertFalse(archeryManager.canSkillShot(), "rank 0 → Skill Shot locked");
        assertFalse(archeryManager.canRetrieveArrows(), "rank 0 → Arrow Retrieval locked");
    }

    @Test
    void skillShotAndRetrievalUnlockAtRankOne() {
        atArcheryLevel(1); // RetroMode Rank_1 = 1 for both subskills
        assertTrue(archeryManager.canSkillShot(), "rank 1 → Skill Shot unlocked");
        assertTrue(archeryManager.canRetrieveArrows(), "rank 1 → Arrow Retrieval unlocked");
    }

    @Test
    void skillShotAppliesRankScaledBonus() {
        atArcheryLevel(1); // rank 1 → 10% bonus
        assertEquals(11.0D, archeryManager.skillShot(10.0D), 1e-9, "rank 1 → +10% of 10");

        atArcheryLevel(100); // rank 2 → 20% bonus
        assertEquals(12.0D, archeryManager.skillShot(10.0D), 1e-9, "rank 2 → +20% of 10");
    }

    @Test
    void skillShotBonusIsCappedByMaxDamage() {
        atArcheryLevel(500); // rank 10 → 100% bonus = +10, but MaxDamage caps the bonus at 9
        assertEquals(19.0D, archeryManager.skillShot(10.0D), 1e-9, "bonus capped at oldDamage + 9");
    }

    @Test
    void skillShotIsNoOpBelowRankOne() {
        atArcheryLevel(0); // rank 0 → 0% bonus, damage unchanged
        assertEquals(10.0D, archeryManager.skillShot(10.0D), 1e-9, "rank 0 → no bonus");
    }
}
