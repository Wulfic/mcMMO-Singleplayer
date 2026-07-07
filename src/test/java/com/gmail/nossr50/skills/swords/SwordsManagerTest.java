package com.gmail.nossr50.skills.swords;

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
 * Proves the Swords Stab damage path (Phase 10.3) against the real bundled configs. Stab damage is
 * {@code Base_Damage(1.0) + rank * Per_Rank_Multiplier(1.5)} for rank &gt; 0. With RetroMode on, Stab
 * unlocks at level 750 (rank 1) and reaches rank 2 at level 1000 in {@code skillranks.yml}.
 */
class SwordsManagerTest {

    private PlatformPlayer platformPlayer;
    private McMMOPlayer mmoPlayer;
    private SwordsManager swordsManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000b2"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        swordsManager = new SwordsManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private void atSwordsLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.SWORDS)).thenReturn(level);
    }

    @Test
    void stabLockedBelowRankOne() {
        atSwordsLevel(749);
        assertFalse(swordsManager.canUseStab(), "level 749 → Stab locked");
        assertEquals(0.0D, swordsManager.getStabDamage(), 1e-9, "rank 0 → no Stab damage");
    }

    @Test
    void stabDamageScalesWithRank() {
        atSwordsLevel(750); // rank 1 → 1.0 + 1 * 1.5 = 2.5
        assertTrue(swordsManager.canUseStab(), "level 750 → Stab unlocked");
        assertEquals(2.5D, swordsManager.getStabDamage(), 1e-9, "rank 1 → 2.5");

        atSwordsLevel(1000); // rank 2 → 1.0 + 2 * 1.5 = 4.0
        assertEquals(4.0D, swordsManager.getStabDamage(), 1e-9, "rank 2 → 4.0");
    }
}
