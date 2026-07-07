package com.gmail.nossr50.skills.tridents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
 * Proves the first leaf skill manager (Phase 10.2) end-to-end: {@link TridentsManager#impaleDamageBonus()}
 * reads the player's {@code Tridents.Impale} rank through {@link RankUtils} and turns it into the damage
 * multiplier. Uses the real bundled configs (RetroMode → Impale unlocks at 50/150/250 … ) and a mocked
 * {@link McMMOPlayer} tracked in {@link UserManager}, so no running server is needed.
 *
 * <p>Formula (legacy): rank &gt; 1 → {@code 1.0 + rank * 0.5}; rank == 1 → {@code 1.0}; rank 0 → {@code 0.0}.
 */
class TridentsManagerTest {

    private PlatformPlayer platformPlayer;
    private McMMOPlayer mmoPlayer;
    private TridentsManager tridentsManager;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        McMMOMod.setGeneralConfig(new GeneralConfig(dataFolder));
        McMMOMod.setRankConfig(new RankConfig(dataFolder));

        platformPlayer = mock(PlatformPlayer.class);
        when(platformPlayer.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000c3"));

        mmoPlayer = mock(McMMOPlayer.class);
        when(mmoPlayer.getPlayer()).thenReturn(platformPlayer);
        UserManager.track(mmoPlayer);

        tridentsManager = new TridentsManager(mmoPlayer);
    }

    @AfterEach
    void tearDown() {
        McMMOMod.setGeneralConfig(null);
        McMMOMod.setRankConfig(null);
        UserManager.clearAll();
    }

    private void atTridentsLevel(int level) {
        when(mmoPlayer.getSkillLevel(PrimarySkillType.TRIDENTS)).thenReturn(level);
    }

    @Test
    void noBonusBelowRankOne() {
        atTridentsLevel(49);
        assertEquals(0.0D, tridentsManager.impaleDamageBonus(), 1e-9, "rank 0 → no bonus");
    }

    @Test
    void rankOneGivesFlatBonus() {
        atTridentsLevel(50);
        assertEquals(1.0D, tridentsManager.impaleDamageBonus(), 1e-9, "rank 1 → 1.0");
    }

    @Test
    void higherRanksScaleWithRank() {
        atTridentsLevel(150); // rank 2
        assertEquals(2.0D, tridentsManager.impaleDamageBonus(), 1e-9, "rank 2 → 1.0 + 2*0.5");

        atTridentsLevel(250); // rank 3
        assertEquals(2.5D, tridentsManager.impaleDamageBonus(), 1e-9, "rank 3 → 1.0 + 3*0.5");
    }
}
