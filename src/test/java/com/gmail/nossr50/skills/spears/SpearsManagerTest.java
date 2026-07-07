package com.gmail.nossr50.skills.spears;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Proves the Spears numeric core (Phase 10.3). Spear Mastery bonus is
 * {@code rank * Rank_Damage_Multiplier(0.4)}; Momentum duration is {@code 20 * rank * 2}. With
 * RetroMode on, Spear Mastery unlocks at level 50 (rank 1) and reaches rank 2 at level 150 in
 * {@code skillranks.yml}.
 */
class SpearsManagerTest {

    private PlatformPlayer platformPlayer;
    private McMMOPlayer mmoPlayer;
    private SpearsManager spearsManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000b5"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        spearsManager = new SpearsManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private void atSpearsLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.SPEARS)).thenReturn(level);
    }

    @Test
    void spearMasteryBonusScalesWithRank() {
        atSpearsLevel(0); // rank 0 → 0
        assertEquals(0.0D, spearsManager.getSpearMasteryBonusDamage(), 1e-9, "rank 0 → 0");

        atSpearsLevel(50); // rank 1 → 1 * 0.4
        assertEquals(0.4D, spearsManager.getSpearMasteryBonusDamage(), 1e-9, "rank 1 → 0.4");

        atSpearsLevel(150); // rank 2 → 2 * 0.4
        assertEquals(0.8D, spearsManager.getSpearMasteryBonusDamage(), 1e-9, "rank 2 → 0.8");
    }

    @Test
    void momentumConstants() {
        assertEquals(40, SpearsManager.getMomentumTickDuration(1), "rank 1 → 20 * (1*2) = 40 ticks");
        assertEquals(80, SpearsManager.getMomentumTickDuration(2), "rank 2 → 20 * (2*2) = 80 ticks");
        assertEquals(2, SpearsManager.getMomentumStrength(), "Momentum strength is fixed at 2");
    }
}
