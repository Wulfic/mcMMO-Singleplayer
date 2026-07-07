package com.gmail.nossr50.skills.axes;

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
 * Proves the Axes damage-math cores (Phase 10.3) against the real bundled configs. Axe Mastery bonus
 * is {@code rank * RankDamageMultiplier(1.0)}; Armor Impact durability damage is
 * {@code rank * DamagePerRank(6.5)}. With RetroMode on, Axe Mastery unlocks at level 50 (rank 1) and
 * Armor Impact at level 1 (rank 1), each reaching rank 2 at level 100 in {@code skillranks.yml}.
 */
class AxesManagerTest {

    private PlatformPlayer platformPlayer;
    private McMMOPlayer mmoPlayer;
    private AxesManager axesManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));
        McMMOMod.setAdvancedConfig(new AdvancedConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId())
                .thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000b3"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        axesManager = new AxesManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        McMMOMod.setAdvancedConfig(null);
        UserManager.clearAll();
    }

    private void atAxesLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.AXES)).thenReturn(level);
    }

    @Test
    void axeMasteryBonusScalesWithRank() {
        atAxesLevel(0); // rank 0 → 0 bonus
        assertEquals(0.0D, Axes.getAxeMasteryBonusDamage(platformPlayer), 1e-9, "rank 0 → 0");

        atAxesLevel(50); // rank 1 → 1 * 1.0 = 1.0
        assertEquals(1.0D, Axes.getAxeMasteryBonusDamage(platformPlayer), 1e-9, "rank 1 → 1.0");

        atAxesLevel(100); // rank 2 → 2 * 1.0 = 2.0
        assertEquals(2.0D, Axes.getAxeMasteryBonusDamage(platformPlayer), 1e-9, "rank 2 → 2.0");
    }

    @Test
    void impactDurabilityDamageScalesWithRank() {
        atAxesLevel(0); // Armor Impact rank 0 → 0
        assertEquals(0.0D, axesManager.getImpactDurabilityDamage(), 1e-9, "rank 0 → 0");

        atAxesLevel(1); // Armor Impact rank 1 → 1 * 6.5
        assertEquals(6.5D, axesManager.getImpactDurabilityDamage(), 1e-9, "rank 1 → 6.5");

        atAxesLevel(100); // Armor Impact rank 2 → 2 * 6.5
        assertEquals(13.0D, axesManager.getImpactDurabilityDamage(), 1e-9, "rank 2 → 13.0");
    }
}
