package com.gmail.nossr50.skills.maces;

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
 * Proves the Maces Crush damage core (Phase 10.3). Crush damage is {@code 0.5 + rank * 1.0} for
 * rank &gt; 0 (config-free), and the Cripple duration/strength are fixed constants. With RetroMode on,
 * Crush unlocks at level 100 (rank 1) and reaches rank 2 at level 250 in {@code skillranks.yml}.
 */
class MacesManagerTest {

    private PlatformPlayer platformPlayer;
    private McMMOPlayer mmoPlayer;
    private MacesManager macesManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000b4"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        macesManager = new MacesManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private void atMacesLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.MACES)).thenReturn(level);
    }

    @Test
    void crushDamageScalesWithRank() {
        atMacesLevel(0); // rank 0 → 0
        assertEquals(0.0D, macesManager.getCrushDamage(), 1e-9, "rank 0 → 0");

        atMacesLevel(100); // rank 1 → 0.5 + 1 = 1.5
        assertEquals(1.5D, macesManager.getCrushDamage(), 1e-9, "rank 1 → 1.5");

        atMacesLevel(250); // rank 2 → 0.5 + 2 = 2.5
        assertEquals(2.5D, macesManager.getCrushDamage(), 1e-9, "rank 2 → 2.5");
    }

    @Test
    void crippleConstantsDependOnTargetType() {
        assertEquals(20, MacesManager.getCrippleTickDuration(true), "player target → 20 ticks");
        assertEquals(30, MacesManager.getCrippleTickDuration(false), "mob target → 30 ticks");
        assertEquals(1, MacesManager.getCrippleStrength(true), "player target → strength 1");
        assertEquals(2, MacesManager.getCrippleStrength(false), "mob target → strength 2");
    }
}
